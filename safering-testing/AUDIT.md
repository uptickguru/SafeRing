# SafeRing App Audit

**Date:** 2026-06-26
**Build:** #91 (iOS on TestFlight) + #52 (Android passed)
**Role:** Mobile QA Lead + Growth PM

---

## 1. Onboarding & Permission Friction ⚠️ CRITICAL

### Problem
SafeRing requires **4+ permissions** (Notifications, Phone, Call Log, SMS) plus **Call Directory Extension activation** (iOS) or **Call Screening** (Android). Each permission is a drop-off point.

### Data points
- iOS permission dialogs are system-level — user can tap "Don't Allow" and get stuck
- Call Directory requires leaving the app → opening Settings → navigating multiple menus → returning
- No progress indicator showing "3 of 5 steps complete"
- No graceful degradation when a permission is denied

### Fix
- **Graceful degradation**: If Call Log is denied, show a partial UI (manual number entry) instead of a blank screen
- **Re-prompt strategy**: Don't hammer users — save denied permissions and re-prompt after a meaningful action (e.g., "Try checking a number" → "We need phone access to do that")
- **Onboarding progress bar**: Show step count ("Step 2 of 5") so users know there's an end

---

## 2. Empty States 🟡 MODERATE

### Problem
No empty states visible in the codebase — `CallHistory`, `ScamReport`, `Home` likely show nothing on first launch.

### Fix
Every list screen needs:
- **Empty state**: Illustration + microcopy ("No calls yet — SafeRing is watching")
- **First-action CTA**: "Check a number" or "Share SafeRing with family"
- **Loading state**: Skeleton loader, not spinner, while fetching scam data
- **Error state**: "Couldn't connect. Tap to retry" with clear action

---

## 3. Retention Risks 🔴 HIGH

### Problem
Passive utility apps (anti-spam, security) have notoriously low retention. Users install, grant permissions, and forget it exists.

### Three specific risks for SafeRing:

**Risk 1: No "Day 1" moment**
- If no scam call arrives in the first week, user has no proof it works
- **Fix**: On Day 2, push a weekly summary notification ("SafeRing has been active — you've missed 0 scam calls this week"). A zero count is still proof it's working.

**Risk 2: No visible presence**
- No widget, no lock screen integration, no notification center summary
- **Fix**: iOS widget showing "Scams blocked today: 0". Android notification channel with persistent status.

**Risk 3: No social proof or sharing**
- No mechanism to say "I blocked a scammer — download SafeRing"
- **Fix**: After blocking a scam, show a share sheet: "⚠️ This number has been reported as a scam by SafeRing users"

---

## 4. Privacy & Trust Issues 🟡 MODERATE

### Problem
A call/SMS scanning app with a Go backend raises legitimate privacy concerns.

### What's needed:
- **Privacy policy link** in onboarding and Settings
- **On-device processing disclosure**: "Scam detection runs on your device. Your call data never leaves your phone."
- **Opt-out controls**: Settings should clearly show what data syncs to backend vs stays local
- **Encryption disclosure**: API comms are likely HTTP/Go — confirm TLS is enforced

---

## 5. Edge Cases & States 🔴 HIGH

### Found these gaps:

| Scenario | Current Behavior | Expected |
|---|---|---|
| No internet at launch | ? | Cache last scam DB, show "Offline — last updated [time]" |
| Call arrives during onboarding | Phone UI takes over, user loses onboarding progress | Save state, resume where left off |
| User has dual SIMs | ? | Detect both numbers |
| Scam DB fetch fails | ? | Retry with backoff, use stale data |
| Permission was denied, later granted | ? | App should auto-detect and enable features |
| Watch is not paired | ? | Don't show watch features, show setup CTA instead |

---

## 6. App Store / Play Store Risks 🟡 MODERATE

### iOS risks:
- **Call Directory Extension** requires specific entitlement and will be reviewed by Apple
- **SMS access** with `READ_SMS` may trigger review rejection unless documented clearly
- **NSMicrophoneUsageDescription** + **NSSpeechRecognitionUsageDescription** in Info.plist — what are these for? If not used, remove. If used, document.

### Android risks:
- **READ_CALL_LOG** + **READ_SMS** = Google Play sensitive permission review. Must submit video demonstrating use.
- **BIND_SCREENING_SERVICE** — only for default dialer apps. May need to clarify this is a call screening app.
- Play Store requires **privacy policy URL** and **data safety section** before publishing.

---

## 7. Technical Debt 🟡 MODERATE

### From what we know:
- **iOS**: `SWIFT_STRICT_CONCURRENCY=legacy` enabled — means Swift 6 concurrency warnings are suppressed. Should migrate to strict and fix the ~30 warnings in the build log.
- **iOS**: `--generate-entitlement-der` in codesign — fine for now, but if Apple removes this flag, builds break.
- **UI tests**: Zero — we just built the Maestro framework but nothing is integrated into CI
- **Backend**: Go backend with no tests visible. `go test ./...` should be minimal.
- **No Crashlytics or analytics**: Firebase Crashlytics is in the project.yml but the build uses `legacy` concurrency — crash reporting needs to be verified working.

---

## 8. Monetization & Business Model 🟡 MODERATE

### Current state:
- Free app, no monetization visible
- No premium tier, no subscriptions

### Suggestions (low-pressure):
- **Free forever** with optional donation / "Buy us a coffee" in Settings
- **Family plan**: Show value by adding family members to the scam network
- **Enterprise angle**: Sell the scam detection API to VoIP providers or phone carriers

---

## Prioritized Fix List

| Priority | Fix | Effort | Impact |
|---|---|---|---|
| 🔴 P0 | Empty states on all lists | 2 days | High — first impression |
| 🔴 P0 | Graceful permission handling | 3 days | High — retention |
| 🔴 P0 | Offline mode / stale data | 2 days | High — reliability |
| 🟡 P1 | Day 1 retention (weekly summary) | 1 day | High — retention |
| 🟡 P1 | Privacy disclosure in onboarding | 1 day | High — trust |
| 🟡 P1 | App Store privacy docs | 1 day | High — launch blocker |
| 🟢 P2 | Widget / notification center | 3 days | Medium — engagement |
| 🟢 P2 | Share-on-block flow | 2 days | Medium — growth |
| 🟢 P2 | Swift 6 concurrency migration | 5 days | Low — tech debt |
| 🔵 P3 | Watch support | 3 days | Low — scope |

---

## Key Metrics to Track

| Metric | Target | Why |
|---|---|---|
| Onboarding completion rate | >70% | Drop-off = lost user |
| Permission grant rate | >90% per permission | Blocking calls need it |
| 7-day retention | >40% | Passive apps bleed fast |
| Scam detection rate | >95% | Core product promise |
| Time to first detection | <7 days | Or user thinks app is broken |
