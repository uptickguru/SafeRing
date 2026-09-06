# Dreamer dedicated Firebase project

## Blocked automation (2026-09-06)

Service account `firebase-adminsdk-…@gmg-safering-android` **cannot create GCP projects**:

> Service accounts cannot create projects without a parent.

## What Kevin does once (Firebase Console / Google Cloud)

1. Create GCP/Firebase project **`gmg-dreamer-android`** (or preferred id) under org **db1k.online** (or personal).
2. Enable **Firebase App Distribution** on that project.
3. Add Android app package **`com.kevinasbury.dreamer`**.
4. Create tester group **`beta-testers`** (or reuse invite emails).
5. Grant the existing SA **Firebase Admin** + App Distribution Admin on the new project  
   *or* mint a new SA key and set GH secret `DREAMER_FIREBASE_SERVICE_ACCOUNT` (base64).
6. Tell SIG the **project id** + whether to use new SA secret.

## After that

Set in CI / gradle hook:

- `DREAMER_FIREBASE_PROJECT_ID=gmg-dreamer-android`
- run `android/scripts/fad_dreamer_dedicated.py`

Or local:

```bash
export FIREBASE_SERVICE_ACCOUNT=/path/to/sa.json
export DREAMER_FIREBASE_PROJECT_ID=gmg-dreamer-android
export DREAMER_APK_URL=https://safering.gulfmeridiangroup.com/downloads/Dreamer-1.0.3-4-release.apk
python3 android/scripts/fad_dreamer_dedicated.py
```

## Current live (shared until dedicated ready)

- Project: `gmg-safering-android`
- App: `1:424555525887:android:04d00bc406128922267eca`
- Release: `1a1drqse860vg`
