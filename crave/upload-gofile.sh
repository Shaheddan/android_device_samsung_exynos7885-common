#!/bin/bash
# Upload the newest PixelOS zip of one device from this Crave workspace to GoFile
# (guest upload, no token). Usage: upload-gofile.sh <device>
# WHY: the zip is over GitHub's 2 GiB release-asset limit, and `crave pull` cannot reach
# Crave from Shade's network, while GoFile downloads work there.
# Ends with "GOFILE <device>: <download page>"; the sha256 above it checks the download.
D=$1
[ -n "$D" ] || { echo "!! usage: upload-gofile.sh <device>"; exit 1; }
OUT=${OUT:-out/target/product/$D}
ZIP=$(ls -t "$OUT"/PixelOS_"$D"-*.zip 2>/dev/null | head -1)
if [ -z "$ZIP" ]; then
  echo "!! no PixelOS_$D zip in $PWD/$OUT; what is there:"
  ls -la "$OUT" 2>&1 | tail -30
  exit 1
fi
ls -la "$ZIP"
( cd "$(dirname "$ZIP")" && sha256sum "$(basename "$ZIP")" )

# Print the downloadPage of a GoFile JSON reply, or fail.
page() {
  python3 -c 'import sys, json
try:
    d = json.load(sys.stdin)
except Exception:
    sys.exit(1)
p = (d.get("data") or {}).get("downloadPage")
if d.get("status") != "ok" or not p:
    sys.exit(1)
print(p)'
}

try() {
  echo "-- uploading to $1"
  R=$(curl -sS --retry 3 --retry-delay 10 -F "file=@$ZIP" "$1")
  L=$(printf '%s' "$R" | page) && { echo "GOFILE $D: $L"; exit 0; }
  echo "   failed, reply: $(printf '%s' "$R" | head -c 300)"
}

# GoFile has moved its upload endpoint over time; try the current global one first,
# then the per-server endpoints (new and old path) from its server list.
try https://upload.gofile.io/uploadfile
S=$(curl -sS https://api.gofile.io/servers | python3 -c 'import sys, json
try:
    print(json.load(sys.stdin)["data"]["servers"][0]["name"])
except Exception:
    pass')
if [ -n "$S" ]; then
  try "https://$S.gofile.io/contents/uploadfile"
  try "https://$S.gofile.io/uploadFile"
fi
echo "!! every GoFile upload attempt failed"
exit 1
