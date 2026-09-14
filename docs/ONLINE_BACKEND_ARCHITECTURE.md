# Sahara Online Backend Architecture

Stage 0 audit and architecture proposal. No application source code was modified to produce this
document. It reflects the state of `app/src/main/java/com/yourteam/sahara` as of this audit.

## 1. Audit Findings

### 1.1 What is fully local today

| Area | Implementation | Notes |
|---|---|---|
| Persistence | Room, `AppDatabase` v6, single SQLite file `sahara_database` | 8 entities: accounts, caregiver_patients, local_session, patients, game_results, reminders, caregiver_alerts, sync_queue |
| Auth | [`AuthRepository`](../app/src/main/java/com/yourteam/sahara/auth/AuthRepository.kt), [`RoomAuthDataSource`](../app/src/main/java/com/yourteam/sahara/auth/RoomAuthDataSource.kt), [`PasswordHasher`](../app/src/main/java/com/yourteam/sahara/auth/PasswordHasher.kt) | PBKDF2WithHmacSHA256, 210k iterations, salted. Session is a single-row `local_session` table (id=1) — one active caregiver session per device, no tokens, no expiry. Explicitly documented in code as "not production-grade... no server verification". |
| Patient access control | `AuthRepository.requirePatient()`, checked in every repository (`GameResultRepository`, `PatientRepository`, `ReminderRepository`) | Enforced entirely client-side against the local `caregiver_patients` link table. There is no server to re-check this, so a modified APK/rooted device can bypass it. |
| Adaptive engine | [`AdaptiveDifficultyEngine`](../app/src/main/java/com/yourteam/sahara/ai/AdaptiveDifficultyEngine.kt), [`PerformanceAnalyzer`](../app/src/main/java/com/yourteam/sahara/ai/PerformanceAnalyzer.kt) | Pure functions over `List<GameResult>`, runs on-device, no network dependency. Deterministic, unit-tested. Should not be replaced. |
| Caregiver dashboard analytics | [`CaregiverDashboardAnalyzer`](../app/src/main/java/com/yourteam/sahara/ai/CaregiverDashboardAnalyzer.kt), [`CaregiverInsightEngine`](../app/src/main/java/com/yourteam/sahara/ai/CaregiverInsightEngine.kt) | Also pure/local, computed from whatever `GameResult` rows exist in the *local* Room DB on that device. |
| Reminders + notifications | `ReminderRepository`, `AndroidAlarmGateway`, `ReminderScheduler`, `ReminderNotifier`, `WorkManager`/`AlarmManager` | 100% local; explicitly documented as "outside SyncManager's queue... never reach a backend". |
| Language | `LanguageManager`, `values/values-hi/values-as` string resources | Fully local, persists to SharedPreferences / platform LocaleManager (API 33+). No backend dependency. |
| Voice | `VoiceManager`, `SpeechRecognizerManager`, `TextToSpeechManager` | Uses on-device Android `SpeechRecognizer` / `TextToSpeech` engines. No backend dependency. |

### 1.2 What is simulated

- **`SimulatedRemoteDataSource`** ([data/remote/SimulatedRemoteDataSource.kt](../app/src/main/java/com/yourteam/sahara/data/remote/SimulatedRemoteDataSource.kt)) is the entire "backend." It is an in-process `ConcurrentHashMap`, seeded with nothing, wiped every time the app process dies. It is wired as the default `RemoteDataSource` inside `SyncManager` (`SaharaApplication.kt:28`).
- **`SyncManager`** ([sync/SyncManager.kt](../app/src/main/java/com/yourteam/sahara/sync/SyncManager.kt)) drains a Room-backed `sync_queue` table and calls `remoteDataSource.uploadGameResult` / `uploadPatient`. Today this only ever reaches the in-memory map above — nothing survives a process restart, and **no second device or caregiver account can ever see it**, so the "caregiver sees patient's real result" flow described in the final product goal does not exist yet even though every other piece (queue, retry, idempotent syncId, WorkManager trigger) is already built correctly and only needs a real endpoint underneath.
- **Reminders and caregiver alerts are not in the sync queue at all** — they are local-only by design comment, so they will need to be added to the sync path from scratch (Stage 4, after game results).

### 1.3 What needs backend support (in priority order, matching Stage 3–5)

1. Caregiver registration/login (replace/augment local `AuthRepository` verification with a server call; keep local Room as an offline cache of "my session")
2. Patient creation + caregiver↔patient linking (currently `AuthDao.link`, local only)
3. `GameResult` upload (the sync queue plumbing already exists; only `RemoteDataSource` needs a real HTTP implementation)
4. `Reminder` upload (not yet in the sync queue — needs `SyncManager.enqueueReminderSync` + queue handling, mirroring the `GameResult` path)
5. `CaregiverAlert` (currently generated only implicitly through `CaregiverDashboardAnalyzer`; `CaregiverAlertRepository`/`CaregiverAlertEntity` exist but nothing currently writes alerts into them from production code paths — worth confirming before wiring sync)
6. Cross-device caregiver dashboard read: today `CaregiverViewModel` reads only the local Room DB (`patientRepository.getPatientById`, `gameResultRepository.getAllGameResults`) — a caregiver on a second device/reinstall sees nothing until a "download from backend" path exists (this is new work, not just upload sync)

### 1.4 Where patient IDs are used / missing

- `Patient.id` / `Patient.syncId` are both `String` UUIDs generated client-side (`java.util.UUID.randomUUID()`), already suitable as a stable client ID for idempotent upsert — good, matches Stage 4's "every synchronized record must have a stable client ID" requirement.
- `GameResultEntity`, `ReminderEntity`, `CaregiverAlertEntity` all carry `patientId: String` and are queried/filtered by it at the DAO level (`WHERE patientId = :patientId`) — patient scoping is already consistent through the local data layer.
- **Gap:** `AccountEntity.id` (caregiver id) and `Patient.id` are both locally generated UUIDs with no namespace tying them to a server-issued identity. When a real backend exists, either (a) the server must accept and store client-generated UUIDs as primary keys (simplest, avoids ID remapping), or (b) the app must be changed to receive server-issued IDs on creation and update local rows — option (a) is recommended to minimize Android-side churn.
- **Gap:** `CaregiverAlertEntity` defaults `patientId = "patient_001"` (a stale placeholder from before the v5→v6 migration introduced real patient UUIDs) in both the entity default and `CaregiverAlertRepository.getAlertsForPatient` default parameter — this should be cleaned up before wiring alert sync, independent of backend work.

### 1.5 What must synchronize (Stage 4 order)

1. `GameResult` (queue infra exists; do this first per Stage 4 instructions)
2. `Reminder` (queue infra does not exist yet)
3. `Patient` profile edits (queue infra exists for insert; update path not enqueued — `AuthRepository.savePatient` edit branch calls `source.updatePatient` directly with no `syncManager.enqueuePatientSync`)
4. `CaregiverAlert` / activity sessions / adaptive profile data (Stage 4 explicitly defers these)

### 1.6 Screens that will need redesign (Stage 6) vs. wiring-only

Elderly-facing (`ui/screens/HomeScreen.kt`, `VoiceScreen.kt`, `MemoryGameScreen.kt`, `AttentionGameScreen.kt`, `SequenceRecallScreen.kt`, `PerformanceScreen.kt`, `LanguageSelectionScreen.kt`) and caregiver-facing (`CaregiverHomeScreen.kt`, `PatientDashboardScreen.kt`, `CognitiveTrendScreen.kt`, `ActivityHistoryScreen.kt`, `DailyRemindersScreen.kt`) screens are all Compose + Material 3 already, reading from ViewModels that expose `StateFlow`. This is a good foundation: the redesign in Stage 6 is a styling/composition pass, not an architecture change — `CaregiverViewModel`/`HomeViewModel` state shapes do not need to change for the visual redesign, only for the backend-wiring stages. `AccountScreens.kt` (login/registration/patient picker) will need the most structural change since it currently talks directly to `AuthRepository`.

### 1.7 Networking gap

There is **no HTTP client dependency at all** in [gradle/libs.versions.toml](../gradle/libs.versions.toml) / [app/build.gradle.kts](../app/build.gradle.kts) (no Retrofit, OkHttp, or Ktor), and the manifest has no `INTERNET` permission. Stage 3 must add both.

---

## 2. Target Architecture

```
┌─────────────────────────┐        HTTPS/JSON         ┌──────────────────────┐
│   Android (Kotlin)      │ ─────────────────────────▶ │   FastAPI (Python)   │
│                         │ ◀───────────────────────── │                      │
│  Compose UI             │                             │  Pydantic schemas    │
│  ViewModel (StateFlow)  │                             │  SQLAlchemy ORM      │
│  Repository             │                             │  Alembic migrations  │
│   ├─ Room (local truth) │                             │  JWT auth            │
│   ├─ SyncQueue (Room)   │                             │                      │
│   └─ RemoteDataSource   │                             └──────────┬───────────┘
│        (Retrofit)       │                                        │ SQL
└─────────────────────────┘                             ┌──────────▼───────────┐
                                                          │     PostgreSQL       │
                                                          └───────────────────────┘
```

Design principle carried over from the existing code: **Room stays the single source of truth for
the UI.** ViewModels never talk to the network directly (today they don't even know `SyncManager`
exists except `CaregiverViewModel`, which only reads `syncStatusInfo` for a status chip). The
network is purely a sync target/source behind `RemoteDataSource`, exactly like today — this
minimizes the diff to existing, tested code.

## 3. Database Schema Proposal (PostgreSQL)

```sql
users (
  id UUID PRIMARY KEY,
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,          -- bcrypt/argon2, server-side hashing (not PBKDF2 client verifier)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
)

patients (
  id UUID PRIMARY KEY,                  -- accepts the client-generated UUID from Patient.syncId
  name TEXT NOT NULL,
  age INT NOT NULL,
  region TEXT NOT NULL,
  language TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
)

caregiver_patient_relationships (
  caregiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  patient_id   UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  consent_at   TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (caregiver_id, patient_id)
)

game_results (
  id UUID PRIMARY KEY,                  -- GameResult.syncId, idempotency key
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  game_type TEXT NOT NULL,
  difficulty TEXT NOT NULL,
  total_pairs INT NOT NULL,
  matched_pairs INT NOT NULL,
  mistakes INT NOT NULL,
  completion_time_seconds BIGINT NOT NULL,
  accuracy REAL NOT NULL,
  completed BOOLEAN NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,     -- from GameResult.timestamp
  synced_at TIMESTAMPTZ NOT NULL DEFAULT now()
)

reminders (
  id UUID PRIMARY KEY,                  -- Reminder.id, idempotency key
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  type TEXT NOT NULL,
  minute_of_day INT NOT NULL,
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  last_completed_epoch_day BIGINT NOT NULL DEFAULT -1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
)

activity_sessions (               -- reserved for Stage 4's later "activity sessions" sync target
  id UUID PRIMARY KEY,
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ,
  metadata JSONB
)

sync_metadata (
  id UUID PRIMARY KEY,
  device_id TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id UUID NOT NULL,
  last_synced_at TIMESTAMPTZ NOT NULL,
  UNIQUE (device_id, entity_type, entity_id)
)
```

All primary keys are UUIDs supplied by the Android client (matching `GameResult.syncId`,
`Patient.syncId`, `Reminder.id`) so `INSERT ... ON CONFLICT (id) DO UPDATE` gives idempotent
upsert with zero ID-remapping on the Android side.

## 4. API Proposal (FastAPI, Stage 1 vertical slice first)

```
POST   /auth/register            {email, password}                     → {access_token, user_id}
POST   /auth/login               {email, password}                     → {access_token, user_id}
GET    /auth/me                  (Bearer token)                        → {id, email}

POST   /patients                 {id, name, age, region, language}     → 201, creates + links to caller
GET    /patients                 (Bearer token)                        → patients linked to caller only
GET    /patients/{id}             (Bearer token, must be linked)        → 200 or 403

POST   /patients/{id}/game-results         {id, gameType, difficulty, ...}  → upsert by id, 200
GET    /patients/{id}/game-results         ?since=<ts>                      → list, caregiver must be linked

POST   /patients/{id}/reminders            {id, title, ...}                 → upsert by id, 200
GET    /patients/{id}/reminders                                             → list

GET    /health                                                              → {status: "ok"}
```

Every `/patients/{id}/...` route re-derives authorization server-side from
`caregiver_patient_relationships`, independent of anything the Android client claims — this is
the piece that makes Stage 5's "Caregiver A must never access Patient B" testable and enforceable
in a way the current client-only `requirePatient()` check cannot guarantee.

## 5. Android Networking Architecture (Stage 3)

```
GameResultRepository / ReminderRepository / PatientRepository
              │ (unchanged public API)
              ▼
        SyncManager                       (unchanged queue-drain logic)
              │
              ▼
     RemoteDataSource (interface)          (unchanged contract)
              │
   ┌──────────┴───────────┐
   ▼                       ▼
SimulatedRemoteDataSource   RetrofitRemoteDataSource   (new)
 (kept for tests/offline    │
  demo fallback)            ▼
                     Retrofit + OkHttp + kotlinx.serialization
                             │
                             ▼
                     AuthInterceptor (attaches Bearer token
                     from encrypted local session store)
```

- Add `Retrofit`, `okhttp` (with a `HttpLoggingInterceptor` gated to debug builds only, never
  logging passwords), and `kotlinx-serialization-json` (or Moshi) to `libs.versions.toml`.
- Add `<uses-permission android:name="android.permission.INTERNET" />` to the manifest.
- Base URL becomes a `BuildConfig` field so debug/staging/production can differ (Stage 10)
  without code changes — e.g. `buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8000/\"")`
  in debug and an override in a `staging`/`release` build type.
- `AuthRepository` gains a remote login/register path; the existing `PasswordHasher`/local
  verifier can remain as an **offline fallback** so a caregiver who already authenticated once can
  still get into the app without connectivity (matches "Android must support... offline-first").
  The server becomes the source of truth for *new* registrations and credential verification.

## 6. Sync Strategy (Stage 4)

- Keep the existing `sync_queue` table and `SyncManager.processPendingSyncQueue()` loop exactly as
  is; only `RemoteDataSource` gets a real implementation.
- Order of rollout, one complete vertical slice at a time, per the mandatory execution rules:
  1. `GameResult` upload only. Verify end-to-end on a real second device/emulator before touching
     anything else.
  2. `Reminder` upload (add `enqueueReminderSync`, extend the `when (item.entityType)` branch in
     `SyncManager`, add an `uploadReminder` to `RemoteDataSource`).
  3. Download path: caregiver dashboard needs a `GET /patients/{id}/game-results` pull, since today
     `CaregiverViewModel` only ever reads local Room. This is new, not covered by the existing
     upload-only queue, and should be its own slice (e.g. triggered alongside `triggerManualSync`,
     merging server results into Room by `syncId` upsert).
  4. `Patient` profile updates, `CaregiverAlert`, activity sessions — deferred, per Stage 4.
- Idempotency: enforced by upserting on the client-generated UUID (`syncId`/`id`) both in Postgres
  (`ON CONFLICT DO UPDATE`) and in Room (`OnConflictStrategy.REPLACE`, already used everywhere).
- Retry: already exponential-backoff via `WorkManager` `BackoffPolicy.EXPONENTIAL` in
  `SyncManager.triggerManualSync` — no change needed, just also enqueue on network-restored
  (`NetworkMonitor.isOnline` already exists and could trigger `triggerManualSync` automatically,
  which is not currently wired anywhere — worth adding in Stage 4).

## 7. Security Strategy (Stage 9, tracked from Stage 0)

- Server-side: bcrypt/argon2 password hashing (not the client's PBKDF2 verifier — that stays only
  as the offline-login fallback check), JWT with short expiry + refresh, per-route authorization
  checks against `caregiver_patient_relationships` (never trust a patient ID the client sends
  without checking the link table), input validation via Pydantic, CORS restricted to known
  origins, all secrets via environment variables (`.env`, never committed).
- Android-side: store the bearer token in `EncryptedSharedPreferences` (or Jetpack Security), never
  log tokens/passwords (the existing debug `HttpLoggingInterceptor` must redact Authorization
  headers), keep `RECORD_AUDIO` and notification permissions as-is (unrelated), no plaintext
  credentials anywhere in source (the current `DemoIdentity.PASSWORD` demo fixture is explicitly
  gated to `BuildConfig.DEBUG` already — keep that pattern for any future seed data).
- Data minimization: do not persist raw speech-recognition audio anywhere (today's
  `SpeechRecognizerManager` already only handles transcribed text, no recording storage — verify
  this remains true).

## 8. UI/UX Redesign Plan (Stage 6 — after Stage 5 works)

- No ViewModel/state-shape changes required; this is a visual/composition pass over existing
  screens listed in §1.6.
- Introduce a small `ui/theme` token set (`Color.kt`/`Theme.kt` already exist and are the right
  place) for Deep Teal / Warm Amber / Sage Green / Warm Off-White, plus a shared typography scale
  sized for the elderly journey (large touch targets ≥56dp on primary actions).
  `SaharaPrimaryButton`/`SaharaSecondaryButton`/`SaharaCard` components already exist as the
  reusable building blocks to restyle centrally rather than screen-by-screen.
- Caregiver-side screens can carry smaller text/denser layout than elderly-side screens, consistent
  with the existing split between `ui/screens/Home*`/`Voice*`/game screens (elderly) and
  `Caregiver*`/`PatientDashboard*`/`CognitiveTrend*`/`ActivityHistory*`/`DailyReminders*`
  (caregiver).
- Sync status surfacing: `SyncStatusCard` already exists and is wired into `CaregiverViewModel`'s
  `syncStatus` — extend it to show "last synced" using the already-tracked
  `SyncManager.lastSuccessfulSyncTime` (currently computed but worth confirming it's rendered
  everywhere the redesign wants it).

## 9. Implementation Sequence (Stages 1–12, as mandated)

1. **Stage 1** — FastAPI vertical slice: register → login → JWT → patient create/list/detail, with
   tests, before touching Android.
2. **Stage 2** — Alembic migrations for the schema in §3, `.env.example`, no committed secrets.
3. **Stage 3** — Add Retrofit/OkHttp, `INTERNET` permission, `RetrofitRemoteDataSource`
   implementing the existing `RemoteDataSource` interface; wire real login end-to-end; test fully
   before Stage 4.
4. **Stage 4** — `GameResult` sync end-to-end first (upload + a new download/merge path), then
   `Reminder`, then the rest, one complete slice at a time with tests between each.
5. **Stage 5** — Two-caregiver/two-patient authorization test, verified against the real backend
   (not just the client-side `requirePatient` check), plus the full patient→backend→caregiver
   demo flow.
6. **Stage 6+** — UI/UX redesign, language/voice polish, adaptive-UX copy, security hardening,
   deployment config, full test pass, demo polish — only after §9.1–§9.5 are stable, per the
   limit-aware execution rules.

## 10. Current Verified Baseline (must not regress)

- 119/119 unit tests passing, 30/30 Pixel 8 device tests passing, build successful, KSP successful,
  lint 0 errors / 37 warnings (per task brief; re-verify at the start of Stage 1 before any code
  changes, since `git status` at audit time shows substantial uncommitted local changes already in
  the working tree — auth module, sync module, and several new test files are all currently
  unstaged).

---

**Stage 0 complete. Awaiting approval before starting Stage 1 (FastAPI backend implementation).**
