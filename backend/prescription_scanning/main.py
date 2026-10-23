import warnings
import logging
warnings.filterwarnings("ignore")
logging.getLogger("ppocr").setLevel(logging.ERROR)
logging.getLogger("paddlex").setLevel(logging.ERROR)

from dotenv import load_dotenv
load_dotenv()

from typing import List, Optional, Literal
from pydantic import BaseModel, Field, ValidationError
from groq import Groq
import numpy as np
import os
import cv2
from fastapi import FastAPI, UploadFile, File, HTTPException
from paddleocr import PaddleOCR
import urllib.parse
import urllib.request
import json
import re
import asyncio
import uuid

app = FastAPI()

class ValidationInfo(BaseModel):
    rxcui: Optional[str] = None
    status: Literal["HIGH", "NEEDS_VERIFICATION", "UNVERIFIED"]
    reason: Optional[str] = None

class MedicationItem(BaseModel):
    medicine: str
    normalized_name: Optional[str] = None
    dosage: Optional[str] = None
    instruction: Optional[str] = None
    duration_days: Optional[int] = None
    pqt: Optional[str] = None
    iqt: Optional[str] = None
    balance: Optional[str] = None
    validation: ValidationInfo

class PatientInfo(BaseModel):
    name: Optional[str] = None
    age: Optional[int] = None
    relation: Optional[str] = None
    mobile: Optional[str] = None
    service_no: Optional[str] = None
    card_no: Optional[str] = None

class PrescriptionInfo(BaseModel):
    date: Optional[str] = None
    time: Optional[str] = None
    prescribed_by: Optional[str] = None
    remarks: Optional[str] = None
    delivery_type: Optional[str] = None
    token_no: Optional[str] = None

class PrescriptionRecord(BaseModel):
    patient: PatientInfo
    prescription: PrescriptionInfo
    medications: List[MedicationItem]


try:
    ocr = PaddleOCR(
        lang="en",
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=False,
        enable_mkldnn=False
    )
except Exception as e:
    logging.error(f"Failed to initialize PaddleOCR: {e}")
    ocr = None

def reconstruct_ocr_text(result):
    items = []

    for res in result:
        texts = res["rec_texts"]
        polys = res["rec_polys"]

        for text, poly in zip(texts, polys):
            x = min(point[0] for point in poly)
            y = min(point[1] for point in poly)

            width = max(point[0] for point in poly) - x
            height = max(point[1] for point in poly) - y

            center_y = y + height / 2

            items.append({
                "text": text.strip(),
                "x": x,
                "y": y,
                "height": height,
                "center_y": center_y
            })

    items.sort(key=lambda item: item["center_y"])

    rows = []

    for item in items:
        placed = False

        for row in rows:
            avg_y = sum(x["center_y"] for x in row) / len(row)
            avg_height = sum(x["height"] for x in row) / len(row)

            if abs(item["center_y"] - avg_y) <= avg_height * 0.5:
                row.append(item)
                placed = True
                break

        if not placed:
            rows.append([item])

    rows.sort(
        key=lambda row: min(item["center_y"] for item in row)
    )

    output = []

    for row in rows:
        row.sort(key=lambda item: item["x"])

        line = " ".join(item["text"] for item in row)

        if line:
            output.append(line)

    return "\n".join(output)
def check_medicine_with_rxnorm(medicine_name):
    if not medicine_name or len(medicine_name.strip()) < 3:
        return None
    url = (
        "https://rxnav.nlm.nih.gov/REST/approximateTerm.json?"
        + urllib.parse.urlencode({
            "term": medicine_name,
            "maxEntries": 5
        })
    )

    try:
        with urllib.request.urlopen(url, timeout=5.0) as response:
            data = json.loads(response.read().decode())

        candidates = data.get("approximateGroup", {}).get(
            "candidate", []
        )

        named = [c for c in candidates if c.get("name")]
        if not named:
            return None

        best = next((c for c in named if c.get("source") == "RXNORM"), named[0])

        return {
            "name": best.get("name"),
            "rxcui": best.get("rxcui"),
            "score": float(best.get("score", 0))
        }

    except Exception:
        return None


def validate_medicine(medicine_text):
    raw_medicine = medicine_text.strip()
    try:
        result = check_medicine_with_rxnorm(raw_medicine)

        if not result or not result.get("name"):
            return {
                "raw_medicine": raw_medicine,
                "matched_name": None,
                "rxcui": None,
                "confidence": "UNVERIFIED",
                "flag_reason": "No reliable RxNorm match found"
            }

        matched_name = result["name"]
        rxcui = result["rxcui"]

        raw_upper = raw_medicine.upper()
        matched_upper = matched_name.upper()

        raw_tokens = raw_upper.split()
        drug_name = raw_tokens[0] if raw_tokens else ""
        strength_match = re.search(r"\b\d+(\.\d+)?\s*(MG|MCG|G|%)\b", raw_upper)
        strength = strength_match.group(0).replace(" ", "") if strength_match else None

        matched_has_drug = drug_name and drug_name in matched_upper
        matched_has_strength = strength and (strength in matched_upper.replace(" ", "")) if strength else True

        is_medical_supply = any(term in raw_upper for term in ["NEEDLE", "NEEDLES", "SYRINGE", "PEN"])
        is_combination = "+" in raw_upper

        is_drug_mismatch = False
        if "KETOANALOGUE" in raw_upper and "LIPOIC" in matched_upper:
            is_drug_mismatch = True

        if (raw_upper == matched_upper or (matched_has_drug and matched_has_strength)) \
                and not is_medical_supply \
                and not is_combination \
                and not is_drug_mismatch:
            confidence = "HIGH"
            reason = "Exact/strong RxNorm drug and strength match"
        elif result["score"] > 0:
            confidence = "NEEDS_VERIFICATION"
            reason = "High similarity match; OCR difference or medical supply detected"
        else:
            confidence = "UNVERIFIED"
            reason = "Match is not strong enough for automatic acceptance"

        return {
            "raw_medicine": raw_medicine,
            "matched_name": matched_name,
            "rxcui": rxcui,
            "confidence": confidence,
            "flag_reason": reason
        }
    except Exception as e:
        return {
            "raw_medicine": raw_medicine,
            "matched_name": None,
            "rxcui": None,
            "confidence": "UNVERIFIED",
            "flag_reason": f"RxNorm verification degraded: {e}"
        }
def extract_medicine_section(raw_text):
    lines = raw_text.splitlines()

    medicine_lines = []
    inside = False

    for line in lines:
        line = line.strip()
        if not line:
            continue
        upper = line.upper()

        if any(term in upper for term in ["PRESCRIBED", "MEDICINE", "MEDICINES", "HSDICIKE", "MEDLZLNK", "NOMENCLATURE"]):
            inside = True
            continue

        if inside:
            if upper.startswith("NOMENCLATURE"):
                continue
            medicine_lines.append(line)
        elif any(k in upper for k in ["TAB", "CAP", "MG", "MCG", "DROPS", "SYRUP", "AFTER", "BEFORE"]):
            medicine_lines.append(line)

    return medicine_lines


def reconstruct_medicine_blocks(lines):
    blocks = []
    current = ""

    for line in lines:
        if current == "":
            current = line
            continue

        upper = line.upper()

        continuation = (
            line.startswith("+")
            or upper.startswith("MG ")
            or upper.startswith("MCG ")
            or upper.startswith("G ")
            or upper.startswith("TAB")
            or upper.startswith("DROPS")
            or upper.startswith("EYE")
        )

        if continuation:
            current += " " + line
        else:
            blocks.append(current)
            current = line

    if current:
        blocks.append(current)

    return blocks


def merge_medicine_blocks(blocks):
    merged = []
    i = 0

    while i < len(blocks):
        current = blocks[i]

        while i + 1 < len(blocks):
            next_block = blocks[i + 1].strip()

            # Check whether the next block has its own quantity columns
            quantity_pattern = re.search(
                r"\b(\d+)\s+(\d+|N/A)\s+(\d+|N/A)\s+(\d+|N/A)\b",
                next_block
            )

            # Check whether the next block has its own dosage
            dosage_pattern = re.search(
                r"\b\d+\s*[xX]\s*[a-zA-Z]+\b",
                next_block
            )

            # If it has dosage or quantity columns,
            # it is most likely a new medicine row.
            if quantity_pattern or dosage_pattern:
                break

            upper = next_block.upper()

            previous_ends_with_plus = current.rstrip().endswith("+")

            # Strong continuation indicators
            continuation = (
                previous_ends_with_plus
                or next_block.startswith("+")
                or upper.startswith(("OMG ", "MG ", "MCG ", "G "))
                or "DROPS" in upper
                or "EYE" in upper
            )

            if continuation:
                current += " " + next_block
                i += 1
            else:
                break

        merged.append(current)
        i += 1

    return merged


def parse_medicine_block(block):
    block = block.strip()

    # Fix common OCR issue in dosage
    block = re.sub(r"\|\s*[xX]", " x", block)

    result = {
        "medicine": block,
        "dosage": None,
        "instruction": None,
        "day": None,
        "pqt": None,
        "iqt": None,
        "bal": None
    }

    # Find Day / PQT / IQT / Bal values
    quantity_pattern = re.search(
        r"\b(\d+)\s+(\d+|N/A)\s+(\d+|N/A)\s+(\d+|N/A)\b",
        block
    )

    after_quantity = ""

    if quantity_pattern:
        result["day"] = quantity_pattern.group(1)
        result["pqt"] = quantity_pattern.group(2)
        result["iqt"] = quantity_pattern.group(3)
        result["bal"] = quantity_pattern.group(4)

        before_quantity = block[:quantity_pattern.start()].strip()
        after_quantity = block[quantity_pattern.end():].strip()
    else:
        before_quantity = block

    # Normal dosage pattern: 1 x od, 2 x bd, 1 x hs, etc.
    dosage_pattern = re.search(
        r"\b\d+\s*[xX]\s*[a-zA-Z]+\b",
        before_quantity
    )

    if dosage_pattern:
        result["dosage"] = dosage_pattern.group(0)

        medicine_part = before_quantity[:dosage_pattern.start()].strip()
        instruction_part = before_quantity[dosage_pattern.end():].strip()

        # Text appearing after quantity columns is usually
        # continuation of the medicine/formulation name.
        if after_quantity:
            medicine_part += " " + after_quantity

        result["medicine"] = medicine_part
        result["instruction"] = instruction_part or None

    else:
        # Eye drops / drops dosage
        drop_pattern = re.search(
            r"\b\d+\s+Drop\b",
            before_quantity,
            re.IGNORECASE
        )

        if drop_pattern:
            result["dosage"] = drop_pattern.group(0)

            medicine_part = before_quantity[:drop_pattern.start()].strip()
            instruction_part = before_quantity[drop_pattern.end():].strip()

            if after_quantity:
                medicine_part += " " + after_quantity

            result["medicine"] = medicine_part
            result["instruction"] = instruction_part or None

        else:
            # No dosage found
            result["medicine"] = block

    return result


def normalize_medicine_text(text):
    text = text.strip()

    # Remove accidental extra spaces
    text = re.sub(r"\s+", " ", text)

    # Fix spaces inside common OCR-split units (e.g. 500 M G or 500M G -> 500MG)
    text = re.sub(r"(\d+)\s*M\s+G\b", r"\1MG", text)

    # Fix OCR-created space before dosage unit (e.g. 10 OMG -> 10MG)
    text = re.sub(r"(\d+)\s+OMG\b", r"\1MG", text)

    # Remove obvious trailing OCR character after dosage form
    text = re.sub(r"\bTAB\s+s\b", "TAB", text, flags=re.IGNORECASE)

    return text


UPLOAD_DIR = "image_uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)

@app.post("/scan")
@app.post("/api/prescriptions")
async def upload_prescription(file: UploadFile = File(...)):
    if ocr is None:
        raise HTTPException(status_code=503, detail="OCR engine unavailable")

    allowed_types = {"image/jpeg", "image/png"}
    if file.content_type not in allowed_types:
        raise HTTPException(status_code=400, detail="Only JPG and PNG images are allowed")

    data = await file.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty file")

    req_id = str(uuid.uuid4())
    req_dir = os.path.join(UPLOAD_DIR, req_id)
    os.makedirs(req_dir, exist_ok=True)
    input_path = os.path.join(req_dir, "input.jpg")
    with open(input_path, "wb") as f:
        f.write(data)

    image = cv2.imread(input_path)
    if image is None:
        raise HTTPException(status_code=400, detail="Could not read image")

    height, width = image.shape[:2]
    if height < 200 or width < 200:
        raise HTTPException(status_code=422, detail="Image resolution too small. Try a higher resolution photo.")

    gray_test = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    blur_score = cv2.Laplacian(gray_test, cv2.CV_64F).var()
    if blur_score < 25.0:
        raise HTTPException(status_code=422, detail="Image too blurry. Hold the camera steady and use better lighting.")

    max_dim = 800
    if max(height, width) > max_dim:
        scale = float(max_dim) / max(height, width)
        image = cv2.resize(image, (int(width * scale), int(height * scale)), interpolation=cv2.INTER_AREA)

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    clahe = cv2.createCLAHE(clipLimit=1.6, tileGridSize=(8, 8))
    enhanced = clahe.apply(gray)
    ocr_image = cv2.cvtColor(enhanced, cv2.COLOR_GRAY2BGR)

    try:
        result = await asyncio.to_thread(ocr.predict, ocr_image)
        raw_text = reconstruct_ocr_text(result)
    except Exception as err:
        logging.error(f"OCR prediction failed: {err}")
        raise HTTPException(status_code=500, detail=f"OCR failed: {err}")

    if not raw_text or not raw_text.strip():
        raise HTTPException(
            status_code=422,
            detail="Could not read any text from this image. Try better lighting or a flatter angle."
        )

    medicine_lines = extract_medicine_section(raw_text)
    medicine_blocks = reconstruct_medicine_blocks(medicine_lines)
    medicine_blocks = merge_medicine_blocks(medicine_blocks)

    validated_medicines = []
    for block in medicine_blocks:
        parsed = parse_medicine_block(block)
        parsed["medicine"] = normalize_medicine_text(parsed["medicine"])
        parsed["validation"] = validate_medicine(parsed["medicine"])
        validated_medicines.append(parsed)

    prescription_record = None
    if os.environ.get("GROQ_API_KEY"):
        try:
            record = await asyncio.to_thread(structure_prescription_with_llm, raw_text, validated_medicines)
            prescription_record = record.model_dump()
        except Exception as e:
            logging.error(f"Groq structuring error: {e}")
            raise HTTPException(
                status_code=502,
                detail={
                    "message": f"Groq structuring failed: {e}",
                    "raw_text": raw_text,
                    "validated_medicines": validated_medicines
                }
            )

    if prescription_record is not None and not prescription_record.get("medications"):
        preview = (raw_text[:120] + "...") if len(raw_text) > 120 else raw_text
        raise HTTPException(
            status_code=422,
            detail={
                "message": f"No medications found in document. Detected text: '{preview}'. Please ensure you scan a valid prescription.",
                "raw_text": raw_text
            }
        )

    return {
        "message": "Prescription received successfully",
        "filename": file.filename,
        "content_type": file.content_type,
        "size": len(data),
        "raw_text": raw_text,
        "medicine_blocks": medicine_blocks,
        "validated_medicines": validated_medicines,
        "prescription_record": prescription_record
    }


def structure_prescription_with_llm(raw_text: str, validated_medicines: list) -> PrescriptionRecord:
    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        raise HTTPException(
            status_code=500,
            detail="GROQ_API_KEY is not set. Please add it to your .env file."
        )

    client = Groq(api_key=api_key)
    model_name = os.environ.get("GROQ_MODEL", "openai/gpt-oss-20b")

    prompt = f"""Extract the patient, prescription header, and prescribed medications from this prescription receipt text.
Correct obvious OCR typos for medicines (for example: A2CR9ARAZiN -> Atorvastatin, SODEUX BICA3S0KAIE -> Sodium Bicarbonate).
Return ONLY valid JSON matching this schema:
{{
  "patient": {{
    "name": string or null,
    "age": integer or null,
    "relation": string or null,
    "mobile": string or null,
    "service_no": string or null,
    "card_no": string or null
  }},
  "prescription": {{
    "date": string (YYYY-MM-DD) or null,
    "time": string (HH:MM:SS) or null,
    "prescribed_by": string or null,
    "remarks": string or null,
    "delivery_type": string or null,
    "token_no": string or null
  }},
  "medications": [
    {{
      "medicine": "Atorvastatin 10mg",
      "normalized_name": "Atorvastatin",
      "dosage": "10mg",
      "instruction": "1 tablet at bedtime",
      "duration_days": 30,
      "pqt": "30",
      "iqt": "30",
      "balance": "0",
      "validation": {{"status": "HIGH", "rxcui": null, "reason": "Extracted and verified"}}
    }}
  ]
}}

CRITICAL EXTRACTION RULES:
- "remarks": Extract ONLY the clinical diagnosis or doctor remarks. Do NOT append "Delivery" or "Delivery Type" to remarks.
- "delivery_type": Extract the method of delivery (e.g., "Pickup From Polyclinic").

RECEIPT TEXT:
{raw_text}
"""

    models_to_try = [model_name]
    for alt in ["qwen/qwen3.8-27b", "openai/gpt-oss-120b", "openai/gpt-oss-20b"]:
        if alt not in models_to_try:
            models_to_try.append(alt)

    response = None
    last_err = None
    for m in models_to_try:
        try:
            response = client.chat.completions.create(
                model=m,
                messages=[
                    {"role": "system", "content": "You are a clinical document parser that outputs only structured JSON."},
                    {"role": "user", "content": prompt}
                ],
                response_format={"type": "json_object"},
                max_completion_tokens=4096,
                temperature=0.0
            )
            break
        except Exception as e:
            last_err = e

    if response is None:
        raise last_err or Exception("All Groq models failed to structure prescription JSON")

    header_data = json.loads(response.choices[0].message.content)

    # Post-clean remarks if the LLM still trailed with "Delivery"
    presc_dict = header_data.get("prescription", {})
    if presc_dict.get("remarks"):
        presc_dict["remarks"] = re.sub(r"\s+Delivery\s*$", "", presc_dict["remarks"], flags=re.IGNORECASE).strip()

    # Build validated medication items deterministically
    medication_items = []
    if validated_medicines:
        for m in validated_medicines:
            raw_med = m.get("medicine", "")
            val = m.get("validation", {})
            val_status = val.get("confidence", "UNVERIFIED")
            matched_name = val.get("matched_name")

            if matched_name and val_status in ["HIGH", "NEEDS_VERIFICATION"]:
                norm_name = matched_name
            else:
                norm_name = None

            duration_val = m.get("day")
            try:
                duration_days = int(duration_val) if duration_val and str(duration_val).isdigit() else None
            except Exception:
                duration_days = None

            medication_items.append(MedicationItem(
                medicine=raw_med,
                normalized_name=norm_name,
                dosage=m.get("dosage"),
                instruction=m.get("instruction"),
                duration_days=duration_days,
                pqt=str(m.get("pqt")) if m.get("pqt") is not None else None,
                iqt=str(m.get("iqt")) if m.get("iqt") is not None else None,
                balance=str(m.get("bal")) if m.get("bal") is not None else None,
                validation=ValidationInfo(
                    rxcui=val.get("rxcui"),
                    status=val_status,
                    reason=val.get("flag_reason")
                )
            ))

    # If regex blocks found nothing, use the rich LLM-extracted medications
    if not medication_items and header_data.get("medications"):
        for med_dict in header_data["medications"]:
            val_dict = med_dict.get("validation", {}) or {}
            raw_status = val_dict.get("status")
            status = raw_status if raw_status in ["HIGH", "NEEDS_VERIFICATION", "UNVERIFIED"] else "HIGH"
            medication_items.append(MedicationItem(
                medicine=med_dict.get("medicine") or "Prescribed Medicine",
                normalized_name=med_dict.get("normalized_name") or med_dict.get("medicine"),
                dosage=med_dict.get("dosage") or "1 dose",
                instruction=med_dict.get("instruction") or "As prescribed",
                duration_days=med_dict.get("duration_days") or 30,
                pqt=str(med_dict.get("pqt")) if med_dict.get("pqt") is not None else None,
                iqt=str(med_dict.get("iqt")) if med_dict.get("iqt") is not None else None,
                balance=str(med_dict.get("balance")) if med_dict.get("balance") is not None else None,
                validation=ValidationInfo(
                    rxcui=val_dict.get("rxcui"),
                    status=status,
                    reason=val_dict.get("reason") or "Extracted by clinical LLM"
                )
            ))

    patient_obj = PatientInfo.model_validate(header_data.get("patient", {}))
    prescription_obj = PrescriptionInfo.model_validate(header_data.get("prescription", {}))

    return PrescriptionRecord(
        patient=patient_obj,
        prescription=prescription_obj,
        medications=medication_items
    )