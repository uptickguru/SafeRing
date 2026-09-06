#!/usr/bin/env python3
"""Upload Dreamer APK to Firebase App Distribution (SafeRing CI SA)."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import time
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


def bootstrap_imports() -> None:
    target = Path(tempfile.gettempdir()) / "dreamer-fad-pkgs"
    marker = target / ".ok"
    if not marker.exists():
        target.mkdir(parents=True, exist_ok=True)
        cmd = [
            sys.executable,
            "-m",
            "pip",
            "install",
            "--quiet",
            "--target",
            str(target),
            "google-auth",
            "requests",
        ]
        print("pip", " ".join(cmd))
        subprocess.check_call(cmd)
        marker.write_text("ok")
    sys.path.insert(0, str(target))


def wait_operation(sess, name: str, timeout_s: int = 180) -> dict:
    # name like operations/workflows/...
    url = f"https://firebase.googleapis.com/v1/{name}"
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        r = sess.get(url)
        r.raise_for_status()
        body = r.json()
        if body.get("done"):
            if "error" in body:
                raise SystemExit(f"operation error: {body['error']}")
            return body
        print("waiting operation...")
        time.sleep(3)
    raise SystemExit(f"operation timeout: {name}")


def find_app(sess, project: str):
    url = f"https://firebase.googleapis.com/v1beta1/projects/{project}/androidApps"
    r = sess.get(url)
    r.raise_for_status()
    apps = r.json().get("apps") or []
    return next((a for a in apps if a.get("packageName") == PACKAGE), None)


def main() -> None:
    sa = sa_path()
    data = json.loads(sa.read_text())
    project = data["project_id"]
    print("project=", project)
    print("sa_email=", data.get("client_email"))

    bootstrap_imports()
    from google.auth.transport.requests import AuthorizedSession
    from google.oauth2 import service_account

    scopes = [
        "https://www.googleapis.com/auth/cloud-platform",
        "https://www.googleapis.com/auth/firebase",
    ]
    creds = service_account.Credentials.from_service_account_file(str(sa), scopes=scopes)
    sess = AuthorizedSession(creds)

    app = find_app(sess, project)
    if not app:
        print("Creating Firebase Android app", PACKAGE)
        url = f"https://firebase.googleapis.com/v1beta1/projects/{project}/androidApps"
        cr = sess.post(url, json={"packageName": PACKAGE, "displayName": DISPLAY})
        print("create", cr.status_code, cr.text[:500])
        cr.raise_for_status()
        body = cr.json()
        if "name" in body and "appId" not in body:
            wait_operation(sess, body["name"])
            # list again
            for _ in range(20):
                app = find_app(sess, project)
                if app:
                    break
                time.sleep(2)
        else:
            app = body
    if not app or "appId" not in app:
        raise SystemExit(f"could not resolve Dreamer app: {app}")
    app_id = app["appId"]
    print("appId=", app_id)

    cfg = sess.get(f"https://firebase.googleapis.com/v1beta1/{app['name']}/config")
    if cfg.status_code == 200:
        body = cfg.content
        try:
            j = cfg.json()
            if "configFileContents" in j:
                import base64

                body = base64.b64decode(j["configFileContents"])
        except Exception:
            pass
        out_cfg = Path(tempfile.gettempdir()) / "dreamer-google-services.json"
        out_cfg.write_bytes(body)
        print("config_bytes", out_cfg.stat().st_size)

    apk_url = os.environ.get("DREAMER_APK_URL", DEFAULT_APK_URL)
    apk = Path(tempfile.gettempdir()) / "dreamer-release.apk"
    print("downloading", apk_url)
    urllib.request.urlretrieve(apk_url, apk)
    print("apk_bytes", apk.stat().st_size)

    env = os.environ.copy()
    env["GOOGLE_APPLICATION_CREDENTIALS"] = str(sa)
    print("installing firebase-tools")
    subprocess.check_call(["npm", "install", "-g", "firebase-tools@13"], env=env)
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
    print("distributing to FAD...")
    proc = subprocess.run(cmd, env=env, text=True, capture_output=True)
    sys.stdout.write(proc.stdout or "")
    sys.stderr.write(proc.stderr or "")
    if proc.returncode != 0:
        raise SystemExit(proc.returncode)

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
