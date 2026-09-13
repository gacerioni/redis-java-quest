#!/usr/bin/env bash
# Publish the course site at https://platformengineer.io/redisjava/ (static files served by the fleet Caddy).
# Builds the site, ships it over SSH as a tarball, swaps it into /opt/redisjava/www/redisjava atomically.
# The Caddy route lives in the platformengineer.io repo (deploy/caddy/Caddyfile, block "platformengineer.io").
#   scripts/deploy_platformengineer.sh
set -euo pipefail
cd "$(dirname "$0")/.."
VM_HOST="${VM_HOST:-35.184.82.232}"
VM_USER="${VM_USER:-gabriel_cerioni_redis_com}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/google_compute_engine}"
SSH="ssh -o ConnectTimeout=20 -i $SSH_KEY $VM_USER@$VM_HOST"
DEST=/opt/redisjava/www

echo "== build"
scripts/site.sh build >/dev/null
test -f course/site/index.html

echo "== upload"
$SSH "sudo rm -rf $DEST/redisjava.new && sudo mkdir -p $DEST/redisjava.new"
COPYFILE_DISABLE=1 tar --no-xattrs -czf - -C course/site . | $SSH "sudo tar -xzf - -C $DEST/redisjava.new"
$SSH "sudo rm -rf $DEST/redisjava.old; [ -d $DEST/redisjava ] && sudo mv $DEST/redisjava $DEST/redisjava.old; sudo mv $DEST/redisjava.new $DEST/redisjava && sudo chown -R root:root /opt/redisjava && sudo chmod -R a+rX /opt/redisjava && sudo rm -rf $DEST/redisjava.old && ls $DEST/redisjava | head -5"

echo "== verify"
for path in "" "fundamentos/01-mapa-do-mundo/" "stylesheets/quest.css" "javascripts/progress.js" "search/search_index.json"; do
  code=$(curl -s -o /dev/null -m 15 -w '%{http_code}' "https://platformengineer.io/redisjava/$path")
  printf '%-45s %s\n' "/redisjava/$path" "$code"
done
