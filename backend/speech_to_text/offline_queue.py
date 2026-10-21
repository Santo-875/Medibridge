import uuid
import sys
from pathlib import Path
from typing import Optional, Dict, Any

# Ensure backend root is on sys.path
backend_root = Path(__file__).resolve().parent.parent
if str(backend_root) not in sys.path:
    sys.path.insert(0, str(backend_root))

from database import SessionLocal
from models import Recording, utcnow

def enqueue_file(file_path: str, profile_id: Optional[str] = None) -> str:
    queue_id = str(uuid.uuid4())
    db = SessionLocal()
    try:
        recording = Recording(
            id=queue_id,
            profile_id=profile_id,
            file_path=str(file_path),
            transcript_tamil="QUEUED",
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(recording)
        db.commit()
    finally:
        db.close()
    return queue_id

def get_next_queued() -> Optional[Dict[str, Any]]:
    db = SessionLocal()
    try:
        rec = db.query(Recording).filter(
            Recording.transcript_tamil == "QUEUED",
            Recording.deleted_at.is_(None)
        ).order_by(Recording.created_at.asc()).first()
        if rec:
            return {"id": rec.id, "file_path": rec.file_path, "status": "QUEUED", "note": None}
    finally:
        db.close()
    return None

def update_status(queue_id: str, status: str, note: Optional[str] = None):
    db = SessionLocal()
    try:
        rec = db.query(Recording).filter(Recording.id == queue_id).first()
        if rec:
            rec.transcript_tamil = status
            if note:
                rec.transcript_english = note
            rec.updated_at = utcnow()
            db.commit()
    finally:
        db.close()

def remove_completed(queue_id: str):
    db = SessionLocal()
    try:
        rec = db.query(Recording).filter(Recording.id == queue_id).first()
        if rec:
            rec.updated_at = utcnow()
            db.commit()
    finally:
        db.close()
