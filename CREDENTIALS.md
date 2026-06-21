# SafeRing Credentials & Secrets

> ⚠️ **PRODUCTION SECRETS** — Do not share, commit, or expose.

---

## Apple Developer Account

| Field | Value |
|-------|-------|
| Team Name | Kevin Asbury |
| Team ID | `53DRV2V873` |
| Account | kevin@...(personal Apple ID) |

### App Store Connect API Key

| Field | Value |
|-------|-------|
| Key ID | `392Z3XJZQS` |
| Issuer ID | `69a6de75-f335-47e3-e053-5b8c7c11a4d1` |
| Private Key | Original file: `media/inbound/AuthKey_392Z3XJZQS---4a1e82c6-5e88-4769-ab26-90c5c26272c9.txt` (257 bytes, no trailing newline) |
| In CI | Stored as GitHub Secret `ASC_API_KEY_BASE64` (base64-encoded) |
| ⚠️ **History** | The copy in `ios/ci_keys/AuthKey_392Z3XJZQS.p8` had a **transposition error** (`oZIzj0DAQeh` became `oZIz0jDAQeh`) that broke CryptoKit/OpenSSL parsing. Fixed 2026-06-21 by reverting to the original from media/inbound. |

### Distribution Certificate

| Field | Value |
|-------|-------|
| Name | Apple Development: Kevin Asbury (3935NLG9N2) |
| Public Cert | `SafeRing_Distribution.cer` |
| .p12 Private Key | **⚠️ NOT on disk.** Needs to be exported from Kevin's Keychain Access |
| Expires | 2027-06-11 |

> **For future cert renewal**: If the CI ever says "No signing certificate found," you need to re-export the dist cert as .p12. Steps: Keychain Access → My Certificates → right-click "Apple Development: Kevin Asbury" → Export → .p12. Then base64-encode it and update the GitHub secret.

### Provisioning Profiles (on disk in `ios/`)

All are **Distribution** profiles (get-task-allow: false), valid until June 2027:

| Profile | Bundle ID | File |
|---------|-----------|------|
| Gus - store-SafeRing- app | `online.db1k.safering.ios` | `ios/Gus_storeSafeRing_app---106820a9-27e7-44bc-9d82-ea983dbe4ee0.mobileprovision` |
| Gus Call Dire Store | `online.db1k.safering.ios.CallDirectoryHandler` | `ios/Gus_Call_Dire_Store---7b2eac6d-209a-4805-8cdb-bd13cf139ffa.mobileprovision` |
| Gus - Store SafeRing | `online.db1k.safering.ios.watchkitapp` | `ios/Gus_Store_SafeRing---34bc8efb-5500-4bdc-86d3-cc32b257793f.mobileprovision` |
| SafeRing Extension AppStore 1 | `online.db1k.safering.ios.CallDirectoryHandler` | `ios/SafeRing_Extension_AppStore_1---221e0545-a566-4ed5-a757-be9a34c016dc.mobileprovision` |
| SafeRing Extension AppStore 2 | `online.db1k.safering.ios.CallDirectoryHandler` | `ios/SafeRing_Extension_AppStore_2---6a577eb5-fee0-4d48-8f58-b569e994c0b2.mobileprovision` |

> **Original files** backed up at `/home/kevin/.openclaw/media/inbound/`. The CI workflow installs them from the repo at build time.

### Bundle IDs

| App | Bundle ID |
|-----|-----------|
| iOS App | `online.db1k.safering.ios` |
| CallDirectory Extension | `online.db1k.safering.ios.CallDirectoryHandler` |
| Watch App | `online.db1k.safering.ios.watchkitapp` |
| Watch Extension | `online.db1k.safering.ios.watchkitapp.watchkitextension` |
| Android App | `online.db1k.safering.android` |

---

## GitHub Secrets (Repository: uptickguru/SafeRing)

### ✅ Set (iOS)

| Secret | Value | Notes |
|--------|-------|-------|
| `ASC_API_KEY_BASE64` | Base64-encoded `AuthKey_392Z3XJZQS.p8` (344 chars) | Corrected 2026-06-21 from original source |
| `ASC_KEY_ID` | `392Z3XJZQS` | Public, not actually secret |
| `ASC_ISSUER_ID` | `69a6de75-f335-47e3-e053-5b8c7c11a4d1` | Public, not actually secret |
| `KEYCHAIN_PASSWORD` | Random hex string | For CI keychain |

### ❌ Still Needed

| Secret | Required For | How to Get |
|--------|-------------|-----------|
| `IOS_DIST_CERT_BASE64` | Manual code signing (not needed if auto-signing works) | Export .p12 from Keychain Access → base64 encode |
| `IOS_DIST_CERT_PASSWORD` | Unlocking the .p12 | The export password you set |
| `FIREBASE_SERVICE_ACCOUNT` | Android Firebase Distribution | Firebase Console → Project Settings → Service Accounts |

---

## Firebase (Android)

| Field | Status |
|-------|--------|
| Project | `gmg-safering-android` (Spark plan) |
| Project ID | `gmg-safering-android` |
| google-services.json | ✅ Committed at `android/app/google-services.json` |
| Fireabse Admin SDK Email | `firebase-adminsdk-fbsvc@gmg-safering-android.iam.gserviceaccount.com` |
| Service Account Key | ✅ Set as GitHub Secret `FIREBASE_SERVICE_ACCOUNT` (2026-06-21) |
| Local Backup | `android/firebase-service-account.json` (**DO NOT COMMIT**) |
| Org Policy | `db1k.online` org — manually overrode `Disable service account key creation` to NOT ENFORCED to allow key generation |

---

## Backend API

| Field | Value |
|-------|-------|
| URL | `https://safering.deathbyathousand.com` |
| Port | 8080 (internal) |
| Scraper Config | See `backend/.env` |
| FTC API Key | `DEMO_KEY` (rate-limited — could upgrade) |
| Reddit | Not configured (client_id/secret empty) |

---

## Live Status (2026-06-21)

| Component | Status | Notes |
|-----------|--------|-------|
| Backend API `safering.deathbyathousand.com` | ✅ LIVE | 212 scam numbers, 177 prefixes |
| iOS Build Pipeline (GitHub Actions) | ✅ WORKING | Archive + Export + TestFlight upload all succeed |
| iOS App on TestFlight | ✅ LIVE | Build 1.0.0(1) already uploaded |
| Android Build Pipeline | ❌ FAILING | Needs Firebase setup (google-services.json + service account) |

---

## Build Number Management

The iOS app uses version `1.0.0`. You need to **bump the build number** before every TestFlight upload. The CI uses `CURRENT_PROJECT_VERSION`. Options:
- **Manual**: Update `project.yml` → `info:` → `CFBundleVersion` each time
- **Auto**: Wire GitHub Run Number (`GITHUB_RUN_NUMBER`) into the build

---

## Files of Interest

| File | Purpose |
|------|---------|
| `ios/ci_keys/AuthKey_392Z3XJZQS.p8` | ASC API private key (gitignored — use GitHub secret) |
| `media/inbound/AuthKey_392Z3XJZQS---4a1e82c6-5e88-4769-ab26-90c5c26272c9.txt` | ORIGINAL uncorrupted key backup |
| `SafeRing_Distribution.cer` | Public distribution certificate |
| `ios/*.mobileprovision` | Distribution provisioning profiles (committed) |
| `.github/workflows/ios.yml` | iOS CI/CD — builds + TestFlight |
| `.github/workflows/android.yml` | Android CI/CD — builds + Firebase |
| `ios/project.yml` | XcodeGen project spec |

### Distribution Certificate .p12
| Field | Value |
|-------|-------|
| File | `ios/SafeRing_Distribution.p12` (gitignored — use GitHub secret) |
| Password | `kailey99` |
| Identity | `Apple Distribution: Kevin Asbury (53DRV2V873)` |
| In CI | Set as `IOS_DIST_CERT_BASE64` + `IOS_DIST_CERT_PASSWORD` secrets |
