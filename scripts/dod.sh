#!/usr/bin/env bash
# Definition of done, in one command:
#   1. text lint (no em dash, no emoji, no tabs)
#   2. unit tests
#   3. launcher regressions
#   4. the gauntlet against $REDIS_URL (every lesson, both clients, every check)
#   5. reference solutions
#   6. strict site build and internal links
#   REDIS_URL=redis://localhost:6379 scripts/dod.sh
set -uo pipefail
cd "$(dirname "$0")/.."
[ -z "${JAVA_HOME:-}" ] || export PATH="$JAVA_HOME/bin:$PATH"
fail=0
echo "== 1/6 lint";        scripts/lint_text.sh || fail=1
echo "== 2/6 unit tests";  ./mvnw -q -o -B test 2>&1 | grep -E 'Tests run:.*Fail|BUILD|ERROR' | tail -3; [ "${PIPESTATUS[0]}" -eq 0 ] || fail=1
echo "== 3/6 launchers";   python3 scripts/test_wrappers.py || fail=1
echo "== 4/6 gauntlet";    scripts/smoke_all.sh || fail=1
echo "== 5/6 solutions";  scripts/verify_solutions.sh || fail=1
echo "== 6/6 site";        scripts/site.sh build 2>&1 | grep -E 'WARNING|ERROR|Documentation built|Site links' | tail -5; [ "${PIPESTATUS[0]}" -eq 0 ] || fail=1
echo; [ $fail -eq 0 ] && echo "DoD: no failures (review any pending environment/observations above)" || echo "DoD: FAILED (see above)"
exit $fail
