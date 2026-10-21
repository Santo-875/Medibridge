import os
import time
import pytest
from datetime import datetime, timezone, timedelta
import jwt
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

os.environ["API_KEY"] = "test_secret_key"
os.environ["JWT_SECRET"] = "test_secret_jwt_key_at_least_32_characters_long"
os.environ["DATABASE_URL"] = "sqlite:///:memory:"

from database import Base, get_db
from main import app
from auth import create_access_token, JWT_SECRET, JWT_ALGORITHM
from auth_router import login_rate_limits
from models import User, Profile, DoseLog, Medication

TEST_ENGINE = create_engine(
    "sqlite:///:memory:",
    connect_args={"check_same_thread": False},
    poolclass=StaticPool
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=TEST_ENGINE)

def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()

app.dependency_overrides[get_db] = override_get_db

@pytest.fixture(autouse=True)
def setup_db():
    Base.metadata.create_all(bind=TEST_ENGINE)
    login_rate_limits.clear()
    yield
    Base.metadata.drop_all(bind=TEST_ENGINE)

client = TestClient(app)

API_HEADERS = {"X-API-Key": "test_secret_key"}

def auth_headers(token: str) -> dict:
    return {
        "Authorization": f"Bearer {token}",
        "X-API-Key": "test_secret_key"
    }

def create_test_user_with_token(user_id: str, username: str, name: str = "Test User") -> str:
    db = TestingSessionLocal()
    u = User(id=user_id, username=username, password_hash="hashed")
    db.add(u)
    p = Profile(id=f"prof-{user_id}", owner_user_id=user_id, name=name)
    db.add(p)
    db.commit()
    db.close()
    return create_access_token(user_id, username)

# ── 1. Authentication Tests ──────────────────────────────────────────────────

def test_register_success():
    res = client.post(
        "/auth/register",
        json={"username": "alex_care", "password": "securePassword123", "name": "Alex Care"},
        headers=API_HEADERS
    )
    assert res.status_code == 200
    data = res.json()
    assert "token" in data
    assert data["user"]["username"] == "alex_care"
    assert data["user"]["name"] == "Alex Care"

    # Verify token can access /profiles
    token = data["token"]
    profiles_res = client.get("/profiles", headers=auth_headers(token))
    assert profiles_res.status_code == 200
    assert len(profiles_res.json()) >= 1

def test_register_validation():
    # Password too short (< 8)
    res = client.post(
        "/auth/register",
        json={"username": "valid_user", "password": "123", "name": "User"},
        headers=API_HEADERS
    )
    assert res.status_code == 400

    # Username too short (< 3)
    res = client.post(
        "/auth/register",
        json={"username": "ab", "password": "password123", "name": "User"},
        headers=API_HEADERS
    )
    assert res.status_code == 400

    # Username invalid characters
    res = client.post(
        "/auth/register",
        json={"username": "bad user!", "password": "password123", "name": "User"},
        headers=API_HEADERS
    )
    assert res.status_code == 400

def test_register_taken_username():
    client.post(
        "/auth/register",
        json={"username": "duplicate_user", "password": "password123", "name": "First"},
        headers=API_HEADERS
    )
    res2 = client.post(
        "/auth/register",
        json={"username": "DUPLICATE_USER", "password": "password123", "name": "Second"},
        headers=API_HEADERS
    )
    assert res2.status_code == 409
    assert "already taken" in res2.json()["detail"].lower()

def test_login_success():
    client.post(
        "/auth/register",
        json={"username": "john_doe", "password": "secretPassword123", "name": "John Doe"},
        headers=API_HEADERS
    )
    res = client.post(
        "/auth/login",
        json={"username": "john_doe", "password": "secretPassword123"},
        headers=API_HEADERS
    )
    assert res.status_code == 200
    data = res.json()
    assert "token" in data
    assert data["user"]["username"] == "john_doe"

def test_login_wrong_password():
    client.post(
        "/auth/register",
        json={"username": "mary_jane", "password": "correctPassword123", "name": "Mary"},
        headers=API_HEADERS
    )
    res = client.post(
        "/auth/login",
        json={"username": "mary_jane", "password": "wrongPassword123"},
        headers=API_HEADERS
    )
    assert res.status_code == 401
    assert res.json()["detail"] == "Invalid username or password"

def test_login_wrong_username():
    res = client.post(
        "/auth/login",
        json={"username": "non_existent_user", "password": "anyPassword123"},
        headers=API_HEADERS
    )
    assert res.status_code == 401
    assert res.json()["detail"] == "Invalid username or password"

def test_login_rate_limiting():
    for _ in range(5):
        res = client.post(
            "/auth/login",
            json={"username": "fake_user", "password": "wrongPassword123"},
            headers=API_HEADERS
        )
        assert res.status_code == 401

    # 6th attempt should be rate limited
    res6 = client.post(
        "/auth/login",
        json={"username": "fake_user", "password": "wrongPassword123"},
        headers=API_HEADERS
    )
    assert res6.status_code == 429

def test_expired_and_invalid_token():
    owner_token = create_test_user_with_token("u-tok", "user_tok")

    # Valid token works
    res = client.get("/profiles", headers=auth_headers(owner_token))
    assert res.status_code == 200

    # Malformed token
    res_bad = client.get("/profiles", headers=auth_headers("invalid.jwt.token"))
    assert res_bad.status_code == 401

    # Expired token
    expired_payload = {
        "sub": "u-tok",
        "username": "user_tok",
        "exp": datetime.now(timezone.utc) - timedelta(days=1),
        "iat": datetime.now(timezone.utc) - timedelta(days=2)
    }
    expired_token = jwt.encode(expired_payload, JWT_SECRET, algorithm=JWT_ALGORITHM)
    res_exp = client.get("/profiles", headers=auth_headers(expired_token))
    assert res_exp.status_code == 401

def test_missing_or_invalid_api_key():
    token = create_test_user_with_token("u-key", "user_key")

    # Missing API key
    res = client.get("/profiles", headers={"Authorization": f"Bearer {token}"})
    assert res.status_code == 401

    # Invalid API key
    res2 = client.get("/profiles", headers={"Authorization": f"Bearer {token}", "X-API-Key": "wrong"})
    assert res2.status_code == 401

# ── 2. Profile Authorization & Care Link Tests ───────────────────────────────

def test_owner_create_and_access_profile():
    owner_token = create_test_user_with_token("u-owner", "owner_user")
    headers = auth_headers(owner_token)

    create_res = client.post(
        "/profiles",
        json={"id": "prof-my-mom", "name": "Mom"},
        headers=headers
    )
    assert create_res.status_code == 200
    assert create_res.json()["name"] == "Mom"

    get_res = client.get("/profiles/prof-my-mom", headers=headers)
    assert get_res.status_code == 200
    assert get_res.json()["is_owner"] is True

def test_stranger_access_denied():
    owner_token = create_test_user_with_token("u-owner-2", "owner_two")
    stranger_token = create_test_user_with_token("u-stranger-2", "stranger_two")

    client.post(
        "/profiles",
        json={"id": "prof-private-2", "name": "Private Profile"},
        headers=auth_headers(owner_token)
    )

    get_res = client.get("/profiles/prof-private-2", headers=auth_headers(stranger_token))
    assert get_res.status_code == 403

def test_care_link_invite_accept_and_permissions():
    owner_token = create_test_user_with_token("u-owner-3", "owner_three")
    caretaker_token = create_test_user_with_token("u-caretaker-3", "caretaker_three")

    client.post(
        "/profiles",
        json={"id": "prof-shared", "name": "Shared Patient"},
        headers=auth_headers(owner_token)
    )

    invite_res = client.post(
        "/care/invite",
        json={"profile_id": "prof-shared", "permission": "view"},
        headers=auth_headers(owner_token)
    )
    assert invite_res.status_code == 200
    code = invite_res.json()["invite_code"]

    accept_res = client.post(
        "/care/accept",
        json={"invite_code": code},
        headers=auth_headers(caretaker_token)
    )
    assert accept_res.status_code == 200
    assert accept_res.json()["permission"] == "view"

    read_res = client.get("/profiles/prof-shared", headers=auth_headers(caretaker_token))
    assert read_res.status_code == 200
    assert read_res.json()["is_owner"] is False

def test_care_link_manage_permission():
    owner_token = create_test_user_with_token("u-owner-4", "owner_four")
    caretaker_token = create_test_user_with_token("u-caretaker-4", "caretaker_four")

    client.post(
        "/profiles",
        json={"id": "prof-managed", "name": "Managed Profile"},
        headers=auth_headers(owner_token)
    )
    invite_res = client.post(
        "/care/invite",
        json={"profile_id": "prof-managed", "permission": "manage"},
        headers=auth_headers(owner_token)
    )
    code = invite_res.json()["invite_code"]

    client.post("/care/accept", json={"invite_code": code}, headers=auth_headers(caretaker_token))

    sub_invite = client.post(
        "/care/invite",
        json={"profile_id": "prof-managed", "permission": "alerts"},
        headers=auth_headers(caretaker_token)
    )
    assert sub_invite.status_code == 200

def test_care_link_revoke():
    owner_token = create_test_user_with_token("u-owner-5", "owner_five")
    caretaker_token = create_test_user_with_token("u-caretaker-5", "caretaker_five")

    client.post(
        "/profiles",
        json={"id": "prof-revoke", "name": "Revoke Test Profile"},
        headers=auth_headers(owner_token)
    )
    invite_res = client.post(
        "/care/invite",
        json={"profile_id": "prof-revoke", "permission": "view"},
        headers=auth_headers(owner_token)
    )
    link_id = invite_res.json()["care_link_id"]
    code = invite_res.json()["invite_code"]

    client.post("/care/accept", json={"invite_code": code}, headers=auth_headers(caretaker_token))

    revoke_res = client.post(
        "/care/revoke",
        json={"care_link_id": link_id},
        headers=auth_headers(owner_token)
    )
    assert revoke_res.status_code == 200

    denied_res = client.get("/profiles/prof-revoke", headers=auth_headers(caretaker_token))
    assert denied_res.status_code == 403

def test_device_registration():
    token = create_test_user_with_token("u-dev", "user_dev")
    res = client.post(
        "/devices",
        json={"fcm_token": "fcm_test_token_12345", "platform": "android"},
        headers=auth_headers(token)
    )
    assert res.status_code == 200
    assert res.json()["status"] in ("registered", "updated")

def test_sync_pull_and_push():
    owner_token = create_test_user_with_token("u-sync-owner", "sync_owner")
    stranger_token = create_test_user_with_token("u-sync-stranger", "sync_stranger")

    client.post(
        "/profiles",
        json={"id": "prof-sync", "name": "Sync Profile"},
        headers=auth_headers(owner_token)
    )

    push_res = client.post(
        "/sync/push",
        json={
            "medications": [
                {
                    "id": "med-1",
                    "profile_id": "prof-sync",
                    "name": "Metformin",
                    "dosage": "500mg",
                    "timing": "After meals"
                }
            ]
        },
        headers=auth_headers(owner_token)
    )
    assert push_res.status_code == 200

    pull_res = client.get("/sync/pull", headers=auth_headers(owner_token))
    assert pull_res.status_code == 200
    pulled = pull_res.json()
    assert len(pulled["medications"]) == 1
    assert pulled["medications"][0]["name"] == "Metformin"

    stranger_pull = client.get("/sync/pull", headers=auth_headers(stranger_token))
    assert stranger_pull.status_code == 200
    assert len(stranger_pull.json()["medications"]) == 0

def test_missed_dose_escalation():
    owner_token = create_test_user_with_token("u-alert-owner", "alert_owner")
    caretaker_token = create_test_user_with_token("u-alert-caretaker", "alert_caretaker")

    client.post("/profiles", json={"id": "prof-alert", "name": "Grandpa"}, headers=auth_headers(owner_token))
    inv = client.post("/care/invite", json={"profile_id": "prof-alert", "permission": "alerts"}, headers=auth_headers(owner_token)).json()
    client.post("/care/accept", json={"invite_code": inv["invite_code"]}, headers=auth_headers(caretaker_token))

    db = TestingSessionLocal()
    med = Medication(
        id="med-alert-1",
        profile_id="prof-alert",
        name="Lisinopril",
        dosage="10mg"
    )
    db.add(med)
    overdue_log = DoseLog(
        id="dose-log-overdue-1",
        profile_id="prof-alert",
        medication_id=med.id,
        scheduled_time="08:00 AM",
        status="PENDING",
        created_at=datetime.now(timezone.utc) - timedelta(minutes=45),
        updated_at=datetime.now(timezone.utc) - timedelta(minutes=45)
    )
    db.add(overdue_log)
    db.commit()
    db.close()

    res = client.post("/care/check-missed-doses", headers=auth_headers(owner_token))
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert data["escalated_count"] >= 1
