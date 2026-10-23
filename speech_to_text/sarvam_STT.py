import os
import requests
from dotenv import load_dotenv

load_dotenv()

from pathlib import Path

def _get_audio_mime(path_str):
    ext = Path(path_str).suffix.lower()
    if ext in [".m4a", ".mp4"]:
        return "audio/mp4"
    elif ext in [".mp3", ".mpeg"]:
        return "audio/mp3"
    elif ext == ".wav":
        return "audio/wav"
    elif ext == ".aac":
        return "audio/aac"
    elif ext == ".ogg":
        return "audio/ogg"
    return "audio/mp4"

def transcribe_and_translate_audio(audio_path):
    key = os.getenv("SARVAM_API_KEY")
    if not key or key == "your_sarvam_api_key_here":
        raise RuntimeError("SARVAM_API_KEY is not configured")

    stt_timeout = float(os.getenv("SARVAM_STT_TIMEOUT", "30.0"))
    translate_timeout = float(os.getenv("SARVAM_TRANSLATE_TIMEOUT", "20.0"))

    url_stt = "https://api.sarvam.ai/speech-to-text"
    headers = {"api-subscription-key": key}
    filename = Path(audio_path).name or "recording.m4a"
    mime = _get_audio_mime(audio_path)

    with open(audio_path, "rb") as f:
        file_bytes = f.read()

    res = None
    for model_name in ["saaras:v4", "saaras:v3"]:
        files = {"file": (filename, file_bytes, mime)}
        data = {"model": model_name, "language_code": "ta-IN"}
        try:
            res = requests.post(url_stt, headers=headers, files=files, data=data, timeout=stt_timeout)
            if res.status_code == 200:
                break
        except Exception:
            continue

    if res is None or res.status_code != 200:
        err_msg = res.text if res is not None else "No response"
        raise RuntimeError(f"Sarvam STT failed: {err_msg}")

    tamil_transcript = res.json().get("transcript", "")
    if not tamil_transcript or not tamil_transcript.strip():
        raise RuntimeError("Sarvam STT returned empty transcript")

    english_transcript = tamil_transcript
    try:
        url_tr = "https://api.sarvam.ai/translate"
        payload = {
            "input": tamil_transcript,
            "source_language_code": "ta-IN",
            "target_language_code": "en-IN",
            "mode": "formal"
        }
        res_tr = requests.post(
            url_tr,
            headers={"api-subscription-key": key, "Content-Type": "application/json"},
            json=payload,
            timeout=translate_timeout
        )
        if res_tr.status_code == 200:
            english_transcript = res_tr.json().get("translated_text", "") or tamil_transcript
    except Exception:
        pass

    return tamil_transcript, english_transcript

def transcribe_audio(audio_path):
    ta, en = transcribe_and_translate_audio(audio_path)
    return f"Tamil: {ta}\n\nEnglish: {en}"