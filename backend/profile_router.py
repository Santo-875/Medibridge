import uuid
from typing import Optional, List
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session
from database import get_db
from models import Profile, CareLink, utcnow
from auth import get_current_user, require_profile_access, User

router = APIRouter(prefix="/profiles", tags=["profiles"])

class ProfileCreate(BaseModel):
    id: Optional[str] = None
    name: str
    avatar: Optional[str] = None

class ProfileResponse(BaseModel):
    id: str
    owner_user_id: str
    name: str
    avatar: Optional[str] = None
    is_owner: bool
    permission: str

@router.post("", response_model=ProfileResponse)
def create_or_upsert_profile(
    payload: ProfileCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    profile_id = payload.id or str(uuid.uuid4())
    existing = db.query(Profile).filter(Profile.id == profile_id).first()

    if existing:
        if existing.owner_user_id != current_user.id:
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Cannot modify a profile owned by another user")
        existing.name = payload.name
        existing.avatar = payload.avatar
        existing.deleted_at = None
        existing.updated_at = utcnow()
        db.commit()
        db.refresh(existing)
        return ProfileResponse(
            id=existing.id,
            owner_user_id=existing.owner_user_id,
            name=existing.name,
            avatar=existing.avatar,
            is_owner=True,
            permission="owner"
        )
    else:
        new_prof = Profile(
            id=profile_id,
            owner_user_id=current_user.id,
            name=payload.name,
            avatar=payload.avatar,
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(new_prof)
        db.commit()
        db.refresh(new_prof)
        return ProfileResponse(
            id=new_prof.id,
            owner_user_id=new_prof.owner_user_id,
            name=new_prof.name,
            avatar=new_prof.avatar,
            is_owner=True,
            permission="owner"
        )

@router.get("", response_model=List[ProfileResponse])
def list_profiles(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    # 1. Owned profiles
    owned = db.query(Profile).filter(
        Profile.owner_user_id == current_user.id,
        Profile.deleted_at.is_(None)
    ).all()

    # 2. Cared profiles via active care link
    care_links = db.query(CareLink).filter(
        CareLink.caretaker_user_id == current_user.id,
        CareLink.status == "active",
        CareLink.deleted_at.is_(None)
    ).all()

    cared_prof_ids = [c.profile_id for c in care_links]
    cared_profiles = db.query(Profile).filter(
        Profile.id.in_(cared_prof_ids),
        Profile.deleted_at.is_(None)
    ).all() if cared_prof_ids else []

    cared_map = {c.profile_id: c.permission for c in care_links}

    results = []
    for p in owned:
        results.append(ProfileResponse(
            id=p.id,
            owner_user_id=p.owner_user_id,
            name=p.name,
            avatar=p.avatar,
            is_owner=True,
            permission="owner"
        ))

    for p in cared_profiles:
        if p.owner_user_id != current_user.id:
            results.append(ProfileResponse(
                id=p.id,
                owner_user_id=p.owner_user_id,
                name=p.name,
                avatar=p.avatar,
                is_owner=False,
                permission=cared_map.get(p.id, "view")
            ))

    return results

@router.get("/{profile_id}", response_model=ProfileResponse)
def get_profile(
    profile_id: str,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    profile = require_profile_access(profile_id, current_user, db, min_permission="alerts")
    is_owner = (profile.owner_user_id == current_user.id)
    perm = "owner"
    if not is_owner:
        cl = db.query(CareLink).filter(
            CareLink.profile_id == profile_id,
            CareLink.caretaker_user_id == current_user.id,
            CareLink.status == "active",
            CareLink.deleted_at.is_(None)
        ).first()
        perm = cl.permission if cl else "view"

    return ProfileResponse(
        id=profile.id,
        owner_user_id=profile.owner_user_id,
        name=profile.name,
        avatar=profile.avatar,
        is_owner=is_owner,
        permission=perm
    )
