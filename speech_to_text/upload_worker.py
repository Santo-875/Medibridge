import os
import requests
from pathlib import Path
from dotenv import load_dotenv

load_dotenv()

from offline_queue import (
    get_next_queued,
    update_status,
    remove_completed
)

SERVER_URL = os.getenv("SPEECH_SERVER_URL", "http://127.0.0.1:8000/api/speech/upload")

def upload_next():
    item = get_next_queued()
    if item is None:
        print("Queue is empty.")
        return

    queue_id = item["id"]
    file_path = Path(item["file_path"])

    if not file_path.exists():
        update_status(queue_id, "FAILED", "Audio file not found")
        return

    update_status(queue_id, "UPLOADING")
    upload_timeout = float(os.getenv("SPEECH_UPLOAD_TIMEOUT", "60.0"))

    try:
        with open(file_path, "rb") as audio:
            response = requests.post(
                SERVER_URL,
                files={
                    "file": (
                        file_path.name,
                        audio,
                        "audio/m4a"
                    )
                },
                timeout=upload_timeout
            )

        if response.status_code >= 400:
            err_msg = response.text
            try:
                err_json = response.json()
                err_msg = err_json.get("detail") or err_json.get("message") or err_msg
            except Exception:
                pass
            update_status(queue_id, "FAILED", f"{response.status_code}: {err_msg}")
            print(f"Upload failed: {response.status_code} {err_msg}")
            return

        update_status(queue_id, "COMPLETED")
        remove_completed(queue_id)
        print(f"Uploaded: {file_path.name}")

    except Exception as error:
        update_status(queue_id, "FAILED", str(error))
        print(f"Upload failed: {error}")

def process_queue():
    while True:
        item = get_next_queued()
        if item is None:
            break
        upload_next()

if __name__ == "__main__":
    process_queue()