from fastapi import FastAPI, UploadFile, File
from pathlib import Path
import shutil
import uuid

from sarvam_stt import transcribe_audio
from llm_summary import generate_medical_summary


app = FastAPI()

AUDIO_DIR = Path("audio_uploads")
AUDIO_DIR.mkdir(exist_ok=True)


@app.post("/api/speech/upload")
async def upload_audio(file: UploadFile = File(...)):

    file_id = str(uuid.uuid4())
    file_path = AUDIO_DIR / f"{file_id}_{file.filename}"

    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)

    # B4: Audio → Sarvam → Transcript
    transcript = transcribe_audio(file_path)

    # B5: Transcript → Gemini → Medical Summary
    summary = generate_medical_summary(transcript)

    return {
        "id": file_id,
        "filename": file.filename,
        "status": "COMPLETED",
        "transcript": transcript,
        "summary": summary
    }