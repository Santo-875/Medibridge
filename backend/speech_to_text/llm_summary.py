import json
import os
from dotenv import load_dotenv
from google import genai
from google.genai import types

load_dotenv()

client = genai.Client(api_key=os.getenv("GEMINI_API_KEY") or "dummy_key")

def generate_medical_summary(transcript: str) -> dict:
    """Takes raw speech transcript and generates a structured clinical medical summary."""
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
        model="gemini-2.5-flash",
        contents=prompt,
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            temperature=0.1,
        ),
    )

    return json.loads(response.text)
