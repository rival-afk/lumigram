#!/bin/bash
# Updates VERSION.json from gradle.properties before each commit
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_PROPERTIES="$ROOT_DIR/gradle.properties"
VERSION_JSON="$ROOT_DIR/VERSION.json"

if [ ! -f "$GRADLE_PROPERTIES" ]; then
    echo "ERROR: gradle.properties not found at $GRADLE_PROPERTIES"
    exit 1
fi

APP_VERSION_CODE=$(grep '^APP_VERSION_CODE=' "$GRADLE_PROPERTIES" | cut -d= -f2 | tr -d ' ')
APP_VERSION_NAME=$(grep '^APP_VERSION_NAME=' "$GRADLE_PROPERTIES" | cut -d= -f2 | tr -d ' ')
APP_PACKAGE=$(grep '^APP_PACKAGE=' "$GRADLE_PROPERTIES" | cut -d= -f2 | tr -d ' ')

if [ -z "$APP_VERSION_CODE" ] || [ -z "$APP_VERSION_NAME" ]; then
    echo "ERROR: Could not read APP_VERSION_CODE or APP_VERSION_NAME from gradle.properties"
    exit 1
fi

cat > "$VERSION_JSON" << EOF
{
  "version_name": "$APP_VERSION_NAME",
  "version_code": $APP_VERSION_CODE,
  "package": "$APP_PACKAGE",
  "build_date": "$(date +%Y-%m-%d)",
  "target_sdk": 36,
  "min_sdk": 23
}
EOF

echo "VERSION.json updated: $APP_VERSION_NAME ($APP_VERSION_CODE)"
git add "$VERSION_JSON"
