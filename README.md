# MediBridge AI — Intelligent Medication Adherence & Clinical Safety Platform

MediBridge is a modular, AI-powered healthcare assistant designed to bridge communication between patients, clinical prescriptions, safety validation, and caretakers.

---

## 🏗️ Architecture & Features

- **Module A (Prescription & Vision Intelligence):** Hybrid CameraX & gallery OCR scanning with LLM structuring (Gemini / Groq fallback), confidence scoring, manual verification workflows, and medical supply classification.
- **Module B (Clinical Safety & Conflict Engine):** Multi-drug interaction screening, duplicate therapy detection, food rule validation, and contraindication flagging.
- **Module C (Smart Scheduling & Dose Monitoring):** Time-slot reminders, 3-state adherence logging (Taken, Snoozed, Missed), and background missed-dose escalation.
- **Module D (Shell & Caretaker Network):** Multi-profile management, invite-code caretaker linkage, synchronized patient monitoring, offline-first sync engine with SQLite/Room, and Medi AI clinical chat assistant.
- **Backend & Sync Engine:** FastAPI asynchronous REST API, SQLite database, APScheduler / in-process missed-dose checker, multi-profile RBAC, and delta sync endpoints (`/sync/push`, `/sync/pull`).

---

## 🚀 Quick Start Guide

### 1. Backend Setup

**Prerequisites:** Python 3.10+

1. Navigate to the backend directory:
   ```bash
   cd backend
   ```

2. Create and activate a virtual environment:
   ```bash
   python -m venv .venv
   # Windows PowerShell:
   .\.venv\Scripts\Activate.ps1
   # macOS / Linux:
   source .venv/bin/activate
   ```

3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

4. Configure environment variables (`backend/.env`):
   ```env
   DATABASE_URL=sqlite:///./medibridge.db
   JWT_SECRET=your_jwt_secret_key_here
   API_KEY=your_internal_api_key_here
   GEMINI_API_KEY=your_gemini_api_key
   GROQ_API_KEY=your_groq_api_key
   SARVAM_API_KEY=your_sarvam_api_key
   ```

5. Start the FastAPI backend server:
   ```bash
   uvicorn main:app --host 0.0.0.0 --port 8000 --reload
   ```

### 2. Expose via Tunnel (for Android Device Testing)

If testing on a physical Android phone or external network, tunnel the local backend:
```bash
ngrok http 8000
```
Update the base URL in `app/src/main/java/com/medibridge/core/network/ApiConstants.kt` or `local.properties` with your assigned ngrok URL or static domain.

### 3. Android App Setup

**Prerequisites:** Android Studio Ladybug / Hedgehog or newer, JDK 17+, Android SDK API 35.

1. Open the project root in Android Studio.
2. Build debug APK from command line:
   ```bash
   # Windows:
   .\gradlew.bat assembleDebug

   # macOS / Linux:
   ./gradlew assembleDebug
   ```
3. The compiled APK is generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 🔔 Missed-Dose Alerts & Caretaker Escalation

1. **Automatic Detection:** The backend runs a background task every 5 minutes checking for scheduled doses that are pending for 30+ minutes past their scheduled time.
2. **Escalation:** An in-system `MISSED_DOSE` notification is recorded for all active caretakers linked to that patient profile.
3. **Delivery:** The caretaker device receives notifications via `/sync/pull`, alerting the caretaker with a prominent home card, a tray item, and a local high-priority alert.
4. **FCM Push (Optional):** To enable remote push via Firebase Cloud Messaging, place your `google-services.json` in `app/` and register tokens via `/devices`.

---

## 🧪 Testing & Verification

- **Backend Unit Tests:**
  ```bash
  pytest backend/tests/
  ```
- **Android Unit Tests:**
  ```bash
  .\gradlew.bat testDebugUnitTest
  ```
