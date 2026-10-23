import uuid
from datetime import datetime, timezone
from typing import Optional, List, Dict, Any
from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session
from database import get_db
from models import (
    User, Profile, Medication, DoseTime, DoseLog,
    Prescription, Recording, SafetyCheck, Notification, CareLink, utcnow
)
from auth import get_current_user, get_accessible_profile_ids, get_user_profile_access

router = APIRouter(prefix="/sync", tags=["sync"])

def normalize_dt(dt: Optional[datetime]) -> Optional[datetime]:
    if dt is None:
        return None
    if dt.tzinfo is not None:
        return dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt

def parse_dt(val: Any) -> Optional[datetime]:
    if not val:
        return None
    if isinstance(val, datetime):
        return normalize_dt(val)
    try:
        if isinstance(val, (int, float)):
            ts = val / 1000.0 if val > 1e11 else float(val)
            dt = datetime.fromtimestamp(ts, tz=timezone.utc)
            return normalize_dt(dt)
        s = str(val).strip().replace("Z", "+00:00")
        dt = datetime.fromisoformat(s)
        return normalize_dt(dt)
    except Exception:
        return None

def is_newer(client_dt: Optional[datetime], server_dt: Optional[datetime]) -> bool:
    if not server_dt:
        return True
    if not client_dt:
        return False
    c = normalize_dt(client_dt)
    s = normalize_dt(server_dt)
    return c > s

def to_iso(dt: Optional[datetime]) -> Optional[str]:
    return dt.isoformat() if dt else None

class SyncPushPayload(BaseModel):
    profiles: Optional[List[Dict[str, Any]]] = []
    medications: Optional[List[Dict[str, Any]]] = []
    dose_times: Optional[List[Dict[str, Any]]] = []
    dose_logs: Optional[List[Dict[str, Any]]] = []
    prescriptions: Optional[List[Dict[str, Any]]] = []
    recordings: Optional[List[Dict[str, Any]]] = []
    safety_checks: Optional[List[Dict[str, Any]]] = []
    notifications: Optional[List[Dict[str, Any]]] = []
    care_links: Optional[List[Dict[str, Any]]] = []

@router.get("/pull")
def sync_pull(
    since: Optional[str] = Query(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    accessible_ids = get_accessible_profile_ids(current_user, db)
    since_dt = parse_dt(since)

    def apply_filter(query, model):
        q = query
        if hasattr(model, "profile_id"):
            q = q.filter(model.profile_id.in_(accessible_ids))
        elif model == Profile:
            q = q.filter(Profile.id.in_(accessible_ids))
        elif model == CareLink:
            q = q.filter(CareLink.profile_id.in_(accessible_ids))

        if since_dt and hasattr(model, "updated_at"):
            q = q.filter(model.updated_at >= since_dt)
        return q.all()

    # Query all accessible rows
    profiles_rows = apply_filter(db.query(Profile), Profile)
    med_rows = apply_filter(db.query(Medication), Medication)
    dose_time_rows = apply_filter(db.query(DoseTime), DoseTime)
    dose_log_rows = apply_filter(db.query(DoseLog), DoseLog)
    presc_rows = apply_filter(db.query(Prescription), Prescription)
    rec_rows = apply_filter(db.query(Recording), Recording)
    safety_rows = apply_filter(db.query(SafetyCheck), SafetyCheck)
    notif_rows = apply_filter(db.query(Notification), Notification)
    care_rows = apply_filter(db.query(CareLink), CareLink)

    return {
        "server_time": to_iso(utcnow()),
        "profiles": [
            {
                "id": p.id,
                "owner_user_id": p.owner_user_id,
                "name": p.name,
                "avatar": p.avatar,
                "created_at": to_iso(p.created_at),
                "updated_at": to_iso(p.updated_at),
                "deleted_at": to_iso(p.deleted_at),
            }
            for p in profiles_rows
        ],
        "medications": [
            {
                "id": m.id,
                "profile_id": m.profile_id,
                "name": m.name,
                "dosage": m.dosage,
                "strength": m.strength,
                "timing": m.timing,
                "food_rule": m.food_rule,
                "duration_days": m.duration_days,
                "source": m.source,
                "rxcui": m.rxcui,
                "created_at": to_iso(m.created_at),
                "updated_at": to_iso(m.updated_at),
                "deleted_at": to_iso(m.deleted_at),
            }
            for m in med_rows
        ],
        "dose_times": [
            {
                "id": d.id,
                "medication_id": d.medication_id,
                "profile_id": d.profile_id,
                "time": d.time,
                "slot": d.slot,
                "created_at": to_iso(d.created_at),
                "updated_at": to_iso(d.updated_at),
                "deleted_at": to_iso(d.deleted_at),
            }
            for d in dose_time_rows
        ],
        "dose_logs": [
            {
                "id": l.id,
                "profile_id": l.profile_id,
                "medication_id": l.medication_id,
                "dose_time_id": l.dose_time_id,
                "scheduled_time": l.scheduled_time,
                "status": l.status,
                "taken_at": to_iso(l.taken_at),
                "snooze_until": to_iso(l.snooze_until),
                "created_at": to_iso(l.created_at),
                "updated_at": to_iso(l.updated_at),
                "deleted_at": to_iso(l.deleted_at),
            }
            for l in dose_log_rows
        ],
        "prescriptions": [
            {
                "id": pr.id,
                "profile_id": pr.profile_id,
                "file_path": pr.file_path,
                "ocr_raw_text": pr.ocr_raw_text,
                "structured_json": pr.structured_json,
                "prescribed_by": pr.prescribed_by,
                "date": pr.date,
                "created_at": to_iso(pr.created_at),
                "updated_at": to_iso(pr.updated_at),
                "deleted_at": to_iso(pr.deleted_at),
            }
            for pr in presc_rows
        ],
        "recordings": [
            {
                "id": r.id,
                "profile_id": r.profile_id,
                "file_path": r.file_path,
                "transcript_tamil": r.transcript_tamil,
                "transcript_english": r.transcript_english,
                "summary_json": r.summary_json,
                "created_at": to_iso(r.created_at),
                "updated_at": to_iso(r.updated_at),
                "deleted_at": to_iso(r.deleted_at),
            }
            for r in rec_rows
        ],
        "safety_checks": [
            {
                "id": s.id,
                "profile_id": s.profile_id,
                "medication_id": s.medication_id,
                "conflict_type": s.conflict_type,
                "severity": s.severity,
                "details": s.details,
                "action_required": s.action_required,
                "created_at": to_iso(s.created_at),
                "updated_at": to_iso(s.updated_at),
                "deleted_at": to_iso(s.deleted_at),
            }
            for s in safety_rows
        ],
        "notifications": [
            {
                "id": n.id,
                "profile_id": n.profile_id,
                "type": n.type,
                "message": n.message,
                "is_read": n.is_read,
                "created_at": to_iso(n.created_at),
                "updated_at": to_iso(n.updated_at),
                "deleted_at": to_iso(n.deleted_at),
            }
            for n in notif_rows
        ],
        "care_links": [
            {
                "id": cl.id,
                "profile_id": cl.profile_id,
                "caretaker_user_id": cl.caretaker_user_id,
                "permission": cl.permission,
                "status": cl.status,
                "invite_code": cl.invite_code,
                "created_at": to_iso(cl.created_at),
                "updated_at": to_iso(cl.updated_at),
                "deleted_at": to_iso(cl.deleted_at),
            }
            for cl in care_rows
        ]
    }

@router.post("/push")
def sync_push(
    payload: SyncPushPayload,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    upserted_count = 0

    def can_manage_profile(profile_id: str) -> bool:
        role = get_user_profile_access(profile_id, current_user, db)
        return role in ("owner", "manage")

    # 1. Upsert Profiles
    for p_data in (payload.profiles or []):
        p_id = p_data.get("id")
        if not p_id:
            continue
        existing = db.query(Profile).filter(Profile.id == p_id).first()
        client_updated = parse_dt(p_data.get("updated_at")) or utcnow()

        if existing:
            # Check owner
            if existing.owner_user_id != current_user.id:
                continue
            if is_newer(client_updated, existing.updated_at):
                existing.name = p_data.get("name", existing.name)
                existing.avatar = p_data.get("avatar", existing.avatar)
                existing.updated_at = client_updated
                existing.deleted_at = parse_dt(p_data.get("deleted_at"))
                upserted_count += 1
        else:
            new_profile = Profile(
                id=p_id,
                owner_user_id=current_user.id,
                name=p_data.get("name", "Alex"),
                avatar=p_data.get("avatar"),
                created_at=parse_dt(p_data.get("created_at")) or utcnow(),
                updated_at=client_updated,
                deleted_at=parse_dt(p_data.get("deleted_at"))
            )
            db.add(new_profile)
            upserted_count += 1

    db.flush()

    # 2. Upsert Medications
    for m_data in (payload.medications or []):
        prof_id = m_data.get("profile_id")
        if not prof_id or not can_manage_profile(prof_id):
            continue
        m_id = m_data.get("id")
        if not m_id:
            continue
        client_updated = parse_dt(m_data.get("updated_at")) or utcnow()
        existing = db.query(Medication).filter(Medication.id == m_id).first()

        if existing:
            if is_newer(client_updated, existing.updated_at):
                existing.name = m_data.get("name", existing.name)
                existing.dosage = m_data.get("dosage", existing.dosage)
                existing.strength = m_data.get("strength", existing.strength)
                existing.timing = m_data.get("timing", existing.timing)
                existing.food_rule = m_data.get("food_rule", existing.food_rule)
                existing.duration_days = m_data.get("duration_days", existing.duration_days)
                existing.source = m_data.get("source", existing.source)
                existing.rxcui = m_data.get("rxcui", existing.rxcui)
                existing.updated_at = client_updated
                existing.deleted_at = parse_dt(m_data.get("deleted_at"))
                upserted_count += 1
        else:
            db.add(Medication(
                id=m_id,
                profile_id=prof_id,
                name=m_data.get("name", "Medicine"),
                dosage=m_data.get("dosage"),
                strength=m_data.get("strength"),
                timing=m_data.get("timing"),
                food_rule=m_data.get("food_rule"),
                duration_days=m_data.get("duration_days"),
                source=m_data.get("source"),
                rxcui=m_data.get("rxcui"),
                created_at=parse_dt(m_data.get("created_at")) or utcnow(),
                updated_at=client_updated,
                deleted_at=parse_dt(m_data.get("deleted_at"))
            ))
            upserted_count += 1

    db.flush()

    # 3. Upsert DoseTimes
    for dt_data in (payload.dose_times or []):
        prof_id = dt_data.get("profile_id")
        if not prof_id or not can_manage_profile(prof_id):
            continue
        dt_id = dt_data.get("id")
        if not dt_id:
            continue
        client_updated = parse_dt(dt_data.get("updated_at")) or utcnow()
        existing = db.query(DoseTime).filter(DoseTime.id == dt_id).first()

        if existing:
            if is_newer(client_updated, existing.updated_at):
                existing.time = dt_data.get("time", existing.time)
                existing.slot = dt_data.get("slot", existing.slot)
                existing.updated_at = client_updated
                existing.deleted_at = parse_dt(dt_data.get("deleted_at"))
                upserted_count += 1
        else:
            db.add(DoseTime(
                id=dt_id,
                medication_id=dt_data.get("medication_id"),
                profile_id=prof_id,
                time=dt_data.get("time", "08:00"),
                slot=dt_data.get("slot", "MORNING"),
                created_at=parse_dt(dt_data.get("created_at")) or utcnow(),
                updated_at=client_updated,
                deleted_at=parse_dt(dt_data.get("deleted_at"))
            ))
            upserted_count += 1

    db.flush()

    # 4. Upsert DoseLogs (allow view/alerts users to log doses if needed, or manage)
    for l_data in (payload.dose_logs or []):
        prof_id = l_data.get("profile_id")
        role = get_user_profile_access(prof_id, current_user, db) if prof_id else None
        if not role:
            continue
        l_id = l_data.get("id")
        if not l_id:
            continue
        client_updated = parse_dt(l_data.get("updated_at")) or utcnow()
        existing = db.query(DoseLog).filter(DoseLog.id == l_id).first()

        if existing:
            if is_newer(client_updated, existing.updated_at):
                existing.status = l_data.get("status", existing.status)
                existing.taken_at = parse_dt(l_data.get("taken_at"))
                existing.snooze_until = parse_dt(l_data.get("snooze_until"))
                existing.updated_at = client_updated
                existing.deleted_at = parse_dt(l_data.get("deleted_at"))
                upserted_count += 1
        else:
            db.add(DoseLog(
                id=l_id,
                profile_id=prof_id,
                medication_id=l_data.get("medication_id"),
                dose_time_id=l_data.get("dose_time_id"),
                scheduled_time=l_data.get("scheduled_time", "08:00 AM"),
                status=l_data.get("status", "PENDING"),
                taken_at=parse_dt(l_data.get("taken_at")),
                snooze_until=parse_dt(l_data.get("snooze_until")),
                created_at=parse_dt(l_data.get("created_at")) or utcnow(),
                updated_at=client_updated,
                deleted_at=parse_dt(l_data.get("deleted_at"))
            ))
            upserted_count += 1

    # 5. Upsert SafetyChecks
    for s_data in (payload.safety_checks or []):
        prof_id = s_data.get("profile_id")
        if not prof_id or not can_manage_profile(prof_id):
            continue
        s_id = s_data.get("id")
        if not s_id:
            continue
        client_updated = parse_dt(s_data.get("updated_at")) or utcnow()
        existing = db.query(SafetyCheck).filter(SafetyCheck.id == s_id).first()

        if existing:
            if is_newer(client_updated, existing.updated_at):
                existing.conflict_type = s_data.get("conflict_type", existing.conflict_type)
                existing.severity = s_data.get("severity", existing.severity)
                existing.details = s_data.get("details", existing.details)
                existing.action_required = s_data.get("action_required", existing.action_required)
                existing.updated_at = client_updated
                existing.deleted_at = parse_dt(s_data.get("deleted_at"))
                upserted_count += 1
        else:
            db.add(SafetyCheck(
                id=s_id,
                profile_id=prof_id,
                medication_id=s_data.get("medication_id"),
                conflict_type=s_data.get("conflict_type", "INTERACTION"),
                severity=s_data.get("severity", "HIGH"),
                details=s_data.get("details", ""),
                action_required=s_data.get("action_required"),
                created_at=parse_dt(s_data.get("created_at")) or utcnow(),
                updated_at=client_updated,
                deleted_at=parse_dt(s_data.get("deleted_at"))
            ))
            upserted_count += 1

    # 6. Upsert Notifications
    for n_data in (payload.notifications or []):
        prof_id = n_data.get("profile_id")
        role = get_user_profile_access(prof_id, current_user, db) if prof_id else None
        if not role:
            continue
        n_id = n_data.get("id")
        if not n_id:
            continue
        client_updated = parse_dt(n_data.get("updated_at")) or utcnow()
        existing = db.query(Notification).filter(Notification.id == n_id).first()

        if existing:
            if is_newer(client_updated, existing.updated_at):
                existing.message = n_data.get("message", existing.message)
                existing.is_read = bool(n_data.get("is_read", existing.is_read))
                existing.updated_at = client_updated
                existing.deleted_at = parse_dt(n_data.get("deleted_at"))
                upserted_count += 1
        else:
            db.add(Notification(
                id=n_id,
                profile_id=prof_id,
                type=n_data.get("type", "MISSED_DOSE"),
                message=n_data.get("message", ""),
                is_read=bool(n_data.get("is_read", False)),
                created_at=parse_dt(n_data.get("created_at")) or utcnow(),
                updated_at=client_updated,
                deleted_at=parse_dt(n_data.get("deleted_at"))
            ))
            upserted_count += 1

    db.commit()

    return {
        "status": "ok",
        "upserted_count": upserted_count,
        "server_time": to_iso(utcnow())
    }
