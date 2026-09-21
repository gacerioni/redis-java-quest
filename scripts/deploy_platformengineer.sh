#!/usr/bin/env bash
# Publish the course and retain the previous tree for rollback.
# No Redis database is changed. See docs/release.md for the verification/rollback contract.
set -euo pipefail
cd "$(dirname "$0")/.."
VM_HOST="${VM_HOST:-35.184.82.232}"
VM_USER="${VM_USER:-gabriel_cerioni_redis_com}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/google_compute_engine}"
SSH=(ssh -o BatchMode=yes -o ConnectTimeout=20 -i "$SSH_KEY" "$VM_USER@$VM_HOST")
DEST=/opt/redisjava/www
COMMIT_SHA=$(git rev-parse HEAD)
RELEASE_ID="$(date -u +%Y%m%dT%H%M%SZ)-${COMMIT_SHA:0:8}"
STAGING="$DEST/redisjava.new-$RELEASE_ID"
BACKUP="$DEST/redisjava.rollback-$RELEASE_ID"
FAILED="$DEST/redisjava.failed-$RELEASE_ID"

[ -z "$(git status --porcelain)" ] || { echo "Commit the reviewed changes before deploying."; exit 1; }
echo "== build"
scripts/site.sh build
[ -z "$(git status --porcelain)" ] || { echo "The build updated tracked sources. Review and commit them before deploying."; exit 1; }
COMMIT_SHA="$COMMIT_SHA" RELEASE_ID="$RELEASE_ID" python3 - <<'PY'
import json, os
from pathlib import Path
Path("course/site/version.json").write_text(json.dumps({
    "commit": os.environ["COMMIT_SHA"], "release": os.environ["RELEASE_ID"]
}) + "\n", encoding="utf-8")
PY

echo "== upload"
"${SSH[@]}" "sudo mkdir '$STAGING'"
COPYFILE_DISABLE=1 tar -czf - -C course/site . | "${SSH[@]}" "sudo tar -xzf - -C '$STAGING'"
"${SSH[@]}" "sudo chown -R root:root '$STAGING' && sudo chmod -R a+rX '$STAGING' && test -f '$STAGING/index.html' && test -f '$STAGING/version.json'"

# Preserve the old tree. Restore it if activating the new tree fails.
"${SSH[@]}" "sudo mv '$DEST/redisjava' '$BACKUP' && { sudo mv '$STAGING' '$DEST/redisjava' || { sudo mv '$BACKUP' '$DEST/redisjava'; exit 1; }; }"
rollback() {
  echo "Verification failed; restoring $BACKUP"
  "${SSH[@]}" "sudo mv '$DEST/redisjava' '$FAILED' && sudo mv '$BACKUP' '$DEST/redisjava'"
}

echo "== verify"
verified=1
for path in "" "trilha/" "trilha/02-conectar/" "fundamentos/01-mapa-do-mundo/" "301-producao/03-tls/" "stylesheets/quest.css" "javascripts/progress.js" "search/search_index.json"; do
  if curl --fail --silent --show-error --location --max-time 20 --output /dev/null "https://platformengineer.io/redisjava/$path"; then
    printf '%-50s %s\n' "/redisjava/$path" "ok"
  else
    verified=0
  fi
done
if ! curl --fail --silent --show-error --max-time 20 "https://platformengineer.io/redisjava/version.json?release=$RELEASE_ID" | COMMIT_SHA="$COMMIT_SHA" python3 -c 'import json,os,sys; data=json.load(sys.stdin); sys.exit(0 if data.get("commit")==os.environ["COMMIT_SHA"] else 1)'; then
  verified=0
fi
if [ "$verified" -ne 1 ]; then
  rollback
  exit 1
fi
echo "Published commit $COMMIT_SHA"
echo "Previous version retained at $BACKUP"
