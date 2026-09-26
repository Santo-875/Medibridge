import sqlite3
from pathlib import Path
from typing import Optional, Dict, Any
import uuid

DB_PATH = Path(__file__).parent / "offline_queue.db"

def _get_connection():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    with _get_connection() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS upload_queue (
                id TEXT PRIMARY KEY,
                file_path TEXT NOT NULL,
                status TEXT NOT NULL,
                note TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.commit()

init_db()

def enqueue_file(file_path: str) -> str:
    """Add a file to the offline queue."""
    queue_id = str(uuid.uuid4())
    with _get_connection() as conn:
        conn.execute(
            "INSERT INTO upload_queue (id, file_path, status) VALUES (?, ?, ?)",
            (queue_id, str(file_path), "QUEUED")
        )
        conn.commit()
    return queue_id

def get_next_queued() -> Optional[Dict[str, Any]]:
    """Fetch the next item with status 'QUEUED'."""
    with _get_connection() as conn:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT id, file_path, status, note FROM upload_queue WHERE status = 'QUEUED' ORDER BY created_at ASC LIMIT 1"
        )
        row = cursor.fetchone()
        if row:
            return {"id": row["id"], "file_path": row["file_path"], "status": row["status"], "note": row["note"]}
    return None

def update_status(queue_id: str, status: str, note: Optional[str] = None):
    """Update status and optional note for a queued item."""
    with _get_connection() as conn:
        conn.execute(
            "UPDATE upload_queue SET status = ?, note = ? WHERE id = ?",
            (status, note, queue_id)
        )
        conn.commit()

def remove_completed(queue_id: str):
    """Remove a successfully completed item from the queue."""
    with _get_connection() as conn:
        conn.execute("DELETE FROM upload_queue WHERE id = ?", (queue_id,))
        conn.commit()
