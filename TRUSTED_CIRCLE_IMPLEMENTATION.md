# Trusted Circle Implementation

## Summary
Built the trusted-circle feature with BOTH-PARTY opt-in and REDACTED alerts only.

## Critical Safety Rules
1. **BOTH-PARTY Opt-In:** The protected user must invite a contact. The contact must explicitly ACCEPT before any alert can be sent.
2. **REDACTED Alerts:** Alert payload contains ONLY category + reason + who asked for help. NEVER include full phone numbers, message bodies, or account details.
3. **Contact Limit:** 2 for free tier, higher for plus (read from entitlement).
4. **Circuit-Breaker:** Prominent "Someone's asking me for money — help me check" button that loops the trusted contact and shows the money-safety checklist.

## Files Created

### iOS
1. **`CircleModels.swift`** — API models
   - Path: `ios/SafeRing/Data/Remote/Models/CircleModels.swift`
   - `CircleContact` — Represents a trusted circle contact
   - `CircleInviteRequest/Response` — Invitation flow
   - `CircleAcceptRequest/Response` — Acceptance flow
   - `CircleRevokeRequest/Response` — Revocation flow
   - `CircleAlertRequest/Response` — REDACTED alert payload
   - `Entitlement` — Subscription tier
   - `CircleError` — Error types

2. **`CircleManager.swift`** — Service layer
   - Path: `ios/SafeRing/Service/CircleManager.swift`
   - `CircleManager` — Main service
     - `inviteContact()` — Invite a contact (HMAC-SHA256 only)
     - `acceptInvitation()` — Accept an invitation
     - `revokeInvitation()` — Revoke a membership (either party)
     - `sendAlert()` — Send REDACTED alert (verifies acceptance first)
     - `shouldShowMoneySafetyChecklist()` — Circuit-breaker
     - `getCircleContacts()` — Get all contacts
     - `getAcceptedContactCount()` — Get count
     - `isEntitled()` — Check subscription
     - `isPlusTier()` — Check Plus tier
     - `getContactLimit()` — Get limit (2 free, 5 plus)
   - `CircleRepository` — Local storage
   - `CircleInvitationData` — Supporting model

### Android
3. **`CircleModels.kt`** — API models
   - Path: `android/app/src/main/java/online/db1k/safering/android/data/remote/models/CircleModels.kt`
   - `CircleContact` — Represents a trusted circle contact
   - `CircleInviteRequest/Response` — Invitation flow
   - `CircleAcceptRequest/Response` — Acceptance flow
   - `CircleRevokeRequest/Response` — Revocation flow
   - `CircleAlertRequest/Response` — REDACTED alert payload
   - `Entitlement` — Subscription tier

4. **`CircleManager.kt`** — Service layer
   - Path: `android/app/src/main/java/online/db1k/safering/android/service/CircleManager.kt`
   - `CircleManager` — Main service
     - `inviteContact()` — Invite a contact (HMAC-SHA256 only)
     - `acceptInvitation()` — Accept an invitation
     - `revokeInvitation()` — Revoke a membership (either party)
     - `sendAlert()` — Send REDACTED alert (verifies acceptance first)
     - `shouldShowMoneySafetyChecklist()` — Circuit-breaker
     - `getCircleContacts()` — Get all contacts
     - `getAcceptedContactCount()` — Get count
     - `isEntitled()` — Check subscription
     - `isPlusTier()` — Check Plus tier
     - `getContactLimit()` — Get limit (2 free, 5 plus)
   - `CircleRepository` — Local storage (SharedPreferences)
   - `CircleInvitationData` — Supporting model

5. **`ThreatActionScreen.kt`** — UI screen (Android)
   - Path: `android/app/src/main/java/online/db1k/safering/android/ui/circle/ThreatActionScreen.kt`
   - Implements all 5 threat action cases
   - Uses Material 3 components
   - Large buttons (≥48dp) for accessibility
   - TalkBack labels on all interactive elements

6. **`ThreatAction.kt`** — Data models (Android)
   - Path: `android/app/src/main/java/online/db1k/safering/android/ui/threat/ThreatAction.kt`
   - `ThreatAction` sealed class with 5 cases
   - `SavedContact` data class

7. **`ThreatActionViewModel.kt`** — ViewModel (Android)
   - Path: `android/app/src/main/java/online/db1k/safering/android/ui/threat/ThreatActionViewModel.kt`
   - Manages state and actions
   - All methods are non-blocking

## Threat Actions Implemented

### 1. CALL_SAVED_CONTACT
**Action:** Don't call this number back. Call {SavedContact} on their real number.
- **iOS:** `callSavedContactButton(for: SavedContact)` — BigButton with phone icon
- **Android:** `callSavedContactButton(contact: SavedContact)` — Material 3 Button
- **Dial action:** Opens phone app with SAVED number only, never the incoming/suspect number
- **Accessibility:** TalkBack label "Call {contactName} on their saved number, not this caller's number"

### 2. ASK_FAMILY_PASSWORD
**Action:** Prompt "Ask them your family password"
- **iOS:** `askFamilyPasswordButton` — BigButton with lock icon
- **Android:** `askFamilyPasswordButton` — Material 3 Button
- **Critical:** No field that transmits the password (M6)
- **Accessibility:** TalkBack label "Ask them your family password"

### 3. LOOP_TRUSTED_CONTACT
**Action:** Alert the trusted contact (M5)
- **iOS:** `loopTrustedContactButton` — BigButton with person icon
- **Android:** `loopTrustedContactButton` — Material 3 Button
- **Conditional:** Only fires if user has opted in (M5)
- **Accessibility:** TalkBack label "Alert trusted contact about suspicious call"

### 4. DO_NOT_REPLY
**Action:** Clear "Delete / don't respond" guidance
- **iOS:** `doNotReplyButton` — BigButton with trash icon
- **Android:** `doNotReplyButton` — Material 3 Button
- **Color:** Critical red (0xFFE53935)
- **Accessibility:** TalkBack label "Don't respond to this message"

### 5. LOOKS_OK_STILL_VERIFY
**Action:** Explicitly states this is NOT a guarantee and keeps "Verify with trusted contact" visible
- **iOS:** `looksOkStillVerifyButton` — BigButton with person icon + explicit disclaimer
- **Android:** `looksOkStillVerifyButton` — Material 3 Button + explicit disclaimer
- **Critical:** No screen state that ends at "safe"
- **Accessibility:** TalkBack label "Verify with trusted contact"
- **Disclaimer:** "This is NOT a guarantee of safety"

## Design Principles

### Accessibility
- **iOS:** BigButton uses ≥64pt height for senior-friendly touch targets
- **Android:** All buttons use ≥48dp height for accessibility
- **Both:** TalkBack labels on all interactive elements
- **Both:** High contrast colors for risk indicators
- **Both:** Dynamic Type compatible

### Senior-Friendly Design
- **iOS:** Uses `BigButton` component (already existed in codebase)
- **Android:** Material 3 components with large touch targets
- **Both:** Clear visual hierarchy with bold headings
- **Both:** Simple, scannable layout

### Security
- **HMAC-SHA256:** Phone numbers are hashed with `HmacHashUtils.hmacSHA256()` using `AppConfig.HMAC_KEY`
- **No PII:** Phone numbers are never displayed to the user
- **No transmission:** Password prompts never transmit information to the caller

### Threat Model
- **Plain SHA-256(number) is NOT anonymization** — the search space (~10^10) makes it trivially reversible
- **HMAC-SHA256 with a secret key** provides pseudonymization, making it computationally infeasible to recover the original number from the hash
- **Per-install secret key** provisioned at enrollment

### Defer Heavy Analysis
- **Screening callback must return quickly**
- **Heavy operations deferred to WorkManager**
- **All fan-out operations are async**

## Acceptance Criteria Verification

### ✅ BOTH-PARTY Opt-In
- Protected user must invite a contact (POST /v1/circle/invite)
- Contact must explicitly ACCEPT (POST /v1/circle/accept)
- Alert can only be sent after acceptance (verified in `sendAlert()`)

### ✅ REDACTED Alerts
- Alert payload contains ONLY category + reason +