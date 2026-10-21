import os
import re
from datetime import datetime, timezone, timedelta
from typing import Optional, List, Dict
import bcrypt
import jwt
from fastapi import Header, HTTPException, Depends, status
from sqlalchemy.orm import Session
from database import get_db
from models import User, Profile, CareLink

EXPECTED_API_KEY = os.getenv("API_KEY", "medibridge_secret_api_key_2026")
JWT_SECRET = os.getenv("JWT_SECRET", "medibridge_super_secret_jwt_key_2026_production_safe_string")
JWT_ALGORITHM = "HS256"
JWT_EXPIRY_DAYS = 7

PERMISSION_LEVELS = {
    "alerts": 1,
    "view": 2,
    "manage": 3,
    "owner": 4
}

def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")

def verify_password(password: str, hashed: Optional[str]) -> bool:
    if not hashed:
        return False
    try:
        return bcrypt.checkpw(password.encode("utf-8"), hashed.encode("utf-8"))
    except Exception:
        return False

def create_access_token(user_id: str, username: str) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": user_id,
        "username": username,
        "iat": now,
        "exp": now + timedelta(days=JWT_EXPIRY_DAYS)
    }
    return jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)

def decode_access_token(token: str) -> Dict:
    try:
        return jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token has expired"
        )
    except jwt.PyJWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authorization token"
        )

def get_current_user(
    authorization: Optional[str] = Header(None, alias="Authorization"),
    x_api_key: Optional[str] = Header(None, alias="X-API-Key"),
    db: Session = Depends(get_db)
) -> User:
    if not x_api_key or x_api_key != EXPECTED_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-API-Key"
        )

    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing or invalid Bearer token"
        )

    token = authorization.split(" ", 1)[1].strip()
    payload = decode_access_token(token)
    user_id = payload.get("sub")

    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token payload"
        )

    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found"
        )

    return user

def get_user_profile_access(profile_id: str, user: User, db: Session) -> Optional[str]:
    profile = db.query(Profile).filter(Profile.id == profile_id, Profile.deleted_at.is_(None)).first()
    if not profile:
        return None

    if profile.owner_user_id == user.id:
        return "owner"

    care_link = db.query(CareLink).filter(
        CareLink.profile_id == profile_id,
        CareLink.caretaker_user_id == user.id,
        CareLink.status == "active",
        CareLink.deleted_at.is_(None)
    ).first()

    if care_link:
        return care_link.permission

    return None

def require_profile_access(
    profile_id: str,
    user: User,
    db: Session,
    min_permission: str = "view"
) -> Profile:
    profile = db.query(Profile).filter(Profile.id == profile_id, Profile.deleted_at.is_(None)).first()
    if not profile:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Profile not found")

    role = get_user_profile_access(profile_id, user, db)
    if not role:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Access denied to this profile")

    user_level = PERMISSION_LEVELS.get(role, 0)
    required_level = PERMISSION_LEVELS.get(min_permission, 2)

    if user_level < required_level:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=f"Requires {min_permission} permission")

    return profile

def get_accessible_profile_ids(user: User, db: Session) -> List[str]:
    owned_ids = db.query(Profile.id).filter(
        Profile.owner_user_id == user.id,
        Profile.deleted_at.is_(None)
    ).all()

    cared_ids = db.query(CareLink.profile_id).filter(
        CareLink.caretaker_user_id == user.id,
        CareLink.status == "active",
        CareLink.deleted_at.is_(None)
    ).all()

    all_ids = set([p[0] for p in owned_ids] + [c[0] for c in cared_ids])
    return list(all_ids)
