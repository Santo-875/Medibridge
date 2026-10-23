import os
import sys
import shutil
import uuid
import json
import logging
import asyncio
from pathlib import Path
from typing import Optional, List, Literal
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from dotenv import load_dotenv

# Ensure root directory and backend directory are in sys.path
_current_dir = Path(__file__).resolve().parent
_root_dir = _current_dir.parent
for _p in [_root_dir, _current_dir]:
    if str(_p) not in sys.path:
        sys.path.insert(0, str(_p))

# ── Load environment variables from .env at startup ───────────────────────────
_env_path = _current_dir / ".env"
if _env_path.exists():
    load_dotenv(dotenv_path=_env_path)
    logging.info(f"[MediBridge] Loaded environment from {_env_path}")
else:
    load_dotenv()

# Database & Routers
from database import init_db, get_db
from models import Prescription, Recording, utcnow
from auth_router import router as auth_router
from sync_router import router as sync_router
from care_router import router as care_router
from profile_router import router as profile_router

# Initialize database schema tables on startup
init_db()

# ── Startup API Key Validation (fail loudly, never silently 500) ──────────────
_REQUIRED_KEYS = {
    "GEMINI_API_KEY": "Google Gemini (clinical summarization, chatbot)",
    "SARVAM_API_KEY": "Sarvam AI (Tamil STT + translation)",
    "GROQ_API_KEY":   "Groq (fast OCR LLM structuring)",
}

_missing_keys: list[str] = []
for _key, _desc in _REQUIRED_KEYS.items():
    _val = os.getenv(_key, "")
    if not _val or _val.startswith("your_") or len(_val) < 10:
        _missing_keys.append(_key)
    else:
        logging.info(f"[MediBridge] ✅  {_key} loaded ({_desc})")

if _missing_keys:
    logging.warning(
        f"[MediBridge] ⚠️  {len(_missing_keys)} API key(s) missing: {', '.join(_missing_keys)}. "
        "Affected features will use fallback clinical data. "
        "Set keys in backend/.env to enable live AI features."
    )

import concurrent.futures
_ocr_executor = concurrent.futures.ThreadPoolExecutor(max_workers=2)

try:
    from prescription_scanning.main import (
        ocr, reconstruct_ocr_text, extract_medicine_section,
        reconstruct_medicine_blocks, merge_medicine_blocks,
        parse_medicine_block, normalize_medicine_text,
        validate_medicine, structure_prescription_with_llm
    )
    logging.info("[MediBridge] ✅ Prescription scanning OCR model initialized and ready.")
except Exception as _e:
    logging.warning(f"[MediBridge] Could not preload OCR model: {_e}")
else:
    logging.info("[MediBridge] ✅  All API keys configured — full AI pipeline active.")

# Initialize main combined FastAPI app
app = FastAPI(
    title="MediBridge Unified Backend",
    description="Unified API providing Prescription/Bill Scanning, Speech-to-Text, Sync & Care Links",
    version="2.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

import numpy as np

ocr_ready = False

async def _warmup_ocr():
    global ocr_ready
    try:
        logging.info("[MediBridge] OCR warmup starting...")
        dummy = np.zeros((64, 64, 3), dtype=np.uint8)
        if ocr is not None:
            await asyncio.to_thread(ocr.predict, dummy)
        ocr_ready = True
        logging.info("[MediBridge] OCR warmup finished. Model is ready.")
    except Exception as e:
        logging.warning(f"[MediBridge] OCR warmup failed: {e}")
        ocr_ready = True

@app.on_event("startup")
async def startup_warmup():
    asyncio.create_task(_warmup_ocr())

@app.get("/api/health")
def get_health():
    return {"ocr_ready": ocr_ready}

app.include_router(auth_router)
app.include_router(profile_router)
app.include_router(sync_router)
app.include_router(care_router)

UPLOAD_DIR = Path("image_uploads")
UPLOAD_DIR.mkdir(exist_ok=True)
AUDIO_DIR = Path("audio_uploads")
AUDIO_DIR.mkdir(exist_ok=True)

# ── Prescription Models ───────────────────────────────────────────────────────
class ValidationInfo(BaseModel):
    rxcui: Optional[str] = None
    status: Literal["HIGH", "NEEDS_VERIFICATION", "UNVERIFIED"] = "HIGH"
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

# ── Health Check ─────────────────────────────────────────────────────────────
@app.get("/")
def read_root():
    return {
        "status": "online",
        "service": "MediBridge Unified Backend",
        "endpoints": [
            "/profiles",
            "/sync/pull",
            "/sync/push",
            "/care/invite",
            "/care/accept",
            "/care/revoke",
            "/devices",
            "/api/prescriptions",
            "/api/speech/upload",
            "/api/billing/pdf"
        ]
    }

# ── Module A: Prescription & Pharmacy Bill Scanning ───────────────────────────
@app.post("/scan")
@app.post("/api/prescriptions")
async def upload_prescription(
    file: UploadFile = File(...),
    profile_id: Optional[str] = Form(None)
):
    if ocr is None:
        raise HTTPException(status_code=503, detail="OCR engine unavailable")

    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="Uploaded file is empty")

    file_id = str(uuid.uuid4())
    req_dir = UPLOAD_DIR / file_id
    req_dir.mkdir(parents=True, exist_ok=True)
    ext = Path(file.filename or "scan.jpg").suffix or ".jpg"
    saved_path = req_dir / f"input{ext}"

    with open(saved_path, "wb") as buffer:
        buffer.write(file_bytes)

    import cv2
    import numpy as np

    image = cv2.imread(str(saved_path))
    if image is None:
        raise HTTPException(status_code=400, detail="Could not decode image for OCR processing.")

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

    med_lines = extract_medicine_section(raw_text)
    blocks = reconstruct_medicine_blocks(med_lines)
    blocks = merge_medicine_blocks(blocks)

    def _process_single_block(b):
        p = parse_medicine_block(b)
        p["medicine"] = normalize_medicine_text(p["medicine"])
        p["validation"] = validate_medicine(p["medicine"])
        return p

    validated = await asyncio.to_thread(lambda: [_process_single_block(b) for b in blocks])

    record = None
    if os.environ.get("GROQ_API_KEY"):
        try:
            record = await asyncio.to_thread(structure_prescription_with_llm, raw_text, validated)
        except Exception as llm_err:
            logging.error(f"Groq structuring error: {llm_err}")
            raise HTTPException(
                status_code=502,
                detail={
                    "message": f"Groq structuring failed: {llm_err}",
                    "raw_text": raw_text,
                    "validated_medicines": validated
                }
            )
    else:
        medication_items = []
        for m in validated:
            raw_med = m.get("medicine", "")
            val = m.get("validation", {})
            medication_items.append(MedicationItem(
                medicine=raw_med,
                normalized_name=val.get("matched_name") or raw_med,
                dosage=m.get("dosage"),
                instruction=m.get("instruction"),
                duration_days=30,
                pqt=str(m.get("pqt")) if m.get("pqt") is not None else None,
                iqt=str(m.get("iqt")) if m.get("iqt") is not None else None,
                balance=str(m.get("bal")) if m.get("bal") is not None else None,
                validation=ValidationInfo(
                    rxcui=val.get("rxcui"),
                    status=val.get("confidence", "UNVERIFIED"),
                    reason=val.get("flag_reason")
                )
            ))
        record = PrescriptionRecord(
            patient=PatientInfo(name=None, age=None, relation=None, mobile=None),
            prescription=PrescriptionInfo(
                date=utcnow().strftime("%Y-%m-%d"),
                time=utcnow().strftime("%H:%M:%S"),
                prescribed_by=None
            ),
            medications=medication_items
        )

    if not record or not record.medications:
        preview = (raw_text[:120] + "...") if len(raw_text) > 120 else raw_text
        raise HTTPException(
            status_code=422,
            detail={
                "message": f"No medications found in document. Detected text: '{preview}'. Please ensure you scan a valid prescription.",
                "raw_text": raw_text
            }
        )

    db = next(get_db())
    try:
        db_presc = Prescription(
            id=file_id,
            profile_id=profile_id,
            file_path=str(saved_path),
            ocr_raw_text=raw_text,
            structured_json=record.model_dump_json() if hasattr(record, "model_dump_json") else str(record),
            prescribed_by=getattr(getattr(record, "prescription", None), "prescribed_by", None),
            date=getattr(getattr(record, "prescription", None), "date", None),
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(db_presc)
        db.commit()
    finally:
        db.close()

    return {
        "id": file_id,
        "message": "Prescription analyzed successfully",
        "filename": file.filename,
        "raw_text": raw_text,
        "prescription_record": record.model_dump()
    }

@app.post("/api/billing/pdf")
async def process_billing_pdf(file: UploadFile = File(...), profile_id: Optional[str] = Form(None)):
    return await upload_prescription(file, profile_id=profile_id)

# ── Speech-to-Text & Medical Summary ─────────────────────────────────────────
@app.post("/api/speech/upload")
async def upload_speech(
    file: UploadFile = File(...),
    profile_id: Optional[str] = Form(None)
):
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="Empty audio file")

    file_id = str(uuid.uuid4())
    ext = Path(file.filename or "recording.m4a").suffix or ".m4a"
    file_path = AUDIO_DIR / f"{file_id}_{ext}"
    
    with open(file_path, "wb") as buffer:
        buffer.write(file_bytes)

    try:
        from speech_to_text.sarvam_stt import transcribe_and_translate_audio
        tamil_text, english_text = transcribe_and_translate_audio(file_path)
    except Exception as e:
        logging.error(f"[MediBridge] Speech-to-text failed: {e}", exc_info=True)
        raise HTTPException(
            status_code=502,
            detail={"code": "STT_FAILED", "message": f"Audio transcription failed: {str(e)}"}
        )

    combined_transcript = f"Tamil: {tamil_text}\n\nEnglish: {english_text}"

    from speech_to_text.llm_summary import generate_medical_summary
    try:
        summary = generate_medical_summary(english_text)
        if summary and not summary.get("summary_failed"):
            summary["doctor_notes_summary"] = f"Tamil: {tamil_text}\nEnglish: {english_text}\n" + (summary.get("doctor_notes_summary") or "")
    except Exception as e:
        logging.error(f"[MediBridge] Gemini summary generation failed: {e}", exc_info=True)
        summary = {
            "summary_failed": True,
            "error": str(e),
            "raw_transcript": english_text,
            "doctor_notes_summary": f"Tamil: {tamil_text}\nEnglish: {english_text}",
            "chief_complaints": [],
            "symptoms": [],
            "diagnosis": None,
            "medications": [],
            "advice_and_precautions": [],
            "follow_up": None
        }

    # Store recording row in DB
    db = next(get_db())
    try:
        db_rec = Recording(
            id=file_id,
            profile_id=profile_id,
            file_path=str(file_path),
            transcript_tamil=tamil_text,
            transcript_english=english_text,
            summary_json=json.dumps(summary) if isinstance(summary, dict) else str(summary),
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(db_rec)
        db.commit()
    finally:
        db.close()

    return {
        "id": file_id,
        "filename": file.filename,
        "status": "COMPLETED",
        "transcript": combined_transcript,
        "transcript_tamil": tamil_text,
        "transcript_english": english_text,
        "summary": summary
    }

class ChatRequest(BaseModel):
    message: str
    patient_context: Optional[str] = None

class ChatResponse(BaseModel):
    reply: str
    source: str

@app.post("/api/chat", response_model=ChatResponse)
async def chat_with_medi_ai(request: ChatRequest):
    msg = request.message.strip()
    logging.info(f"[CHAT] Received request: '{msg[:60]}...'")
    if not msg:
        raise HTTPException(status_code=400, detail="Message cannot be empty")

    prompt = f"""You are Medi, an intelligent, empathetic clinical medication and healthcare assistant inside the MediBridge app.
Provide accurate, compassionate, and medically sound guidance to the patient.

GUIDELINES:
1. Directly and helpfully answer the patient's question in a clear, friendly, conversational tone.
2. If the patient asks about their medications, schedule, or safety, reference their patient context below.
3. If they ask general medical or healthcare questions (e.g. headaches, fever, diet, sleep, wound care, cold, hydration), explain clearly and concisely with practical advice.
4. If they describe red-flag emergency symptoms (severe chest pain, sudden numbness/paralysis, difficulty breathing, severe bleeding), immediately urge emergency care (call emergency services or visit the nearest ER).
5. Format your response cleanly using markdown (bold headings, bullet points •) for readability on mobile screens. Keep paragraphs crisp and easy to scan.

--- PATIENT REGIMEN CONTEXT ---
{request.patient_context or "No active prescription medications registered."}

--- PATIENT QUESTION ---
{msg}
"""

    gemini_key = os.getenv("GEMINI_API_KEY")
    if gemini_key and gemini_key != "your_gemini_api_key_here":
        try:
            from google import genai
            client = genai.Client(api_key=gemini_key)
            for gmodel in ["gemini-3.6-flash", "gemini-3.8-flash"]:
                try:
                    resp = await asyncio.to_thread(
                        client.models.generate_content,
                        model=gmodel,
                        contents=prompt
                    )
                    if resp and resp.text and resp.text.strip():
                        return ChatResponse(reply=resp.text.strip(), source="gemini")
                except Exception:
                    continue
        except Exception as e:
            logging.warning(f"Gemini chat failed: {e}")

    groq_key = os.getenv("GROQ_API_KEY")
    if groq_key:
        try:
            from groq import Groq
            groq_client = Groq(api_key=groq_key)
            models = ["openai/gpt-oss-20b", "qwen/qwen3.8-27b", "openai/gpt-oss-120b"]
            for m in models:
                try:
                    def _call_groq(model_name):
                        return groq_client.chat.completions.create(
                            model=model_name,
                            messages=[
                                {"role": "system", "content": "You are Medi, a compassionate clinical healthcare and medication AI assistant."},
                                {"role": "user", "content": prompt}
                            ],
                            temperature=0.3,
                            max_completion_tokens=1024
                        )
                    completion = await asyncio.to_thread(_call_groq, m)
                    content = completion.choices[0].message.content
                    if content and content.strip():
                        return ChatResponse(reply=content.strip(), source="groq")
                except Exception:
                    continue
        except Exception as e:
            logging.warning(f"Groq chat failed: {e}")

    raise HTTPException(status_code=502, detail="AI services are temporarily unavailable. Please try again shortly.")

class SafetyAnalyzeRequest(BaseModel):
    candidateMedicine: Optional[dict] = None
    findings: list[dict] = []
    crossVerified: bool = False
    reviewRecommended: bool = False

class SafetyAnalyzeResponse(BaseModel):
    flagged: bool
    severity: str
    title: str
    message: str
    recommendation: str

@app.post("/api/safety/analyze", response_model=SafetyAnalyzeResponse)
async def analyze_safety(request: SafetyAnalyzeRequest):
    prompt = f"""You are a clinical communication assistant in MediBridge AI.
The factual safety engine has detected the following structured medication safety findings:
{request.model_dump_json()}

Task:
Provide a polite, professional, and patient-understandable explanation.
Do NOT prescribe, change dosage, or diagnose. Do NOT invent new findings.
Return ONLY valid JSON matching this schema:
{{"flagged": boolean, "severity": "LOW"|"MODERATE"|"HIGH", "title": string, "message": string, "recommendation": string}}
"""
    gemini_key = os.getenv("GEMINI_API_KEY")
    if gemini_key and gemini_key != "your_gemini_api_key_here":
        try:
            from google import genai
            client = genai.Client(api_key=gemini_key)
            for gmodel in ["gemini-3.6-flash", "gemini-3.8-flash"]:
                try:
                    resp = await asyncio.to_thread(
                        client.models.generate_content,
                        model=gmodel,
                        contents=prompt
                    )
                    if resp and resp.text:
                        raw = resp.text.strip().replace("```json", "").replace("```", "").strip()
                        parsed = json.loads(raw)
                        return SafetyAnalyzeResponse(**parsed)
                except Exception:
                    continue
        except Exception as e:
            logging.warning(f"Gemini safety analysis failed: {e}")

    groq_key = os.getenv("GROQ_API_KEY")
    if groq_key:
        try:
            from groq import Groq
            groq_client = Groq(api_key=groq_key)
            models = ["openai/gpt-oss-20b", "qwen/qwen3.8-27b", "openai/gpt-oss-120b"]
            for m in models:
                try:
                    def _call_groq(model_name):
                        return groq_client.chat.completions.create(
                            model=model_name,
                            messages=[
                                {"role": "system", "content": "You are a clinical communication safety assistant. Output only raw JSON."},
                                {"role": "user", "content": prompt}
                            ],
                            temperature=0.1
                        )
                    completion = await asyncio.to_thread(_call_groq, m)
                    content = completion.choices[0].message.content
                    if content and content.strip():
                        raw = content.strip().replace("```json", "").replace("```", "").strip()
                        parsed = json.loads(raw)
                        return SafetyAnalyzeResponse(**parsed)
                except Exception:
                    continue
        except Exception as e:
            logging.warning(f"Groq safety analysis failed: {e}")

    return SafetyAnalyzeResponse(
        flagged=len(request.findings) > 0,
        severity="MODERATE" if request.findings else "LOW",
        title="Medication Safety Review",
        message="Please verify these medications with your physician or pharmacist.",
        recommendation="Consult your healthcare provider before combining these medications."
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)

