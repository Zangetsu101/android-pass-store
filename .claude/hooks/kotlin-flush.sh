#!/bin/bash

queue_file="/tmp/claude-kotlin-${CLAUDE_SESSION_ID:-default}.txt"

[ -f "$queue_file" ] || exit 0

mapfile -t files < <(sort -u "$queue_file")
rm -f "$queue_file"

[ ${#files[@]} -eq 0 ] && exit 0

project_dir="${CLAUDE_PROJECT_DIR:-.}"

[ -f "$project_dir/gradlew" ] || exit 0

(cd "$project_dir" && ./gradlew formatKotlin --quiet --console=plain > /dev/null 2>&1)
exit_code=$?

if [ $exit_code -ne 0 ]; then
  echo "formatKotlin failed (exit $exit_code)" >&2
fi

exit 0
