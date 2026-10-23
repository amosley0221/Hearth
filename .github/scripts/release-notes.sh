#!/usr/bin/env bash
# Prints the CHANGELOG.md section for a version ("## v1.2.0" or "## 1.2.0"), plus install notes.
set -euo pipefail
VERSION="${1#v}"
notes=$(awk -v v="$VERSION" '
  /^## / { if (found) exit; h=$2; sub(/^v/, "", h); if (h == v) { found=1; next } }
  found { print }
' CHANGELOG.md)
if [ -z "${notes//[[:space:]]/}" ]; then notes="Maintenance release."; fi
printf '%s\n\n' "$notes"
cat <<NOTES
---
**Downloads**
- \`hearth-tv-${VERSION}.apk\`: TV launcher (Google TV / Android TV). Includes the iPad web remote.
- \`hearth-remote-${VERSION}.apk\`: phone remote (Android).

Both install over the previous version. No uninstall needed.
NOTES
