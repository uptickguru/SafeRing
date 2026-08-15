## M7 — Submit-to-Check Features (continued)

| Item | Status |
|------|--------|
| **Key Files** | `SubmitToCheckViewModel.kt`, `EmailCheckScreen.kt`, `AttachmentScanScreen.kt`, `TranscriptCheckScreen.kt`, `NoRecordingTest.kt` |
| **Gaps (continued)** | **Partial**: `EmailCheckScreen.kt` and `AttachmentScanScreen.kt` reference `SafeGreen`, `WarningYellow`, `CriticalRed` colors that don't exist in the file scope — they must be `private fun` helpers or top-level `@Composable` extensions defined elsewhere. The screen