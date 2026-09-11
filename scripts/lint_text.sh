#!/usr/bin/env bash
# Style gate: no em/en dashes, no emoji, no tabs in lesson sources and pages. Portable (perl, not GNU grep).
cd "$(dirname "$0")/.."
files=$(find course/docs src/main/java src/main/resources/lessons.csv README.md -type f \( -name '*.md' -o -name '*.java' -o -name '*.csv' \))
status=0
dashes=$(perl -CSD -ne 'print "$ARGV:$.: $_" if /\x{2014}|\x{2013}/' $files)
[ -n "$dashes" ] && { echo "$dashes"; echo "^ em/en dash found"; status=1; }
emoji=$(perl -CSD -ne 'print "$ARGV:$.: $_" if /[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}\x{2B50}\x{2705}\x{274C}]/' $files)
[ -n "$emoji" ] && { echo "$emoji"; echo "^ emoji found"; status=1; }
tabs=$(perl -ne 'print "$ARGV:$.: $_" if /\t/' $(echo "$files" | grep -E '\.(md|java)$'))
[ -n "$tabs" ] && { echo "$tabs"; echo "^ tab found"; status=1; }
[ $status -eq 0 ] && echo "lint ok: no dashes, no emoji, no tabs in $(echo "$files" | wc -l | tr -d ' ') files"
exit $status
