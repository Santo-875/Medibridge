import re
import time
import uuid
from collections import defaultdict
from typing import Optional, List, Dict
from fastapi import APIRouter, Depends, HTTPException, Header, Request, status
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session
from database import get_db
from models import User, Profile, utcnow
from auth import (
    EXPECTED_API_KEY, hash_password, verify_password,
    create_access_token
)

router = APIRouter(prefix="/auth", tags=["auth"])

USERNAME_REGEX = re.compile(r"^[a-zA-Z0-9_]{3,20}$")
login_rate_limits: Dict[str, List[float]] = defaultdict(list)

def check_login_rate_limit(request: Request):
    ip = request.client.host if request.client else "unknown"
    now = time.time()
    attempts = [t for t in login_rate_limits[ip] if now - t < 60.0]
    if len(attempts) >= 5:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Too many login attempts. Please wait a minute before retrying."
        )
    attempts.append(now)
    login_rate_limits[ip] = attempts

def verify_api_key(x_api_key: Optional[str] = Header(None, alias="X-API-Key")):
    if not x_api_key or x_api_key != EXPECTED_API_KEY:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or missing X-API-Key"
        )

class RegisterRequest(BaseModel):
    username: str
    password: str
    name: str

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
    db: Session = Depends(get_db),
    _: None = Depends(verify_api_key)
):
    raw_username = payload.username.strip().lower()
    if not USERNAME_REGEX.match(raw_username):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Username must be 3-20 characters long and contain only letters, numbers, and underscores."
        )

    if len(payload.password) < 8:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Password must be at least 8 characters long."
        )

    existing = db.query(User).filter(User.username == raw_username).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Username is already taken."
        )

    user_id = str(uuid.uuid4())
    pw_hash = hash_password(payload.password)
    user = User(
        id=user_id,
        username=raw_username,
        password_hash=pw_hash,
        created_at=utcnow(),
        updated_at=utcnow()
    )
    db.add(user)

    display_name = payload.name.strip() or raw_username
    profile = Profile(
        id=str(uuid.uuid4()),
        owner_user_id=user_id,
        name=display_name,
        created_at=utcnow(),
        updated_at=utcnow()
    )
    db.add(profile)
    db.commit()

    token = create_access_token(user_id=user.id, username=user.username)
    return AuthResponse(
        token=token,
        user=UserResponse(
            id=user.id,
            username=user.username,
            name=profile.name
        )
    )

@router.post("/login", response_model=AuthResponse)
def login(
    payload: LoginRequest,
    request: Request,
    db: Session = Depends(get_db),
    _: None = Depends(verify_api_key)
):
    check_login_rate_limit(request)

    raw_username = payload.username.strip().lower()
    user = db.query(User).filter(User.username == raw_username).first()

    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password"
        )

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
