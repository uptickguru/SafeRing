# A5000 + SIG control of SafeRing (2026-08-20)

Master: SIG on sig-core. A5000 hops to Mac for code; TF codesign needs Mac GUI Terminal.

## From sig-core
```bash
/home/kevin/gmi-control/bin/gmi-master status
/home/kevin/safering-build/safering-build-agent.sh status
/home/kevin/safering-build/safering-build-agent.sh tf-list
# tf-trigger NNN opens Terminal on Mac — needs GUI login
```

## From A5000
```bash
ssh -o BatchMode=yes -i ~/.ssh/id_ed25519 kevinasbury@100.77.211.97 "hostname; ls ~/SafeRing/ios/build/tf-upload-*.command | tail"
# Prefer asking SIG to run gmi-master / build-agent (fleet receipts)
```

## TF truth
- Not pure SSH archive (errSecInternalComponent)
- open -a Terminal ~/SafeRing/ios/build/tf-upload-NNN.command
- Done only: UPLOAD SUCCEEDED + Delivery UUID
