import os
from pathlib import Path
from sqlalchemy import create_engine
from sqlalchemy.orm import declarative_base, sessionmaker
from dotenv import load_dotenv

_env_path = Path(__file__).parent / ".env"
if _env_path.exists():
    load_dotenv(dotenv_path=_env_path)
else:
    load_dotenv()

raw_url = os.getenv("DATABASE_URL", "").strip()
if not raw_url:
    # Local fallback when Supabase connection string is not yet supplied in .env
    DB_URL = "sqlite:///./medi_bridge.db"
elif raw_url.startswith("postgres://"):
    DB_URL = raw_url.replace("postgres://", "postgresql+psycopg://", 1)
elif raw_url.startswith("postgresql://") and not raw_url.startswith("postgresql+psycopg://"):
    DB_URL = raw_url.replace("postgresql://", "postgresql+psycopg://", 1)
else:
    DB_URL = raw_url

connect_args = {"check_same_thread": False} if DB_URL.startswith("sqlite") else {}

engine = create_engine(DB_URL, connect_args=connect_args, pool_pre_ping=True)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def init_db():
    from models import (
        User, Profile, CareLink, Medication,
        DoseTime, DoseLog, Prescription, Recording,
        SafetyCheck, Notification, Device
    )
    Base.metadata.create_all(bind=engine)
