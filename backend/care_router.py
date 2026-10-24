import uuid
import secrets
from datetime import timedelta
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session
from database import get_db
from models import CareLink, Profile, Device, DoseLog, Medication, Notification, utcnow
from auth import get_current_user, require_profile_access, User

router = APIRouter(prefix="", tags=["care_and_devices"])

class InviteRequest(BaseModel):
    profile_id: str
    permission: Optional[str] = "view"  # alerts, view, manage

class AcceptRequest(BaseModel):
    invite_code: str

class RevokeRequest(BaseModel):
    care_link_id: str

class DeviceRegisterRequest(BaseModel):
    fcm_token: str
    platform: Optional[str] = "android"

def generate_invite_code() -> str:
    # 6-character alphanumeric code without easily confused characters (e.g. 0/O, 1/I)
    chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(chars) for _ in range(6))

@router.post("/care/invite")
def create_invite(
    payload: InviteRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    profile = require_profile_access(payload.profile_id, current_user, db, min_permission="manage")
    perm = payload.permission if payload.permission in ("alerts", "view", "manage") else "view"

    # Generate unique code
    for _ in range(5):
        code = generate_invite_code()
        if not db.query(CareLink).filter(CareLink.invite_code == code).first():
            break

    care_link = CareLink(
        id=str(uuid.uuid4()),
        profile_id=profile.id,
        permission=perm,
        status="pending",
        invite_code=code,
        created_at=utcnow(),
        updated_at=utcnow()
    )
    db.add(care_link)
    db.commit()
    db.refresh(care_link)

    return {
        "care_link_id": care_link.id,
        "profile_id": care_link.profile_id,
        "invite_code": care_link.invite_code,
        "permission": care_link.permission,
        "status": care_link.status
    }

@router.post("/care/accept")
def accept_invite(
    payload: AcceptRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    code = payload.invite_code.strip().upper()
    link = db.query(CareLink).filter(
        CareLink.invite_code == code,
        CareLink.status == "pending",
        CareLink.deleted_at.is_(None)
    ).first()

    if not link:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invalid or expired invite code")

    # Ensure user is not accepting their own profile
    profile = db.query(Profile).filter(Profile.id == link.profile_id).first()
    if profile and profile.owner_user_id == current_user.id:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="You already own this profile")

    link.caretaker_user_id = current_user.id
    link.status = "active"
    link.updated_at = utcnow()
    db.commit()
    db.refresh(link)

    return {
        "care_link_id": link.id,
        "profile_id": link.profile_id,
        "profile_name": profile.name if profile else "Patient",
        "permission": link.permission,
        "status": link.status
    }

@router.post("/care/revoke")
def revoke_care_link(
    payload: RevokeRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    link = db.query(CareLink).filter(CareLink.id == payload.care_link_id).first()
    if not link:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Care link not found")

    profile = db.query(Profile).filter(Profile.id == link.profile_id).first()
    is_owner = (profile and profile.owner_user_id == current_user.id)
    is_caretaker = (link.caretaker_user_id == current_user.id)

    if not (is_owner or is_caretaker):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Cannot revoke this care link")

    link.status = "revoked"
    link.deleted_at = utcnow()
    link.updated_at = utcnow()
    db.commit()

    return {"status": "revoked", "care_link_id": link.id}

@router.get("/care/links")
def list_care_links(
    profile_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    require_profile_access(profile_id, current_user, db, min_permission="manage")
    links = db.query(CareLink).filter(
        CareLink.profile_id == profile_id,
        CareLink.deleted_at.is_(None)
    ).all()

    return [
        {
            "id": l.id,
            "profile_id": l.profile_id,
            "caretaker_user_id": l.caretaker_user_id,
            "permission": l.permission,
            "status": l.status,
            "invite_code": l.invite_code,
            "created_at": l.created_at.isoformat() if l.created_at else None,
            "updated_at": l.updated_at.isoformat() if l.updated_at else None,
        }
        for l in links
    ]

@router.post("/devices")
def register_device(
    payload: DeviceRegisterRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    # Upsert by user_id and fcm_token
    existing = db.query(Device).filter(
        Device.user_id == current_user.id,
        Device.fcm_token == payload.fcm_token
    ).first()

    if existing:
        existing.platform = payload.platform or "android"
        existing.updated_at = utcnow()
        db.commit()
        db.refresh(existing)
        return {"status": "updated", "device_id": existing.id}
    else:
        new_dev = Device(
            id=str(uuid.uuid4()),
            user_id=current_user.id,
            fcm_token=payload.fcm_token,
            platform=payload.platform or "android",
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(new_dev)
        db.commit()
        db.refresh(new_dev)
        return {"status": "registered", "device_id": new_dev.id}

@router.post("/care/check-missed-doses")
def check_and_escalate_missed_doses(
    db: Session = Depends(get_db)
):
    now = utcnow()
    threshold = now - timedelta(minutes=30)

    pending_logs = db.query(DoseLog).filter(
        DoseLog.status == "PENDING",
        DoseLog.deleted_at.is_(None),
        DoseLog.created_at <= threshold
    ).all()

    escalated = []
    for log in pending_logs:
        med = db.query(Medication).filter(Medication.id == log.medication_id).first()
        med_name = med.name if med else "Prescribed Medication"
        profile = db.query(Profile).filter(Profile.id == log.profile_id).first()
        patient_name = profile.name if profile else "Patient"

        care_links = db.query(CareLink).filter(
            CareLink.profile_id == log.profile_id,
            CareLink.status == "active",
            CareLink.deleted_at.is_(None),
            CareLink.permission.in_(["alerts", "manage", "owner", "view"])
        ).all()

        for cl in care_links:
            # Avoid duplicate alert for same log
            ref_tag = f"[log:{log.id[:8]}]"
            existing_notif = db.query(Notification).filter(
                Notification.profile_id == log.profile_id,
                Notification.type == "MISSED_DOSE",
                Notification.message.like(f"%{ref_tag}%")
            ).first()

            if not existing_notif:
                notif = Notification(
                    id=str(uuid.uuid4()),
                    profile_id=log.profile_id,
                    type="MISSED_DOSE",
                    message=f"Critical Dose Alert: {patient_name} has not logged dose '{med_name}' scheduled for {log.scheduled_time} (30+ min overdue) {ref_tag}",
                    is_read=False,
                    created_at=utcnow(),
                    updated_at=utcnow()
                )
                db.add(notif)
                escalated.append({
                    "profile_id": log.profile_id,
                    "caretaker_user_id": cl.caretaker_user_id,
                    "medication": med_name,
                    "notification_id": notif.id
                })

    db.commit()
    return {
        "status": "ok",
        "escalated_count": len(escalated),
        "escalated": escalated
    }

