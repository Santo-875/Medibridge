import io
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
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
    assert "structuring failed" in data["message"].lower()
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

def test_junk_prescription_filtering_with_mocked_llm():
    from prescription_scanning.main import RawLLMExtraction, MedicationExtractionItem, process_raw_extraction

    junk_raw_text = """
    CENTRAL HOSPITAL DISPENSARY
    PATIENT: RAMESH KUMAR 58Y M
    DIAGNOSIS: CA BLADDER / CKD / GLAUCOMA Delivery
    SL NO   ITEM NAME   DOSAGE  PQT
    1       TAB ATORVASTATIN 10MG   1 x od
    2       CA BLADDER / CKD / GLAUCOMA Delivery
    3       NO
    4       NEEDLE 31G 5MM  10
    """

    mock_llm_data = RawLLMExtraction(
        medications=[
            MedicationExtractionItem(
                name="Atorvastatin 10mg",
                generic_name="Atorvastatin",
                strength="10mg",
                dose_per_intake="10mg",
                frequency="OD",
                duration_days=None,
                is_medicine=True,
                category="medicine",
                confidence=0.95
            ),
            MedicationExtractionItem(
                name="CA BLADDER / CKD / GLAUCOMA Delivery",
                is_medicine=False,
                category="diagnosis",
                confidence=0.2
            ),
            MedicationExtractionItem(
                name="NO",
                is_medicine=False,
                category="noise",
                confidence=0.1
            ),
            MedicationExtractionItem(
                name="NEEDLE 31G 5MM",
                quantity="10",
                is_medicine=False,
                category="supply",
                confidence=0.9
            )
        ]
    )

    record = process_raw_extraction(mock_llm_data)

    med_names = [m.name for m in record.medications]
    assert "Atorvastatin 10mg" in med_names
    assert not any("CA BLADDER" in name for name in med_names)
    assert not any("CKD" in name for name in med_names)
    assert "NO" not in med_names
    assert not any("NEEDLE" in name for name in med_names)

    supply_names = [s.name for s in record.supplies]
    assert any("NEEDLE" in s for s in supply_names)

    atorvastatin_item = next(m for m in record.medications if "Atorvastatin" in m.name)
    assert atorvastatin_item.duration_days is None

def test_clean_printout_with_mocked_llm():
    from prescription_scanning.main import RawLLMExtraction, MedicationExtractionItem, process_raw_extraction

    clean_data = RawLLMExtraction(
        medications=[
            MedicationExtractionItem(
                name="Metformin 500mg",
                generic_name="Metformin",
                strength="500mg",
                dose_per_intake="500mg",
                frequency="BD",
                timing="After meals",
                duration_days=30,
                is_medicine=True,
                confidence=0.98
            ),
            MedicationExtractionItem(
                name="Omeprazole 20mg",
                generic_name="Omeprazole",
                strength="20mg",
                dose_per_intake="20mg",
                frequency="OD",
                timing="Before breakfast",
                duration_days=None,
                is_medicine=True,
                confidence=0.92
            ),
            MedicationExtractionItem(
                name="Disposable Syringe 5ml",
                quantity="5",
                is_medicine=False,
                category="supply",
                confidence=0.95
            )
        ]
    )

    record = process_raw_extraction(clean_data)
    assert len(record.medications) == 2
    assert len(record.supplies) == 1
    assert record.supplies[0].name == "Disposable Syringe 5ml"

    omeprazole = next(m for m in record.medications if "Omeprazole" in m.name)
    assert omeprazole.duration_days is None

def test_handwritten_noisy_text_with_mocked_llm():
    from prescription_scanning.main import RawLLMExtraction, MedicationExtractionItem, process_raw_extraction

    noisy_data = RawLLMExtraction(
        medications=[
            MedicationExtractionItem(
                name="Atorvastatin 10mg",
                generic_name="Atorvastatin",
                strength="10mg",
                frequency="OD",
                duration_days=None,
                is_medicine=True,
                confidence=0.85
            ),
            MedicationExtractionItem(
                name="Paracetamol 650mg",
                generic_name="Paracetamol",
                strength="650mg",
                frequency="SOS",
                duration_days=None,
                is_medicine=True,
                confidence=0.88
            ),
            MedicationExtractionItem(
                name="NO",
                is_medicine=False,
                confidence=0.1
            ),
            MedicationExtractionItem(
                name="Needles 10 nos",
                is_medicine=False,
                category="supply",
                confidence=0.8
            )
        ]
    )

    record = process_raw_extraction(noisy_data)
    med_names = [m.name for m in record.medications]
    assert "Atorvastatin 10mg" in med_names
    assert "Paracetamol 650mg" in med_names
    assert "NO" not in med_names
    assert not any("Needles" in name for name in med_names)

    assert len(record.supplies) == 1
    assert "Needles" in record.supplies[0].name

def test_dose_code_parsing_and_quantity_check():
    from prescription_scanning.main import parse_dose_code, check_quantity

    d1 = parse_dose_code("1 x td", "After Meal")
    assert d1["units_per_intake"] == 1
    assert d1["times_per_day"] == 3
    assert d1["freq_code"] == "TDS"

    w1 = check_quantity("ALPHA KETOANALOGUE 200 MG TAB", 1, 3, 30, "90")
    assert w1 == []

    d2 = parse_dose_code("2 x bd", "After Meal")
    assert d2["units_per_intake"] == 2
    assert d2["times_per_day"] == 2
    assert d2["freq_code"] == "BD"

    w2 = check_quantity("SODIUM BICARBONATE-500MG TAB", 2, 2, 30, "120")
    assert w2 == []

    w3 = check_quantity("MISMATCH TAB", 1, 1, 30, "15")
    assert "quantity doesn't match dose/frequency" in w3

    w_drop = check_quantity("DORZOLAMIDE EYE DROPS", 1, 2, 30, "1")
    assert w_drop == []

def test_opd_receipt_fixture_mocked_llm():
    from pathlib import Path
    from prescription_scanning.main import structure_prescription_with_llm

    fixture_path = Path(__file__).parent / "fixtures" / "opd_receipt.txt"
    if not fixture_path.exists():
        fixture_path = Path("tests/fixtures/opd_receipt.txt")

    with open(fixture_path, "r", encoding="utf-8") as f:
        raw_text = f.read()

    record = structure_prescription_with_llm(raw_text)

    assert len(record.medications) == 12
    assert len(record.supplies) == 1

    supply_item = record.supplies[0]
    assert "NEEDLE" in supply_item.name.upper()

    for m in record.medications:
        assert len(m.name.strip()) >= 3
        upper = m.name.upper()
        assert not any(diag in upper for diag in ["DM2", "HTN", "CA BLADDER", "CKD", "GLAUCOMA", "DIAGNOSIS", "REMARKS"])
        assert "DELIVERY" not in upper
        assert upper != "NO"

import os

@pytest.mark.skipif(
    not os.environ.get("GEMINI_API_KEY") and not os.environ.get("GROQ_API_KEY"),
    reason="LLM API key required for live test"
)
def test_opd_receipt_fixture_live_llm():
    from pathlib import Path
    from prescription_scanning.main import structure_prescription_with_llm

    fixture_path = Path(__file__).parent / "fixtures" / "opd_receipt.txt"
    if not fixture_path.exists():
        fixture_path = Path("tests/fixtures/opd_receipt.txt")

    with open(fixture_path, "r", encoding="utf-8") as f:
        raw_text = f.read()

    record = structure_prescription_with_llm(raw_text)

    assert len(record.medications) == 12
    assert len(record.supplies) == 1

    for m in record.medications:
        assert len(m.name.strip()) >= 3
        upper = m.name.upper()
        assert not any(diag in upper for diag in ["DM2", "HTN", "CA BLADDER", "CKD", "GLAUCOMA", "DIAGNOSIS", "REMARKS"])
        assert "DELIVERY" not in upper
        assert upper != "NO"
