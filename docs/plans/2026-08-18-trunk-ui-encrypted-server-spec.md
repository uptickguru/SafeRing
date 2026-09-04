# GMG SafeCall / SafeRing — Trunk Product Spec
**Status:** GO TRUNK (locked)  
**Date:** 2026-08-18  
**Edge:** HITL + SafeCall DID + bank-style IVR — not Phone-mic in-call  

---

## **1. Decisions locked**

| Decision | Choice |
|----------|--------|
| Live audio path | **SafeCall trunk only** (SignalWire) |
| In-call Phone STT | Not product core |
| Onboarding | Link **trusted people ↔ E.164** |
| Server PII | **Encrypted at rest** (trusted contacts, names, codes metadata carefully) |
| Modify trusted / household | **Step-up:** FIDO2 (preferred) or TOTP |
| Caregiver HITL | Approve unknown → issue code |
| Hangup | SignalWire API |

---

## **2. Threat model (short)**

| Asset | Risk if leaked |
|-------|----------------|
| Senior + trusted E.164 | Stalking / social engineering |
| Names, household link | Profile of family graph |
| Codes | Short window reconnect abuse |
| Call events | Sensitive patterns |

| Control | |
|---------|--|
| Encrypt PII fields at rest (envelope encryption) |
| TLS in transit everywhere |
| App stores contacts in Keychain/Keystore + optional server sync ciphertext |
| Step-up auth to **add/change/remove** trusted numbers |
| Codes hashed at rest; plain only once to caregiver UI |
| Minimal logs (no raw numbers in plain logs) |

---

## **3. Encryption model**

### **3.1 Keys**

```
K_master     — cloud KMS / age / libsodium secret (ops; never in git)
K_household  — per-household data key (DEK), wrapped by K_master
K_device     — on-device Keychain key for local HouseholdStore
```

| Field | At rest (server) | Client |
|-------|------------------|--------|
| `senior_e164`, `trusted_e164[]`, display names | AES-GCM ciphertext with `K_household` | Plain in Keychain after unlock |
| `household_id` | Opaque public id (ULID) | Same |
| Incident `suspect_hint` | Encrypted if free text | — |
| Access **code** | **SHA-256(code ‖ household_id ‖ pepper)** only | Plain shown once on approve |
| Call SIDs, statuses | OK plain (provider ids) | — |
| ANI on SafeCall inbound | Store **HMAC/hash** default; full E.164 encrypted if needed for blocklist | — |

### **3.2 APIs never**

- Return full trusted list without session + device binding  
- Log E.164 in plaintext  
- Allow PATCH trusted without step-up token  

### **3.3 Step-up (FIDO2 / TOTP)**

| Action | Required |
|--------|----------|
| Onboard create household + first trusted | App PIN / biometrics local; server session |
| **Add / change / remove trusted number** | **FIDO2** (tier+) or **TOTP** challenge → `step_up_token` (5 min) |
| Approve SafeCall code | Caregiver session (push + app auth); not full FIDO every time |
| Drop live call | Caregiver session |
| Export / delete household | FIDO2 or TOTP |

**Tiering**

| Tier | Modify trusted |
|------|----------------|
| Free / basic | TOTP (authenticator) or SMS OTP to **existing** trusted (weaker) |
| **Higher** | **FIDO2** (platform passkey / security key) required to modify |

Kevin FIDO note: dual enroll Hello + roaming; UV required — align caregiver web/app with that later.

---

## **4. Domain model**

```
Household
  id, created_at
  senior_profile_enc          # name, locale
  settings_enc                # silence_unknown preferred, etc.
  dek_wrapped
  totp_secret_enc? / fido_ creds refs
  tier: basic | plus

TrustedContact[]  (1–3 MVP)
  id, role (primary|backup|caregiver)
  display_name_enc
  e164_enc
  can_approve_codes: bool
  can_drop_call: bool

Device
  device_id, platform, push_token_enc
  household_id, role (senior|caregiver)

Incident
  id, household_id
  status: pending|approved|denied|bridging|live|ended|expired
  suspect_hint_enc?
  code_hash, code_expires_at, code_consumed
  caller_from_enc_or_hmac
  sw_call_sid, sw_conference
  timestamps

SafeCallSession
  incident_id, events[] (no PII payloads)
```

---

## **5. Complete UI map**

### **5.1 Shared visual**
Ivory / gold / soft burgundy HELP / sage Call — existing SafeRing bar.  
**Senior:** huge type, 3 tabs. **Caregiver:** denser but same tokens.

### **5.2 Onboarding (senior device) — 5 steps**

| Step | Screen | Fields / actions |
|------|--------|------------------|
| 0 | Welcome | Tagline · Continue |
| 1 | Your name | Senior name |
| 2 | **Trusted person 1** | Name + phone (E.164) · “This is who we call & who can approve SafeCall” |
| 3 | **Trusted person 2** (optional) | Same · Skip |
| 4 | Family password / local PIN | Gate app; not the SafeCall code |
| 5 | Protections | Toggle guides: Silence Unknown · Notifications · “How SafeCall works” · Finish |

**On finish:** write **Keychain** household; register device; upload **encrypted** senior + trusted blob to server; create household id.

### **5.3 Onboarding (caregiver device / link)**

| Step | |
|------|--|
| Open invite link / scan QR from senior Settings | |
| Authenticate (Apple/Google or magic link) | |
| Confirm household · enable push | |
| Optional: enroll **TOTP or FIDO2** for “change numbers” | |

### **5.4 Senior app — tabs**

| Tab | Content |
|-----|---------|
| **Home** | HELP (max) · Not sure · Call primary trusted · **SafeCall** status card · Message · Code (family password) |
| **History** | After-call check-ins · SafeCall outcomes (no raw scam ANI unless policy) |
| **Settings** | Trusted list (masked) · Start over · SMS filter · Fall tips · “Add caregiver” · Security |

**Home — SafeCall card states**

| State | UI |
|-------|-----|
| Idle | “Unknown callers use SafeCall” · short how-to |
| Code active | **Code ######** · number to dial · expires in mm:ss · “Family approved a callback” |
| Live | **ON SAFECALL** · monitored · End (requests hangup) · HELP |
| Ended | “Call ended” · OK? check-in |

### **5.5 Caregiver app — tabs**

| Tab | Content |
|-----|---------|
| **Home** | Pending **Approve / Deny** cards · Active codes · Live calls **Drop** |
| **People** | Senior · trusted list (masked ·••1234) · **Edit** → step-up |
| **Activity** | Incident timeline |
| **Settings** | Alerts · FIDO2/TOTP enroll · tier |

**Approve sheet**

```
Unknown attempt (time)
[optional hint]
[ Deny ]  [ Approve talk ]
→ shows: Call {SAFECALL_DID}
         Code 482193 #
         Expires 20:00
[ Share with senior ] [ Copy ]
```

**Live sheet**

```
SafeCall live
From: •••• (or city/hash)
Senior + you ringing/bridged
[ Drop call ]  [ Alert another ]
```

### **5.6 Modify trusted (step-up)**

```
People → Edit trusted
→ “Confirm it’s you”
   [ Use passkey / security key ]  or  [ Authenticator code ]
→ step_up_token
→ Add / change / remove number
→ Re-encrypt blob · sync server
```

### **5.7 Settings — Security**

| Row | |
|-----|--|
| App lock (biometrics) | |
| Enroll passkey (FIDO2) | Higher tier |
| Authenticator (TOTP) | Basic+ |
| Devices | |
| Download my data / Delete household | Step-up |

### **5.8 Widgets (keep)**
Deep link to Home SafeCall card / caregiver pending — not Phone mic Protect as primary.

### **5.9 Deprecate primary UX**
Protect Call mic path → Settings “Advanced / experimental” or remove from Home.

---

## **6. Server API (trunk)**

### **6.1 Public / device**

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/v1/households` | device attest | create; body ciphertext blob |
| GET | `/v1/households/{id}` | session | returns ciphertext + meta |
| POST | `/v1/households/{id}/trusted` | session + **step_up** | modify contacts |
| DELETE | `/v1/households/{id}/trusted/{tid}` | session + **step_up** | |
| POST | `/v1/auth/step-up/totp` | session | → step_up_token |
| POST | `/v1/auth/step-up/webauthn/*` | session | FIDO2 |
| POST | `/v1/safecall/incidents` | session | pending unknown |
| GET | `/v1/safecall/incidents` | session | list |
| POST | `/v1/safecall/incidents/{id}/approve` | caregiver session | returns **code once** |
| POST | `/v1/safecall/incidents/{id}/deny` | caregiver | |
| POST | `/v1/safecall/incidents/{id}/hangup` | caregiver/senior | SW hangup |
| GET | `/v1/safecall/events?household_id=` | session | **SSE** realtime |
| GET | `/v1/safecall/status` | open/health | no PII |

### **6.2 SignalWire (Caruso)**

| Method | Path |
|--------|------|
| POST | `/v1/safecall/sw/inbound` |
| POST | `/v1/safecall/sw/gather` |
| POST | `/v1/safecall/sw/status` |

IVR: GMG SafeCall Monitor · consent · code · bridge senior+trusted (decrypt E.164 only in memory for Dial).

### **6.3 Realtime**

| Tech | Role |
|------|------|
| **SSE** | App open: incident/code/live/ended |
| **APNs/FCM** | Background alert |
| Not required MVP | SignalR (.NET); can add Azure SignalR later if multi-service |

Hangup path: UI → REST → SignalWire API (not only socket).

---

## **7. Onboarding ↔ trunk link**

```
Onboard trusted E.164
  → encrypted on device + server
  → caregiver linked
Unknown rings (no free talk)
  → caregiver Approve
  → code
Scammer dials SAFECALL_DID + code
  → server decrypts senior+trusted briefly
  → Dial both
  → monitored; hangup API
```

---

## **8. Screen inventory (build order)**

| # | Screen | Platform | Priority |
|---|--------|----------|----------|
| 1 | Onboarding trusted 1–2 | iOS | P0 |
| 2 | Senior Home SafeCall card | iOS | P0 |
| 3 | Caregiver pending Approve/Deny | iOS | P0 |
| 4 | Code reveal + share | iOS | P0 |
| 5 | Live + Drop | iOS | P0 |
| 6 | People + step-up edit | iOS | P1 |
| 7 | TOTP enroll | iOS | P1 |
| 8 | FIDO2/passkey enroll | iOS | P1 higher tier |
| 9 | Android caregiver parity | Android | P1 |
| 10 | Android senior + screening | Android | P1 |

---

## **9. Implementation phases**

| Phase | Deliverable |
|-------|-------------|
| **T0** | SW DID + Caruso webhook → existing safecall routes |
| **T1** | Encrypt-at-rest household blob + approve code (hash) |
| **T2** | iOS caregiver Approve + senior code card |
| **T3** | Live SSE + hangup button |
| **T4** | Step-up TOTP to edit trusted |
| **T5** | FIDO2 tier + Android |

---

## **10. What “done” means for viable trunk MVP**

- [ ] Onboard links ≥1 trusted number (encrypted server-side)  
- [ ] Caregiver approves → code + DID instructions  
- [ ] SW IVR consent + code → bridges senior + trusted  
- [ ] Drop call works via API  
- [ ] SSE or push updates UI  
- [ ] Edit trusted requires TOTP or FIDO2  
- [ ] No plaintext trusted numbers in DB/logs  

---

## **11. Out of scope MVP**

- Phone-mic Protect as default  
- Auto merge into cell PSTN  
- Full medical fall device  
- SignalR specifically (SSE first)  

---

**End spec.**
