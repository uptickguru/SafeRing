# GMG SafeCall / SafeRing — Comprehensive Plan
**Date:** 2026-08-18  
**Status:** ADOPTED DIRECTION (product)  
**Edge:** HITL family gate + monitored SIP path (scammer hangs up first)  
**Brand line (IVR):** GMG SafeCall Monitor  

---

## **1. Product thesis**

| Principle | Meaning |
|-----------|---------|
| **Scammer quits first** | Loud consent + monitor announce; no silent cell tap as primary |
| **HITL > fake AI** | Family/Caruso confirms unknowns; AI assists, does not sole-gate |
| **Own the media path** | Live STT/monitor/hangup only on **our SignalWire SIP** |
| **Known vs unknown** | Contacts pass; unknowns do not get a free live pitch on cell |
| **PII** | Prefer ephemeral STT; recording only with **explicit IVR consent** + retention policy |
| **Cheap + grant-ready** | One primary DID + codes; optional number pool; senior Android tier |

**One line:** Unknowns must call **SafeCall**, enter a **code** after family OK, hear **bank-style monitor notice**, then we bridge **senior + trusted person** and can drop the call by API.

---

## **2. SignalWire: one number vs pool**

### **2.1 Multiple calls to the same number?**

| Question | Answer |
|----------|--------|
| Can one DID take many concurrent calls? | **Yes** — normal CPaaS; each inbound = new call SID / concurrent sessions |
| Limit | Account concurrent-call caps + cost; not “one call locks the number” |
| IVR per call | Each caller gets own gather/code session |

**Default: one primary SafeCall number (or vanity/800 later)** + **per-incident codes**.  
Simpler ops, cheaper, easier senior instructions (“always call this number”).

### **2.2 When to use a pool**

| Use pool when | Why |
|---------------|-----|
| Regional / campaign DIDs | Local presence |
| Abuse / spam to main DID | Rotate public target |
| High volume multi-tenant | Isolate noise |
| Grant pilots per county | Reporting by line |

Pool still uses **same IVR + code** backend. Codes map to `incident_id`, not to “which DID forever.”

### **2.3 Recommended**

| Phase | Numbers |
|-------|---------|
| **MVP** | **1** SignalWire number (US) |
| **Scale** | Optional pool + same app flow |
| **Scammer-facing** | Always same IVR script |

---

## **3. Call flow (canonical)**

```
A. Unknown rings senior cell
     → iOS: Silence Unknown / no answer preference
     → Android: CallScreening reject/silence unknown
     → Family alert (push/SMS): "Unknown tried {name}"

B. Family HITL (app or Caruso console)
     → DENY → log, block list optional
     → ALLOW TALK → create incident + 6-digit code (TTL 15–30 min)
     → SMS to senior (and optional template for callback story)

C. Scammer (or "legit" party) dials SafeCall DID
     → Answer
     → IVR announce (see §4)
     → Gather code
     → Validate code + not expired + not consumed
     → Optional: second confirm

D. Bridge
     → Dial senior (PSTN or CallKit if online)
     → Dial trusted person (simultaneous or sequential)
     → Conference
     → Optional live STT / keyword / HITL drop
     → Hangup API on risk or family "Drop"

E. After
     → Incident closed
     → Optional short retention if consent-to-record
     → Default: destroy media; keep events only
```

**Senior never has to merge mid-scam call.** Edge case B (merge) stays power-user later.

---

## **4. IVR script (bank-style edge)**

**On answer (TTS or recorded, calm, clear):**

> “You have reached the **GMG SafeCall Monitor** system.  
> This call may be **monitored and recorded** for safety and training.  
> Enter your **access code**, then press pound.  
> By entering the code, you **agree** that this call is monitored and may be recorded.”

Then: `gather` DTMF code.

| Fail | Behavior |
|------|----------|
| Bad code ×3 | “Invalid. Goodbye.” + hangup + flag ANI |
| Expired code | “Code expired. Ask the family for a new code.” |
| Success | “Connecting. This call remains monitored.” → bridge |

**Legal:** Counsel on two-party states; IVR consent is the product edge and the compliance story.  
**Product:** Recording flag **on** only after successful code + consent line; else listen/STT policy can be separate (prefer STT ephemeral without long archive unless grant/QA needs record).

---

## **5. Real-time monitor + hangup (our SIP only)**

| Capability | How |
|------------|-----|
| Live audio | Conference media on SignalWire |
| STT | Media stream → worker; keywords; no archive default |
| UI socket | WSS: `call_id`, state, risk, keyword |
| Hangup API | SW: end participant / end conference / complete call SID |
| Family drop | Console/app → API → hangup |
| Announce already played | Scammer heard SafeCall — many hang before bridge |

---

## **6. iOS comprehensive plan**

### **6.1 App modes**

| Mode | Who |
|------|-----|
| **Senior** | Large UI, HELP, status, codes display |
| **Caregiver** | Approve/deny unknown, create code, live drop, history |
| **Dual** | Same install, role switch (PIN) |

### **6.2 iOS capabilities (use what Apple allows)**

| Feature | Implementation |
|---------|----------------|
| Trusted person / HELP | `tel:` + SMS — **keep** |
| Message Filter Junk | Extension — **keep** |
| Call Directory labels | Optional blocklist — **keep** |
| Widgets / Shortcuts Protect | Deep link to **SafeCall status**, not fake Phone mic |
| Silence Unknown | Guide in onboarding (system setting) |
| Call state | `CXCallObserver` for after-call / “call ended” only |
| Push | Unknown attempt, code created, live SafeCall, drop |
| CallKit | **Inbound SafeCall leg** to senior if we ring VoIP; else PSTN dial-out from SW |
| Socket | WSS while Caregiver/Senior app foreground or via push + fetch |
| Fall / Watch | §9 — Watch Fall Detection **guide** + optional companion later |

### **6.3 iOS screens (MVP)**

| Screen | Purpose |
|--------|---------|
| Home | HELP · Call person · **SafeCall status** · Tips |
| Caregiver Home | Pending unknowns · Approve/Deny · Active codes · Live calls |
| Incident detail | Who/when · Approve → show code · Share code SMS |
| Live SafeCall | Monitored · risk · **Drop call** · Leave |
| After-call | Check-in (existing) |
| Settings | Trusted contacts (1–3) · Notifications · SMS filter · Fall tips |
| Onboarding | Contacts pass · Silence Unknown · Family link · Mic only if needed later |

### **6.4 iOS does *not* depend on**

- Phone mic mid-cell STT as primary  
- XCUITest / tap injection  
- Live caller ID ANI on screened cell calls  

### **6.5 iOS MVP build phases**

| Phase | Deliverable |
|-------|-------------|
| **i0** | Backend: incidents, codes, SW inbound IVR stub, hangup API |
| **i1** | Caregiver approve/deny + code display + push |
| **i2** | SignalWire IVR + conference bridge senior+family |
| **i3** | App WSS live UI + Drop call |
| **i4** | Optional STT keywords → risk UI + auto-suggest drop |
| **i5** | Polish widgets as SafeCall shortcuts; Message Filter stay |

### **6.6 iOS data (PII)**

| Store | Don’t store (default) |
|-------|------------------------|
| Incident id, timestamps, decision | Full call recording unless consent+policy |
| Code hash + TTL | Raw audio forever |
| ANI on SafeCall inbound | Cell “mystery” ANI we never had |
| Risk events / keyword categories | Open transcript vault |
| Household E.164 (on-device + server minimal) | Extra PII |

---

## **7. Android plan (cheaper senior phones)**

### **7.1 Advantages**

| Advantage | Use |
|-----------|-----|
| `CallScreeningService` | Often **see unknown From** → better family alert |
| Cheaper devices | Primary senior SKU |
| FAD / Play later | Sideload careful with Advanced Protection |
| Foreground services | Ongoing SafeCall status |
| BT classic/BLE | Cheaper fobs (§9) |

### **7.2 Android MVP**

| Feature | Notes |
|---------|-------|
| Same caregiver/senior modes | Shared backend |
| Call screening | Silence/reject unknown + notify with **number when available** |
| Optional NLS | SMS junk-ish (existing direction) |
| Full-screen intent | Incoming SafeCall / family alert |
| WSS + FCM | Same events as iOS |
| Telecom ConnectionService | Later if we become dialer-like — **not** MVP |

### **7.3 Android phases**

| Phase | Deliverable |
|-------|-------------|
| **a0** | Shared API with iOS |
| **a1** | Screening + unknown alert **with number** |
| **a2** | Caregiver parity |
| **a3** | Live SafeCall UI + drop |
| **a4** | BLE fob HELP button |

### **7.4 Android risks**

- OEM call-screening quirks  
- Notification permission / restricted settings (document senior setup)  
- Don’t require default dialer for MVP  

---

## **8. Backend / SignalWire architecture**

```
[SignalWire DID]
    → LaML / SWML: answer, play consent, gather code
    → Webhook: POST code → API validate
    → on OK: dial senior + caregiver into conference
    → optional: media stream → STT worker
    → control API: hangup, mute, play more prompts

[API]
    households, members, devices
    incidents, codes (TTL, single-use)
    call_sessions (sw call SIDs)
    events (for socket + audit)
    HITL actions (approve, deny, drop)

[Realtime]
    WSS gateway (or ably/pusher if faster MVP)
    Push: APNs + FCM

[Workers]
    STT optional
    Code expiry sweeper
    Grant metrics export
```

### **8.1 Cost control**

| Lever | Choice |
|-------|--------|
| Numbers | 1 DID MVP |
| STT | On only when bridged + flag |
| Record | Off default; on if consent path + short TTL |
| HITL | Family first; Caruso overflow queue |
| Android seniors | Lower device cost |

---

## **9. Fall alert & senior assist (cheap)**

### **9.1 Honest iPhone path (now)**

| Do | Don’t |
|----|-------|
| Guide: **Apple Watch Fall Detection** + SOS + Medical ID | Fake “phone in pocket fall AI” as medical device |
| In-app **HELP** → trusted person | Claim hospital-grade monitoring without certs |
| After fall setup checklist | Replace Watch with vibes |

### **9.2 Watch**

| Option | Role |
|--------|------|
| **Apple Watch** | Best fall detection; SafeRing companion later (alerts, HELP complication) |
| **Wear OS** | Android seniors; similar companion |
| MVP | **Deep links + education**, not custom fall ML |

### **9.3 Neck fob / BLE (cheap tier)**

| Design | |
|--------|--|
| Hardware | BLE button fob (white-label) or nRF52-class |
| Bond | Pair to senior phone in SafeRing |
| Press | HELP → SMS/call trusted + push family |
| Long press | Optional second action (SafeCall status) |
| Fall | Cheap fobs usually **button-only**; true fall = Watch or dedicated medical pendant (Life Alert class) — partner, don’t fake |
| Cost target | Commodity BLE button ≪ medical pendant |

### **9.4 Phased assist**

| Phase | Ship |
|-------|------|
| S0 | Senior safety tips (Watch Fall + Medical ID) — **exists direction** |
| S1 | HELP reliability + multi-caregiver |
| S2 | BLE fob HELP |
| S3 | Watch complication / wear OS tile |
| S4 | Partner medical pendant API if grant requires |

---

## **10. Grants / GTM angle (notes only)**

| Angle | Narrative |
|-------|-----------|
| Elder fraud prevention | HITL + monitored reconnect, not spyware |
| Consent IVR | Bank-style transparency |
| Rural / low-cost Android | Cheaper handsets + one DID |
| Caregiver workforce | Family + optional Caruso-style operators |
| Metrics | Unknowns blocked, codes issued, scammer abandon before bridge, drops, HELP |

No claim of clinical fall device without proper pathway.

---

## **11. What we stop prioritizing**

| Deprioritize | Why |
|--------------|-----|
| Phone-mic live STT primary | Session busy / no edge |
| XCUITest / tap merge automation | Not shippable |
| Notes call-record scrape | Not real-time |
| Silent always-on cell tap | Impossible + wrong brand |

**Keep:** HELP, Message Filter, widgets as SafeCall entry, after-call, labels, family tripwire.

---

## **12. Success metrics (MVP)**

| Metric | Target spirit |
|--------|----------------|
| Unknown free conversations | Near zero when settings on |
| Scammer abandon on IVR | High (code/consent) |
| Time family → code | &lt; 2 min |
| Bridge join success | Measurable |
| False family denies | Track |
| HELP delivery | &gt; 99% attempt |
| Cost per protected household / mo | Low enough for grant + consumer tier |

---

## **13. Immediate next steps**

| # | Action | Owner |
|---|--------|--------|
| 1 | SignalWire project + **1 DID** + IVR consent/gather webhook | Eng |
| 2 | API: household, incident, code TTL, validate | Eng |
| 3 | Caregiver iOS: approve → code | Eng |
| 4 | Conference bridge senior + trusted | Eng |
| 5 | Hangup API + basic live UI | Eng |
| 6 | Android screening + same API | Eng |
| 7 | Fall tips + BLE fob research spike | Product |
| 8 | Counsel pass on IVR record wording | Legal |
| 9 | Caruso HITL runbook (SLA, scripts) | Ops |

---

## **14. Decision lock**

| Decision | Choice |
|----------|--------|
| Telephony | **SignalWire** primary for SafeCall |
| Numbers | **Single DID MVP**; pool later |
| Live audio | **Only on SafeCall SIP path** |
| Gate | **Family HITL + code** |
| IVR | **GMG SafeCall Monitor** + consent + code |
| Hangup | **Provider API** |
| iOS ANI on cell screen | **Not required** |
| Android ANI | **Use when screening gives it** |
| Fall | Watch guide + HELP; BLE fob later |
| Record | Consent-gated; default minimal retention |

---

**End of plan.**
