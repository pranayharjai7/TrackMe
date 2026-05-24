# TrackMe Technical Architecture & Implementation Deep-Dive

**Version:** 2.0.0 (Wear OS Integration Update)  
**Modules:** `:app` (Phone), `:wear` (Watch), `:wear-bridge` (Shared Protocol)

## 1. Multi-Module Ecosystem

The project has evolved into a tri-module architecture to support seamless cross-device synchronization and high-performance wearable operations.

### Module Responsibilities
- **`:app` (Android Application)**: The central hub for long-term planning, deep analytics, and cloud synchronization via Supabase. Built with Clean Architecture (UI -> Domain -> Data).
- **`:wear` (Wear OS Application)**: A specialized, low-latency UI for in-gym execution. Leverages `Compose Material 3 for Wear` and `Health Services` for real-time biometric tracking.
- **`:wear-bridge` (Android Library)**: The strict protocol layer. Contains the shared `Data Layer` models, message paths, and `WearNodeDiscovery` logic to ensure binary compatibility between Phone and Watch.

---

## 2. The Wear OS Sync Protocol (`:wear-bridge`)

To ensure ultra-low latency and zero data loss in disconnected gym environments, the app utilizes a custom state-snapshot protocol over the `Wearable Data Layer API`.

### Data Flow Logic
1.  **Phone-Side Bridge**: Observes `WorkoutSessionManager.uiState` (StateFlow).
2.  **Serialization**: Converts the complex domain state into a compact `SessionStatePayload` using `kotlinx-serialization`.
3.  **Data Client**: Pushes the payload to the `/session/active` path on the Wearable Data Layer.
4.  **Watch-Side Listener**: Reconstructs the UI state from the payload.
5.  **Command Handling**: Watch actions (e.g., `COMPLETE_SET`) are sent as high-priority messages to the phone via the `Message Client` for authoritative processing.

### Idempotency & Conflict Resolution
- Every command includes a `commandId` (UUID) and `timestamp`.
- The Phone app maintains a sliding window of processed command IDs to prevent duplicate set logging during reconnection events.
- **Last-Write-Wins (LWW)** strategy is applied for weight/rep adjustments.

---

## 3. Advanced Analytics Engines (`:domain`)

TrackMe moves beyond simple logging by implementing several proprietary calculation engines.

### Readiness Score Engine (`ReadinessScoreCalculator`)
Calculates a 0-100 score based on three primary inputs from Health Connect:
- **HRV Deviation**: Percentage change in RMSSD vs. 7-day rolling average.
- **RHR Trend**: Deviations in Resting Heart Rate.
- **Sleep Quality**: Weighted duration and deep sleep efficiency.

### Strength Projection Engine (`OneRMProjectionEngine`)
Uses the Epley formula optimized with a decay function for historical data to project 1RM across multiple time horizons (14, 30, 90, 365 days).

### Muscle Fatigue Mapping
Tracks volume (Sets × Reps × Weight) per muscle group per 24-hour window, applying a linear recovery decay to visualize overtraining risks in the **Stimulus Heatmap**.

---

## 4. Data Persistence & Cloud Sync

### Room Database Strategy
The app uses 14 specialized tables with `deletedAt` soft-deletion and `isSynced` flags.
- **Migrations**: Currently at version 10.
- **Performance**: Indices optimized for `userId` + `date` + `deletedAt` lookups to support high-speed history scrolling.

### Supabase Cloud Integration
- **Auth**: native Google Sign-In and Credential Manager.
- **Postgrest**: Bi-directional sync using `updatedAt` timestamps.
- **Row Level Security (RLS)**: Strictly enforces data isolation between users at the Postgres level.

---

## 5. Security & Hardware Integration

- **Security Crypto**: All Supabase session tokens and API keys are stored in `EncryptedSharedPreferences`.
- **Health Connect API**: Granular read permissions for 40+ metric types, with a robust mapping layer in `HealthMetricMapper`.
- **Wear Health Services**: Direct access to heart rate and calorie sensors, piped back to the phone session via the bridge.

## 6. Development & Build
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 36
- **Min SDK**: 26 (Phone), 30 (Wear OS 3+)
- **Dependency Injection**: Hilt (Standard across all modules)
- **Networking**: Ktor 2.3.x with Supabase Kotlin SDK 2.6.x
