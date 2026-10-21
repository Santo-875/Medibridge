import uuid
from datetime import datetime, timezone
from sqlalchemy import Column, String, Integer, Boolean, DateTime, ForeignKey, Text
from sqlalchemy.orm import relationship
from database import Base

def utcnow():
    return datetime.now(timezone.utc)

class User(Base):
    __tablename__ = "users"

    id = Column(String(64), primary_key=True)
    username = Column(String(32), unique=True, index=True, nullable=True)
    password_hash = Column(String(256), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)

    profiles = relationship("Profile", back_populates="owner")
    devices = relationship("Device", back_populates="user")

class Profile(Base):
    __tablename__ = "profiles"

    id = Column(String(64), primary_key=True)
    owner_user_id = Column(String(64), ForeignKey("users.id"), nullable=False, index=True)
    name = Column(String(128), nullable=False)
    avatar = Column(String(256), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    owner = relationship("User", back_populates="profiles")
    care_links = relationship("CareLink", back_populates="profile")
    medications = relationship("Medication", back_populates="profile")

class CareLink(Base):
    __tablename__ = "care_links"

    id = Column(String(64), primary_key=True)
    profile_id = Column(String(64), ForeignKey("profiles.id"), nullable=False, index=True)
    caretaker_user_id = Column(String(64), ForeignKey("users.id"), nullable=True, index=True)
    permission = Column(String(32), nullable=False, default="view")  # alerts, view, manage
    status = Column(String(32), nullable=False, default="pending", index=True)  # pending, active, revoked
    invite_code = Column(String(64), unique=True, index=True, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    profile = relationship("Profile", back_populates="care_links")

class Medication(Base):
    __tablename__ = "medications"

    id = Column(String(64), primary_key=True)
    profile_id = Column(String(64), ForeignKey("profiles.id"), nullable=False, index=True)
    name = Column(String(256), nullable=False)
    dosage = Column(String(128), nullable=True)
    strength = Column(String(128), nullable=True)
    timing = Column(String(128), nullable=True)
    food_rule = Column(String(128), nullable=True)
    duration_days = Column(Integer, nullable=True)
    source = Column(String(64), nullable=True)
    rxcui = Column(String(64), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    profile = relationship("Profile", back_populates="medications")
    dose_times = relationship("DoseTime", back_populates="medication")
    dose_logs = relationship("DoseLog", back_populates="medication")

class DoseTime(Base):
    __tablename__ = "dose_times"

    id = Column(String(64), primary_key=True)
    medication_id = Column(String(64), ForeignKey("medications.id"), nullable=False, index=True)
    profile_id = Column(String(64), ForeignKey("profiles.id"), nullable=False, index=True)
    time = Column(String(32), nullable=False)  # HH:MM
    slot = Column(String(32), nullable=False, default="MORNING")
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    medication = relationship("Medication", back_populates="dose_times")

class DoseLog(Base):
    __tablename__ = "dose_logs"

    id = Column(String(64), primary_key=True)
    profile_id = Column(String(64), ForeignKey("profiles.id"), nullable=False, index=True)
    medication_id = Column(String(64), ForeignKey("medications.id"), nullable=False, index=True)
    dose_time_id = Column(String(64), nullable=True)
    scheduled_time = Column(String(64), nullable=False)
    status = Column(String(32), nullable=False, default="PENDING")  # PENDING, TAKEN, MISSED, SNOOZED
    taken_at = Column(DateTime(timezone=True), nullable=True)
    snooze_until = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

    medication = relationship("Medication", back_populates="dose_logs")

class Prescription(Base):
    __tablename__ = "prescriptions"

    id = Column(String(64), primary_key=True)
    profile_id = Column(String(64), ForeignKey("profiles.id"), nullable=True, index=True)
    file_path = Column(String(512), nullable=True)
    ocr_raw_text = Column(Text, nullable=True)
    structured_json = Column(Text, nullable=True)
    prescribed_by = Column(String(256), nullable=True)
    date = Column(String(64), nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

class Recording(Base):
    __tablename__ = "recordings"

    id = Column(String(64), primary_key=True)
    profile_id = Column(String(64), ForeignKey("profiles.id"), nullable=True, index=True)
    file_path = Column(String(512), nullable=True)
    transcript_tamil = Column(Text, nullable=True)
    transcript_english = Column(Text, nullable=True)
    summary_json = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

class SafetyCheck(Base):
    __tablename__ = "safety_checks"

    id = Column(String(64), primary_key=True)
    profile_id = Column(String(64), ForeignKey("profiles.id"), nullable=False, index=True)
    medication_id = Column(String(64), nullable=True)
    conflict_type = Column(String(64), nullable=False)
    severity = Column(String(32), nullable=False)  # HIGH, MEDIUM, LOW
    details = Column(Text, nullable=False)
    action_required = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

class Notification(Base):
    __tablename__ = "notifications"

    id = Column(String(64), primary_key=True)
    profile_id = Column(String(64), ForeignKey("profiles.id"), nullable=False, index=True)
    type = Column(String(64), nullable=False)
    message = Column(Text, nullable=False)
    is_read = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow, index=True)
    deleted_at = Column(DateTime(timezone=True), nullable=True)

class Device(Base):
    __tablename__ = "devices"

    id = Column(String(64), primary_key=True)
    user_id = Column(String(64), ForeignKey("users.id"), nullable=False, index=True)
    fcm_token = Column(String(512), nullable=False)
    platform = Column(String(32), default="android")
    created_at = Column(DateTime(timezone=True), default=utcnow)
    updated_at = Column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)

    user = relationship("User", back_populates="devices")
