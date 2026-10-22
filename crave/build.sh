#!/bin/bash
# Build PixelOS 16 QPR2 (16.2) for Royna's Exynos7885 Samsung devices on a Crave node.
# Queue it from WSL in ~/crave-a30, on Crave's PixelOS project (82), e.g.:
#   crave run --projectID 82 --no-patch -- "curl -sfL https://raw.githubusercontent.com/Shaheddan/android_device_samsung_exynos7885-common/<commit>/crave/build.sh | DEVICES='a10 a20' VARIANT=userdebug bash"
# DEVICES: any of a10 a20 a20e a30 a30s a40, space separated, built in that order.
# WHY each step:
#  - the workspace is re-initialised to PixelOS's sixteen-qpr2 manifest, so the node
#    builds that branch whatever Crave's project happens to be cached at;
#  - one shared local manifest plus a generated device/vendor pair per device: the
#    common projects are listed once and a new device needs no new manifest file;
#  - every requested device is synced in one resync and built one after another in
#    this job, because the account can only run one Crave job at a time;
#  - each zip goes to GoFile right after its own build (it is over GitHub's 2 GiB
#    asset limit and Crave clears out/ after the job); a failed device is reported and
#    the next one still builds.
# No `set -e`: build/envsetup.sh is not written for it; critical steps fail explicitly.
VARIANT=${VARIANT:-userdebug}
DEVICES=${DEVICES:-a30}
BRANCH=${BRANCH:-pixelos-16.2}
COMMON=device/samsung/exynos7885-common
RAW=https://raw.githubusercontent.com/Shaheddan/android_device_samsung_exynos7885-common/$BRANCH

for d in $DEVICES; do
  case " a10 a20 a20e a30 a30s a40 " in
    *" $d "*) ;;
    *) echo "!! unknown device '$d' (known: a10 a20 a20e a30 a30s a40)"; exit 1 ;;
  esac
done
echo "== PixelOS 16.2 $VARIANT for:$(printf ' %s' $DEVICES)"

repo init -u https://github.com/PixelOS-AOSP/android_manifest.git -b sixteen-qpr2 --git-lfs \
  || { echo "!! repo init failed"; exit 1; }
rm -rf .repo/local_manifests && mkdir -p .repo/local_manifests || exit 1
curl -sfL "$RAW/manifests/exynos7885.xml" -o .repo/local_manifests/exynos7885.xml \
  || { echo "!! could not fetch manifests/exynos7885.xml"; exit 1; }
{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo '<manifest>'
  for d in $DEVICES; do
    echo "  <project path=\"device/samsung/$d\" name=\"Shaheddan/android_device_samsung_$d\" remote=\"github\" revision=\"$BRANCH\" />"
    echo "  <project path=\"vendor/samsung/$d\" name=\"Shaheddan/proprietary_vendor_samsung_$d\" remote=\"github\" revision=\"bumblebee\" />"
  done
  echo '</manifest>'
} > .repo/local_manifests/devices.xml || exit 1
cat .repo/local_manifests/devices.xml

/opt/crave/resync.sh || { echo "!! resync failed"; exit 1; }

source build/envsetup.sh
LOG=$(mktemp)
OK=; FAIL=
for d in $DEVICES; do
  echo "==================== $d"
  # PixelOS's breakfast lunches custom_<device>-<its current release>-$VARIANT
  if breakfast "$d" "$VARIANT" && m pixelos; then
    bash "$COMMON/crave/upload-gofile.sh" "$d" | tee -a "$LOG"
    if [ "${PIPESTATUS[0]}" = 0 ]; then OK="$OK $d"; else FAIL="$FAIL $d(upload)"; fi
  else
    echo "!! $d: build failed"
    FAIL="$FAIL $d(build)"
  fi
done

# Repeat the results at the very end so they are the last lines of the log.
echo "===================="
grep '^GOFILE ' "$LOG"
echo "BUILT:${OK:- none}"
echo "FAILED:${FAIL:- none}"
[ -z "$FAIL" ]
