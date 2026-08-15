# ThreatActionView/ThreatActionScreen Implementation

## Summary
Built `ThreatActionView` (iOS SwiftUI) and `ThreatActionScreen` (Android Compose) driven by the `recommendedAction` enum from M0.

## Critical Safety Rule
**This is the most important screen in the app.** When anything is flagged, the UI must DRIVE A HUMAN ACTION and must NEVER present a "you're safe, proceed" that closes the loop on the AI's verdict.

## Files Created

### iOS
1. **`ThreatActionView.swift`** — Main SwiftUI view component
   - Path: `ios/SafeRing/UI/Components/ThreatActionView.swift`
   - Implements all 5 threat action cases
   - Uses `BigButton` for large, senior-friendly touch targets (≥64pt)
   - High contrast colors for risk indicators
   - VoiceOver/TalkBack labels on all interactive elements
   - Dynamic Type compatible

2. **`ThreatAction.swift`** — Domain model
   - Path: `ios/SafeRing/Domain/Models/ThreatAction.swift`
   - `ThreatAction` enum with 5 cases
   - `SavedContact` struct for contact-to-call data

3. **`ThreatActionViewModel.swift`** — ViewModel
   - Path: `ios/SafeRing/ViewModel/ThreatActionViewModel.swift`
   - Manages state and actions
   - All methods are non-blocking

### Android
4. **`ThreatActionScreen.kt`** — Main Composable screen
   - Path: `android/app/src/main/java/online/db1k/safering/android/ui/threat/ThreatActionScreen.kt`
   - Implements all 5 threat action cases
   - Uses Material 3 components
   - Large buttons (≥48dp) for accessibility
   - TalkBack labels on all interactive elements
   - High contrast colors for risk indicators

5. **`ThreatAction.kt`** — Data models
   - Path: `android/app/src/main/java/online/db1k/safering/android/ui/threat/ThreatAction.kt`
   - `ThreatAction` sealed class with 5 cases
   - `SavedContact` data class for contact-to-call data

6. **`ThreatActionViewModel.kt`** — ViewModel
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
- **Hint:** "This will prompt you to ask the caller for your family password without transmitting any information"

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

### ✅ Every enum case routes to a human action
- `CALL_SAVED_CONTACT` → Dial saved contact
- `ASK_FAMILY_PASSWORD` → Prompt to ask password
- `LOOP_TRUSTED_CONTACT` → Alert trusted contact
- `DO_NOT_REPLY` → Mark as do-not-reply
- `LOOKS_OK_STILL_VERIFY` → Verify with trusted contact

### ✅ The dial action targets the SAVED number only
- iOS: `openPhoneApp(with: String)` pre-fills the saved number
- Android: `openPhoneApp(savedNumber: String)` dials the saved number
- **Never** the incoming/suspect number

### ✅ No code path renders a terminal "safe/proceed"
- All screens have action buttons that keep the loop open
- No terminal state that says "you're safe"
- Always actionable

### ✅ Screen passes VoiceOver/TalkBack
- iOS: VoiceOver labels on all interactive elements
- Android: TalkBack labels on all interactive elements
- High contrast colors for accessibility

## Implementation Details

### iOS Implementation
```swift
struct ThreatActionView: View {
    let recommendedAction: ThreatAction
    let callerLabel: String
    let savedContact: SavedContact?
    let userOptedIn: Bool
    let numberHash: String
    let wasBlocked: Bool

    var body: some View {
        ScrollView {
            VStack(spacing: AppTheme.spacingLG) {
                headerSection  // Risk indicator
                actionButtonsSection  // Action buttons
                guidanceSection  // Additional guidance
                footerSection  // Always actionable
            }
        }
        .background(Color("appBackground"))
        .navigationTitle("Threat Detected")
    }
}
```

### Android Implementation
```kotlin
@Composable
fun ThreatActionScreen(
    recommendedAction: ThreatAction,
    callerLabel: String,
    savedContact: SavedContact?,
    userOptedIn: Boolean,
    numberHash: String,
    wasBlocked: Boolean,
    onHumanAction: (ThreatAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Threat Detected") }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            headerSection  // Risk indicator
            actionButtonsSection  // Action buttons
            guidanceSection  // Additional guidance
            footerSection  // Always actionable
        }
    }
}
```

## Related Files
- `CallScreeningService.kt` — Android screening service (already
