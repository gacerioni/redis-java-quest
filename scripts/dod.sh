#!/usr/bin/env bash
# Definition of done, in one command:
#   1. text lint (no em dash, no emoji, no tabs)
#   2. unit tests
#   3. the gauntlet against $REDIS_URL (every lesson, both clients, every check)
#   4. strict site build
#   REDIS_URL=redis://localhost:6379 scripts/dod.sh
set -uo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home}"
fail=0
echo "== 1/5 lint";        scripts/lint_text.sh || fail=1
echo "== 2/5 unit tests";  ./mvnw -q -o -B test 2>&1 | grep -E 'Tests run:.*Fail|BUILD|ERROR' | tail -3; [ "${PIPESTATUS[0]}" -eq 0 ] || fail=1
echo "== 3/5 gauntlet";    scripts/smoke_all.sh || fail=1
echo "== 4/5 solutions";  scripts/verify_solutions.sh || fail=1
echo "== 5/5 site";        scripts/site.sh build 2>&1 | grep -E 'WARNING|ERROR|Documentation built' | tail -5; [ "${PIPESTATUS[0]}" -eq 0 ] || fail=1
echo; [ $fail -eq 0 ] && echo "DoD: all green" || echo "DoD: FAILED (see above)"
exit $fail
