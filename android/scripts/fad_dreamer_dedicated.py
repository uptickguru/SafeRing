#!/usr/bin/env python3
"""Create dedicated Firebase project + Android app for Dreamer, then FAD upload."""
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
# Desired dedicated project id (must be globally unique-ish)
CANDIDATE_PROJECTS = [
    os.environ.get("DREAMER_FIREBASE_PROJECT_ID", "").strip(),
    "gmg-dreamer-android",
    "gmg-dreamer-mobile",
    "kev-dreamer-android",
]
CANDIDATE_PROJECTS = [p for p in CANDIDATE_PROJECTS if p]
GROUP = "beta-testers"
DEFAULT_APK_URL = (
    "https://safering.gulfmeridiangroup.com/downloads/Dreamer-1.0.3-4-release.apk"
)
NOTES = os.environ.get(
    "DREAMER_FIREBASE_RELEASE_NOTES",
    "Dreamer 1.0.3 (4) on dedicated Firebase project.",
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
        subprocess.check_call(
            [
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
        )
        marker.write_text("ok")
    sys.path.insert(0, str(target))


def wait_op(sess, name: str, timeout_s: int = 300) -> dict:
    url = f"https://firebase.googleapis.com/v1/{name}"
    # some ops use cloudresourcemanager
    if name.startswith("operations/") and "workflows" not in name:
        url = f"https://cloudresourcemanager.googleapis.com/v1/{name}"
    if "/operations/" in name and not name.startswith("operations/"):
        pass
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        # try firebase first then CRM
        for base in (
            f"https://firebase.googleapis.com/v1/{name}",
            f"https://cloudresourcemanager.googleapis.com/v1/{name}",
            f"https://firebase.googleapis.com/v1beta1/{name}",
        ):
            r = sess.get(base)
            if r.status_code == 404:
                continue
            if r.status_code >= 400:
                print("op poll", base, r.status_code, r.text[:200])
                continue
            body = r.json()
            if body.get("done"):
                if "error" in body:
                    raise SystemExit(f"op error: {body['error']}")
                return body
            print("waiting", name, "...")
            time.sleep(3)
            break
        else:
            time.sleep(3)
    raise SystemExit(f"timeout {name}")


def main() -> None:
    sa = sa_path()
    data = json.loads(sa.read_text())
    print("sa_project=", data.get("project_id"))
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

    # List accessible Firebase projects
    r = sess.get("https://firebase.googleapis.com/v1beta1/projects")
    print("list projects", r.status_code)
    print(r.text[:1500])
    r.raise_for_status()
    results = r.json().get("results") or r.json().get("projects") or []
    print("accessible_count", len(results))
    for p in results:
        print(" -", p.get("projectId") or p.get("project_id") or p)

    # Prefer existing dedicated if present
    dedicated = None
    for cand in CANDIDATE_PROJECTS + ["gmg-dreamer-android", "gmg-dreamer-mobile"]:
        for p in results:
            pid = p.get("projectId") or p.get("project_id")
            if pid == cand:
                dedicated = pid
                break
        if dedicated:
            break

    if not dedicated:
        # Try create GCP project then add Firebase
        for cand in (CANDIDATE_PROJECTS or ["gmg-dreamer-android"]):
            print("Attempting create project", cand)
            body = {
                "projectId": cand,
                "name": "GMG Dreamer Android",
            }
            cr = sess.post(
                "https://cloudresourcemanager.googleapis.com/v1/projects",
                json=body,
            )
            print("crm create", cr.status_code, cr.text[:500])
            if cr.status_code in (200, 201):
                op = cr.json()
                if op.get("name"):
                    wait_op(sess, op["name"])
                dedicated = cand
                break
            if cr.status_code == 409:
                dedicated = cand
                break
        if not dedicated:
            raise SystemExit(
                "BLOCKED: cannot create dedicated Firebase/GCP project with this SA. "
                "Create project gmg-dreamer-android in console and grant the SA Firebase Admin."
            )

        # Add Firebase to project
        print("Adding Firebase to", dedicated)
        fr = sess.post(
            f"https://firebase.googleapis.com/v1beta1/projects/{dedicated}:addFirebase",
            json={},
        )
        print("addFirebase", fr.status_code, fr.text[:500])
        if fr.status_code not in (200, 201, 409):
            if fr.status_code == 400 and "already" in fr.text.lower():
                pass
            elif "name" in fr.json() if fr.headers.get("content-type","").startswith("application/json") else False:
                wait_op(sess, fr.json()["name"])
            else:
                # try wait if operation
                try:
                    j = fr.json()
                    if j.get("name"):
                        wait_op(sess, j["name"])
                except Exception:
                    if fr.status_code >= 400:
                        raise SystemExit(f"addFirebase failed: {fr.text[:800]}")

    project = dedicated
    print("DEDICATED_PROJECT=", project)

    # Ensure Android app
    url = f"https://firebase.googleapis.com/v1beta1/projects/{project}/androidApps"
    r = sess.get(url)
    print("list apps", r.status_code, r.text[:400])
    apps = []
    if r.status_code == 200:
        apps = r.json().get("apps") or []
    app = next((a for a in apps if a.get("packageName") == PACKAGE), None)
    if not app:
        print("Creating Android app on dedicated project")
        cr = sess.post(url, json={"packageName": PACKAGE, "displayName": DISPLAY})
        print("create app", cr.status_code, cr.text[:500])
        cr.raise_for_status()
        body = cr.json()
        if "appId" not in body and body.get("name"):
            wait_op(sess, body["name"])
            for _ in range(30):
                rr = sess.get(url)
                rr.raise_for_status()
                apps = rr.json().get("apps") or []
                app = next((a for a in apps if a.get("packageName") == PACKAGE), None)
                if app:
                    break
                time.sleep(2)
        else:
            app = body
    if not app or "appId" not in app:
        raise SystemExit(f"no app: {app}")
    app_id = app["appId"]
    print("DEDICATED_APP_ID=", app_id)

    # config
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
        out_cfg = Path(tempfile.gettempdir()) / "dreamer-google-services-dedicated.json"
        out_cfg.write_bytes(body)
        print("config_bytes", out_cfg.stat().st_size)
        import base64 as _b64

        b64 = _b64.b64encode(body).decode("ascii")
        print("CONFIG_B64_START")
        for i in range(0, len(b64), 80):
            print("CONFIG_B64_CHUNK " + b64[i : i + 80])
        print("CONFIG_B64_END")

    # Ensure tester group exists (best-effort via firebase tools later)
    apk_url = os.environ.get("DREAMER_APK_URL", DEFAULT_APK_URL)
    apk = Path(tempfile.gettempdir()) / "dreamer-release.apk"
    print("downloading", apk_url)
    urllib.request.urlretrieve(apk_url, apk)
    print("apk_bytes", apk.stat().st_size)

    env = os.environ.copy()
    env["GOOGLE_APPLICATION_CREDENTIALS"] = str(sa)
    subprocess.check_call(["npm", "install", "-g", "firebase-tools@13"], env=env)

    # Create group if missing
    subprocess.run(
        [
            "firebase",
            "appdistribution:group:create",
            GROUP,
            "--project",
            project,
        ],
        env=env,
        text=True,
        capture_output=True,
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
    print("distributing...")
    proc = subprocess.run(cmd, env=env, text=True, capture_output=True)
    sys.stdout.write(proc.stdout or "")
    sys.stderr.write(proc.stderr or "")
    if proc.returncode != 0:
        # retry without group if group missing
        print("retry without groups...")
        cmd2 = [
            "firebase",
            "appdistribution:distribute",
            str(apk),
            "--app",
            app_id,
            "--release-notes",
            NOTES,
            "--project",
            project,
        ]
        proc = subprocess.run(cmd2, env=env, text=True, capture_output=True)
        sys.stdout.write(proc.stdout or "")
        sys.stderr.write(proc.stderr or "")
        if proc.returncode != 0:
            raise SystemExit(proc.returncode)

    print("DEDICATED_PROJECT=", project)
    print("DEDICATED_APP_ID=", app_id)
    print(
        "DEDICATED_CONSOLE=",
        f"https://console.firebase.google.com/project/{project}/appdistribution/app/android:{PACKAGE}/releases",
    )
    print(
        "DEDICATED_TESTER_APP=",
        f"https://appdistribution.firebase.google.com/testerapps/{app_id}",
    )
    print("DREAMER_DEDICATED_FAD_OK")


if __name__ == "__main__":
    main()
