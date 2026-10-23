from fastapi import FastAPI, UploadFile, File, HTTPException
from pathlib import Path
import shutil
import uuid
import os

from sarvam_STT import transcribe_audio
from llm_summary import generate_medical_summary

app = FastAPI()

AUDIO_DIR = Path("audio_uploads")
AUDIO_DIR.mkdir(exist_ok=True)

@app.post("/api/speech/upload")
async def upload_audio(file: UploadFile = File(...)):
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(
            status_code=400,
            detail={"code": "EMPTY_AUDIO", "message": "Uploaded audio file is empty"}
        )

    file_id = str(uuid.uuid4())
    ext = Path(file.filename or "recording.m4a").suffix or ".m4a"
    file_path = AUDIO_DIR / f"{file_id}_{ext}"

    with open(file_path, "wb") as buffer:
        buffer.write(file_bytes)

    try:
        transcript = transcribe_audio(file_path)
    except Exception as err:
        raise HTTPException(
            status_code=502,
            detail={"code": "STT_FAILED", "message": str(err)}
        )

    summary = generate_medical_summary(transcript)

    return {
        "id": file_id,
        "filename": file.filename,
        "status": "COMPLETED",
        "transcript": transcript,
        "summary": summary
    }