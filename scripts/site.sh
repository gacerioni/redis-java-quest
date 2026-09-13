#!/usr/bin/env bash
# Build or serve the course site with the project venv.
#   scripts/site.sh build      -> course/site (static, ready for any web server)
#   scripts/site.sh serve      -> http://127.0.0.1:8000
set -euo pipefail
cd "$(dirname "$0")/.."
[ -x .venv/bin/mkdocs ] || { python3 -m venv .venv && .venv/bin/pip install -q -r course/requirements.txt; }
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/openjdk-21.0.2/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"
if [ ! -f target/quest.jar ]; then ./mvnw -q -o -B -DskipTests package || ./mvnw -q -B -DskipTests package; fi
NO_COLOR=1 java -jar target/quest.jar steps --js 2>/dev/null | grep -v JAVA_TOOL_OPTIONS > course/docs/javascripts/steps.js
case "${1:-build}" in
  build) .venv/bin/mkdocs build -f course/mkdocs.yml --strict ;;
  serve) .venv/bin/mkdocs serve -f course/mkdocs.yml -a 127.0.0.1:8000 ;;
  *) echo "usage: scripts/site.sh build|serve"; exit 2 ;;
esac
