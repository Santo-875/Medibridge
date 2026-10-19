# MediBridge One-Click Runner (Backend + Cloudflare Tunnel)
$ErrorActionPreference = "Continue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Starting MediBridge Unified Backend on Port 8000...   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Start FastAPI Backend in background job
$backendJob = Start-Job -ScriptBlock {
    Set-Location "$using:PSScriptRoot\backend"
    & "$using:PSScriptRoot\.venv\Scripts\uvicorn.exe" main:app --host 0.0.0.0 --port 8000
}

Start-Sleep -Seconds 3

Write-Host "`nBackend started! Now creating Cloudflare Public Tunnel..." -ForegroundColor Green
Write-Host "NOTE: Copy the HTTPS URL below and paste it into the Android App Settings screen.`n" -ForegroundColor Yellow

# 2. Launch cloudflared tunnel
cloudflared tunnel --url http://localhost:8000
