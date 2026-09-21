#!/usr/bin/env bash
# The gauntlet: seed, run every lesson with both clients, check every lesson.
#   REDIS_URL=redis://localhost:6379 QUEST_PREFIX=gauntlet scripts/smoke_all.sh
# Reference solutions run in a temporary copy; student stubs are never overwritten.
# Exit 3 is reported as pending (capability unavailable or an unobserved failover).
set -uo pipefail
cd "$(dirname "$0")/.."
export NO_COLOR=1
export REDIS_URL="${REDIS_URL:-redis://localhost:6379}"
export QUEST_PREFIX="${QUEST_PREFIX:-gauntlet}"
REPO_ROOT="$PWD"
WORK_COPY=$(mktemp -d "${TMPDIR:-/tmp}/quest-smoke.XXXXXX") || exit 1
trap 'rm -rf "$WORK_COPY"' EXIT
rsync -a --exclude target --exclude .git --exclude .venv --exclude .env --exclude course/site --exclude .claude --exclude .dev ./ "$WORK_COPY/" || exit 1
cd "$WORK_COPY" || exit 1
for dir in solutions/l*/; do
  cp "$dir"*.java "src/main/java/com/emberrealm/quest/lessons/$(basename "$dir")/" || exit 1
done
JAR=target/quest.jar
LOG="$REPO_ROOT/target/smoke.log"
mkdir -p target
mkdir -p "$REPO_ROOT/target"
: > "$LOG"

if [ -x ./mvnw ]; then ./mvnw -q -B -DskipTests package; else mvn -q -B -DskipTests package; fi || { echo "build failed"; exit 1; }

pass=0; fail=0; skip=0
step() {
  local label="$1"; shift
  printf '%-32s' "$label"
  echo "===== $label" >> "$LOG"
  local start=$(date +%s)
  java -jar "$JAR" "$@" >> "$LOG" 2>&1
  local code=$? end=$(date +%s)
  case $code in
    0) echo "ok     ($((end-start))s)"; pass=$((pass+1));;
    3) echo "pending (see $LOG)"; skip=$((skip+1));;
    *) echo "FAIL   exit=$code (see $LOG)"; fail=$((fail+1)); tail -n 12 "$LOG" | sed 's/^/      | /';;
  esac
}

echo "Local smoke run; prefix: $QUEST_PREFIX (connection credentials omitted)"
step "reset" reset --yes
step "seed" seed
for id in $(tail -n +2 src/main/resources/lessons.csv | cut -d';' -f1); do
  step "run $id jedis"   run "$id" jedis
  step "run $id lettuce" run "$id" lettuce
  if [ -d "solutions/l${id//-/_}" ]; then
    # 101-01 includes the manual counter change before the coding exercise.
    if [ "$id" = "101-01" ]; then step "manual step $id" solve "$id" mexa --yes; fi
    step "exercise $id both" exercise "$id" both
  fi
  step "check $id"       check "$id"
done
echo
echo "pass=$pass fail=$fail skip=$skip"
[ "$fail" -eq 0 ]
