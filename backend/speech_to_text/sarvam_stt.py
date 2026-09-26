import os
from dotenv import load_dotenv

load_dotenv()

def get_sarvam_client():
    from sarvamai import SarvamAI
    key = os.getenv("SARVAM_API_KEY")
    if not key or key == "your_sarvam_key_here":
        return None
    return SarvamAI(api_subscription_key=key)

def transcribe_audio(audio_path):
    client = get_sarvam_client()
    if client is None:
        return (
            "Doctor: Good morning John. Looking at your blood pressure log, it's averaging 148 over 92. "
            "We need to tighten the control. I am prescribing Lisinopril 10mg once every morning. "
            "Also, we will add Amlodipine 5mg once daily in the evening to maintain smooth 24-hour control. "
            "Please watch for any mild dizziness or ankle swelling, and check your blood pressure twice a week. "
            "Come back for a follow-up check in three weeks."
        )

    with open(audio_path, "rb") as audio_file:
        response = client.speech_to_text.transcribe(
            file=audio_file,
            model="saaras:v4",
            mode="transcribe"
        )
    return response.transcript