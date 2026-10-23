import cv2
import numpy as np
import os
import sys
from pathlib import Path
from fastapi.testclient import TestClient

backend_dir = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(backend_dir))

from main import app

def run_tests():
    client = TestClient(app)
    
    source_img_path = backend_dir / "test_prescription.jpg"
    if not source_img_path.exists():
        for f in (backend_dir / "image_uploads").glob("*.jpg"):
            if f.is_file():
                source_img_path = f
                break

    print(f"Using source prescription image: {source_img_path}")
    base_img = cv2.imread(str(source_img_path))
    assert base_img is not None, "Failed to load base test image"

    clear_path = backend_dir / "test_clear.jpg"
    cv2.imwrite(str(clear_path), base_img)

    blurry_img = cv2.GaussianBlur(base_img, (51, 51), 0)
    blurry_path = backend_dir / "test_blurry.jpg"
    cv2.imwrite(str(blurry_path), blurry_img)

    h, w = base_img.shape[:2]
    matrix = cv2.getRotationMatrix2D((w // 2, h // 2), 15.0, 1.0)
    angled_img = cv2.warpAffine(base_img, matrix, (w, h), borderValue=(255, 255, 255))
    angled_path = backend_dir / "test_angled.jpg"
    cv2.imwrite(str(angled_path), angled_img)

    test_cases = [
        ("Clear Prescription", clear_path),
        ("Blurry Prescription", blurry_path),
        ("Angled Prescription", angled_path),
    ]

    for name, path in test_cases:
        print(f"\n--- Testing {name} ---")
        with open(path, "rb") as f:
            resp = client.post(
                "/api/prescriptions",
                files={"file": (path.name, f, "image/jpeg")}
            )

        print(f"Status Code: {resp.status_code}")
        resp_text = resp.text
        
        assert "John Doe" not in resp_text, f"FAILURE: 'John Doe' returned in response for {name}!"
        print(f"PASS: 'John Doe' is NOT in response for {name}.")

        if name == "Blurry Prescription":
            assert resp.status_code == 422, f"Expected 422 for blurry image, got {resp.status_code}"
            assert "blurry" in resp_text.lower(), f"Expected blur warning, got: {resp_text}"
            print("PASS: Blurry image properly rejected with 422 early.")
        else:
            print(f"Response snippet: {resp_text[:200]}...")

    for p in [clear_path, blurry_path, angled_path]:
        if p.exists():
            p.unlink()

    print("\nALL 3 PRESCRIPTION TESTS PASSED! Never returned 'John Doe'.")

if __name__ == "__main__":
    run_tests()
