# SafeRing — Complete Reference

**AI-powered scam call & SMS detection for seniors.**  
No accounts. Zero PII off-device. Just protection.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Project Structure](#2-project-structure)
3. [Backend API](#3-backend-api)
4. [Infrastructure & Deployment](#4-infrastructure--deployment)
5. [iOS App Setup](#5-ios-app-setup)
6. [Android App Setup](#6-android-app-setup)
7. [TestFlight Pipeline](#7-testflight-pipeline)
8. [Scrapers — Current State & Fixes Needed](#8-scrapers--current-state--fixes-needed)
9. [Current Live State (June 2026)](#9-current-live-state-june-2026)

---

## 1. Architecture Overview

```
┌─────────────────────────────┐
│         Scam Feed           │
│  (FTC, BBB, Reddit,         │
│   user reports)             │
└──────────┬──────────────────┘
           │ pull → parse → index
           ▼
┌─────────────────────────────┐
│      Pattern Database       │
│  ┌───────────────────────┐  │
│  │ Known scam numbers    │  │  SHA-256 hashed
│  │ Spoofed prefixes      │  │  prefix patterns
│  │ Scam type tags        │  │
│  └───────────────────────┘  │
└──────────┬──────────────────┘
           │ serve via API
           ▼
┌──────────────────────────────────────────────┐
│          Mobile App Architecture             │
│                                              │
│  ┌──────────────────┐  ┌──────────────────┐  │
│  │   Pre-Call       │  │   SMS Scanner    │  │
│  │   Caller ID +    │  │   Background     │  │
│  │   Scam Warning   │  │   classification │  │
│  └────────┬─────────┘  └────────┬─────────┘  │
│           │                     │            │
│  ┌────────▼─────────────────────▼─────────┐  │
│  │    Local Classifier                    │  │
│  │    - Number hash lookup               │  │
│  │    - SMS text inference (on-device)   │  │
│  └───────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

### Zero PII Guarantee

```
Incoming call: +1 (555) 123-4567
                      │
              Client: SHA-256("15551234567")
                      │
              Server lookup against risk DB
                      │
              Response: { risk: 0.87, type: "IRS-Impersonation" }
```

- Original numbers **never** sent over network
- SHA-256 is one-way
- SMS bodies analyzed entirely on-device
- No accounts, no login, no tracking

---

## 2. Project Structure

```
SafeRing/
├── ARCHITECTURE.md          # Full architecture spec
├── REFERENCE.md             # This file
├── seed_data.py             # DB seed script (47 known scam numbers)
│
├── ios/                     # SwiftUI + Swift 6
│   ├── SafeRing.xcodeproj/  # Xcode project
│   ├── project.yml          # XcodeGen config
│   ├── exportOptions.plist  # IPA export config
│   ├── .github/workflows/  # GitHub Actions CI/CD
│   │   └── ios.yml         # Auto-build + TestFlight
│   ├── SafeRing/            # Main app target
│   │   ├── App/             # Entry point, root view
│   │   ├── Data/            # SwiftData + API client
│   │   ├── Domain/          # Business logic + ML
│   │   ├── Extension/       # CallDirectory manager
│   │   ├── UI/              # Screens + components
│   │   └── Util/            # Hashing, logging, config
│   └── CallDirectoryHandler/ # CallKit extension target
│
├── android/                 # Kotlin + Jetpack Compose
│   └── app/src/main/java/com/safering/android/
│       ├── data/            # Room DB + Retrofit
│       ├── domain/          # Use cases + ML
│       ├── service/         # CallScreen + SMS receiver
│       └── ui/              # Screens + components
│
├── backend/                 # Go API server
│   ├── cmd/server/main.go   # Entry point
│   ├── internal/
│   │   ├── config/          # Env-based config
│   │   ├── handler/         # HTTP handlers + middleware
│   │   ├── model/           # Data models
│   │   ├── store/           # SQLite/Postgres DB layer
│   │   ├── ml/              # ML training pipeline
│   │   └── scraper/         # FTC, BBB, Reddit scrapers
│   ├── Dockerfile
│   ├── docker-compose.yml
│   ├── deploy.yml           # Kubernetes manifest
│   └── .env                 # Environment defaults
│
└── memory/                  # Session notes
```

---

## 3. Backend API

### Base URL (current): `https://safering.deathbyathousand.com/v1`
### Domain (intended): `https://safering.dbat.com/v1` (DNS pending)

| Endpoint | Method | Description | Rate Limit |
|---|---|---|---|
| `/check?hash=<sha256>` | GET | Lookup hashed number → risk score + type | 100/min |
| `/prefixes` | GET | Known scam prefixes for caching | 10/min |
| `/stats` | GET | Aggregate stats | 10/min |
| `/report` | POST | Submit anonymous report (hash only) | 20/min |
| `/model` | GET | Latest ML model info | 5/min |
| `/model/latest` | GET | Download model weights | 5/min |
| `/health` | GET | Health check (no auth needed) | N/A |

### Rate Limiting
- Per IP (anonymized /24 prefix)
- Soft: returns cached response
- Hard: 429 Retry-After

### Response Format

```json
// GET /v1/check?hash=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
{
  "found": true,
  "risk_score": 0.95,
  "scam_type": "irs-impersonation",
  "report_count": 128
}

// GET /v1/stats
{
  "total_scam_numbers": 47,
  "total_prefixes": 12,
  "last_scrape": "2026-06-11T00:00:00Z",
  "top_types": [
    {"type": "irs-impersonation", "count": 8},
    {"type": "tech-support", "count": 6}
  ]
}
```

---

## 4. Infrastructure & Deployment

### Current Setup (June 2026)

| Component | Value |
|---|---|
| Server | `web-hosting-portal` (154.12.228.51) |
| OS | Linux 6.8.0-107-generic (Ubuntu) |
| Backend | Go binary on port 8080 |
| Reverse proxy | Caddy (port 443 → 8080) |
| Domain (live) | `safering.deathbyathousand.com` |
| Domain (pending) | `safering.dbat.com` (A record set, DNS propagating) |
| Database | SQLite (`./safering.db`) |

### Running the Backend

```bash
# From source
cd SafeRing/backend
export DATABASE_URL=safering.db
export SERVER_PORT=8080
go run cmd/server/main.go

# Or with binary
./safering-server

# Or Docker
docker compose up -d

# Seed with initial scam data
python3 seed_data.py
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_HOST` | `0.0.0.0` | Bind address |
| `SERVER_PORT` | `8080` | HTTP port |
| `DATABASE_URL` | `safering.db` | SQLite path or Postgres DSN |
| `SCRAPER_INTERVAL` | `6h` | Scraper poll interval |
| `SCRAPER_FTC_ENABLED` | `true` | FTC scraper on/off |
| `SCRAPER_FTC_API_KEY` | `DEMO_KEY` | FTC API key (api.data.gov/signup) |
| `SCRAPER_BBB_ENABLED` | `true` | BBB scraper on/off |
| `SCRAPER_REDDIT_ENABLED` | `true` | Reddit scraper on/off |
| `LOG_LEVEL` | `info` | debug/info/warn/error |
| `RATE_LIMIT_PER_IP` | `100` | Max requests per window |
| `RATE_LIMIT_WINDOW` | `1m` | Rate window duration |

### Caddy Config (for reboot persistence)

```
safering.deathbyathousand.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
    log { output file /srv/utp/logs/caddy_access.log }
}
```

> **Note:** `/etc/caddy/Caddyfile` on disk still has port `9093` (stale).  
> The running config was updated via Caddy admin API. To persist, edit line 296:
> `reverse_proxy 127.0.0.1:9093` → `reverse_proxy 127.0.0.1:8080`  
> This requires `sudo`.

---

## 5. iOS App Setup

### Requirements
- Xcode 15+ (16.2 on build Mac)
- iOS 17.0+ target
- Swift 6
- Apple Developer Program account

### Bundle IDs
| Target | Bundle ID |
|---|---|
| Main app | `online.db1k.safering.ios` |
| CallDirectory extension | `online.db1k.safering.ios.CallDirectoryHandler` |
| Watch app | `online.db1k.safering.ios.watchkitapp` |
| Watch extension | `online.db1k.safering.ios.watchkitapp.watchkitextension` |

### Local Build
```bash
cd SafeRing/ios
xcodegen generate        # Generate project from project.yml
open SafeRing.xcodeproj  # Open in Xcode
# Select your team, build to device
```

### API Config
The app connects to the backend at the URL in `SafeRing/Util/AppConfig.swift`:
```swift
static let defaultBaseURL = "https://safering.deathbyathousand.com"
```

---

## 6. Android App Setup

### Requirements
- Android Studio Hedgehog+
- Kotlin 1.9+
- Min SDK 26
- Target SDK 34

### Build
```bash
cd SafeRing/android
./gradlew assembleDebug
# Install APK on device
```

### Key Permissions
- `CALL_SCREENING` — Pre-call scam detection
- `BIND_ACCESSIBILITY_SERVICE` — Mid-call audio analysis
- `RECEIVE_SMS` — SMS scam classification
- `POST_NOTIFICATIONS` — Alert user

---

## 7. TestFlight Pipeline

### Current State
- **Build 2** uploaded successfully to TestFlight (Apple ID: 6778584210)
- Signed with `Apple Distribution: Kevin Asbury (53DRV2V873)`
- App Store Connect app record: created
- Distribution cert: created and imported

### Certificates & Keys

| Credential | Value |
|---|---|
| Team ID | `53DRV2V873` |
| Issuer ID | `69a6de75-f335-47e3-e053-5b8c7c11a4d1` |
| API Key ID | `392Z3XJZQS` |
| Distribution Cert | `Apple Distribution: Kevin Asbury (53DRV2V873)` |
| Cert Hash (SHA-1) | `4F09D517D71DA730C64E13150E4458B5C3C98152` |
| Store Profile UUID | `a2d731cf-520d-4611-84be-c223ea4da498` |

### GitHub Actions Workflow

The file at `.github/workflows/ios.yml` pushes to TestFlight on every `main` push.  
**Still needs GitHub secrets set up** (see below).

#### Required GitHub Secrets

| Secret | Description |
|---|---|
| `ASC_API_KEY_BASE64` | Base64 of `AuthKey_392Z3XJZQS.p8` |
| `ASC_KEY_ID` | `392Z3XJZQS` |
| `ASC_ISSUER_ID` | `69a6de75-f335-47e3-e053-5b8c7c11a4d1` |
| `IOS_DIST_CERT_BASE64` | Base64 of the `.p12` (password: `p12pass123`) |
| `IOS_DIST_CERT_PASSWORD` | `p12pass123` |
| `IOS_PROVISIONING_PROFILE_BASE64` | Base64 of the provisioning profile |

### Manual Build on Mac (SSH)

The build Mac is `kevins-2019-macbook-pro` (macOS 15.7.8, Xcode 16.2)  
Reachable via Tailscale at `100.100.104.77` (SSH key: `~/.ssh/safering_build`)

```bash
# Full build pipeline (requires keychain setup first)
ssh -i ~/.ssh/safering_build kevinasbury@100.100.104.77

# Build with codesign disabled
cd ~/Desktop/safering-ios
xcodebuild -project SafeRing.xcodeproj -scheme SafeRing -sdk iphoneos \
  -configuration Release -destination generic/platform=iOS \
  -archivePath /tmp/SafeRing.xcarchive archive \
  DEVELOPMENT_TEAM=53DRV2V873 CODE_SIGN_STYLE=Automatic \
  SWIFT_STRICT_CONCURRENCY=legacy CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO -allowProvisioningUpdates

# Manual sign with keychain
security unlock-keychain -p build123 ~/Library/Keychains/safering-build.keychain-db
codesign --force --sign 4F09D517D71DA730C64E13150E4458B5C3C98152 \
  --keychain ~/Library/Keychains/safering-build.keychain-db \
  --entitlements /tmp/ent.plist /tmp/SafeRing.xcarchive/Products/Applications/SafeRing.app

# IPA + upload
cp ~/Library/Developer/Xcode/UserData/Provisioning\ Profiles/store_profile.mobileprovision \
  /tmp/SafeRing.xcarchive/Products/Applications/SafeRing.app/embedded.mobileprovision
mkdir -p /tmp/SafeRingIPA/Payload && cp -R "$APP" /tmp/SafeRingIPA/Payload/
cd /tmp/SafeRingIPA && zip -r /tmp/SafeRing.ipa Payload/
xcrun altool --upload-app -f /tmp/SafeRing.ipa --type ios \
  --apiKey *** --apiIssuer 69a6de75-f335-47e3-e053-5b8c7c11a4d1
```

---

## 8. Scrapers — Current State & Fixes Needed

All three scrapers are currently broken due to source URL/API changes.

### FTC Consumer Sentinel (`ftc.go`)

**Current URL:** `https://www.ftc.gov/enforcement/consumer-sentinel-network/data.json`  
**Error:** Returns 403 (Akamai protected)

**Fix needed:**
- The FTC publishes data in PDF/CSV format at `https://www.ftc.gov/news-events/data`  
- The scraper needs to either:
  a. Use the FTC's API program (requires registration)
  b. Scrape the downloadable CSV from their data portal
  c. Subscribe to their data feed via `data.ftc.gov`

### BBB Scam Tracker (`bbb.go`)

**Current URL:** `https://www.bbb.org/robotose/api/scamtracker/reports?page=1&pageSize=50&sort=dateDesc`  
**Error:** 404 — The URL has a typo ("robotose" should probably be different) and the API has moved

**Fix needed:**
- The BBB has restructured their scam tracker; new URL structure needed
- Check `https://www.bbb.org/scamtracker/lookupscam` (web page, not API)
- May need to scrape the HTML or find their current API endpoint

### Reddit (`reddit.go`)

**Current behavior:** Returns 403  
**Fix needed:**
- Reddit changed their API access in 2023
- Now requires OAuth even for public subreddit data
- Currently uses anonymous access; needs `clientID` and `clientSecret` configured in `.env`
- Register an app at `https://www.reddit.com/prefs/apps` to get credentials

### Data Solution (Current)

The DB has 47 seed scam numbers and 12 known scam prefixes via `seed_data.py`. This provides working data while scrapers are being fixed. The seed covers 18 scam types:

- IRS impersonation, Social Security, tech support, Medicare, grandparent, romance, sweepstakes, credit card, utility, shipping, student loan, and more.

---

## 9. Current Live State (June 2026)

| Area | Status | Details |
|---|---|---|
| Backend API | ✅ Live | `safering.deathbyathousand.com` returns proper JSON |
| Scam Database | ✅ Seeded | 47 numbers, 12 prefixes (18 scam types) |
| Scrapers | ❌ Broken | FTC/BBD/Reddit need URL fixes |
| iOS Build | ✅ Compiled | Signed + uploaded to TestFlight (Build 2) |
| TestFlight | ✅ Uploaded | App ID: 6778584210 |
| GitHub Actions | ⚠️ Needs secrets | 6 secrets need to be added to repo |
| CallDirectory Ext | ⚠️ Needs profile | Distribution profile not yet created |
| Apple Watch | ❌ Not built | Bundle IDs exist, no profiles yet |
| safering.dbat.com | ⚠️ DNS pending | A record set, waiting propagation |
| Caddy Persistence | ❌ Needs sudo | /etc/caddy/Caddyfile needs 1 line edit |
