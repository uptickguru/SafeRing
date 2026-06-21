#!/bin/bash
cd /home/kevin/.openclaw/workspace/SafeRing/backend
export SERVER_PORT=8080
export LOG_LEVEL=info
export DATABASE_URL=/home/kevin/.openclaw/workspace/SafeRing/backend/safering.db
nohup ./safering-server > /tmp/safering.log 2>&1 &
echo "SafeRing server started (PID: $!)"
