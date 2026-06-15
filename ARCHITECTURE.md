# SafeRing — Architecture Specification

**Project:** AI-powered scam call/sms detection for seniors
**Target:** Android (Kotlin + Compose) → iOS (Swift + SwiftUI)
**Status:** Spec v1.0

---

## 1. Core Philosophy

> "Protect without complexity. Zero PII off-device. Just works."

- No account creation, no login, no personal data
- Phone numbers leave the device **only as SHA-256 hashes**
- Audio never leaves the device (Android speech analysis is on-device TFLite)
- Senior-targeted UX: big text, one-tap actions, zero config for basic protection

---

## 2. Architecture Overview

```
┌─────────────────────────────┐
│          Scam Feed          │
│  (FTC, IC3, BBB, AARP,     │
│   Reddit, user reports)     │
└──────────┬──────────────────┘
           │ pull → parse → index
           ▼
┌─────────────────────────────┐
│      Pattern Database       │
│  ┌───────────────────────┐  │
│  │ Known scam numbers    │  │  hashed
│  │ Spoofed prefixes      │  │  prefixes
│  │ Scam script keywords  │  │  (set)
│  │ ML model weights      │  │
│  └───────────────────────┘  │
└──────────┬──────────────────┘
           │ serve via API
           ▼
┌────────────────────────────────────────────┐
│  Mobile App Architecture (both platforms)   │
│                                            │
│  ┌─────────────────┐  ┌──────────────────┐ │
│  │   Pre-Call      │  │   SMS Scanner    │ │
│  │   Caller ID +   │  │   Background     │ │
│  │   Scam Warning  │  │   SMS classifier │ │
│  └────────┬────────┘  └────────┬─────────┘ │
│           │                    │           │
│  ┌────────▼────────────────────▼─────────┐  │
│  │    Local ML Engine (TFLite / CoreML) │  │
│  │    - Number classification           │  │
│  │    - SMS text classification         │  │
│  │    - Speech audio classification     │  │
│  │      (Android only)                  │  │
│  └──────────────────────────────────────┘  │
│                                            │
│  ┌──────────────────────────────────────┐  │
│  │    Sync Engine (periodic / on-       │  │
│  │    demand: pull fresh scam data)     │  │
│  └──────────────────────────────────────┘  │
└────────────────────────────────────────────┘
```

---

## 3. Feature Matrix

| Feature | Android | iOS | Notes |
|---|---|---|---|
| Incoming call number lookup | ✅ CallScreeningService | ✅ CallKit Directory | Pre-answer warning |
| Mid-call scam phrase detection | ✅ Accessibility Service | ❌ | On-device TFLite only |
| SMS scam classification | ✅ SMS Retriever / reader | ✅ | On-device |
| Auto-block known scammers | ✅ | ✅ | |
| Report a scam (1 tap) | ✅ | ✅ | Sends hash only |
| Family share / alerts | ✅ | ✅ | Optional opt-in |
| Offline mode | ✅ (cached DB) | ✅ (cached DB) | |

---

## 4. Zero PII Guarantee — How It Works

```
Incoming call: +1 (555) 123-4567
                      │
              Client: SHA-256("15551234567")
                      │
              Server lookup: is 0x3f8a... in risk DB?
                      │
              Response: { risk: 0.87, label: "IRS-Impression" }
```

- The original number **never** gets sent over the network
- SHA-256 is one-way — cannot reverse back to the original number
- Server never stores IP → number mappings; anonymous query only
- User reports: pre-hashed number + optional scam type tag (no PII)
- Audio analysis: runs entirely on-device, never recorded or transmitted
- No accounts = no profile = no data to leak

---

## 5. Scam Intelligence Pipeline

```
                 ┌─────────────────────┐
                 │   Scrapers (server)  │
                 │  ──────────────────  │
                 │  • FTC Consumer      │
                 │  • FBI IC3           │
                 │  • BBB Scam Tracker  │
                 │  • AARP Fraud Watch  │
                 │  • Reddit r/scams    │
                 │  • User reports      │
                 └──────────┬──────────┘
                            │ parse + classify
                            ▼
                 ┌─────────────────────┐
                 │   Pattern Extractor  │
                 │  ──────────────────  │
                 │  • Extract number    │  → hash for DB
                 │  • Extract prefix    │  → prefix pattern
                 │  • Extract script    │  → keyword model
                 │  • Tag scam type     │  → IRS, Tech Support, Grandparent, Romance, etc.
                 └──────────┬──────────┘
                            │
                            ▼
                 ┌─────────────────────┐
                 │   Distribution API   │
                 │  ──────────────────  │
                 │  GET /v1/check?hash= │  → risk + tags
                 │  GET /v1/prefixes    │  → known scam prefixes
                 │  GET /v1/model       │  → latest TFLite weights
                 │  POST /v1/report     │  → user report (hash only)
                 │  GET /v1/stats       │  → anonymous aggregate stats
                 └─────────────────────┘
```

---

## 6. ML Pipeline

### Number Classification
- **Input:** Phone number hash + prefix
- **Model:** LightGBM or small neural net (on-device)
- **Features:** Number age, prefix region, associated scam types, user report count
- **Output:** Risk score 0.0–1.0 + scam type tags

### SMS Text Classification
- **Input:** SMS body text (on-device only)
- **Model:** DistilBERT or MobileBERT quantized (TFLite/CoreML)
- **Classes:** Legitimate, Spam, Scam (IRS, Tech Support, Phishing, Romance, etc.)
- **Output:** Classification + confidence score

### Speech Audio Classification (Android only)
- **Input:** Real-time audio buffer via Accessibility Service
- **Model:** Small CNN or RNN (streaming inference)
- **Trigger phrases:** "Social Security", "gift card", "wire transfer", "IRS", "your computer", "Medicare card"
- **Output:** Probability that a scam is in progress; vibration + overlay warning
- **Privacy:** Buffers are ephemeral — processed and discarded immediately. Never written to disk.

### Model Updates
- Server trains new models weekly from aggregated (anonymized) data
- App checks for model updates on launch and periodically in background
- Versioned model files downloaded and cached locally

---

## 7. Backend API Spec

### Base URL: `https://api.safering.app/v1`

| Endpoint | Method | Description | Auth | Rate Limit |
|---|---|---|---|---|
| `/check` | GET | `?hash=<sha256> → { risk, tags }` | No | 100/min |
| `/prefixes` | GET | `→ [ { prefix, risk } ]` | No | 10/min |
| `/model` | GET | `?type=number|sms → model file` | No | 5/min |
| `/model/latest` | GET | `→ { version, url, sha256 }` | No | 5/min |
| `/report` | POST | `{ hash, tag, timestamp }` | No | 20/min |
| `/stats` | GET | `→ { total_reports, top_scams }` | No | 10/min |

### Rate limiting
- Per IP (anonymized via prefix /24)
- Soft limit: return cached response
- Hard limit: 429 with Retry-After

---

## 8. Offline Behavior

- **First launch:** Download initial scam DB + model files
- **Online:** Background sync every 6h (or on manual refresh)
- **Offline:** Use cached DB + local model inference. Full function on cached data.
- **Staleness:** Show "Last updated 3 days ago" if cache is stale. Still protects.
- **Cache size:** ~5-10 MB (hashed numbers + model weights)

---

## 9. Project Structure

### Android (`SafeRing/android/`)
```
app/
├── src/main/java/com/safering/android/
│   ├── SafeRingApp.kt              # Application class
│   ├── MainActivity.kt             # Compose entry
│   ├── di/                          # Hilt DI modules
│   ├── data/
│   │   ├── local/                   # Room DB, DataStore
│   │   ├── remote/                  # Retrofit API client
│   │   ├── repository/             # Repository pattern
│   │   └── model/                  # Data classes
│   ├── domain/
│   │   ├── model/                  # Domain models
│   │   ├── usecase/               # Business logic
│   │   └── ml/                    # TFLite wrappers
│   ├── service/
│   │   ├── CallScreeningService.kt # Pre-call screening
│   │   ├── CallAudioService.kt    # Accessibility for mid-call
│   │   └── SmsReceiver.kt         # SMS classification
│   ├── ui/
│   │   ├── theme/                 # Material 3 theme
│   │   ├── screens/
│   │   │   ├── home/              # Main dash
│   │   │   ├── callhistory/       # Call log
│   │   │   ├── settings/          # Preferences
│   │   │   └── report/            # Report scam
│   │   └── components/            # Shared composables
│   └── util/                      # Helpers
├── src/main/res/                   # Resources
├── src/main/AndroidManifest.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### iOS (`SafeRing/ios/`)
```
SafeRing.xcodeproj/
SafeRing/
├── App/
│   ├── SafeRingApp.swift           # App entry
│   └── ContentView.swift          # SwiftUI root
├── Data/
│   ├── Local/                     # SwiftData models
│   ├── Remote/                    # URLSession API client
│   └── Repository/               # Repository
├── Domain/
│   ├── Models/                    # Domain models
│   ├── UseCases/                  # Business logic
│   └── ML/                       # CoreML wrappers
├── Extension/
│   ├── CallDirectoryHandler.swift # CallKit extension
│   └── SmsClassifier.swift       # SMS detection
├── UI/
│   ├── Theme/                    # Design system
│   ├── Screens/
│   │   ├── Home/                 # Dashboard
│   │   ├── CallHistory/          # Call log
│   │   ├── Settings/             # Preferences
│   │   └── Report/               # Report scam
│   └── Components/               # Shared views
└── Util/                         # Helpers
```

### Backend (`SafeRing/backend/`)
```
├── cmd/server/main.go              # Go server entry
├── internal/
│   ├── handler/                    # HTTP handlers
│   ├── model/                      # Data models
│   ├── store/                      # DB layer
│   ├── ml/                         # Model training pipeline
│   └── scraper/                    # Scam feed scrapers
├── migrations/                     # DB migrations
├── Dockerfile
└── deploy.yml
```

---

## 10. Senior-Friendly UX Decisions

- **Font:** System dynamic text + minimum 16sp on Android, body on iOS
- **Colors:** High contrast mode by default, no pastels
- **Notifications:** ONE actionable type — "Scam Call Blocked". Tapping opens history.
- **Settings:** Single toggle for core features. Option to "Make it smarter" for detailed settings.
- **No login, no signup, no email, no ads.**
- **First run wizard:** 3 screens, 30 seconds max
  1. "Let's protect you" → Grant phone permission
  2. "We'll check scam calls" → Grant Call Screening (Android) / CallKit (iOS)
  3. "All set. Nothing to configure."

---

## 11. MVP Scope (Phase 1)

| Deliverable | Est. Complexity |
|---|---|
| Android project scaffolding | Low |
| CallScreeningService + number lookup | Medium |
| Android SMS scanner | Medium |
| Scam database sync engine | Medium |
| iOS CallKit extension | Medium |
| iOS SMS classifier | Medium |
| Go backend (scraper + API) | High |
| Basic scam feed (FTC + BBB) | Medium |

**Phase 1 target:** Working pre-call warning + SMS scan on both platforms. Mid-call audio (Android) in Phase 2.

---

## 12. Non-Goals (for now)

- Cross-platform framework (Flutter/RN) — native is correct for call APIs
- VoIP / SIP integration — rejected (see SIP section)
- User accounts / cloud sync
- Network-level protection (carrier-level blocking)
- iOS mid-call audio — not possible

---

## 13. Data Flow Diagram (Sequence)

```
User's phone rings
        │
        ▼
┌───────────────────┐
│ OS OnIncomingCall  │
└────────┬──────────┘
         │
         ▼
┌───────────────────┐     ┌──────────────────┐
│ Hash phone number │────→│ Local DB lookup   │
│ SHA-256           │     │ (offline cache)   │
└────────┬──────────┘     └────────┬─────────┘
         │                         │
         │ miss?                   │ hit?
         ▼                         ▼
    ┌─────────┐            ┌──────────────┐
    │ API call │            │ Show result  │
    │ (server) │            │ immediately  │
    └────┬────┘            └──────────────┘
         │
         ▼
    ┌─────────┐
    │ Response │
    │ risk=0.9 │
    │ tag=IRS  │
    └────┬────┘
         │
         ▼
    ┌───────────────────┐
    │ Show overlay:      │
    │ ⚠ HIGH RISK       │
    │ IRS Impersonation │
    │ [Block] [Answer]  │
    └───────────────────┘
```

---

## 14. Future Phases

- **Phase 2:** Android mid-call audio analysis, family alert network
- **Phase 3:** Proactive scam pattern alerts ("New scam wave in your area")
- **Phase 4:** Automated call transcript analysis (opt-in, on-device, privacy-first)
- **Phase 5:** Carrier partnership / STIR/SHAKEN integration
