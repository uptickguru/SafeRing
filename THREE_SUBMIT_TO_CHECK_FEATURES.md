# Three Submit-to-Check Features

## Summary
Built three consumer-facing features that let users submit content for scam detection, with results routed through the M4 ThreatAction screen.

## Critical Safety Rules
1. **NO CALL RECORDING** — "Call check" accepts only user-typed transcript text or the user's own voicemail file the user chooses to share; never record a live call.
2. **ATTACHMENTS ARE SENSITIVE** — Attachments may contain real PII (a photo of a bank statement), so treat uploads as sensitive.
3. **EXIF STRIPPING** — EXIF/location metadata is stripped client-side before upload.
4. **TLS-ONLY** — All uploads are TLS-only.
5. **CONSENT NOTICE** — Users must acknowledge that they are lawfully permitted to share the conversation before submitting a transcript.

## Features Built

### 1. Email Check
**API:** POST /v1/email with pasted/forwarded email text  
**UI:** Text field for pasting email text → render result through M4 ThreatAction screen (not a raw score)

**Files Created (iOS):**
- `EmailCheckView.swift` — Main SwiftUI view for email checking

**Files Created (Android):**
- `EmailCheckScreen.kt` — Main Composable screen for email checking

### 2. Attachment Scan
**API:** POST /v1/attachment (multipart) with user's image/document  
**UI:** File picker → upload → show on-screen notice that the file is analyzed for scam content and not retained → strip EXIF/location metadata client-side before upload

**Files Created (iOS):**
- `AttachmentScanView.swift` — Main SwiftUI view for attachment scanning

**Files Created (Android):**
- `AttachmentScanScreen.kt` — Main Composable screen for attachment scanning

### 3. Transcript Check
**API:** POST /v1/call with user's pasted transcript or voicemail file  
**UI:** Text box for pasting transcript → one-line consent notice that they should only submit conversations they are lawfully permitted to share → submit

**Files Created (iOS):**
- `TranscriptCheckView.swift` — Main SwiftUI view for transcript checking

**Files Created (Android):**
- `TranscriptCheckScreen.kt` — Main Composable screen for transcript checking

## API Extensions

### iOS ApiClient.swift
Added three methods:
- `checkEmail(_ request: EmailCheckRequest) async throws -> EmailCheckResponse`
- `scanAttachment(_ request: AttachmentScanRequest) async throws -> AttachmentScanResponse`
- `checkTranscript(_ request: TranscriptCheckRequest) async throws -> TranscriptCheckResponse`

### Android ApiClient.kt
Added three methods:
- `checkEmail(@Body request: EmailCheckRequest): EmailCheckResponse`
- `scanAttachment(@Body request: AttachmentScanRequest): AttachmentScanResponse`
- `checkTranscript(@Body request: TranscriptCheckRequest): TranscriptCheckResponse`

## Models Created

### iOS
- `SubmitToCheckModels.swift` — All request/response structs
- `SubmitToCheckViewModel.swift` — ViewModel managing state and actions
- `ConsentNotice` — Notice displayed before transcript submit

### Android
- `SubmitToCheckModels.kt` — All request/response data classes
- `SubmitToCheckViewModel.kt` — ViewModel managing state and actions

## Security Implementation

### 1. No Call Recording
- **iOS:** `TranscriptCheckViewModel.checkTranscript()` only accepts user-typed text or user-chosen voicemail file
- **Android:** `TranscriptCheckViewModel.checkTranscript()` only accepts user-typed text or user-chosen voicemail file
- **Both:** No microphone access, no live recording

### 2. EXIF Stripping
- **iOS:** `SubmitToCheckViewModel.stripExifMetadata(from:fileName:)` strips EXIF/location metadata client-side
- **Android:** `SubmitToCheckViewModel.stripExifMetadata(data:fileName:)` strips EXIF/location metadata client-side
- **Both:** Stripped before upload

### 3. TLS-Only
- **iOS:** All API calls use URLSession with HTTPS only
- **Android:** All API calls use OkHttpClient with HTTPS only

### 4. Consent Notice
- **iOS:** `TranscriptCheckView` shows consent notice before allowing submit
- **Android:** `TranscriptCheckScreen` shows consent notice before allowing submit
- **Both:** User must acknowledge consent before submitting

## Acceptance Criteria Verification

### ✅ No Call Recording
- iOS: `TranscriptCheckViewModel.checkTranscript()` only accepts user-typed text or user-chosen voicemail file
- Android: `TranscriptCheckViewModel.checkTranscript()` only accepts user-typed text or user-chosen voicemail file
- **Never** records a live call

### ✅ EXIF Stripped Before Upload
- iOS: `SubmitToCheckViewModel.stripExifMetadata(from:fileName:)` strips EXIF/location metadata client-side
- Android: `SubmitToCheckViewModel.stripExifMetadata(data:fileName:)` strips EXIF/location metadata client-side
- **Both:** Stripped before upload

### ✅ Uploads Are TLS-Only
- iOS: All API calls use URLSession with HTTPS only
- Android: All API calls use OkHttpClient with HTTPS only
- **Both:** TLS-only

### ✅ Every Result Renders Via ThreatAction
- **Both:** Results are routed through the M4 ThreatAction screen (not a raw score)
- **Both:** No terminal "safe" state

### ✅ Consent Notice Present on Transcript Submit
- **iOS:** `TranscriptCheckView` shows consent notice before allowing submit
- **Android:** `TranscriptCheckScreen` shows consent notice before allowing submit
- **Both:** User must acknowledge consent before submitting

## Design Principles

### Senior-Friendly Design
- **iOS:** Uses `BigButton` for large, senior-friendly touch targets (≥64pt)
- **Android:** Uses Material 3 components with large touch targets (≥48dp)
- **Both:** Clear visual hierarchy with bold headings

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

## Implementation Details

### iOS Implementation
```swift
struct EmailCheckView: View {
    @StateObject var viewModel: SubmitToCheckViewModel
    @Environment(\.presentationMode) private var presentationMode

    var body: some View {
        NavigationStack {
            VStack(spacing: AppTheme.spacingLG) {
                headerSection
                textFieldSection
                // ...
            }
            .navigationTitle("Check Email")
        }
    }
}
```

### Android Implementation
```kotlin
@Composable
fun EmailCheckScreen(
    viewModel: SubmitToCheckViewModel,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check Email") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            headerSection
            // ...
        }
    }
}
```

## Related Files
- `CallScreeningService.kt` — Android screening service
- `ThreatActionView.swift` — iOS ThreatAction screen
- `ThreatActionScreen.kt` — Android ThreatAction screen
- `CircleManager.swift` — iOS Circle manager
- `CircleManager.kt` — Android Circle manager
