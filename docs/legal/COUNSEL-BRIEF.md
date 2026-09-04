# Counsel brief — GMG Shield (facts package)

**For:** Attorney reviewing consumer app Privacy + Terms  
**Client:** Kevin Asbury (publisher under personal name) · DBA Gulf Meridian Group / GMG Shield  
**Date:** 2026-09-04  
**Not legal advice** — engineering/product facts only.

## Product one-liner

Free family tripwire for seniors: giant HELP to one trusted contact, OS spam filters where available, optional Protect Call (user-started room mic + spoken notice on Speaker), caretaker HITL for SafeCall-style gates. Not a mid-ring RoboKiller clone. Not a call recorder archive product.

## Publisher

- App Store / Play: under Kevin’s developer identity  
- Support: support@gulfmeridiangroup.com (confirm)  
- Public legal: https://safering.gulfmeridiangroup.com/legal/

## HITL (non-negotiable product rule)

| Decision | Who |
|----------|-----|
| HELP / Call trusted person | User |
| SafeCall Approve / Deny / Drop | Caretaker human |
| Money / trust / hang up | Human forever |
| Filters / keyword cues | Software assist only |

No auto-approve stranger onto senior without human where HITL UI exists.

## Protect Call technical facts

- User initiates  
- Room microphone + speech recognition (prefer on-device)  
- Optional TTS warning when Speaker confirmed  
- Sequential notice-then-listen (not silent simultaneous spy ideal)  
- No product feature to retain full call WAV as archive  
- Cannot force iOS Phone Speaker API; coach + confirm  
- Not a private cellular media tap  

## Data inventory (implementations may vary by build)

**On device:** owner display name, trusted contact name/E.164, family password in protected storage, filter keywords, Senior/Caretaker mode, optional 6-digit Senior lock, toggles, local history where present.

**Optional server (safering.gulfmeridiangroup.com):** SafeCall onboard/approve/hangup, optional unwanted-report / device-comms style events, infrastructure logs. Prefer hashed/minimized IDs for lab onboard patterns.

**Not sold:** no data-broker sale model.

## Third parties

Apple, Google, device OS speech engines, optional SignalWire (SafeCall DID +18336524143), hosting/Caddy edge.

## Marketing claims — refuse list

- 100% scam prevention  
- Secret recording of all calls for court  
- “Legal in all 50 states guaranteed” (product is notice-first; counsel owns legal)  
- AI replaces family judgment  
- Emergency services substitute  

## Free tier

Protect + HELP + filters + **one** trusted contact. Family/multi-contact / full trunk may be paid later.

## Ask counsel for

1. Final Privacy + Terms (liability cap, governing law Florida draft on site, arbitration Y/N)  
2. Recording/notice wording safe for multi-state users  
3. Whether support page needs physical address  
4. App Store nutrition / Play data safety alignment  
5. Personal-name publisher vs entity for future insurance  

## Contact for technical questions

Engineering via Kevin / SIG control plane; live status endpoint: `GET https://safering.gulfmeridiangroup.com/v1/safecall/status`
