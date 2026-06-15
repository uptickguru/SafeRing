# SafeRing 🛡️

**AI-powered scam call & SMS detection for seniors.**

No accounts. No PII off-device. Just protection that works.

## Overview

SafeRing protects seniors from phone scams by:
- **Checking incoming calls** against a live scam database before they answer
- **Warning mid-call** when scam trigger phrases are detected (Android only)
- **Scanning SMS** for scam, phishing, and spam messages
- **One-tap reporting** to help protect the community (zero PII)

## Platforms

| Platform | Status | Features |
|---|---|---|
| Android | ✅ Phase 1 | Pre-call + mid-call + SMS |
| iOS | ✅ Phase 1 | Pre-call + SMS (no mid-call) |
| Backend | ✅ | Go API + scraper pipeline |

## Architecture

```
┌─────────────┐    ┌──────────────┐    ┌─────────────┐
│ Android App │    │  iOS App     │    │  Go Backend │
│ (Compose)   │    │  (SwiftUI)   │    │  (API + DB) │
└──────┬──────┘    └──────┬───────┘    └──────┬──────┘
       │  SHA-256 hashed  │                    │
       └──────────────────┴────────────────────┘
                          │
                          ▼
                ┌──────────────────┐
                │  Scam Database   │
                │  FTC, IC3, BBB,  │
                │  AARP, Reddit    │
                └──────────────────┘
```

**Zero PII guarantee:** Phone numbers are SHA-256 hashed client-side before any network call. Audio analysis is 100% on-device. No accounts, no profiles, no data to leak.

## Quick Start

### Android
```bash
cd android
./gradlew assembleDebug
# Install APK on device
```

### iOS
```bash
cd ios
xcodegen generate
open SafeRing.xcodeproj
# Build to device (simulator won't do CallKit)
```

### Backend
```bash
cd backend
docker-compose up -d
# API at http://localhost:8080
```

## Project Structure

```
SafeRing/
├── ARCHITECTURE.md     # Full architecture spec
├── android/            # Kotlin + Jetpack Compose
├── ios/                # Swift + SwiftUI
├── backend/            # Go API + scrapers
└── docs/               # Design docs
```

## Privacy by Design

SafeRing was built around a single rule: **no personal data ever leaves the device.**

- Phone numbers → SHA-256 hash before network
- Audio analysis → on-device TFLite, buffers are ephemeral
- SMS analysis → on-device classifier, text never transmitted
- Reports → hash + category only, no timestamp binding to IP
- No accounts, no login, no tracking, no ads
