# SafeRing — Settings & Configuration Reference

## Overview

SafeRing is a **zero-config** app by design (senior-friendly). There are very few user-facing settings. This document covers everything that needs to be configured per platform, including the backend API, permissions, and environment variables.

---

## 1. Backend API Configuration

### Server Address (used by both apps)

Both Android and iOS apps point to the same backend:

```
Default: https://safering.deathbyathousand.com
```

**Config file locations:**
- Android: `android/app/src/main/java/online/db1k/safering/android/util/AppConfig.kt` → `DEFAULT_BASE_URL`
- iOS: `ios/SafeRing/Util/AppConfig.swift` → `defaultBaseURL`

### API Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `GET /health` | — | Server health check |
| `GET /v1/check?hash=<sha256>` | No auth | Lookup hashed number against scam DB |
| `GET /v1/prefixes` | No auth | Fetch known scam prefixes |
| `GET /v1/stats` | No auth | Aggregate scam stats |
| `POST /v1/report` | No auth | Submit scam report (hash only) |
| `GET /v1/model?type=<number\|sms>` | No auth | ML model file download |
| `GET /v1/model/latest` | No auth | Latest model version info |

### Backend Environment Variables (`backend/.env`)

| Variable | Default | Description |
|---|---|---|
| `SERVER_HOST` | `0.0.0.0` | Bind address |
| `SERVER_PORT` | `8080` | HTTP port |
| `DATABASE_URL` | `safering.db` | SQLite file; use postgres:// for prod |
| `SCRAPER_INTERVAL` | `6h` | How often to scrape scam feeds |
| `SCRAPER_FTC_ENABLED` | `true` | FTC Consumer Sentinel scraper |
| `SCRAPER_FTC_API_KEY` | `DEMO_KEY` | FTC API key (api.data.gov) |
| `SCRAPER_BBB_ENABLED` | `true` | BBB Scam Tracker scraper |
| `SCRAPER_REDDIT_ENABLED` | `true` | Reddit r/scams scraper |
| `SCRAPER_TWITTER_BEARER_TOKEN` | `""` | X/Twitter API bearer token |
| `RATE_LIMIT_PER_IP` | `100` | Max requests per IP per window |
| `RATE_LIMIT_WINDOW` | `1m` | Rate limit window |
| `LOG_LEVEL` | `info` | Logging level |
| `ML_MODEL_DIR` | `./data/models` | ML model storage |
| `ML_TRAIN_INTERVAL` | `24h` | Model training frequency |

---

## 2. Android App Settings

### App-level Configuration (`AppConfig.kt`)

| Constant | Default Value | Notes |
|---|---|---|
| `DEFAULT_BASE_URL` | `https://safering.deathbyathousand.com` | Backend URL |
| `API_VERSION` | `v1` | API version prefix |
| `REQUEST_TIMEOUT_SECONDS` | `15` | HTTP timeout |
| `MAX_RETRIES` | `2` | Retry count for failed requests |
| `SYNC_INTERVAL_HOURS` | `6` | Background DB sync interval |
| `AUTO_BLOCK_THRESHOLD` | `0.85` | Auto-block risk score |
| `WARNING_THRESHOLD` | `0.3` | Warning label risk score |
| `ALERT_THRESHOLD` | `0.6` | Full-screen alert risk score |
| `CACHE_MAX_AGE_HOURS` | `6` | Cache expiry |
| `MAX_CALL_LOGS` | `500` | Local call log limit |
| `MAX_SMS_LOGS` | `500` | Local SMS log limit |
| `LOG_RETENTION_DAYS` | `30` | Log auto-cleanup |

### Android Permissions Required (`AndroidManifest.xml`)

| Permission | Purpose |
|---|---|
| `READ_PHONE_STATE` | Detect incoming calls |
| `READ_CALL_LOG` | View call history (for scam logs) |
| `POST_NOTIFICATIONS` | Scam alert notifications |
| `INTERNET` | API calls |
| `FOREGROUND_SERVICE` | Background protection |
| `RECEIVE_SMS` | SMS scam detection |
| `READ_SMS` | SMS content analysis |

### User-facing Settings (Settings Screen)

| Setting | Type | Default | Description |
|---|---|---|---|
| Auto-Block Scam Calls | Toggle | ON | Block calls above risk threshold |
| Show Scam Alert Notifications | Toggle | ON | Notifications for suspected scams |
| Store SMS Body | Toggle | OFF | Save message text locally |

### Permissions Screen Actions

- **Call Screening Access** → Opens default apps settings (user must set SafeRing as default call screening app)
- **Phone Permission** → Opens app settings to grant phone permission

---

## 3. iOS App Settings

### App-level Configuration (`AppConfig.swift`)

| Constant | Default Value | Notes |
|---|---|---|
| `defaultBaseURL` | `https://safering.deathbyathousand.com` | Backend URL |
| `apiVersion` | `v1` | API version prefix |
| `requestTimeout` | `15` seconds | HTTP timeout |
| `maxRetries` | `2` | Retry count |
| `syncInterval` | `21600` (6 hours) | Background sync interval |
| `autoBlockThreshold` | `0.85` | Auto-block risk score |
| `warningThreshold` | `0.3` | Warning label risk score |
| `alertThreshold` | `0.6` | Full-screen alert risk score |
| `cacheMaxAge` | `21600` (6 hours) | Cache expiry |
| `maxCallLogs` | `500` | Local call log limit |
| `maxSmsLogs` | `500` | Local SMS log limit |
| `logRetentionPeriod` | `2592000` (30 days) | Log auto-cleanup |
| `appGroupIdentifier` | `group.online.db1k.safering.ios` | Shared container for app+extensions |

### User-facing Settings (SettingsView)

| Setting | Type | Default | Description |
|---|---|---|---|
| Call Protection | Toggle | ON | Screen incoming calls for scams |
| SMS Scanning | Toggle | ON | Check text messages for scams |
| Auto-Block Known Scams | Toggle | ON | Block confirmed scam callers |
| Save SMS Content | Toggle | OFF | Store message text locally for review |

**Advanced Settings (hidden behind "Make it smarter"):**
- Clear Scam Cache
- Force Data Sync

### System Integration

- **Call Screening Extension** → Opens system settings to enable Call Directory extension
- Must have App Groups capability enabled in Xcode with identifier `group.online.db1k.safering.ios`

### Background Tasks

- Task ID: `online.db1k.safering.ios.sync-scam-data`
- Must be registered in both code and `Info.plist`
- Requires network connectivity, no external power needed

---

## 4. Build & CI Configuration

### Android Build (`android/build.gradle.kts`)

```
build.gradle.kts       — Root project
app/build.gradle.kts   — App module (dependencies, signing, compile SDK)
settings.gradle.kts    — Module includes
gradle.properties      — JVM args, AndroidX, Kotlin settings
```

**Keystore:** The Android APK signing was fixed on Jun 21. The keystore is configured in app/build.gradle.kts.

### iOS Build

```
project.yml      — Xcode project generation (XcodeGen)
exportOptions.plist  — Export for App Store / TestFlight
```

**Signing:** Uses distribution certificate `SafeRing_Distribution.p12` with provisioning profiles.

### CI Workflows (`.github/workflows/`)

| Workflow | Platform | Output |
|---|---|---|
| `android-build.yml` | Android | Firebase App Distribution |
| `ios-build.yml` | iOS | TestFlight via App Store Connect |

---

## 5. Setting Up a New Environment

### Backend

```bash
# 1. Clone and configure
cd backend
cp .env.example .env
# Edit .env with your settings

# 2. Run with Docker
docker compose up -d

# 3. Or run directly (requires Go 1.21+)
go run cmd/server/main.go
```

### Android

```bash
# Open in Android Studio or build via CLI
cd android
./gradlew assembleRelease
```

### iOS

```bash
# Requires macOS with Xcode 15+
cd ios
# Open .xcodeproj or use XcodeGen
xcodegen generate
open SafeRing.xcodeproj
```

---

## 6. Risk Thresholds (both platforms)

| Score Range | Behavior |
|---|---|
| 0.00 – 0.29 | ✅ Safe — No action |
| 0.30 – 0.59 | ⚠️ Warning — Label shown |
| 0.60 – 0.84 | 🔶 Alert — Full-screen warning |
| 0.85 – 1.00 | 🔴 Auto-Block — Blocked silently |

*Note: `risk_score` and `risk` are the same field; duplicate due to API evolution.*

---

*Last updated: 2026-06-22*
