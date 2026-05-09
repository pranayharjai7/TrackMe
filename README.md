<div align="center">

# 🏋️ TrackMe

**A modern Android workout tracker built for serious lifters.**

Log sessions, build weekly routines, track strength over time, and sync body metrics from your wearables — all offline-first with seamless cloud backup.

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Supabase](https://img.shields.io/badge/Backend-Supabase-3ECF8E?style=flat-square&logo=supabase&logoColor=white)](https://supabase.com)
[![License](https://img.shields.io/badge/License-MIT-A78BFA?style=flat-square)](#license)

</div>

---

## ✨ Features

| | Feature | Description |
|---|---|---|
| 📅 | **Weekly Planner** | Build Mon–Sun workout routines, assign exercises to each day, drag-to-reorder sets |
| 🏃 | **Live Session Logging** | Log weight × reps per set in real time with a built-in rest timer |
| 📈 | **Progress Analytics** | Strength charts, workout heatmap, volume-per-muscle breakdown, personal records |
| 🤸 | **Exercise Library** | 800+ exercises with animated GIFs, step-by-step instructions, and YouTube links |
| 💓 | **Health Connect** | Pulls weight, steps, height, and calories from Samsung Health, Fitbit, and other wearables |
| ☁️ | **Offline-First Sync** | All data lives in Room first; a last-write-wins bidirectional merge keeps Supabase in sync |
| 🔐 | **Auth** | Email/password and Google Sign-In via Supabase Auth and Android Credential Manager |

---

## 📱 Screens

```
Auth → Onboarding → Home ──┬── Weekly Planner → Day Editor → Exercise Search
                            ├── Active Session
                            ├── Progress
                            └── Profile
```

| Screen | What you see |
|--------|-------------|
| **Home** | 7-day week strip, today's workout, active-session banner, streak counter, latest Health Connect snapshot, recent PRs |
| **Weekly Planner** | Mon–Sun cards — tap a rest day to create it, tap an existing day to edit it |
| **Day Editor** | Planned exercises with drag-to-reorder, add from library, set targets per exercise |
| **Active Session** | Live set logger (weight + reps), 90 s rest timer, progress bar, finish session |
| **Exercise Detail** | Animated GIF, primary & secondary muscles, instructions, Watch on YouTube |
| **Progress** | Activity heatmap, per-exercise strength line charts, weekly muscle volume bars, body stats from HC |
| **Profile** | Unit preference (kg / lbs), fitness goal chip selector, Health Connect status & re-sync, sign out |

---

## 🏗️ Architecture

TrackMe follows **Clean Architecture** with three layers and an **offline-first** data strategy.

```
┌──────────────────────────────────────────────┐
│                  UI Layer                     │
│  Compose Screens  ←→  ViewModels (StateFlow)  │
└────────────────────┬─────────────────────────┘
                     │ Use Cases
┌────────────────────▼─────────────────────────┐
│                Domain Layer                   │
│  Repository Interfaces · Domain Models        │
│  Use Cases (LogSet, FinishSession, …)         │
└────────────────────┬─────────────────────────┘
                     │
┌────────────────────▼─────────────────────────┐
│                 Data Layer                    │
│  Room (offline source of truth)               │
│  Supabase (cloud backup, RLS per userId)      │
│  Health Connect (wearable metrics)            │
│  SyncWorker (WorkManager, 15-min periodic)    │
└──────────────────────────────────────────────┘
```

### Sync Strategy

Every mutable entity carries an `isSynced` flag and an `updatedAt` timestamp. `SyncWorker` runs a **last-write-wins** bidirectional merge on every table:

- **Local only** → push to Supabase, mark synced
- **Remote only** → insert into Room with `isSynced = true`
- **Both sides** → whichever has the newer `updatedAt` wins; `isSynced = false` forces a push on tie

A sync is triggered on every local write, on app foreground, and every 15 minutes when connected.

---

## 🛠️ Tech Stack

### Core
| Library | Version | Purpose |
|---------|---------|---------|
| Kotlin | — | Language |
| Jetpack Compose BOM | `2024.10.00` | Declarative UI |
| Material 3 | — | Design system |
| Navigation Compose | `2.8.4` | Screen navigation |
| Lifecycle / ViewModel | `2.8.7` | MVVM state management |

### Data & Networking
| Library | Version | Purpose |
|---------|---------|---------|
| Room | `2.6.1` | Local SQLite database |
| Supabase Kotlin SDK | `2.6.1` BOM | Postgres backend + Auth |
| Ktor Android client | `2.3.12` | HTTP transport for Supabase |
| DataStore Preferences | `1.1.1` | User preferences & onboarding state |
| kotlinx.serialization | `1.6.3` | JSON serialization for DTOs |

### Background & Sync
| Library | Version | Purpose |
|---------|---------|---------|
| WorkManager | `2.9.1` | Periodic & immediate sync jobs |
| Health Connect | `1.1.0-rc01` | Wearable data (weight, steps, calories) |

### Dependency Injection
| Library | Version | Purpose |
|---------|---------|---------|
| Hilt | `2.52` | DI framework |
| Hilt Navigation Compose | `1.2.0` | ViewModel injection in nav graph |
| Hilt Work | `1.2.0` | Injects DAOs/repos into WorkManager workers |

### UI & Media
| Library | Version | Purpose |
|---------|---------|---------|
| Coil Compose + GIF | `2.7.0` | Exercise animation loading |
| Vico (M3) | `1.15.0` | Strength charts and heatmaps |
| Reorderable | `2.4.3` | Drag-to-reorder exercise lists |
| Credential Manager | `1.3.0` | Native Google Sign-In |
| Security Crypto | `1.1.0-alpha06` | Encrypted token storage |

### Testing
| Library | Version | Purpose |
|---------|---------|---------|
| JUnit | `4.13.2` | Unit tests |
| MockK | `1.13.13` | Kotlin-idiomatic mocking |
| Turbine | `1.2.0` | Flow testing |
| kotlinx-coroutines-test | `1.9.0` | Coroutine test utilities |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- A Supabase project (free tier works)
- Google Cloud project with an OAuth 2.0 Web Client ID (for Google Sign-In)

### 1. Clone
```bash
git clone https://github.com/pranayharjai7/TrackMe.git
cd TrackMe
```

### 2. Configure secrets

Create `local.properties` in the project root (it is git-ignored):

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
GOOGLE_WEB_CLIENT_ID=your-web-client-id.apps.googleusercontent.com
```

### 3. Set up Supabase

Create the following tables (all with Row Level Security enabled, filtered by `user_id`):

```
workout_plans, workout_days, planned_exercises,
workout_sessions, session_sets, health_snapshots
```

Refer to the entity data classes under `data/local/entity/` for the exact schema — each Kotlin field maps 1-to-1 to a column (snake_case in Supabase).

### 4. Build & run

Open the project in Android Studio and run on a device or emulator with **API 26+**.

> **Health Connect** requires a physical device. On Android 9–13 install the [Health Connect app](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) first; on Android 14+ it is built into the OS.

---

## 📂 Project Structure

```
app/src/main/java/com/trackme/
├── data/
│   ├── health/          # HealthConnectManager — reads wearable records
│   ├── local/           # Room database, 8 DAOs, 8 entities
│   ├── remote/          # Supabase sources + serializable DTOs
│   └── repository/      # Repository implementations
├── domain/
│   ├── model/           # Immutable domain models
│   ├── repository/      # Repository interfaces
│   └── usecase/         # Business logic (LogSet, FinishSession, …)
├── sync/
│   ├── SyncWorker.kt    # Bidirectional LWW merge for all workout tables
│   ├── HealthSyncWorker.kt
│   └── SyncManager.kt   # Enqueues immediate + periodic jobs
├── ui/
│   ├── auth/            # Login / sign-up
│   ├── home/            # Dashboard
│   ├── onboarding/      # First-launch setup
│   ├── workout/         # Planner, day editor, exercise search, active session
│   ├── progress/        # Charts and analytics
│   ├── profile/         # Settings and Health Connect management
│   ├── navigation/      # NavGraph + Routes
│   ├── components/      # Shared Compose components
│   └── theme/           # Dark theme — charcoal (#111118) + violet (#A78BFA)
└── di/                  # Hilt modules (Database, Repository, Network)
```

---

## 🎨 Design

TrackMe uses a **dark-first Material 3** theme.

| Token | Hex | Usage |
|-------|-----|-------|
| Background | `#111118` | App background |
| Surface | `#1C1C27` | Cards and sheets |
| Violet | `#A78BFA` | Primary accent, CTAs |
| Coral | `#F87171` | Destructive actions, HR zones |
| Teal | `#34D399` | Success states, connected status |

---

## 🗺️ Roadmap

- [ ] AI workout suggestions based on volume and recovery
- [ ] Muscle fatigue heatmap (body-map visualization)
- [ ] One-rep max estimation from logged sets
- [ ] Nutrition tracking with macro goals
- [ ] Plate calculator and warm-up set suggestions
- [ ] Apple Health / Google Fit bridging
- [ ] Widget for today's workout

---

## 📄 License

```
MIT License — Copyright (c) 2026 Pranay Harjai
```

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files, to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software.
