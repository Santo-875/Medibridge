# MediBridge AI — Android Shell

> **24-hour hackathon shell** | Branch: `integration` | Kotlin + Jetpack Compose + Material3

A modular AI-powered medication management app. This repository is the **integration shell** — 4 teams wire their modules into this codebase.

---

## 🏗️ Project Structure

```
com.medibridge/
├── core/
│   ├── model/          ← MedicationObject.kt  ← SHARED CONTRACT (read this first!)
│   ├── db/             ← Room DB: MedicationEntity, MedicationDao, AppDatabase
│   └── theme/          ← Color.kt, Type.kt, Theme.kt (MediBridgeTheme)
│
├── moduleA_prescription/   ← Module A: OCR + AI Extraction (STUB)
├── moduleB_safety/         ← Module B: Drug Interaction Checker (STUB)
├── moduleC_schedule/       ← Module C: Schedule Engine + Reminders (STUB)
└── moduleD_shell/          ← Module D: Navigation, Home, Chatbot, Settings
    ├── navigation/         ← Screen.kt, MediBridgeNavGraph.kt
    ├── ui/                 ← HomeScreen, ChatbotScreen, RemindersScreen,
    │                           ScannerScreen, SettingsScreen, MainShell
    └── viewmodel/          ← SettingsViewModel
```

---

## 📦 MedicationObject Schema (core contract)

> **All 4 modules read/write ONLY via `MedicationObject`.**  
> Persist via `dao.upsertMedication(med.toEntity())`, restore via `entity.fromEntity()`.

| Field | Type | Description | Owner |
|-------|------|-------------|-------|
| `id` | `String` | UUID, primary key | Module A |
| `name` | `String` | Medicine name | Module A |
| `strength` | `String` | e.g. "500mg" | Module A |
| `dose` | `String` | e.g. "1 tablet" | Module A |
| `frequency` | `String` | e.g. "Twice daily" | Module A |
| `timing` | `String` | e.g. "After breakfast" | Module A |
| `duration` | `String` | e.g. "30 days" | Module A |
| `confidence` | `Float` | 0.0–1.0 AI confidence | Module A |
| `needsVerification` | `Boolean` | True if confidence < 0.80 | Module A |
| `verifiedByUser` | `Boolean` | User confirmed | Module D |
| `crossVerified` | `Boolean` | Module B ran safety check | Module B |
| `conflicts` | `List<MedicationConflict>` | Drug interactions found | Module B |
| `reviewRecommended` | `Boolean` | Doctor review suggested | Module B |
| `schedule` | `List<ScheduleSlot>` | Daily dose schedule | Module C |
| `adherence` | `List<AdherenceRecord>` | Dose-taken log | Module C/D |
| `summary` | `String` | Patient-readable summary | Module A/D |
| `sideEffects` | `List<String>` | Known side effects | Module A |
| `visibleTo` | `List<String>` | Role access control | Future |
| `imageUrl` | `String?` | Prescription image URL | Module A |

### Sub-types

```kotlin
data class MedicationConflict(
    val withMedId: String,   // References another med's ID
    val type: String,        // "interaction" | "duplicate" | "contraindication" | "dosage_warning"
    val detail: String       // Human-readable explanation
)

data class ScheduleSlot(
    val time: String,        // "08:00" (24h)
    val slot: String,        // "Morning" | "Afternoon" | "Night"
    val withFood: Boolean
)

data class AdherenceRecord(
    val date: String,        // ISO date "2024-11-01"
    val status: String       // "taken" | "missed" | "snoozed"
)
```

### Adding a new field (how to extend)
1. Add to `MedicationObject` with a default value
2. Add to `MedicationEntity` (as a column or JSON string)
3. Update `toEntity()` and `fromEntity()` in `MedicationObject.kt`
4. Bump `version` in `AppDatabase.kt` and add a `Migration`

---

## 🧩 Module Integration Guide

### Module A — Prescription Scanner
- **Package:** `com.medibridge.moduleA_prescription`
- **Entry point:** `ScannerScreen.kt` (replace placeholder)
- **Output:** `MedicationObject` → persist via `dao.upsertMedication(med.toEntity())`
- **Key TODOs:** CameraX + ML Kit OCR, confidence scoring, verification UI

### Module B — Safety Cross-Verification
- **Package:** `com.medibridge.moduleB_safety`
- **Trigger:** After any new medication is added
- **Read:** `dao.getAllMedications()`, check interactions
- **Write back:** `dao.upsertMedication(entity.copy(crossVerified = true, conflicts = ...))`

### Module C — Schedule Engine
- **Package:** `com.medibridge.moduleC_schedule`
- **Wire into:** `RemindersScreen.kt` (Taken/Missed/Snooze onClick stubs)
- **Write adherence:** Update `AdherenceRecord` list in the entity
- **Notifications:** Use AlarmManager or WorkManager for reminders

### Module D — Shell & Chatbot
- **Package:** `com.medibridge.moduleD_shell`
- **Chatbot:** `ChatbotScreen.kt` → replace `dummyBotReply()` with Retrofit AI call
- **Home:** Replace `mockMedications` with `dao.getAllMedications()` Flow

---

## 🚀 Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material3 |
| Navigation | Navigation Compose |
| DB | Room (KSP annotation processor) |
| State | ViewModel + StateFlow |
| Images | Coil |
| Network | Retrofit + OkHttp (future AI calls) |
| Serialization | Gson (JSON fields in Room) |

---

## 🎨 Theme

All colors are defined in `core/theme/Color.kt`.  
**Never hardcode colors** — always use `MaterialTheme.colorScheme.*`.

| Role | Light | Dark |
|------|-------|------|
| Primary | `#0D9488` (Teal) | `#0D9488` |
| Background | `#F8FAFC` | `#121212` |
| On Background | `#0F172A` | `#E0E0E0` |
| Verified | `#16A34A` (Green) | `#4ADE80` |
| Review | `#D97706` (Amber) | `#FBBF24` |
| Conflict | `#DC2626` (Red) | `#F87171` |

---

## 🔧 Build & Run

```bash
# Clone and switch to integration branch
git clone https://github.com/YOUR_ORG/MediBridge.git
cd MediBridge
git checkout integration

# Open in Android Studio Hedgehog or later
# Build → Run on emulator (API 26+)
```

**Requirements:**
- Android Studio Hedgehog 2023.1.1+
- JDK 11+
- Android SDK API 35
- Gradle 8.7

---

## 📁 Navigation Routes

```kotlin
Screen.Home       → "home"       (bottom nav)
Screen.Reminders  → "reminders"  (bottom nav)
Screen.Settings   → "settings"   (bottom nav)
Screen.Scanner    → "scanner"    (from Home top bar icon)
Screen.Chatbot    → "chatbot"    (from Home FAB "Medi")
```

To add a new module screen:
1. Add `object MyScreen : Screen("my_screen")` to `Screen.kt`
2. Add `composable(Screen.MyScreen.route) { MyScreen(...) }` to `MediBridgeNavGraph.kt`

---

## 👥 Team

| Module | Owner | Status |
|--------|-------|--------|
| Shell (D) | — | ✅ Shell built |
| Prescription (A) | — | 🔲 Stub ready |
| Safety (B) | — | 🔲 Stub ready |
| Schedule (C) | — | 🔲 Stub ready |

---

*MediBridge AI — Hackathon 2024 · Branch: `integration`*
