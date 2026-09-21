#!/usr/bin/env bash
# Proves every reference solution in solutions/ passes its lesson check.
# Copies the repo to a temp dir, drops the solutions over the stubs, builds offline, then for each lesson
# with a solution: runs the lab (the check needs its state), runs the exercise with both clients, runs the check.
#   REDIS_URL=redis://localhost:6379 scripts/verify_solutions.sh
set -uo pipefail
cd "$(dirname "$0")/.."
export NO_COLOR=1
[ -z "${JAVA_HOME:-}" ] || export PATH="$JAVA_HOME/bin:$PATH"
export REDIS_URL="${REDIS_URL:-redis://localhost:6379}"
export QUEST_PREFIX="${QUEST_PREFIX:-solutions}"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/quest-solutions.XXXXXX") || { echo "mktemp failed"; exit 1; }
trap 'rm -rf "$TMP"' EXIT
rsync -a --exclude target --exclude .git --exclude .venv --exclude .env --exclude course/site --exclude .claude --exclude .dev ./ "$TMP/" || { echo "rsync failed"; exit 1; }
cd "$TMP" || { echo "cannot cd to $TMP"; exit 1; }
[ -f pom.xml ] && [ -d solutions ] || { echo "temp copy incomplete, refusing to continue"; exit 1; }
echo "working copy: $TMP"
for d in solutions/l*/; do
  pkg=$(basename "$d")
  cp "$d"*.java "src/main/java/com/emberrealm/quest/lessons/$pkg/"
done
if [ -x ./mvnw ]; then ./mvnw -q -o -B -DskipTests package; else mvn -q -o -B -DskipTests package; fi || { echo "build with solutions failed"; exit 1; }
fail=0
java -jar target/quest.jar seed >/dev/null 2>&1 || { echo "FAIL seed"; exit 1; }
for d in solutions/l*/; do
  id=$(basename "$d" | sed 's/^l//; s/_/-/')
  java -jar target/quest.jar run "$id" jedis >/dev/null 2>&1 || { echo "FAIL lab $id"; fail=1; continue; }
  if [ "$id" = "101-01" ]; then
    java -jar target/quest.jar solve "$id" mexa --yes >/dev/null 2>&1 || { echo "FAIL manual step $id"; fail=1; continue; }
  fi
  for c in jedis lettuce; do
    java -jar target/quest.jar exercise "$id" "$c" >/dev/null 2>&1 || { echo "FAIL exercise $id $c"; fail=1; }
  done
  if java -jar target/quest.jar check "$id" >/dev/null 2>&1; then echo "ok    $id (solutions pass)"; else echo "FAIL  check $id"; fail=1; fi
done
exit $fail
