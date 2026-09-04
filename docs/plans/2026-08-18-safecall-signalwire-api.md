# SafeCall + SignalWire — eng notes

## Realtime: SSE (not SignalR binary)

| Choice | Why |
|--------|-----|
| **SSE** `GET /v1/safecall/events?household_id=` | Native browser/iOS `URLSession` streaming; simple Go hub |
| SignalR | Great on **.NET**; not needed for MVP |
| WebSocket | Upgrade later if bi-directional chat required |
| Burst/push | APNs/FCM still for background; SSE when app open |

App opens SSE when caregiver/senior is on SafeCall screens. Hangup is **REST** → SignalWire API.

## SignalWire console setup

1. Create project + buy **one** DID  
2. Voice webhook (when call comes in):  
   `POST https://safering.deathbyathousand.com/v1/safecall/sw/inbound`  
3. Set env on server:  
   `SIGNALWIRE_SPACE_URL` `SIGNALWIRE_PROJECT_ID` `SIGNALWIRE_API_TOKEN`  
   `SAFECALL_DID` `SAFECALL_PUBLIC_BASE`  
4. Redeploy backend  

## API quick test

```bash
# Create incident (family saw unknown)
curl -sS -X POST "$BASE/v1/safecall/incidents" -H 'Content-Type: application/json' \
  -d '{"household_id":"hh_demo","senior_e164":"+17255550100","trusted_e164":"+17255550101","suspect_hint":"claimed IRS"}' | jq .

# Approve → code
curl -sS -X POST "$BASE/v1/safecall/incidents/INC_ID/approve" | jq .

# SSE
curl -N "$BASE/v1/safecall/events?household_id=hh_demo"

# Hangup live call
curl -sS -X POST "$BASE/v1/safecall/incidents/INC_ID/hangup" | jq .
```

## Flow

inbound SW → IVR consent + code → validate → Dial senior+trusted → status webhooks → SSE events → hangup API
