# GMI Job Contract v0

**Master:** SIG (Hermes default @ sig-core)  
**Human gate:** Kevin  
**Rule:** Documented only — no hidden workers. External actions (TF upload, social post, money) require Kevin approval.

## Schema (JSON)

```json
{
  "id": "sr-tf-144",
  "owner": "sig",
  "worker": "macbook-build",
  "goal": "TestFlight SafeRing 1.0.0/144",
  "action": "safering-tf-trigger",
  "args": {"build": 144},
  "cwd": "/Users/kevinasbury/SafeRing",
  "approval": "required",
  "created_at": "2026-08-20T17:48:00Z",
  "receipt_path": null
}
```

| Field | Required | Notes |
|-------|----------|--------|
| `id` | yes | unique slug |
| `owner` | yes | always `sig` for fleet jobs |
| `worker` | yes | see workers table |
| `goal` | yes | human one-liner |
| `action` | yes | menu verb in `gmi-master` |
| `args` | no | object |
| `approval` | yes | `none` \| `required` \| `approved` |
| `receipt_path` | after run | log or OUT json |

## Workers

| worker id | Host | Mechanism |
|-----------|------|-----------|
| `sig-local` | sig-core | local scripts |
| `macbook-build` | Mac `100.77.211.97` | SSH + scripts / GUI Terminal TF |
| `a5000-mascteab` | A5000 | Mascteab bridge / console |
| `ghostbane` | sig-core | Temporal / poller |
| `hermes-peer:<name>` | remote gateway | `hermes peer dm` (when API server wired) |
| `kanban:<profile>` | profile worker | `hermes kanban` |

## Drop paths (sig-core)

| Path | Role |
|------|------|
| `/home/kevin/gmi-control/jobs/IN/*.json` | pending (human or SIG write) |
| `/home/kevin/gmi-control/jobs/OUT/*.json` | receipts |
| `/home/kevin/gmi-control/jobs/archive/` | done |

No auto-poller unless Kevin enables a documented cron.

## CLI

```bash
/home/kevin/gmi-control/bin/gmi-master help
/home/kevin/gmi-control/bin/gmi-master status
/home/kevin/gmi-control/bin/gmi-master registry
```
