#!/bin/bash
# Retry APK upload to GitHub Release at 5 AM
# Tries git push first, then release asset upload

TOKEN="${GITHUB_TOKEN:-<configured-in-env>}"
REPO="louise-mars/kiro"
APK="/tmp/kiro/app/build/outputs/apk/debug/app-debug.apk"

LOG="/tmp/apk_push_log.txt"
exec > $LOG 2>&1

echo "=== $(date) ==="

# Check APK exists
if [ ! -f "$APK" ]; then
    echo "APK not found at $APK"
    exit 1
fi

# Try git push first
cd /tmp/kiro
git push origin master --force 2>&1
if [ $? -eq 0 ]; then
    echo "Git push succeeded!"
else
    echo "Git push failed, trying API approach..."
fi

# Try upload via GitHub API
RELEASE_ID=309294244
curl -s --connect-timeout 30 -T "$APK" \
  -H "Authorization: token $TOKEN" \
  "https://github.com/repos/$REPO/releases/$RELEASE_ID/assets?name=app-debug.apk" \
  -w "\nHTTP_CODE:%{http_code}" 2>&1 | tail -3

echo "=== Done $(date) ==="
