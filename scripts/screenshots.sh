#!/usr/bin/env bash
# Capture the course site at two widths with headless Chrome, into docs/screens/.
# Needs the site served (scripts/site.sh serve, or the preview on :8010). CHROME can point at any Chrome binary.
#   BASE=http://localhost:8010 FORCE=1 scripts/screenshots.sh
set -uo pipefail
cd "$(dirname "$0")/.."
BASE="${BASE:-http://localhost:8010}"
OUT="${OUT:-docs/screens}"
CHROME="${CHROME:-}"
if [ -z "$CHROME" ]; then
  for c in "$(ls -d ../chrome-headless-shell/*/chrome-headless-shell-mac-arm64/chrome-headless-shell 2>/dev/null | head -1)" \
           "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" "$(command -v chromium 2>/dev/null)" "$(command -v google-chrome 2>/dev/null)"; do
    [ -n "$c" ] && [ -x "$c" ] && CHROME="$c" && break
  done
fi
[ -x "${CHROME:-}" ] || { echo "no Chrome binary found; set CHROME=/path/to/chrome"; exit 1; }
mkdir -p "$OUT"
PROFILE="${TMPDIR:-/tmp}/quest-shots-profile"; rm -rf "$PROFILE"; mkdir -p "$PROFILE"
shot() { # name width path [height]
  local name="$1" w="$2" path="$3" h="${4:-1200}"
  [ -z "${FORCE:-}" ] && [ -f "$OUT/$name-$w.png" ] && { echo "  keep $OUT/$name-$w.png"; return; }
  "$CHROME" --headless --no-sandbox --disable-gpu --disable-dev-shm-usage --hide-scrollbars --no-first-run --user-data-dir="$PROFILE" \
    --window-size="$w,$h" --virtual-time-budget=8000 --timeout=12000 --screenshot="$OUT/$name-$w.png" "$BASE$path" >"$PROFILE/last.log" 2>&1 \
    && echo "  $OUT/$name-$w.png" || { echo "  FAILED $name-$w"; tail -3 "$PROFILE/last.log"; }
}
for w in 1680 1280; do
  shot landing        "$w" "/" 1500
  shot comece-aqui    "$w" "/comece-aqui/redis-cloud/" 1400
  shot curso-tipos    "$w" "/101-tipos/" 1200
  shot licao-hash     "$w" "/101-tipos/02-hash/" 2600
  shot licao-bloqueio "$w" "/102-eventos/04-conexoes-bloqueantes/" 2600
  shot licao-hybrid   "$w" "/201-busca/03-hybrid/" 2600
  shot quiz-tipos     "$w" "/101-tipos/quiz/" 1600
done
echo "done: $(ls "$OUT" | wc -l | tr -d ' ') files in $OUT"
