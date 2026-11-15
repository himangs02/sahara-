# Sahara 🧠

**Sahara — an offline-first, AI-assisted cognitive wellness and caregiver support platform for older adults.**

Sahara is an Android application designed to make everyday cognitive engagement, reminders, voice interaction, and caregiver monitoring simple and accessible. It combines an elderly-friendly interface with adaptive cognitive activities, multilingual voice support, local persistence, and a FastAPI backend for secure caregiver/patient data management and synchronization.

## ✨ Highlights

- 🧩 **Cognitive Activities** — memory, attention, and sequence-recall games designed for short, repeatable sessions.
- 🤖 **Adaptive Difficulty** — activity difficulty and recommendations can adapt to recent performance and trends.
- 👨‍👩‍👧 **Caregiver Dashboard** — patient overview, activity history, cognitive trends, alerts, and performance insights.
- 🔔 **Smart Reminders** — scheduled reminders with local notifications and persistent reminder state.
- 🎙️ **Voice Interaction** — speech recognition and text-to-speech with command handling for a hands-free experience.
- 🌐 **Multilingual Foundation** — language switching with support for English, Hindi, and Assamese.
- 📱 **Elderly-Friendly UI** — large, clear interactions, reusable components, and accessibility-focused visual design.
- 💾 **Offline-First Storage** — Room database keeps core app functionality available without a network connection.
- 🔄 **Background Sync** — WorkManager-based synchronization with retry/backoff and online-state awareness.
- 🔐 **Secure Authentication** — local account/session handling plus JWT-backed backend authentication.
- ☁️ **Online Backend** — FastAPI + PostgreSQL services for caregiver accounts, patient management, game results, and reminders.
- 🧪 **Test Coverage** — unit tests and Android instrumentation/device tests are included in the repository.

## 🏗️ Architecture

### Android

```text
Jetpack Compose UI
        │
        ▼
   ViewModels
        │
        ▼
   Repositories
   ┌────┴─────┐
   ▼          ▼
 Room DB   Remote Data Source
   │          │
   │          ▼
   │     Retrofit + OkHttp
   │          │
   └────┬─────┘
        ▼
   Sync Manager
        │
        ▼
 FastAPI Backend
        │
        ▼
   PostgreSQL
```

### Backend

```text
FastAPI
  │
  ├── Auth / JWT
  ├── Patients
  ├── Game Results
  ├── Reminders
  └── Health
  │
  ▼
SQLAlchemy
  │
  ▼
Alembic
  │
  ▼
PostgreSQL
```

## 🛠️ Tech Stack

| Area | Technology |
|---|---|
| Android | Kotlin |
| UI | Jetpack Compose |
| Architecture | ViewModel + Repository pattern |
| Local Database | Room |
| Background Work | WorkManager |
| Networking | Retrofit + OkHttp |
| Serialization | Kotlinx Serialization |
| Security | AndroidX Security / JWT |
| Backend | FastAPI |
| Database | PostgreSQL |
| ORM | SQLAlchemy |
| Migrations | Alembic |
| Testing | JUnit + Android Instrumentation Tests |
| Build System | Gradle Kotlin DSL |

## 📂 Project Structure

```text
sahara-/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/yourteam/sahara/
│       │   │   ├── ai/             # Adaptive difficulty & recommendation logic
│       │   │   ├── api/            # Retrofit/API integration
│       │   │   ├── auth/           # Local auth, sessions & token storage
│       │   │   ├── data/           # Room, repositories & remote sources
│       │   │   ├── language/       # Language management
│       │   │   ├── model/          # Domain models
│       │   │   ├── navigation/     # App navigation
│       │   │   ├── notifications/  # Reminder notifications
│       │   │   ├── sync/           # Offline sync & background jobs
│       │   │   ├── ui/
│       │   │   │   ├── components/ # Reusable UI components
│       │   │   │   ├── screens/    # App screens
│       │   │   │   └── theme/      # App theme & typography
│       │   │   ├── viewmodel/      # Screen state & business coordination
│       │   │   └── voice/          # Speech recognition & TTS
│       │   └── res/
│       ├── test/                   # Unit tests
│       └── androidTest/            # Instrumentation/device tests
│
├── backend/
│   ├── app/
│   │   ├── auth/
│   │   ├── patients/
│   │   ├── game_results/
│   │   ├── reminders/
│   │   ├── core/
│   │   ├── db/
│   │   └── schemas/
│   ├── alembic/
│   ├── tests/
│   ├── Dockerfile
│   ├── docker-compose.yml
│   └── requirements.txt
│
├── docs/
│   └── ONLINE_BACKEND_ARCHITECTURE.md
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew
```

## 🚀 Getting Started

### Prerequisites

**Android**
- Android Studio
- JDK 11+
- Android SDK with API 37
- An Android device or emulator

**Backend**
- Python 3.12+
- PostgreSQL 16+
- Docker & Docker Compose (recommended for local setup)

### 1. Clone the repository

```bash
git clone https://github.com/himangs02/sahara-.git
cd sahara-
```

### 2. Run the Android app

Open the project in **Android Studio**, allow Gradle to sync, then:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

You can then run the app from Android Studio on a connected device or emulator.

### 3. Configure the backend

```bash
cd backend
cp .env.example .env
```

Set the required environment variables in `.env`:

```env
DATABASE_URL=postgresql+psycopg://sahara_user:changeme@localhost:5432/sahara
JWT_SECRET=replace-with-a-long-random-secret
JWT_ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=60
```

Start PostgreSQL:

```bash
docker compose up -d db
```

Install backend dependencies:

```bash
python -m venv .venv
```

Windows:

```powershell
.venv\Scripts\activate
```

macOS/Linux:

```bash
source .venv/bin/activate
```

Then:

```bash
pip install -r requirements.txt
alembic upgrade head
uvicorn app.main:app --reload
```

Backend endpoints will be available at:

- API: `http://127.0.0.1:8000`
- Swagger UI: `http://127.0.0.1:8000/docs`
- ReDoc: `http://127.0.0.1:8000/redoc`

## 🔐 Backend API

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/auth/register` | Register caregiver |
| POST | `/auth/login` | Login and receive JWT |
| GET | `/auth/me` | Get current caregiver |
| POST | `/patients` | Create patient |
| GET | `/patients` | List linked patients |
| GET | `/patients/{id}` | Get patient |
| PATCH | `/patients/{id}` | Update patient |
| POST | `/patients/{id}/game-results` | Sync game result |
| GET | `/patients/{id}/game-results` | Retrieve game results |
| POST | `/patients/{id}/reminders` | Sync reminder |
| GET | `/patients/{id}/reminders` | Retrieve reminders |
| GET | `/health` | Health check |

The backend re-validates caregiver/patient relationships server-side rather than trusting identifiers supplied by the mobile client.

## 🧩 Core Modules

### Cognitive Engine

Sahara includes adaptive logic for:
- performance analysis
- difficulty adjustment
- activity recommendations
- caregiver insights
- cognitive trend analysis

### Cognitive Games

The Android app contains activities such as:
- Memory Game
- Attention Game
- Sequence Recall

Each session can capture performance data such as accuracy, mistakes, matched pairs, difficulty, and completion time.

### Caregiver Experience

Caregivers can access:
- Patient dashboard
- Activity history
- Cognitive trends
- Daily reminders
- Performance summaries
- Caregiver alerts
- Synchronization status

### Voice & Language

The voice layer includes:
- Speech recognition
- Text-to-speech
- Voice commands
- Voice state management
- Language switching

The current multilingual foundation includes **English, Hindi, and Assamese**.

## 🔄 Offline-First & Sync

Sahara is designed around an offline-first model.

Core data is persisted locally with Room, while synchronization is handled through a dedicated sync manager and WorkManager.

The sync architecture supports:
- queued changes
- network-aware synchronization
- retry with exponential backoff
- remote synchronization through Retrofit
- local data remaining usable when offline

This makes the app suitable for environments where connectivity is intermittent or unavailable.

## 🧪 Testing

The repository contains both unit and Android instrumentation tests covering areas such as:

- authentication flows
- caregiver dashboard
- language and voice interaction
- patient isolation/authorization
- personalization
- reminders and notifications
- persistence
- backend API behavior
- game results

Run Android unit tests with:

```bash
./gradlew test
```

Run Android instrumentation tests with:

```bash
./gradlew connectedAndroidTest
```

Run backend tests with:

```bash
cd backend
pytest
```

## 🐳 Docker Backend

The backend includes Docker support for local development:

```bash
cd backend
docker compose up --build
```

The bundled Compose setup is intended for development and testing rather than production deployment.

## 📌 Current Status

Sahara is an actively developed project with an offline-first Android experience, adaptive cognitive activity engine, caregiver workflows, multilingual voice interaction, reminders, and a FastAPI/PostgreSQL backend.

Some advanced backend synchronization and production hardening areas remain under active development.

## 🎯 Project Goals

Sahara aims to bridge the gap between:

**elder-friendly interaction + cognitive engagement + caregiver visibility + reliable offline operation**

The long-term goal is to provide a practical, culturally adaptable and accessible digital companion that can support older adults and their caregivers without making technology feel complicated.

## 🤝 Contributing

Contributions are welcome.

1. Fork the repository.
2. Create a feature branch.
3. Make your changes.
4. Add or update tests.
5. Open a pull request.

## 📄 License

No open-source license has been declared in this repository yet. Until a license is added, the project should be treated as **all rights reserved**.

## 👨‍💻 Author

**Himangshu Kumar Das**

GitHub: [@himangs02](https://github.com/himangs02)

---

⭐ If you find Sahara interesting, consider starring the repository.
