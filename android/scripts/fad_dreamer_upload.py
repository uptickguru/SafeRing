#!/usr/bin/env python3
"""Upload Dreamer APK to Firebase App Distribution (same SA as SafeRing CI)."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

PACKAGE = "com.kevinasbury.dreamer"
DISPLAY = "Dreamer"
GROUP = "beta-testers"
DEFAULT_APK_URL = (
    "https://safering.gulfmeridiangroup.com/downloads/Dreamer-1.0.3-4-release.apk"
)
NOTES = os.environ.get(
    "DREAMER_FIREBASE_RELEASE_NOTES",
    "Dreamer 1.0.3 (4): Aim confirm+history, Goals P1-P5 + 15-min 10X board, "
    "selectable chimes, section help.",
)


def sa_path() -> Path:
    p = os.environ.get("FIREBASE_SERVICE_ACCOUNT") or os.environ.get(
        "GOOGLE_APPLICATION_CREDENTIALS"
    )
    if not p or not Path(p).is_file():
        raise SystemExit("FIREBASE_SERVICE_ACCOUNT file missing")
    return Path(p)


def main() -> None:
    sa = sa_path()
    data = json.loads(sa.read_text())
    project = data["project_id"]
    print("project=", project)
    print("sa_email=", data.get("client_email"))

    # deps
    subprocess.check_call(
        [sys.executable, "-m", "pip", "install", "--quiet", "google-auth", "requests"]
    )
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    scopes = [
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/firebase",
    ]
    creds = service_account.Credentials.from_service_account_file(str(sa), scopes=scopes)
    sess = AuthorizedSession(creds)

    url = f"https://firebase.googleapis.com/v1beta1/projects/{project}/androidApps"
    r = sess.get(url)
    r.raise_for_status()
    apps = r.json().get("apps") or []
    app = next((a for a in apps if a.get("packageName") == PACKAGE), None)
    if not app:
        print("Creating Firebase Android app", PACKAGE)
        cr = sess.post(url, json={"packageName": PACKAGE, "displayName": DISPLAY})
        print("create", cr.status_code, cr.text[:400])
        cr.raise_for_status()
        app = cr.json()
    app_id = app["appId"]
    print("appId=", app_id)

    # config json for monorepo wiring
    cfg = sess.get(f"https://firebase.googleapis.com/v1beta1/{app['name']}/config")
    out_cfg = Path(tempfile.gettempdir()) / "dreamer-google-services.json"
    if cfg.status_code == 200:
        # API returns binary config file content in JSON field sometimes
        body = cfg.content
        try:
            j = cfg.json()
            if "configFileContents" in j:
                import base64

                body = base64.b64decode(j["configFileContents"])
        except Exception:
            pass
        out_cfg.write_bytes(body)
        print("config_bytes", out_cfg.stat().st_size, "path", out_cfg)

    apk_url = os.environ.get("DREAMER_APK_URL", DEFAULT_APK_URL)
    apk = Path(tempfile.gettempdir()) / "dreamer-release.apk"
    print("downloading", apk_url)
    urllib.request.urlretrieve(apk_url, apk)
    print("apk_bytes", apk.stat().st_size)

    # firebase-tools distribute
    env = os.environ.copy()
    env["GOOGLE_APPLICATION_CREDENTIALS"] = str(sa)
    subprocess.check_call(
        ["npm", "install", "-g", "firebase-tools@13"],
        env=env,
    )
    cmd = [
        "firebase",
        "appdistribution:distribute",
        str(apk),
        "--app",
        app_id,
        "--groups",
        GROUP,
        "--release-notes",
        NOTES,
        "--project",
        project,
    ]
    print("running", " ".join(cmd))
    proc = subprocess.run(cmd, env=env, text=True, capture_output=True)
    sys.stdout.write(proc.stdout or "")
    sys.stderr.write(proc.stderr or "")
    if proc.returncode != 0:
        raise SystemExit(proc.returncode)

    # friendly links
    print("DREAMER_FAD_APP_ID=", app_id)
    print(
        "DREAMER_FAD_CONSOLE=",
        f"https://console.firebase.google.com/project/{project}/appdistribution/app/android:{PACKAGE}/releases",
    )
    print(
        "DREAMER_FAD_TESTER_APP=",
        f"https://appdistribution.firebase.google.com/testerapps/{app_id}",
    )
    print("DREAMER_FAD_OK")


if __name__ == "__main__":
    main()
