import requests
from pathlib import Path

from offline_queue import (
    get_next_queued,
    update_status,
    remove_completed
)


SERVER_URL = "http://127.0.0.1:8000/api/speech/upload"


def upload_next():
    item = get_next_queued()

    if item is None:
        print("Queue is empty.")
        return

    queue_id = item["id"]
    file_path = Path(item["file_path"])

    if not file_path.exists():
        update_status(
            queue_id,
            "FAILED",
            "Audio file not found"
        )
        return

    update_status(queue_id, "UPLOADING")

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
                timeout=60
            )

        response.raise_for_status()

        update_status(queue_id, "COMPLETED")

        remove_completed(queue_id)

        print(f"Uploaded: {file_path.name}")

    except Exception as error:
        update_status(
            queue_id,
            "FAILED",
            str(error)
        )

        print(f"Upload failed: {error}")


def process_queue():
    while True:
        item = get_next_queued()

        if item is None:
            break

        upload_next()


if __name__ == "__main__":
    process_queue()