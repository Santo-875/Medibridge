import io
import cv2
import numpy as np
import pytest
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient

import prescription_scanning.main as presc_module
from prescription_scanning.main import app as scan_app, check_medicine_with_rxnorm, validate_medicine
import main as backend_main
from main import app as backend_app

def create_test_image(sharp: bool = True, width: int = 400, height: int = 400) -> io.BytesIO:
    if sharp:
        img = np.zeros((height, width, 3), dtype=np.uint8)
        cv2.putText(img, "RX ONLY", (50, 100), cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 255, 255), 2)
        cv2.line(img, (0, 0), (width, height), (255, 255, 255), 2)
    else:
        img = np.ones((height, width, 3), dtype=np.uint8) * 128
    
    _, encoded = cv2.imencode(".jpg", img)
    return io.BytesIO(encoded.tobytes())

def test_ocr_empty_text_returns_422_without_mock_data():
    client = TestClient(scan_app)
    img_bytes = create_test_image(sharp=True)

    with patch.object(presc_module, "ocr") as mock_ocr:
        mock_ocr.predict.return_value = []
        resp = client.post(
            "/scan",
            files={"file": ("test.jpg", img_bytes, "image/jpeg")}
        )

    assert resp.status_code == 422
    body = resp.text
    assert "John Doe" not in body
    assert "Metformin" not in body
    assert "Atorvastatin" not in body
    assert "Could not read any text" in body

def test_rxnorm_api_unreachable_degrades_to_unverified():
    with patch("urllib.request.urlopen", side_effect=Exception("Connection timed out")):
        res = check_medicine_with_rxnorm("Metformin 500mg")
        assert res is None

        val = validate_medicine("UnknownDrug 100mg")
        assert val["confidence"] == "UNVERIFIED"

def test_ocr_engine_unavailable_returns_503():
    client = TestClient(scan_app)
    img_bytes = create_test_image(sharp=True)

    with patch.object(presc_module, "ocr", None):
        resp = client.post(
            "/scan",
            files={"file": ("test.jpg", img_bytes, "image/jpeg")}
        )

    assert resp.status_code == 503
    assert "OCR engine unavailable" in resp.json()["detail"]

def test_groq_failure_returns_502_with_raw_ocr_data():
    client = TestClient(scan_app)
    img_bytes = create_test_image(sharp=True)

    mock_ocr_result = [{
        "rec_texts": ["PARACETAMOL 500MG TAB 1 x od 10"],
        "rec_polys": [[[10, 10], [100, 10], [100, 30], [10, 30]]]
    }]

    with patch.object(presc_module, "ocr") as mock_ocr, \
         patch.dict("os.environ", {"GROQ_API_KEY": "dummy_key"}), \
         patch.object(presc_module, "structure_prescription_with_llm", side_effect=Exception("Groq rate limit exceeded")):
        mock_ocr.predict.return_value = mock_ocr_result
        resp = client.post(
            "/scan",
            files={"file": ("test.jpg", img_bytes, "image/jpeg")}
        )

    assert resp.status_code == 502
    data = resp.json()["detail"]
    assert "Groq structuring failed" in data["message"]
    assert "PARACETAMOL 500MG" in data["raw_text"]
    assert len(data["validated_medicines"]) > 0

def test_speech_round_trip_error_path():
    client = TestClient(backend_app)
    
    resp_empty = client.post(
        "/api/speech/upload",
        files={"file": ("empty.m4a", io.BytesIO(b""), "audio/m4a")}
    )
    assert resp_empty.status_code in [400, 422]
    assert resp_empty.headers.get("content-type") == "application/json"

    with patch("speech_to_text.sarvam_stt.transcribe_and_translate_audio", side_effect=RuntimeError("Sarvam service timed out")):
        resp_fail = client.post(
            "/api/speech/upload",
            files={"file": ("silence.m4a", io.BytesIO(b"\x00" * 100), "audio/m4a")}
        )
        assert resp_fail.status_code == 502
        assert resp_fail.headers.get("content-type") == "application/json"
        detail = resp_fail.json()["detail"]
        assert detail["code"] == "STT_FAILED"
        assert "timed out" in detail["message"]

def test_speech_round_trip_success_path():
    client = TestClient(backend_app)

    with patch("speech_to_text.sarvam_stt.transcribe_and_translate_audio", return_value=("வணக்கம்", "Hello")):
        with patch("speech_to_text.llm_summary.generate_medical_summary", return_value={"summary_failed": False, "diagnosis": "Healthy", "medications": []}):
            resp = client.post(
                "/api/speech/upload",
                files={"file": ("speech.m4a", io.BytesIO(b"dummy audio content"), "audio/m4a")}
            )
            assert resp.status_code == 200
            assert resp.headers.get("content-type") == "application/json"
            data = resp.json()
            assert data["status"] == "COMPLETED"
            assert data["transcript_tamil"] == "வணக்கம்"
            assert data["transcript_english"] == "Hello"
            assert data["summary"]["diagnosis"] == "Healthy"
