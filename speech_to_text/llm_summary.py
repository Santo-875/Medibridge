import json
import os
import logging
from dotenv import load_dotenv

load_dotenv()

def generate_medical_summary(transcript: str) -> dict:
    key = os.getenv("GEMINI_API_KEY")
    if key and key != "your_gemini_api_key_here":
        try:
            from google import genai
            from google.genai import types

            client = genai.Client(api_key=key)
            prompt = f"""
            You are an expert clinical documentation assistant.
            Analyze the following doctor-patient audio consultation transcript and generate a structured medical summary.

            Transcript:
            \"\"\"{transcript}\"\"\"

            Return ONLY a valid JSON object with the following schema:
            {{
                "chief_complaints": [string],
                "symptoms": [string],
                "diagnosis": string or null,
                "medications": [
                    {{
                        "name": string,
                        "dosage": string or null,
                        "frequency": string or null,
                        "duration": string or null,
                        "instructions": string or null
                    }}
                ],
                "advice_and_precautions": [string],
                "follow_up": string or null,
                "doctor_notes_summary": string
            }}
            """

            response = client.models.generate_content(
                model="gemini-3.5-flash",
                contents=prompt,
                config=types.GenerateContentConfig(
                    response_mime_type="application/json",
                    temperature=0.1,
                ),
            )
            data = json.loads(response.text)
            data["summary_failed"] = False
            return data
        except Exception as e:
            logging.error(f"Gemini summary generation failed: {e}")
            return {
                "summary_failed": True,
                "error": str(e),
                "raw_transcript": transcript,
                "doctor_notes_summary": transcript,
                "chief_complaints": [],
                "symptoms": [],
                "diagnosis": None,
                "medications": [],
                "advice_and_precautions": [],
                "follow_up": None
            }

    return {
        "summary_failed": True,
        "error": "GEMINI_API_KEY is not configured",
        "raw_transcript": transcript,
        "doctor_notes_summary": transcript,
        "chief_complaints": [],
        "symptoms": [],
        "diagnosis": None,
        "medications": [],
        "advice_and_precautions": [],
        "follow_up": None
    }
