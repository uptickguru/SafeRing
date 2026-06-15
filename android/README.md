# SafeRing — Android

AI-powered scam call & SMS detection for seniors.

## Overview

SafeRing is a privacy-first Android application that protects users from phone scams using:
- **Call Screening** — Real-time number lookup against scam databases (via `CallScreeningService`)
- **SMS Classification** — On-device ML classification of incoming SMS messages
- **Speech Analysis** — (Android-only) On-device real-time audio analysis for scam phrase detection
- **Report & Share** — One-tap anonymous scam reporting

## Zero PII Guarantee

SafeRing operates with a strict zero-PII policy:
- **Phone numbers** are SHA-256 hashed before any network transmission
- **Audio** is processed on-device only — never recorded, stored, or transmitted
- **No accounts** — no login, no signup, no email, no personal data
- **SMS bodies** are classified on-device and never leave the device

## Build Instructions

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17** (included with Android Studio)
- **Android SDK** API 34
- **Kotlin** 1.9.22

### Quick Start

1. Clone the repository:
   ```bash
   git clone git@github.com:safering/safering-android.git
   cd safering-android
   ```

2. Open the project in Android Studio:
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to `safering-android/` and open

3. Sync Gradle:
   - Android Studio will auto-detect the Gradle wrapper
   - Click "Sync Now" in the notification bar
   - Or run: `./gradlew build --refresh-dependencies`

4. Configure `local.properties`:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

5. Run on device or emulator:
   - Select a target device (API 26+)
   - Click Run (▶) or run: `./gradlew installDebug`

### Build Variants

- **debug** — For development, debuggable, relaxed ProGuard
- **release** — For production, minified, resources shrunk, ProGuarded

```bash
# Build release APK
./gradlew assembleRelease

# Build debug APK
./gradlew assembleDebug

# Run tests
./gradlew test

# Run instrumentation tests
./gradlew connectedCheck
```

### Required Permissions

SafeRing requests the following permissions at runtime:

| Permission | Purpose | Required |
|---|---|---|
| `READ_PHONE_STATE` | Screen incoming calls | Yes |
| `RECEIVE_SMS` | Classify incoming SMS | Yes |
| `READ_SMS` | Read SMS for classification | Yes |
| `POST_NOTIFICATIONS` | Alert about detected scams | Yes |
| `RECORD_AUDIO` | Mid-call speech analysis | Optional |
| `BIND_SCREENING_SERVICE` | Call screening system service | Yes |
| `BIND_ACCESSIBILITY_SERVICE` | Speech analysis | Optional |
| `FOREGROUND_SERVICE` | Background sync | Yes |
| `FOREGROUND_SERVICE_PHONE_CALL` | Call screening | Yes |
| `FOREGROUND_SERVICE_DATA_SYNC` | Scam data sync | Yes |

## Architecture

SafeRing follows **clean architecture** with three layers:

### Data Layer
- **Room** for local SQLite caching of scam numbers, prefixes, call logs, and SMS logs
- **Retrofit + OkHttp** for API communication
- **DataStore** for user preferences

### Domain Layer
- **TFLite** for on-device ML (number classification, SMS classification, speech analysis)
- **Use cases** for business logic orchestration

### UI Layer
- **Jetpack Compose** with **Material 3** for the UI
- **Hilt** for dependency injection
- **Navigation Compose** for screen navigation

## Project Structure

```
app/
├── src/main/java/com/safering/android/
│   ├── SafeRingApp.kt              # Application class
│   ├── MainActivity.kt             # Single-activity Compose entry
│   ├── di/                          # Hilt DI modules
│   ├── data/
│   │   ├── local/                   # Room DB, entities, DAOs
│   │   ├── remote/                  # Retrofit API, DTOs
│   │   └── repository/             # ScamRepository (offline-first)
│   ├── domain/
│   │   ├── model/                  # Domain models (CallRisk, SmsRisk)
│   │   ├── usecase/               # Business logic use cases
│   │   └── ml/                    # TFLite wrappers
│   ├── service/
│   │   ├── CallScreeningService.kt # Pre-call screening
│   │   ├── CallAudioService.kt    # Mid-call speech analysis
│   │   ├── SmsReceiver.kt         # SMS classification
│   │   └── SyncWorker.kt          # Background data sync
│   ├── ui/
│   │   ├── theme/                 # Material 3 theming
│   │   ├── screens/
│   │   │   ├── home/              # Dashboard
│   │   │   ├── callhistory/       # Call log
│   │   │   ├── settings/          # Preferences
│   │   │   └── report/            # Scam reporting
│   │   ├── components/            # Shared composables
│   │   └── navigation/            # Nav graph
│   └── util/                      # Utilities
├── src/main/res/                   # Resources
├── src/main/AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Jetpack Compose BOM | 2024.02.00 | UI framework |
| Material 3 | 1.2.0 | Design system |
| Hilt | 2.50 | DI |
| Room | 2.6.1 | Local database |
| Retrofit | 2.9.0 | HTTP client |
| OkHttp | 4.12.0 | Network layer |
| TFLite | 2.14.0 | On-device ML |
| WorkManager | 2.9.0 | Background sync |
| DataStore | 1.0.0 | Preferences |
| Navigation Compose | 2.7.7 | Screen navigation |

## API

SafeRing communicates with the SafeRing backend API at `https://api.safering.app/v1/`.

### Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/check?hash=` | GET | Look up a hashed phone number |
| `/prefixes` | GET | Get known scam prefixes |
| `/model/latest` | GET | Get latest ML model info |
| `/report` | POST | Submit an anonymous report |
| `/model` | GET | Download ML model file |
| `/stats` | GET | Anonymous aggregate stats |

All API calls are unauthenticated — the SHA-256 hash provides anonymity.

## Senior-Friendly Design

- **Minimum 16sp body text** (vs Material 3 default of 14sp)
- **High contrast colors** — no pastels, clear semantic colors
- **56dp+ touch targets** — easier tapping for users with reduced dexterity
- **Big toggles** — entire row is tappable, not just the switch
- **One-tap actions** — report a scam in 2 taps max
- **Zero configuration** — protection works out of the box

## License

Proprietary. © 2026 SafeRing.

## Related

- [Backend API](https://github.com/safering/safering-backend)
- [iOS App](https://github.com/safering/safering-ios)
- [Architecture Overview](../ARCHITECTURE.md)
# CI test trigger
# CI retry
