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
    status: str = "UNVERIFIED"
    reason: Optional[str] = None

class MedicationItem(BaseModel):
    medicine: str
    name: Optional[str] = None
    generic_name: Optional[str] = None
    normalized_name: Optional[str] = None
    strength: Optional[str] = None
    form: Optional[str] = None
    dose_per_intake: Optional[str] = None
    dosage: Optional[str] = None
    frequency: Optional[str] = None
    instruction: Optional[str] = None
    timing: Optional[str] = None
    duration_days: Optional[int] = None
    quantity: Optional[str] = None
    pqt: Optional[str] = None
    iqt: Optional[str] = None
    balance: Optional[str] = None
    is_medicine: bool = True
    category: Optional[str] = "medicine"
    confidence: float = 1.0
    source_line: Optional[str] = None
    verification_status: Optional[str] = "UNVERIFIED"
    validation: ValidationInfo
    warnings: List[str] = []

class SupplyItem(BaseModel):
    name: str
    category: str = "supply"
    quantity: Optional[str] = None
    source_line: Optional[str] = None

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

class MedicationExtractionItem(BaseModel):
    name: str
    generic_name: Optional[str] = None
    strength: Optional[str] = None
    form: Optional[str] = None
    dose_per_intake: Optional[str] = None
    dosage: Optional[str] = None
    frequency: Optional[str] = None
    instruction: Optional[str] = None
    timing: Optional[str] = None
    duration_days: Optional[int] = None
    quantity: Optional[str] = None
    pqt: Optional[str] = None
    iqt: Optional[str] = None
    balance: Optional[str] = None
    is_medicine: bool = True
    category: Optional[str] = "medicine"
    confidence: float = 1.0
    source_line: Optional[str] = None
    warnings: List[str] = []

class RawLLMExtraction(BaseModel):
    patient: Optional[PatientInfo] = None
    prescription: Optional[PrescriptionInfo] = None
    medications: List[MedicationExtractionItem] = []

class PrescriptionRecord(BaseModel):
    patient: PatientInfo
    prescription: PrescriptionInfo
    medications: List[MedicationItem]
    supplies: List[SupplyItem] = []


try:
    ocr = PaddleOCR(
        lang="en",
        use_doc_orientation_classify=False,
        use_doc_unwarping=False,
        use_textline_orientation=True,
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
    search_term = re.sub(r"\bFRUSEMIDE\b", "FUROSEMIDE", medicine_name.strip(), flags=re.IGNORECASE)
    url = (
        "https://rxnav.nlm.nih.gov/REST/approximateTerm.json?"
        + urllib.parse.urlencode({
            "term": search_term,
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
        raw_upper_norm = re.sub(r"\bFRUSEMIDE\b", "FUROSEMIDE", raw_upper)
        matched_upper = matched_name.upper()

        raw_tokens = raw_upper_norm.split()
        drug_name = raw_tokens[0] if raw_tokens else ""
        strength_match = re.search(r"\b\d+(\.\d+)?\s*(MG|MCG|G|%)\b", raw_upper_norm)
        strength = strength_match.group(0).replace(" ", "") if strength_match else None

        matched_has_drug = drug_name and drug_name in matched_upper
        matched_has_strength = strength and (strength in matched_upper.replace(" ", "")) if strength else True

        is_medical_supply = any(term in raw_upper for term in ["NEEDLE", "NEEDLES", "SYRINGE", "PEN"])
        is_combination = "+" in raw_upper

        is_drug_mismatch = False
        if "KETOANALOGUE" in raw_upper and "LIPOIC" in matched_upper:
            is_drug_mismatch = True

        if (raw_upper_norm == matched_upper or (matched_has_drug and matched_has_strength)) \
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
    if re.search(r"\bPRESCRIBED\s+MEDICINES?\b", raw_text, re.IGNORECASE):
        parts = re.split(r"\bPRESCRIBED\s+MEDICINES?\b", raw_text, maxsplit=1, flags=re.IGNORECASE)
        section_text = parts[1]
        lines = section_text.splitlines()
        inside = True
    else:
        lines = raw_text.splitlines()
        inside = False

    medicine_lines = []
    stop_keywords = [
        "DIAGNOSIS", "DIAGNOSES", "IMPRESSION", "REMARKS", "CLINICAL NOTES",
        "CHIEF COMPLAINTS", "CA BLADDER", "CKD", "SIGNATURE", "PRESCRIBED BY",
        "DELIVERY", "DISPENSED", "TOTAL", "GRAND TOTAL", "AMOUNT", "DATE:",
        "TIME:", "VISIT AGAIN", "FOLLOW UP", "ADVICE", "INSTRUCTIONS"
    ]

    for line in lines:
        line = line.strip()
        if not line:
            continue
        upper = line.upper()

        if inside:
            if any(term in upper for term in stop_keywords):
                break
            if upper.startswith("NOMENCLATURE") or re.search(r"\bNOMENCLATURE\b", upper):
                continue
            medicine_lines.append(line)
        else:
            if any(term in upper for term in ["PRESCRIBED", "MEDICINE", "MEDICINES"]):
                inside = True
                continue
            if any(k in upper for k in ["TAB", "CAP", "MG", "MCG", "DROPS", "SYRUP", "AFTER", "BEFORE"]):
                if not any(term in upper for term in stop_keywords):
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


def parse_dose_code(dosage_str: Optional[str], instruction_str: Optional[str] = ""):
    units_per_intake = None
    times_per_day = None
    freq_code = None
    timing_desc = None

    combined = f"{dosage_str or ''} {instruction_str or ''}".strip()
    drop_m = re.search(r"(\d+)\s+drop(?:s)?", combined, re.I)
    if drop_m:
        units_per_intake = int(drop_m.group(1))
        if "twice a day" in combined.lower() or "twice" in combined.lower():
            times_per_day = 2
            freq_code = "BD"
        elif "thrice" in combined.lower() or "three times" in combined.lower():
            times_per_day = 3
            freq_code = "TDS"
        else:
            times_per_day = 1
            freq_code = "OD"
    else:
        m = re.search(r"(\d+)\s*[xX]\s*([a-zA-Z]+)", dosage_str or "")
        if m:
            units_per_intake = int(m.group(1))
            code = m.group(2).lower()
            if code in ["od", "hs"]:
                times_per_day = 1
                freq_code = code.upper()
                if code == "hs":
                    timing_desc = "At bedtime"
            elif code in ["bd", "bid"]:
                times_per_day = 2
                freq_code = "BD"
            elif code in ["td", "tds", "tid"]:
                times_per_day = 3
                freq_code = "TDS"
            elif code in ["qid"]:
                times_per_day = 4
                freq_code = "QID"
            else:
                freq_code = code.upper()

    if re.search(r"\bLA\b", instruction_str or ""):
        timing_desc = (timing_desc + ", Local Application") if timing_desc else "Local Application"

    return {
        "units_per_intake": units_per_intake,
        "times_per_day": times_per_day,
        "freq_code": freq_code,
        "timing_desc": timing_desc
    }


def check_quantity(medicine_name: str, units_per_intake: Optional[int], times_per_day: Optional[int], days_val: Optional[int], pqt_str: Optional[str]) -> List[str]:
    warnings = []
    upper = medicine_name.upper()
    is_liquid_or_drop = any(term in upper for term in ["DROP", "DROPS", "SYRUP", "SUSPENSION", "SOLUTION", "ELIXIR", "OINTMENT", "GEL", "CREAM", "LOTION", "LA"])
    if is_liquid_or_drop:
        return warnings

    try:
        if pqt_str is not None and days_val is not None and units_per_intake is not None and times_per_day is not None:
            pqt = int(str(pqt_str).strip())
            days = int(days_val)
            expected_pqt = units_per_intake * times_per_day * days
            if pqt != expected_pqt:
                warnings.append("quantity doesn't match dose/frequency")
    except (ValueError, TypeError):
        pass

    return warnings


def parse_prescription_receipt_table(raw_text: str) -> Optional[RawLLMExtraction]:
    if not re.search(r"\bPRESCRIBED\s+MEDICINES?\b", raw_text, re.IGNORECASE):
        return None

    header_part, med_part = re.split(r"\bPRESCRIBED\s+MEDICINES?\b", raw_text, maxsplit=1, flags=re.IGNORECASE)

    patient = PatientInfo()
    name_m = re.search(r"Patient\s*Name\s*:\s*([^:\n]+?)(?=\s+Age|\s*$)", header_part, re.IGNORECASE)
    if name_m:
        patient.name = name_m.group(1).strip()
    age_m = re.search(r"Age\s*:\s*(\d+)", header_part, re.IGNORECASE)
    if age_m:
        patient.age = int(age_m.group(1))
    rel_m = re.search(r"Relation\s*:\s*([^:\n]+?)(?=\s+Service|\s*$)", header_part, re.IGNORECASE)
    if rel_m:
        patient.relation = rel_m.group(1).strip()
    mob_m = re.search(r"Mobile\s*(?:No)?\s*:\s*(\d+)", header_part, re.IGNORECASE)
    if mob_m:
        patient.mobile = mob_m.group(1).strip()
    serv_m = re.search(r"Service\s*(?:No)?\s*:\s*([^\s\n]+)", header_part, re.IGNORECASE)
    if serv_m:
        patient.service_no = serv_m.group(1).strip()
    card_m = re.search(r"Card\s*(?:No)?\s*:\s*([^\s\n]+)", header_part, re.IGNORECASE)
    if card_m:
        patient.card_no = card_m.group(1).strip()

    presc = PrescriptionInfo()
    date_m = re.search(r"OPD\s*Date\s*:\s*(\d{4}-\d{2}-\d{2})", header_part, re.IGNORECASE)
    if date_m:
        presc.date = date_m.group(1)
    time_m = re.search(r"OPD\s*Time\s*:\s*(\d{2}:\d{2}:\d{2})", header_part, re.IGNORECASE)
    if time_m:
        presc.time = time_m.group(1)
    doc_m = re.search(r"Prescribed\s*By\s*:\s*([^:\n]+?)(?=\s+Card|\s*$)", header_part, re.IGNORECASE)
    if doc_m:
        presc.prescribed_by = doc_m.group(1).strip()
    rem_m = re.search(r"OPD\s*ReMarks\s*:\s*([^:\n]+?)(?=\s+Delivery|\s*$)", header_part, re.IGNORECASE)
    if rem_m:
        presc.remarks = rem_m.group(1).strip()
    deliv_m = re.search(r"Type\s*:\s*([^\n]+)", header_part, re.IGNORECASE)
    if deliv_m:
        presc.delivery_type = deliv_m.group(1).strip()
    tok_m = re.search(r"Token\s*(?:No)?\s*:\s*([^\s\n]+)", header_part, re.IGNORECASE)
    if tok_m:
        presc.token_no = tok_m.group(1).strip()

    lines = [line.strip() for line in med_part.splitlines() if line.strip()]
    qty_regex = re.compile(r"\b(\d+)\s+(\d+|N/A)\s+(\d+|N/A)\s+(\d+|N/A)\s*$", re.IGNORECASE)
    dosage_regex = re.compile(r"\b(\d+\s*[xX]\s*[a-zA-Z]+|\d+\s+Drop(?:s)?(?:\s+twice\s+a\s+day)?)\b", re.IGNORECASE)

    raw_items: List[MedicationExtractionItem] = []
    current_row = None

    def _build_row_item(row: dict) -> MedicationExtractionItem:
        raw_nom = " ".join(row["nom_parts"])
        raw_nom = re.sub(r"\s+", " ", raw_nom).strip()
        raw_nom = re.sub(r"(\d+)\s*M\s+G\b", r"\1MG", raw_nom)
        raw_nom = re.sub(r"(\d+)\s+OMG\b", r"\1MG", raw_nom)
        raw_nom = re.sub(r"\bTAB\s+s\b", "TAB", raw_nom, flags=re.IGNORECASE)

        raw_inst = " ".join(row["instruction_parts"]).strip()

        dose_info = parse_dose_code(row["dosage"], raw_inst)
        units = dose_info["units_per_intake"]
        freq = dose_info["freq_code"]
        timing = dose_info["timing_desc"] or raw_inst

        supply_keywords = ["NEEDLE", "NEEDLES", "SYRINGE", "SYRINGES", "LANCET", "STRIP", "STRIPS"]
        is_sup = any(k in raw_nom.upper() for k in supply_keywords)

        form = "tablet"
        if "CAP" in raw_nom.upper():
            form = "capsule"
        elif "DROP" in raw_nom.upper():
            form = "drops"

        str_match = re.search(r"\b\d+(\.\d+)?\s*(?:MG|MCG|G|%W/V|%)\b", raw_nom, re.IGNORECASE)
        strength = str_match.group(0) if str_match else None

        day_val = int(row["day"]) if row["day"] and str(row["day"]).isdigit() else None

        return MedicationExtractionItem(
            name=raw_nom,
            strength=strength,
            form=form,
            dose_per_intake=f"{units} tablet" if units and form == "tablet" else (str(units) if units else row["dosage"]),
            dosage=row["dosage"],
            frequency=freq,
            instruction=raw_inst or None,
            timing=timing or None,
            duration_days=day_val,
            quantity=row["pqt"],
            pqt=row["pqt"],
            iqt=row["iqt"],
            balance=row["bal"],
            is_medicine=not is_sup,
            category="supply" if is_sup else "medicine",
            confidence=1.0,
            source_line=raw_nom
        )

    for line in lines:
        if re.search(r"\bNOMENCLATURE\b", line, re.IGNORECASE):
            continue

        qty_match = qty_regex.search(line)
        if qty_match:
            if current_row:
                raw_items.append(_build_row_item(current_row))

            day = qty_match.group(1)
            pqt = qty_match.group(2)
            iqt = qty_match.group(3)
            bal = qty_match.group(4)
            before_qty = line[:qty_match.start()].strip()

            dos_match = dosage_regex.search(before_qty)
            if dos_match:
                nom_part = before_qty[:dos_match.start()].strip()
                dos_part = dos_match.group(0).strip()
                inst_part = before_qty[dos_match.end():].strip()
            else:
                nom_part = before_qty
                dos_part = None
                inst_part = None

            current_row = {
                "nom_parts": [nom_part] if nom_part else [],
                "dosage": dos_part,
                "instruction_parts": [inst_part] if inst_part else [],
                "day": day,
                "pqt": pqt,
                "iqt": iqt,
                "bal": bal
            }
        else:
            if current_row:
                continuation_line = line
                freq_wrap_match = re.search(r"\b(twice\s+a\s+day|a\s+day|twice|daily|once\s+a\s+day)\b", continuation_line, re.IGNORECASE)
                if freq_wrap_match:
                    found_freq = freq_wrap_match.group(0)
                    current_row["instruction_parts"].append(found_freq)
                    continuation_line = (continuation_line[:freq_wrap_match.start()] + " " + continuation_line[freq_wrap_match.end():]).strip()

                if continuation_line:
                    current_row["nom_parts"].append(continuation_line)

    if current_row:
        raw_items.append(_build_row_item(current_row))

    return RawLLMExtraction(
        patient=patient,
        prescription=presc,
        medications=raw_items
    )


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

    max_dim = 1800
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

    _, encoded_jpg = cv2.imencode(".jpg", ocr_image)
    ocr_image_bytes = encoded_jpg.tobytes()

    try:
        record = await asyncio.to_thread(structure_prescription_with_llm, raw_text, None, ocr_image_bytes)
    except HTTPException:
        raise
    except Exception as e:
        med_lines = extract_medicine_section(raw_text)
        blocks = reconstruct_medicine_blocks(med_lines)
        validated = [validate_medicine(b) for b in blocks] if blocks else []
        logging.error(f"AI structuring error: {e}")
        raise HTTPException(
            status_code=502,
            detail={
                "message": f"AI structuring failed: {e}",
                "raw_text": raw_text,
                "validated_medicines": validated
            }
        )

    if not record.medications and not record.supplies:
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
        "medications": [m.model_dump() for m in record.medications],
        "supplies": [s.model_dump() for s in record.supplies],
        "patient": record.patient.model_dump() if record.patient else {},
        "prescription": record.prescription.model_dump() if record.prescription else {},
        "warnings": [],
        "prescription_record": record.model_dump()
    }

EXTRACTION_SYSTEM_PROMPT = """Extract the patient info, prescription details, prescribed medications, and medical supplies from this prescription receipt or doctor's order.
Only genuine medications should have is_medicine=true and category="medicine".
If the document contains 'PRESCRIBED MEDICINE', start parsing medications only after that header; everything above it (patient info, remarks, delivery type, diagnosis) is header data and must never be parsed as medicines.
In table-layout receipts (columns: Nomenclature | Dosage | Instruction | Day | PQT | IQT | Bal), medicine names may wrap across up to 5 lines where dosage appears only on the first line; merge wrapped lines into ONE medicine (multi-ingredient tablets joined with '+').
Parse dose codes: 'N x CODE' means N units per intake. od/hs=1/day, bd=2/day, td/tds/tid=3/day, qid=4/day. '1 Drop twice a day' = 1 drop, 2/day. 'LA' = local application. hs = at bedtime.
Medical supplies (such as pen needles, syringes, lancets, test strips, gauze, cotton) must have is_medicine=false and category="supply".
Keep combination and supplement tablets even if they are multi-ingredient formulations (status UNVERIFIED).
Diagnoses (such as DM2, HTN, CA BLADDER, CKD, GLAUCOMA), medical conditions, remarks, delivery instructions, and OCR noise (such as 'NO', 'SL NO', table headers) are NOT medicines.
Do not invent dosage, frequency, or duration if not printed; leave them null.
Fix obvious OCR typos in medicine names (e.g. A2CR9ARAZiN -> Atorvastatin, FRUSEMIDE -> Furosemide).
Normalise abbreviations (OD, BD, TDS, TID, QID, HS, SOS, AC, PC).
Return a JSON object matching the requested schema."""

def extract_prescription_with_gemini(image_bytes: bytes, raw_text: str) -> RawLLMExtraction:
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key or api_key == "your_gemini_api_key_here":
        raise ValueError("GEMINI_API_KEY is not configured")

    from google import genai
    from google.genai import types

    client = genai.Client(api_key=api_key)
    model_name = os.environ.get("GEMINI_MODEL", "gemini-3.8-flash")

    prompt = f"""{EXTRACTION_SYSTEM_PROMPT}

OCR RAW TEXT:
{raw_text}
"""
    contents = [
        types.Part.from_bytes(data=image_bytes, mime_type="image/jpeg"),
        prompt
    ]
    config = types.GenerateContentConfig(
        response_mime_type="application/json",
        response_schema=RawLLMExtraction,
        temperature=0.0
    )

    models_to_try = [model_name]
    for alt in ["gemini-3.8-flash", "gemini-3.6-flash", "gemini-2.5-flash"]:
        if alt not in models_to_try:
            models_to_try.append(alt)

    last_err = None
    for m in models_to_try:
        try:
            resp = client.models.generate_content(
                model=m,
                contents=contents,
                config=config
            )
            if resp and resp.text:
                return RawLLMExtraction.model_validate_json(resp.text)
        except Exception as e:
            last_err = e
            continue

    raise last_err or Exception("Gemini extraction failed")

def extract_prescription_with_groq(raw_text: str) -> RawLLMExtraction:
    api_key = os.environ.get("GROQ_API_KEY")
    if not api_key:
        raise ValueError("GROQ_API_KEY is not set")

    client = Groq(api_key=api_key)
    model_name = os.environ.get("GROQ_MODEL", "openai/gpt-oss-20b")

    prompt = f"""{EXTRACTION_SYSTEM_PROMPT}

Return a valid JSON object matching this schema:
{{
  "patient": {{"name": null, "age": null, "relation": null, "mobile": null}},
  "prescription": {{"date": null, "time": null, "prescribed_by": null, "remarks": null, "delivery_type": null, "token_no": null}},
  "medications": [
    {{
      "name": "Medication Name",
      "generic_name": null,
      "strength": null,
      "form": "tablet|capsule|syrup|drops|injection|other",
      "dose_per_intake": null,
      "dosage": null,
      "frequency": "OD|BD|TDS|QID|HS|SOS|other",
      "instruction": null,
      "timing": "Before food|After food|Bedtime",
      "duration_days": null,
      "quantity": null,
      "pqt": null,
      "iqt": null,
      "balance": null,
      "is_medicine": true,
      "category": "medicine",
      "confidence": 1.0,
      "source_line": null
    }}
  ]
}}

OCR RAW TEXT:
{raw_text}
"""
    models_to_try = [model_name]
    for alt in ["openai/gpt-oss-20b", "qwen/qwen3.8-27b", "openai/gpt-oss-120b"]:
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
            if response and response.choices and response.choices[0].message.content:
                data = json.loads(response.choices[0].message.content)
                return RawLLMExtraction.model_validate(data)
        except Exception as e:
            last_err = e

    raise last_err or Exception("All Groq models failed to structure prescription JSON")

def process_raw_extraction(raw_data: RawLLMExtraction) -> PrescriptionRecord:
    supplies: List[SupplyItem] = []
    med_candidates: List[MedicationExtractionItem] = []

    supply_keywords = ["NEEDLE", "NEEDLES", "SYRINGE", "SYRINGES", "LANCET", "STRIP", "STRIPS", "DRESSING", "GAUZE", "COTTON", "BANDAGE", "CATHETER"]
    diagnosis_keywords = ["CA BLADDER", "CKD", "GLAUCOMA", "CARCINOMA", "DIAGNOSIS", "DIAGNOSES", "DM2", "HTN", "HYPERTENSION", "DIABETES"]
    noise_keywords = ["NO", "YES", "NA", "N/A", "NIL", "NONE", "SL NO", "ITEM", "TOTAL", "DELIVERY"]

    for item in raw_data.medications:
        name = (item.name or "").strip()
        if not name or len(name) < 3:
            continue

        upper_name = name.upper()
        if upper_name in noise_keywords or re.search(r"\bDelivery\b", name, re.IGNORECASE):
            continue

        if any(diag in upper_name for diag in diagnosis_keywords):
            continue

        is_supply = (
            item.is_medicine is False
            or (item.category and item.category.lower() == "supply")
            or any(s in upper_name for s in supply_keywords)
        )

        if is_supply:
            supplies.append(SupplyItem(
                name=name,
                category="supply",
                quantity=item.quantity or item.pqt or item.dose_per_intake,
                source_line=item.source_line or name
            ))
            continue

        if item.confidence < 0.4:
            continue

        med_candidates.append(item)

    def _verify_single(m: MedicationExtractionItem) -> MedicationItem:
        search_term = m.generic_name or m.name
        val_res = validate_medicine(search_term)
        is_verified = val_res.get("confidence") == "HIGH"
        verification_status = "VERIFIED" if is_verified else "UNVERIFIED"

        validation_obj = ValidationInfo(
            rxcui=val_res.get("rxcui"),
            status=verification_status,
            reason=val_res.get("flag_reason") or ("RxNorm verified" if is_verified else "Unverified")
        )

        norm_name = val_res.get("matched_name") if is_verified else m.name
        dosage_val = m.dosage or m.dose_per_intake or m.strength
        instruction_val = m.instruction or m.timing or m.frequency

        parsed_dose = parse_dose_code(dosage_val, instruction_val)
        units = parsed_dose["units_per_intake"]
        tpd = parsed_dose["times_per_day"]
        freq_parsed = parsed_dose["freq_code"] or m.frequency
        timing_parsed = parsed_dose["timing_desc"] or m.timing

        dose_val = m.dose_per_intake or (f"{units} tablet" if units and (m.form == "tablet" or "TAB" in (m.name or "").upper()) else (str(units) if units else dosage_val))
        freq_val = freq_parsed or m.frequency
        timing_val = timing_parsed or instruction_val
        duration = m.duration_days
        pqt_val = m.pqt or m.quantity

        qty_warnings = check_quantity(m.name, units, tpd, duration, pqt_val)

        return MedicationItem(
            medicine=m.name,
            name=m.name,
            generic_name=m.generic_name,
            normalized_name=norm_name,
            strength=m.strength,
            form=m.form,
            dose_per_intake=dose_val,
            dosage=dosage_val,
            frequency=freq_val,
            instruction=instruction_val,
            timing=timing_val,
            duration_days=duration,
            quantity=pqt_val,
            pqt=pqt_val,
            iqt=m.iqt,
            balance=m.balance,
            is_medicine=True,
            category="medicine",
            confidence=m.confidence,
            source_line=m.source_line or m.name,
            verification_status=verification_status,
            validation=validation_obj,
            warnings=qty_warnings
        )

    from concurrent.futures import ThreadPoolExecutor
    with ThreadPoolExecutor(max_workers=8) as executor:
        medication_items = list(executor.map(_verify_single, med_candidates))

    patient_obj = raw_data.patient or PatientInfo()
    prescription_obj = raw_data.prescription or PrescriptionInfo()

    if prescription_obj.remarks:
        prescription_obj.remarks = re.sub(r"\s+Delivery\s*$", "", prescription_obj.remarks, flags=re.IGNORECASE).strip()

    return PrescriptionRecord(
        patient=patient_obj,
        prescription=prescription_obj,
        medications=medication_items,
        supplies=supplies
    )

def structure_prescription_with_llm(
    raw_text: str,
    validated_medicines: list = None,
    image_bytes: bytes = None
) -> PrescriptionRecord:
    raw_extraction: Optional[RawLLMExtraction] = None
    extraction_err = None

    if image_bytes and os.environ.get("GEMINI_API_KEY"):
        try:
            raw_extraction = extract_prescription_with_gemini(image_bytes, raw_text)
        except Exception as e:
            extraction_err = e

    if raw_extraction is None and os.environ.get("GROQ_API_KEY"):
        try:
            raw_extraction = extract_prescription_with_groq(raw_text)
        except Exception as e:
            extraction_err = e

    if raw_extraction is None and re.search(r"\bPRESCRIBED\s+MEDICINES?\b", raw_text, re.IGNORECASE):
        raw_extraction = parse_prescription_receipt_table(raw_text)

    if raw_extraction is not None:
        return process_raw_extraction(raw_extraction)

    if validated_medicines:
        items = []
        for m in validated_medicines:
            raw_med = m.get("medicine", "").strip()
            if not raw_med or len(raw_med) < 3 or raw_med.upper() in ["NO", "NA"]:
                continue
            val = m.get("validation", {})
            status = "VERIFIED" if val.get("confidence") == "HIGH" else "UNVERIFIED"
            items.append(MedicationItem(
                medicine=raw_med,
                name=raw_med,
                dosage=m.get("dosage"),
                instruction=m.get("instruction"),
                duration_days=int(m.get("day")) if m.get("day") and str(m.get("day")).isdigit() else None,
                pqt=str(m.get("pqt")) if m.get("pqt") is not None else None,
                iqt=str(m.get("iqt")) if m.get("iqt") is not None else None,
                balance=str(m.get("bal")) if m.get("bal") is not None else None,
                verification_status=status,
                validation=ValidationInfo(
                    rxcui=val.get("rxcui"),
                    status=status,
                    reason=val.get("flag_reason")
                )
            ))
        if items:
            return PrescriptionRecord(
                patient=PatientInfo(),
                prescription=PrescriptionInfo(),
                medications=items,
                supplies=[]
            )

    raise HTTPException(
        status_code=502,
        detail={
            "message": f"AI structuring failed: {extraction_err}",
            "raw_text": raw_text,
            "validated_medicines": validated_medicines or []
        }
    )