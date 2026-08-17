# SafeRing Message Filter — network assist (exceptional clients)

## When offline is not enough

Offline filter uses:
- allow / block sender lists (App Group)
- keyword / phrase list (App Group)
- link + urgency heuristics

**Network assist** is for **exceptional clients** only (high-risk households, managed installs, enterprise-ish packs):
- brand-new scam language packs weekly
- coordinated campaign fingerprints
- short-code reputation that changes fast

## How Apple network deferral works

1. Offline rules return `.none` (no strong opinion).
2. Extension calls `context.deferQueryRequestToNetwork`.
3. iOS POSTs a **privacy-constrained** payload to  
   `ILMessageFilterExtensionNetworkURL` from the extension Info.plist.
4. Your server returns an action (junk / allow / none / categories).
5. Extension completes with that action.

**Requirements**
- HTTPS endpoint you control
- Fast response (seconds, not long ML jobs)
- No building a general SMS-content warehouse; minimize retention
- Feature flag in App Group: `mf.network_assist = true` only for those clients

## Suggested SafeRing API (design)

```
POST /v1/message-filter
Authorization: Bearer <install_or_tenant_token>
Content-Type: application/json

{
  "sender_fingerprint": "<hmac or digits-only hashed with install key>",
  "body_features": {
    "keyword_hits": ["gift card"],
    "has_url": true,
    "url_hosts": ["bit.ly"],
    "lang": "en"
  },
  "client": { "tier": "exceptional", "app": "1.0.0" }
}
```

**Prefer features over raw body** when possible.  
If raw body is ever sent for exceptional tier: encrypt, short TTL, no permanent store, contract + disclosure.

```
Response 200:
{ "action": "junk" | "allow" | "none" | "promotion" | "transaction", "reason": "campaign_2026_usps" }
```

## Client wiring

| Piece | Value |
|-------|--------|
| Info.plist | `ILMessageFilterExtensionNetworkURL` |
| App Group | `mf.network_assist` bool |
| Main app Settings | Hidden or “Advanced protection” for exceptional SKU |
| Backend | Same risk DB as Android hash check where possible |

## Enablement path (product)

1. Default: **network off**, offline word list on.  
2. Exceptional client SKU / server flag flips `mf.network_assist`.  
3. Ship plist URL in that build flavor (or remote-config is **not** available inside extension Info.plist — URL is build-time; flag is runtime).

So: **one binary can gate network with the flag**, but the URL must be present in the extension plist when you want network at all.

## Do not

- Turn network on for every free user by default  
- Log full SMS bodies server-side indefinitely  
- Use network path as a substitute for on-device keywords (latency + privacy)
