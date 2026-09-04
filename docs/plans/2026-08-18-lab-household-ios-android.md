# Lab household — locked

| Role | Platform | Number (last4) |
|------|----------|----------------|
| Grandma Senior | iOS | …8889 |
| Caretaker | Android | …7808 |
| DID | SignalWire | …4143 |

## Live Caruso API (not SIG /v1/households)

| Method | Path |
|--------|------|
| POST | `/v1/safecall/onboard` client_number + trusted_contacts[] + udid |
| POST | `/v1/safecall/sw/inbound` |
| POST | `/v1/safecall/sw/code` |
| POST | `/v1/safecall/notify-trusted` |
| POST | `/v1/safecall/companion/message` |
| POST | `/v1/safecall/approve` |

Onboard result: ok=True trusted_count=1 client_hash=408bb3f946368500

## Bridge test
1. Trigger unknown / notify-trusted → caretaker gets 800 + code (SMS)
2. Or Approve via companion
3. Caller dials DID + code
4. Bridge rings grandma …8889 and caretaker …7808

## Android
Install `app-debug.apk`. Set senior/help number to grandma. Full Caretaker Approve UI still iOS-first; live path uses companion/notify/approve on server.
