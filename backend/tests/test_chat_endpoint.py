import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import pytest
from fastapi.testclient import TestClient
from main import app

client = TestClient(app)

def test_chat_empty_message():
    response = client.post("/api/chat", json={"message": "   "})
    assert response.status_code == 400

def test_chat_valid_message():
    response = client.post(
        "/api/chat",
        json={
            "message": "What is the recommended dose for Paracetamol?",
            "patient_context": "Paracetamol 500mg"
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "reply" in data
    assert len(data["reply"]) > 10
    assert data["source"] in ["gemini", "groq"]
