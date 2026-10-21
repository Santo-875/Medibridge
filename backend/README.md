# MediBridge Unified Backend

FastAPI backend providing persistent synchronization, care links, AI prescription OCR, and doctor consultation speech-to-text processing for MediBridge AI.

---

## 🚀 Deployment to Render / Railway

### 1. Render Deployment
1. Create a new **Web Service** on [Render](https://render.com).
2. Connect your Git repository (branch `scanSTT` or main).
3. Set the **Root Directory** to `backend`.
4. Choose **Docker** as the Runtime environment (Render will automatically detect `backend/Dockerfile`).
5. Configure the following **Environment Variables** in the Render Dashboard:

| Key | Description | Example |
|---|---|---|
| `DATABASE_URL` | Supabase PostgreSQL Connection String | `postgresql+psycopg://postgres.xxx:pass@aws-0-xx.pooler.supabase.com:6543/postgres` |
| `API_KEY` | Shared API key for client authentication | `medibridge_secret_api_key_2026` |
| `GROQ_API_KEY` | Groq API Key for LLaMA 3.3 clinical reasoning | `gsk_...` |
| `SARVAM_API_KEY` | Sarvam AI Key for Tamil/Indic speech-to-text | `...` |
| `GEMINI_API_KEY` | Google Gemini API Key for vision parsing | `AIzaSy...` |

6. Click **Deploy Web Service**. Once deployed, Render provides a permanent HTTPS URL (e.g., `https://medibridge-backend.onrender.com`).

---

### 2. Railway Deployment
1. In [Railway](https://railway.app), create a new project and select **Deploy from GitHub repo**.
2. Under Settings:
   - Set **Root Directory** to `/backend`.
   - Set **Builder** to `DOCKERFILE`.
3. Add the environment variables listed above (`DATABASE_URL`, `API_KEY`, `GROQ_API_KEY`, etc.).
4. Under Networking, click **Generate Domain** to get your public HTTPS URL.

---

### 3. Updating the Android App
Once deployed, set the permanent URL in the root project `.env`:
```properties
BACKEND_URL=https://your-app-name.onrender.com/
API_KEY=medibridge_secret_api_key_2026
```
Rebuild the Android application, or paste the URL once inside the hidden **Developer Options** section on the app's Profile & Settings screen.

---

## 💻 Local Development & Tunnels
For local development without cloud hosting:
1. Copy `.env.example` to `.env` and fill in API keys:
   ```bash
   cp .env.example .env
   ```
2. Run the local tunnel script from the repository root:
   ```powershell
   .\run_tunnel.ps1
   ```
   *`run_tunnel.ps1` starts Uvicorn locally on port 8000 and opens a Cloudflare tunnel for Android device testing.*

---

## 🧪 Testing Backend Authorization & Sync
Run the test suite using pytest:
```bash
python -m pytest -v
```
All 10 tests verify:
- API key verification & missing `X-User-Id` rejection
- Owner profile authorization and stranger access denial
- Care link invitations, acceptance, and tiered permissions (`view`, `manage`, `alerts`)
- Care link revocation
- Device FCM token registration
- Sync pull & push with last-write-wins merging
- Missed dose 30-minute escalation
