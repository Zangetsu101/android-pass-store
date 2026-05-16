#!/bin/bash

input=$(cat)
tool_name=$(echo "$input" | jq -r '.tool_name')

if [ "$tool_name" = "MultiEdit" ]; then
  file_paths=$(echo "$input" | jq -r '.tool_input.edits[].file_path')
else
  file_paths=$(echo "$input" | jq -r '.tool_input.file_path')
fi

queue_file="/tmp/claude-kotlin-${CLAUDE_SESSION_ID:-default}.txt"

while IFS= read -r file_path; do
  [ -z "$file_path" ] || [ "$file_path" = "null" ] && continue
  echo "$file_path" | grep -qE '\.kt$' || continue
  echo "$file_path" >> "$queue_file"
done <<< "$file_paths"

exit 0
