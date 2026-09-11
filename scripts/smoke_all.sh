#!/usr/bin/env bash
# The gauntlet: seed, run every lesson with both clients, check every lesson.
#   REDIS_URL=redis://localhost:6379 QUEST_PREFIX=gauntlet scripts/smoke_all.sh
# Exit code 0 only when everything that has code passes. Lessons without code yet are reported as skipped.
set -uo pipefail
cd "$(dirname "$0")/.."
export NO_COLOR=1
export REDIS_URL="${REDIS_URL:-redis://localhost:6379}"
export QUEST_PREFIX="${QUEST_PREFIX:-gauntlet}"
JAR=target/quest.jar
LOG=target/smoke.log
mkdir -p target
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
    3) echo "skip   (em breve)"; skip=$((skip+1));;
    *) echo "FAIL   exit=$code (see $LOG)"; fail=$((fail+1)); tail -n 12 "$LOG" | sed 's/^/      | /';;
  esac
}

echo "Redis: $REDIS_URL   prefix: $QUEST_PREFIX"
step "reset" reset --yes
step "seed" seed
for id in $(tail -n +2 src/main/resources/lessons.csv | cut -d';' -f1); do
  step "run $id jedis"   run "$id" jedis
  step "run $id lettuce" run "$id" lettuce
  step "check $id"       check "$id"
done
echo
echo "pass=$pass fail=$fail skip=$skip"
[ "$fail" -eq 0 ]
