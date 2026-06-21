# SafeRing iOS

AI-powered scam call and SMS detection for seniors. Privacy-first, zero-config protection.

> **Zero PII Policy:** Phone numbers are SHA-256 hashed before any network call. SMS bodies are analyzed entirely on-device. No accounts, no login, no data collection.

## Requirements

- Xcode 15.0+
- iOS 16.0+
- Swift 5.9+
- An Apple Developer account (for CallKit extension and App Groups)

## Building with Xcode

### 1. Open the project

```bash
open SafeRing.xcodeproj
```

### 2. Configure Signing & Capabilities

1. Select the **SafeRing** target
2. Go to **Signing & Capabilities**
3. Select your development team
4. Add **App Groups** capability with identifier `group.com.safering.ios`
5. Select the **CallDirectoryHandler** target
6. Add the same **App Groups** capability with `group.com.safering.ios`

### 3. Build & Run

Select your simulator or device and press **Cmd+R**.

> **Note on CallKit:** The CallDirectory extension requires a real device to function. It will not work in the simulator. On first launch, go to **Settings → Phone → Call Blocking & Identification** and enable SafeRing.

## Building with XcodeGen (Alternative)

If you prefer to generate the Xcode project from a spec:

### 1. Install XcodeGen

```bash
brew install xcodegen
```

### 2. Generate the project

```bash
cd SafeRing/ios
xcodegen generate
```

This will produce `SafeRing.xcodeproj` from `project.yml`.

### 3. Follow the same signing steps above.

## Building with `xcodebuild` (CI)

```bash
cd SafeRing/ios

# Generate project if needed
xcodegen generate

# Build the main app
xcodebuild \
  -project SafeRing.xcodeproj \
  -scheme SafeRing \
  -sdk iphoneos \
  -derivedDataPath build \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO

# Build the extension
xcodebuild \
  -project SafeRing.xcodeproj \
  -scheme CallDirectoryHandler \
  -sdk iphoneos \
  -derivedDataPath build \
  CODE_SIGN_IDENTITY="" \
  CODE_SIGNING_REQUIRED=NO \
  CODE_SIGNING_ALLOWED=NO
```

## Project Structure

```
SafeRing/ios/
├── SafeRing.xcodeproj/           # Xcode project file
├── project.yml                   # XcodeGen config
├── Package.swift                 # SPM config (Apple frameworks only)
├── SafeRing/                     # Main app target
│   ├── App/                      # App entry point, root view
│   ├── Data/
│   │   ├── Local/                # SwiftData models + ScamStore
│   │   ├── Remote/               # URLSession API client + models
│   │   └── Repository/           # Offline-first repository
│   ├── Domain/
│   │   ├── Models/               # CallRisk, SmsRisk
│   │   ├── UseCases/             # Business logic
│   │   └── ML/                   # CoreML wrappers
│   ├── Extension/                # App-side managers for system extensions
│   ├── UI/
│   │   ├── Theme/               # Design tokens, fonts, colors
│   │   ├── Screens/             # Home, History, Settings, Report, Onboarding
│   │   └── Components/          # RiskBadge, BigButton, ScamAlert, CallRow
│   ├── Util/                    # HashUtils, Logger, AppConfig
│   ├── Assets.xcassets/         # Color sets, icons
│   ├── Info.plist
│   └── SafeRing.entitlements
└── CallDirectoryHandler/         # CallKit app extension target
    ├── CallDirectoryHandler.swift
    └── Info.plist
```

## Architecture

### Data Flow

```
Incoming Call/SMS
       │
       ▼
Hash number (SHA-256)
       │
       ├──→ Check local cache (SwiftData) → instant result
       │
       └──→ If miss/stale: query API (hashed only)
                │
                └──→ Update local cache → return result
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| **No external dependencies** | SPM only; all integration uses Apple frameworks |
| **SwiftData** | Modern, Swift-native persistence, works with SwiftUI |
| **Offline-first** | Local cache enables protection without internet |
| **SHA-256 all PII** | Numbers never leave device in plaintext |
| **On-device ML** | SMS analysis; models can be updated via API |
| **CallKit extension** | System-level call screening before the phone rings |
| **BackgroundTasks** | 6-hour sync cycle; battery-friendly |

### Rate Limiting

| Endpoint | Limit |
|---|---|
| GET /v1/check | 100 req/min |
| GET /v1/prefixes | 10 req/min |
| POST /v1/report | 20 req/min |

## Privacy Guarantees

- ✅ **No accounts** — no signup, no login, no email
- ✅ **No raw phone numbers sent** — only SHA-256 hashes
- ✅ **No message content transmitted** — SMS analyzed on-device
- ✅ **No audio transmitted** — mid-call analysis is on-device only
- ✅ **No tracking** — no analytics SDKs, no telemetry
- ✅ **No advertising** — no ad SDKs, no data sharing

## License

Copyright © 2024 SafeRing. All rights reserved.
# CI test trigger
# CI retry
