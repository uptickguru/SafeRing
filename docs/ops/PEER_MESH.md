# Hermes Peer Mesh — readiness (v0)

## Goal

Cross-machine `hermes peer dm <peer> "…"` so SIG can message other Hermes gateways like local profiles.

## Requirements (upstream)

1. Peer runs **api_server** platform (`API_SERVER_ENABLED` + `API_SERVER_KEY` + port).
2. Local: `hermes peer add <name> --url http://host:port --key <API_SERVER_KEY>`
3. Key stored in env/credential store — **never Telegram**.

## Live scan 2026-08-20

| Item | Status |
|------|--------|
| `hermes peer list` | **No peers registered** |
| Default `hermes serve` / dashboard | **No processes** |
| API_SERVER in default `.env` | **Not configured** (or empty) |
| Listening 8377 (typical peer API) | **No** |
| OpenClaw `:18789` | Live (Claw hub — **not** Hermes peer API) |
| Clyde gateway | **Running** (Telegram) — no peer API found |
| A5000 Hermes gateway | **Running** — peer API not probed as enabled |

## Blocked on Kevin go

| Step | Why |
|------|-----|
| Generate `API_SERVER_KEY` | Secret; not invent in chat |
| Enable api_server on default and/or clyde | New network surface — explicit approve |
| `hermes peer add` for each | Needs URL + key |
| Optional Tailscale-only bind | Prefer TS IP, not public internet |

## Recommended layout (when approved)

| Peer name | URL (example) | Notes |
|-----------|---------------|--------|
| `sig` | `http://100.109.111.66:<port>` | default profile API |
| `clyde` | profile isolated serve if used | optional |
| `a5000` | `http://100.78.140.28:<port>` | if Hermes API on A5000 |

## Commands (after keys exist)

```bash
hermes peer add clyde --url http://127.0.0.1:PORT --key "$KEY"
hermes peer list
hermes peer dm clyde "health check from SIG"
```

## Until then

Use: SSH + kanban + Ghostbane + Mascteab bridge + `gmi-master` job menu.
