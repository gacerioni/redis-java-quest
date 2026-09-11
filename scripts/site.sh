#!/usr/bin/env bash
# Build or serve the course site with the project venv.
#   scripts/site.sh build      -> course/site (static, ready for any web server)
#   scripts/site.sh serve      -> http://127.0.0.1:8000
set -euo pipefail
cd "$(dirname "$0")/.."
[ -x .venv/bin/mkdocs ] || { python3 -m venv .venv && .venv/bin/pip install -q -r course/requirements.txt; }
case "${1:-build}" in
  build) .venv/bin/mkdocs build -f course/mkdocs.yml --strict ;;
  serve) .venv/bin/mkdocs serve -f course/mkdocs.yml -a 127.0.0.1:8000 ;;
  *) echo "usage: scripts/site.sh build|serve"; exit 2 ;;
esac
