# TrackMe Test Strategy

This project uses a layered Android test suite so production checks can catch regressions close to where they happen.

## Test Types

### JVM unit tests

Location:

- `app/src/test/java`
- `wear/src/test/java`
- `wear-bridge/src/test/java`

Purpose:

- Domain algorithms: readiness, fatigue, 1RM projection, plateau detection, consistency matrix, validation helpers.
- Repository behavior with mocked DAOs and remote sources.
- View model state transitions with coroutine test dispatchers.
- DTO serialization for Supabase column compatibility.
- Wear protocol reducers and payload serialization.

Run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :wear:testDebugUnitTest
.\gradlew.bat :wear-bridge:testDebugUnitTest
```

### Android instrumented tests

Location:

- `app/src/androidTest/java`
- `wear/src/androidTest/java`

Purpose:

- Room database and DAO behavior against a real SQLite implementation.
- Migration SQL checks for schema-sensitive releases.
- Android framework integrations such as notifications.
- Compose/Wear flows that require Android runtime support.

Run on an emulator or device:

```powershell
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat :wear:connectedDebugAndroidTest
```

### Local database tests

The app uses Room locally. Database coverage includes:

- Insert, replace, query, ordering, and soft-delete filtering.
- Tombstone visibility for later sync.
- In-progress session lookup and completed-set filtering.
- Health metric replacement by user.
- Migration 9 to 10 analytics table recreation.

These tests do not require Supabase credentials.

### Remote database boundary tests

Supabase network tests should not hit production by default. Current local coverage verifies:

- DTOs serialize with Supabase snake_case column names.
- Legacy remote rows with missing optional fields still decode.
- Repository delete flows create local tombstones, attempt remote upserts, and enqueue retry sync when remote writes fail.

For true remote integration tests, use a disposable Supabase project or local Supabase stack with test-only credentials. Do not run destructive remote tests against production user data.

## Release Check

Before a release candidate:

```powershell
.\gradlew.bat clean testDebugUnitTest :wear:testDebugUnitTest :wear-bridge:testDebugUnitTest
.\gradlew.bat assembleDebug
```

Then run instrumentation on an emulator or device:

```powershell
.\gradlew.bat connectedDebugAndroidTest :wear:connectedDebugAndroidTest
```

## Edge Cases Covered

- Missing, stale, invalid, and outlier workout analytics inputs.
- Insufficient history for readiness, plateau, and projection calculations.
- User-scoped cache isolation.
- Bodyweight exercise fallbacks.
- Soft-deleted workout plans, days, planned exercises, sessions, and sets.
- Sync retry tombstones when remote writes fail.
- Legacy Supabase records missing newly added optional target fields.
- Health metrics replacement without cross-user deletion.
- Notification rendering on Android runtime.
- Wear phone payload mapping and protocol reducers.

## Adding New Tests

Prefer the narrowest layer that can prove behavior:

- Pure calculation or mapper: JVM unit test.
- Coroutine/view model flow: JVM unit test with `kotlinx-coroutines-test`.
- Room SQL/DAO/migration behavior: Android instrumented test.
- Compose interaction: instrumented UI test.
- Supabase schema or RLS behavior: disposable remote/local Supabase integration test, never production.
