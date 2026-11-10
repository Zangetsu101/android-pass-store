#!/usr/bin/env bash
# Prints versionName from app/build.gradle.kts.
# Exits with an error if the file has no versionName.
set -euo pipefail

version_name="$(sed -nE 's/^\s*versionName\s*=\s*"([^"]+)".*/\1/p' app/build.gradle.kts)"
if [ -z "$version_name" ]; then
  echo "::error::Cannot read versionName from app/build.gradle.kts." >&2
  exit 1
fi
echo "$version_name"
