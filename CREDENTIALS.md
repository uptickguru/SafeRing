# SafeRing Credentials & Secrets

> ⚠️ **PRODUCTION SECRETS** — Do not share, commit, or expose.

---

## Apple Developer Account

| Field | Value |
|-------|-------|
| Team Name | Kevin Asbury |
| Team ID | `53DRV2V873` |
| Account | _(Kevin's personal Apple ID)_ |

### App Store Connect API Key

| Field | Value |
|-------|-------|
| Key ID | `392Z3XJZQS` |
| Issuer ID | `69a6de75-f335-47e3-e053-5b8c7c11a4d1` |
| Private Key | `ios/ci_keys/AuthKey_392Z3XJZQS.p8` (ES256) |

### Distribution Certificate

| Field | Value |
|-------|-------|
| Name | Apple Development: Kevin Asbury (3935NLG9N2) |
| File | `SafeRing_Distribution.cer` (public cert only) |
| Status | **⚠️ Missing .p12** — need private key export from Keychain Access |
| Expires | 2027-06-11 |

> **Note:** The .cer is a development cert, not a true distribution cert. For TestFlight builds, you'll need a distribution certificate exported as .p12. Go to Keychain Access → My Certificates → right-click "Apple Development: Kevin Asbury" → Export → .p12.

### Provisioning Profiles

**None on disk.** The iOS workflow relies on Xcode automatic signing (`-allowProvisioningUpdates`) which generates profiles on-the-fly using the ASC API key. If manual profiles are needed later, store them here as `.mobileprovision` files.

### Bundle IDs

| App | Bundle ID |
|-----|-----------|
| iOS App | `online.db1k.safering.ios` |
| CallDirectory Extension | `online.db1k.safering.ios.CallDirectoryHandler` |
| Watch App | `online.db1k.safering.ios.watchkitapp` |
| Watch Extension | `online.db1k.safering.ios.watchkitapp.watchkitextension` |
| Android App | `online.db1k.safering.android` |

---

## Firebase (Android App Distribution)

| Field | Status |
|-------|--------|
| Project | _(not set — needs Firebase project creation)_ |
| Service Account JSON | **❌ Missing** — download from Firebase Console → Project Settings → Service Accounts → Generate new private key |
| google-services.json | **❌ Missing** — download from Firebase Console → Project Settings → Your apps |

---

## Backend API

| Field | Value |
|-------|-------|
| URL | `https://safering.deathbyathousand.com` |
| Port | 8080 (internal) |
| Scraper API Keys | See `.env` in `backend/` |
| FTC API Key | `DEMO_KEY` (rate-limited — upgrade to real key) |
| Reddit API | Not configured (client_id/secret empty) |

---

## GitHub Repository Secrets Needed

Set these in repo Settings → Secrets and variables → Actions:

### iOS (`github.com/uptickguru/SafeRing`)

| Secret | Value | Source |
|--------|-------|--------|
| `ASC_API_KEY_BASE64` | Base64 of `AuthKey_392Z3XJZQS.p8` | `ios/ci_keys/AuthKey_392Z3XJZQS.p8` |
| `ASC_KEY_ID` | `392Z3XJZQS` | From p8 filename |
| `ASC_ISSUER_ID` | `69a6de75-f335-47e3-e053-5b8c7c11a4d1` | From App Store Connect |
| `KEYCHAIN_PASSWORD` | _(any random string)_ | Set this |
| `IOS_DIST_CERT_BASE64` | **⚠️ Not set yet** | Need .p12 export |
| `IOS_PROVISIONING_PROFILE_BASE64` | **⚠️ Not set** | Optional with auto signing |

### Android

| Secret | Value | Source |
|--------|-------|--------|
| `FIREBASE_SERVICE_ACCOUNT` | **❌ Missing** | Firebase Console |
| `google-services.json` | **❌ Missing** (file, not a secret) | Firebase Console |

---

## Previous Builds

| Date | What | Artifact |
|------|------|----------|
| 2026-06-19 | Server binary rebuilt (commit `ac53052`) | `backend/safering-server` |
| 2026-06-11 | Distribution cert generated | `SafeRing_Distribution.cer` |
