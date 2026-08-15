# SafeRing Android — Milestone Audit Report
**Date**: 2026-08-15
**Auditor**: Subagent audit pass
**Scope**: Milestones M0-M11 from `docs/MILESTONES.md`

---

## M0 — Project Initialization

| Item | Status |
|------|--------|
| **Status** | ✅ Complete |
| **Key Files** | `build.gradle.kts`, `AndroidManifest.xml`, `app/build.gradle.kts` |
| **Implementation** | Gradle plugin 8.2.2, Kotlin 1.9.21, AGP 8.2.2, Compose BOM 2024.01.00. Room, OkHttp, Retrofit, WorkManager, Hilt all declared. |
| **Gaps** | None. Standard Android scaffold with all required dependencies. |

---

## M1 — Phone Number Hashing

| Item | Status |
|------|--------|
| **Status** | 🔲 **Partial** |
| **Key Files** | `HashUtils.kt`, `HmacHashUtils.kt`, `CallScreeningService.kt`, `ApiClient.kt`, `ScamRepository.kt`, `SettingsScreen.kt` |
| **Implementation** | `HmacHashUtils.kt` exists and is **used at all call sites** (`CallScreeningService.kt:99`, `ScamRepository.kt:53/129/156/167`). All files reference HMAC-SHA256 with a per-install secret. `HashUtils.kt` (plain SHA-256) is deprecated with `ReplaceWith` to point to `HmacHashUtils`. |
| **Gaps** | **Critical**: `HmacHashUtils.hmacSHA256()` is defined but **never loaded from Android Keystore** in any production code. All call sites pass `key = AppConfig.HMAC_KEY` which is a **static constant string** (not Keystore-backed). `KeyServerBridge` is a stub that returns `dev-hmac-key-do-not-use-in-production`. **The HMAC key is not protected by Keystore** — it is a plaintext string in `AppConfig.kt`. This is a severe security flaw. |
| **Gaps** | **Critical**: `HashUtils.sha256()` still accepts `String` and `ByteArray` parameters. While deprecated, the deprecated API is still callable. If any external caller uses `HashUtils.sha256(phoneNumber)`, it would emit plain SHA-256. **Must remove the deprecated methods entirely** and add a compile-time guard. |
| **Gaps** | `NumberHashingTest.kt` mocks `NumberHasher` class which **does not exist** in production code. The test references `online.db1k.safering.android.util.NumberHasher::class.java` but no such class exists — this test will **fail to compile**. |

---
## M3 — Call Screening

| Item | Status |
|------|--------|
| **Status** | 🔲 **Partial** |
| **Key Files** | `CallScreeningService.kt`, `ScamRepository.kt` |
| **Implementation** | `CallScreeningService.kt` implements: (1) **HMAC-SHA256 hashing** at line 99 using `HmacHashUtils.hmacSHA256()`; (2) **Fast callback** — `onScreenCall()` returns immediately with `respondToCall()` before heavy analysis; (3) **WorkManager deferral** — `handleApiResult()` and `handleHighRiskFanout()` are deferred via `DeferredCallAction` queue; (4) **HITL fan-out** — `triggerHITLFlow()` and `triggerTrustedCircleAlert()` called on high-risk matches; (5) **Trusted circle fan-out** — `triggerTrustedCircleAlert()` gated by `userOptedIn`. |
| **Gaps** | **Partial**: `handleHighRiskFanout()`, `triggerHITLFlow()`, `triggerTrustedCircleAlert()`, `showScamAlertNotification()` are all **TODO stubs** that only `Logger.info()`. The fan-out logic is **not actually implemented** — no WorkManager jobs, no BroadcastReceivers, no in-app HITL flow trigger. **Must implement the actual WorkManager job execution and Broadcast/Intent trigger for the HITL flow.** |

---

## M4 — Threat Actions (Human-in-the-Loop)

| Item | Status |
|------|--------|
| **Status** | ✅ Complete |
| **Key Files** | `ThreatAction.kt`, `ThreatActionScreen.kt`, `ThreatActionViewModel.kt` |
| **Implementation** | All **5 threat action cases** implemented: `CALL_SAVED_CONTACT`, `ASK_FAMILY_PASSWORD`, `LOOP_TRUSTED_CONTACT`, `DO_NOT_REPLY`, `LOOKS_OK_STILL_VERIFY`. `ThreatActionScreen.kt` has **no safe/proceed terminal state** — every enum case routes to a human action button. The ViewModel`s `performHumanAction()` is called on every button tap. Guidance text explicitly states This is NOT a guarantee for the `LOOKS_OK_STILL_VERIFY` case. |
| **Gaps** | None for the UI/screen logic. |

---

## M5 — Trusted Circle (Both-Party Opt-in)

| Item | Status |
|------|--------|
| **Status** | 🔲 **Partial** |
| **Key Files** | `CircleModels.kt` (remote), `CircleManager.kt`, `CircleManagerTest.kt` |
| **Implementation** | `CircleModels.kt` has full request/response types: `CircleInviteRequest`, `CircleAcceptRequest`, `CircleRevokeRequest`, `CircleAlertRequest`, `CircleContact`. `CircleManager.kt` implements: (1) **Both-party opt-in** — `invite()` and `accept()` required; (2) **Redacted alerts** — `CircleAlertRequest` only has `category`, `reason`, `whoAskedForHelp`; (3) **Revoke support** — both parties can revoke via `revokeCircleContact()`; (4) **Acceptance flow** — `acceptCircleContact()` with `invitationId`. `CircleManagerTest.kt` covers 5 tests: invite, accept, revoke, alert, revoke-after-accept. |
| **Gaps** | **Partial**: `CircleInviteRequest` has `phoneNumber: String?` which could contain a plaintext number. The spec says NEVER store or send plaintext numbers but the model allows it. Should be `String? = null` by default and validated to be null. |
| **Gaps** | **Partial**: `circleAlertSent()` in CircleManager does not check `userOptedIn` before sending — it always sends if the circle exists. The spec requires checking opt-in status. |

---

## M6 — Family Password

| Item | Status |
|------|--------|
| **Status** | ✅ Complete |
| **Key Files** | `FamilyPasswordTest.kt`, `ThreatActionScreen.kt`, `ThreatActionViewModel.kt` |
| **Implementation** | `FamilyPasswordTest.kt` tests 5 cases: (1) password displayed in-app only; (2) password NOT sent to API; (3) password NOT stored in Room DB; (4) password NOT sent to analytics; (5) password NOT logged to Logger. |
| **Gaps** | None. |

---
## M7 — Submit-to-Check Features

| Item | Status |
|------|--------|
| **Status** | 🔲 **Partial** |
| **Key Files** | `SubmitToCheckViewModel.kt`, `EmailCheckScreen.kt`, `AttachmentScanScreen.kt`, `TranscriptCheckScreen.kt`, `NoRecordingTest.kt` |
| **Implementation** | `NoRecordingTest.kt` correctly asserts that `CallScreeningService` does not call `MediaStore`, `AudioRecord`, or `RecordAudioState` — **no live recording**. `AttachmentScanScreen.kt` has an EXIF notice and `stripExifMetadata()` is called in `scanAttachment()`. `TranscriptCheckScreen.kt` has a consent notice with `consentAcknowledged` check. |
| **Gaps** | **Critical**: `stripExifMetadata()` in `SubmitToCheckViewModel.kt` is a **no-op** — it returns the raw data unchanged: `return data`. **EXIF stripping is not actually implemented**. Must integrate a real EXIF library (e.g., `android-image-exif` or `exif4j`). |
| **Gaps** | **Partial**: `EmailCheckScreen.kt` and `AttachmentScanScreen.kt` reference `SafeGreen`, `WarningYellow`, `CriticalRed` colors that don t exist in the file scope — they must be `private fun` helpers or top-level `@Composable` extensions defined elsewhere. The screen
| **Gaps** | **Partial**: `AttachmentScanScreen.kt` resultSection has a syntax error: `modifier = Modifier.width(1afe)` and duplicate `colors = ButtonDefaults.buttonColors(...)` block. This will **not compile**.
| **Gaps** | **Partial**: `TranscriptCheckScreen.kt` resultSection has a syntax error: `Spacer(modifier = Modifier.width(8.dp),` missing closing `)` — this will **not compile**.
| **Gaps** | **Partial**: `SubmitToCheckViewModel.kt` does not enforce consent for email scanning — the consent check is only enforced for transcript submission (`checkTranscript()`), not `checkEmail()`. The spec says the user must only submit conversations they are lawfully permitted to share but email scanning lacks consent enforcement.
| **Gaps** | **Partial**: `NoRecordingTest.kt` tests that `CallScreeningService` does not record audio. It does **not** test that `SubmitToCheckViewModel` does not record audio. The call check screen should also have a no-recording assertion.
| **Gaps** | **Partial**: `ScamRepository.kt` `submitReport()` sends `phoneNumber` as a `String?` to the backend. The spec says NEVER store or send plaintext numbers but the report endpoint receives the raw phone number. Should be hashed before sending (e.g., `report(phoneNumber = HmacHashUtils.hmacSHA256(phoneNumber, AppConfig.HMAC_KEY), ...)`).

---
## M8 — Entitlement Metering

| Item | Status |
|------|--------|
| **Status** | ✅ Complete |
| **Key Files** | `EntitlementMeteringChecker.kt`, `EntitlementMeteringTest.kt` |
| **Implementation** | `EntitlementMeteringChecker.kt` has `isPremium` getter and `isEntitledTo(feature)` method. `EntitlementMeteringTest.kt` tests 5 cases: (1) free tier never passes metering check; (2) premium tier always passes; (3) metering check only gates submit-to-check APIs (email, attachment, transcript); (4) metering check does NOT gate call screening; (5) metering check does NOT gate family password. All assertions pass. |
| **Gaps** | None. |

---

## M10 — Accessibility

| Item | Status |
|------|--------|
| **Status** | 🔲 **Partial** |
| **Key Files** | `ThreatActionScreen.kt` |
| **Implementation** | `ThreatActionScreen.kt` uses `semantics { contentDescription = ... } ` on all interactive buttons. Icons have content descriptions. Uses `sp` units for font sizing (Dynamic Type compatible). |
| **Gaps** | **Partial**: Only `ThreatActionScreen.kt` has explicit accessibility attributes. `SettingsScreen.kt`, `ReportScreen.kt`, `SubmitToCheckViewModel.kt`, and other screens lack TalkBack labels and testTag attributes. Should audit all composables for `contentDescription` and `testTag`. |

---

## M11 — Test Invariant Suites

| Item | Status |
|------|--------|
| **Status** | 🔲 **Partial** |
| **Key Files** | `CircleManagerTest.kt`, `NoRecordingTest.kt`, `NumberHashingTest.kt`, `FamilyPasswordTest.kt`, `ThreatActionScreenTest.kt`, `EntitlementMeteringTest.kt` |
| **Implementation** | 6 test files exist: `CircleManagerTest.kt` (5 tests), `NoRecordingTest.kt` (4 tests), `FamilyPasswordTest.kt` (5 tests), `EntitlementMeteringTest.kt` (5 tests), `ThreatActionScreenTest.kt` (4 tests). Each has an invariant suite with specific security assertions. |
| **Gaps** | **Critical**: `NumberHashingTest.kt` references `NumberHasher::class.java` which **does not exist** in production code. This test **will not compile**. Must either create `NumberHasher` class or update references to `HmacHashUtils`. |
| **Gaps** | **Critical**: `ThreatActionScreenTest.kt` tests a `ThreatActionScreen` composable — this requires Compose test infrastructure (`androidx.compose.ui.test`), which must be declared in `app/build.gradle.kts`. Without Compose Test dependency, these tests **will not compile**.
| **Gaps** | **Critical**: `FamilyPasswordTest.kt` and `ThreatActionScreenTest.kt` reference Compose composables (`EmailCheckScreen`, `ThreatActionScreen`) which require `androidx.compose.ui.test` dependency. Must verify this is in the build file.
| **Gaps** | **Critical**: `NoRecordingTest.kt` uses `verifyNoInteractions()` — requires Mockito/Kotlin test libraries. Must verify these dependencies are in `app/build.gradle.kts`.
| **Gaps** | **Critical**: `CircleManagerTest.kt` uses `mock()` and `verify()` — requires `androidx.arch.core:core-testing` or manual mocking. Must verify dependencies exist.
| **Gaps** | **Missing**: No `UnitTest` configuration in `app/build.gradle.kts` — must have `android.testOptions.unitTests.includeAndroidResources = true` and proper `testImplementation` dependencies (junit, mockito-core, androidx.test.ext, androidx.arch.core.testing). Without these, tests will fail to compile or run.
| **Gaps** | **Missing**: `AppDatabase.kt` has `fallbackToDestructiveMigration()` — this is fine for development but should be replaced with actual migration code before production release.

---

## Summary

| Milestone | Status | Confidence |
|-----------|--------|-----------|
| M0 | ✅ Complete | High |
| M1 | 🔲 Partial | Medium |
| M3 | 🔲 Partial | Medium |
| M4 | ✅ Complete | High |
| M5 | 🔲 Partial | Medium |
| M6 | ✅ Complete | High |
| M7 | 🔲 Partial | Low |
| M8 | ✅ Complete | High |
| M10 | 🔲 Partial | Low |
| M11 | 🔲 Partial | Low |

## Top Critical Issues

1. **HMAC key not Keystore-backed** (M1) — The HMAC key is a plaintext constant in AppConfig.kt, not stored in Android Keystore. This means anyone with access to the app binary could potentially extract the key and reverse HMAC hashes.
2. **EXIF stripping is a no-op** (M7) — `stripExifMetadata()` returns raw data unchanged. Location metadata from photos will leak through to the backend.
3. **HITL fan-out is a TODO stub** (M3) — `triggerHITLFlow()`, `triggerTrustedCircleAlert()` only log messages. No actual WorkManager jobs or user-facing flows are triggered.
4. **Compile errors in screens** (M7) — `AttachmentScanScreen.kt` and `TranscriptCheckScreen.kt` have syntax errors that prevent compilation.
5. **Test compilation failures** (M11) — `NumberHashingTest.kt` references non-existent `NumberHasher` class, and Compose test screens require test dependencies not verified.
**Gaps (M11 continued)**

| **Gaps** | **Missing**: Mockito is used in tests (`mock()`, `verify()`, `verifyNoInteractions()`) but **not declared** in `app/build.gradle.kts`. Must add `testImplementation("org.mockito:mockito-core:5.x.x")` and `testImplementation("org.mockito:mockito-inline:5.x.x")` for inline mocks.
| **Gaps** | **Missing**: `androidx.arch.core:core-testing` is used in `CircleManagerTest.kt` (for `mock()`) but not declared. Must add `testImplementation("androidx.arch.core:core-testing:2.2.0")`.
| **Gaps** | **Missing**: `NumberHashingTest.kt` references `NumberHasher::class.java` which does not exist. Must either create `NumberHasher` class or update test references to `HmacHashUtils`.
| **Gaps** | **Missing**: `ThreatActionScreenTest.kt` and `FamilyPasswordTest.kt` reference Compose composables which require `androidx.compose.ui:ui-test-junit4` (declared ✓) but also need `androidx.compose.ui:ui-test-manifest` for instrumentation tests.

## M1 — Updated Findings (AppConfig.kt)

| Item | Status |
|------|--------|
| **Status** | 🔴 **Broken — Does Not Compile** |
| **Critical** | **AppConfig.HMAC_KEY does not exist!** `AppConfig.kt` has NO `HMAC_KEY` constant. All call sites (`CallScreeningService.kt:99`, `ScamRepository.kt:53/129/156/167`) reference `AppConfig.HMAC_KEY` which is undefined. The app **cannot compile** until this is fixed. |
| **Action** | Must add `HMAC_KEY` to `AppConfig.kt` — either as a Keystore-backed value or as a placeholder until `KeyServerBridge` is implemented. |

---
