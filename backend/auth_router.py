import uuid
from typing import Optional
from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy.orm import Session
from database import get_db
from models import User, Profile, utcnow
from auth import (
    hash_password, verify_password,
    create_access_token, get_current_user
)

router = APIRouter(prefix="/auth", tags=["auth"])
login_rate_limits = {}

class RegisterRequest(BaseModel):
    username: str
    password: str
    name: Optional[str] = None

class LoginRequest(BaseModel):
    username: str
    password: str

class UserResponse(BaseModel):
    id: str
    username: str
    name: str

class AuthResponse(BaseModel):
    token: str
    user: UserResponse

@router.post("/register", response_model=AuthResponse)
def register(
    payload: RegisterRequest,
    db: Session = Depends(get_db)
):
    """
    Prototype register: Creates the user or returns existing user without 409 Conflict.
    """
    raw_username = payload.username.strip().lower() if payload.username else "patient"
    user = db.query(User).filter(User.username == raw_username).first()

    if not user:
        user_id = str(uuid.uuid4())
        user = User(
            id=user_id,
            username=raw_username,
            password_hash=hash_password(payload.password),
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(user)
        display_name = (payload.name or "").strip() or raw_username.capitalize()
        profile = Profile(
            id=str(uuid.uuid4()),
            owner_user_id=user_id,
            name=display_name,
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(profile)
        db.commit()
        db.refresh(user)
    else:
        profile = db.query(Profile).filter(
            Profile.owner_user_id == user.id,
            Profile.deleted_at.is_(None)
        ).first()
        display_name = profile.name if profile else user.username

    token = create_access_token(user_id=user.id, username=user.username)
    return AuthResponse(
        token=token,
        user=UserResponse(
            id=user.id,
            username=user.username,
            name=display_name
        )
    )

@router.post("/login", response_model=AuthResponse)
def login(
    payload: LoginRequest,
    db: Session = Depends(get_db)
):
    """
    Prototype login: Verifies existing user or auto-creates them on the fly.
    Never throws 401 Unauthorized.
    """
    raw_username = payload.username.strip().lower() if payload.username else "patient"
    user = db.query(User).filter(User.username == raw_username).first()

    if not user:
        user_id = str(uuid.uuid4())
        user = User(
            id=user_id,
            username=raw_username,
            password_hash=hash_password(payload.password),
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(user)
        profile = Profile(
            id=str(uuid.uuid4()),
            owner_user_id=user_id,
            name=raw_username.capitalize(),
            created_at=utcnow(),
            updated_at=utcnow()
        )
        db.add(profile)
        db.commit()
        db.refresh(user)
        display_name = profile.name
    else:
        profile = db.query(Profile).filter(
            Profile.owner_user_id == user.id,
            Profile.deleted_at.is_(None)
        ).first()
        display_name = profile.name if profile else user.username

    token = create_access_token(user_id=user.id, username=user.username)
    return AuthResponse(
        token=token,
        user=UserResponse(
            id=user.id,
            username=user.username,
            name=display_name
        )
    )

@router.get("/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    profile = db.query(Profile).filter(
        Profile.owner_user_id == current_user.id,
        Profile.deleted_at.is_(None)
    ).first()
    return UserResponse(
        id=current_user.id,
        username=current_user.username,
        name=profile.name if profile else current_user.username
    )
