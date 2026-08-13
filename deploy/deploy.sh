#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SERVER="${DEPLOY_SERVER:-ubuntu@pyflow.icu}"
REMOTE_DIR="${DEPLOY_REMOTE_DIR:-/home/ubuntu/workflow-agent-chat}"
OLD_REMOTE_DIR="/home/ubuntu/schema-platform/apps/workflow-agent-chat"

echo "[deploy] upload source to ${SERVER}:${REMOTE_DIR}"
ssh "${SERVER}" "mkdir -p '${REMOTE_DIR}'"
rsync -az --delete \
  --exclude '.git/' \
  --exclude '.playwright-cli/' \
  --exclude '.workbuddy/' \
  --exclude 'frontend/node_modules/' \
  --exclude 'frontend/dist/' \
  --exclude 'backend/target/' \
  --exclude '.env' \
  "${ROOT_DIR}/" "${SERVER}:${REMOTE_DIR}/"

echo "[deploy] build and restart isolated compose project"
ssh "${SERVER}" bash -s -- "${REMOTE_DIR}" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
cd "$REMOTE_DIR"

if [ ! -f .env ]; then
  echo "[deploy] missing ${REMOTE_DIR}/.env" >&2
  echo "[deploy] create it from .env.example and set production secrets first" >&2
  exit 1
fi

sudo docker compose --project-name workflow-agent-chat up -d --build --remove-orphans
sudo docker compose --project-name workflow-agent-chat ps
REMOTE

echo "[deploy] configure nginx path"
ssh "${SERVER}" bash -s -- "${REMOTE_DIR}" <<'REMOTE'
set -euo pipefail
REMOTE_DIR="$1"
NGINX_CONF=/etc/nginx/sites-available/schema-platform
if ! sudo grep -qF 'location /workflow-agent-chat/' "$NGINX_CONF"; then
  sudo python3 - "$NGINX_CONF" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
block = r'''
    # ==================== Workflow Agent Chat ====================
    location /workflow-agent-chat/api/ {
        rewrite ^/workflow-agent-chat/(.*) /$1 break;
        proxy_pass http://127.0.0.1:5301;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
    }

    location = /workflow-agent-chat/health {
        proxy_pass http://127.0.0.1:5301/health;
        proxy_set_header Host $host;
    }

    location /workflow-agent-chat/ {
        proxy_pass http://127.0.0.1:5301/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
'''
if not text.rstrip().endswith('}'):
    raise SystemExit('nginx config does not end with a server block')
path.write_text(text.rstrip()[:-1] + block + '}\n')
PY
fi
sudo nginx -t
sudo systemctl reload nginx
REMOTE

ssh "${SERVER}" "if [ -d '${OLD_REMOTE_DIR}' ]; then sudo rm -rf '${OLD_REMOTE_DIR}'; fi"

echo "[deploy] health check"
ssh "${SERVER}" "curl --fail --silent --show-error https://pyflow.icu/workflow-agent-chat/health >/dev/null"
echo "[deploy] done: https://pyflow.icu/workflow-agent-chat/"
