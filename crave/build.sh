#!/bin/bash
# Build PixelOS 16 QPR2 (16.2) for one of Royna's Exynos7885 Samsung devices on a Crave node.
# Queue it from WSL in ~/crave-a30, on Crave's PixelOS project (82), e.g.:
#   crave run --projectID 82 --no-patch -- "curl -sfL https://raw.githubusercontent.com/Shaheddan/android_device_samsung_exynos7885-common/<commit>/crave/build.sh | DEVICE=a30s VARIANT=userdebug bash"
# DEVICE: one of a10 a20 a20e a30 a30s a40. ONE device per job: Crave's rules forbid
# building several devices in one build ("One Build should only build for one
# device/target", even when they share a common tree), so this refuses more than one.
# Device trees and device vendor trees both come from their pixelos-16.2 branches (the
# vendor ones add the hardware/samsung namespace import that libteecl needs).
# WHY each step:
#  - the workspace is re-initialised to PixelOS's sixteen-qpr2 manifest, so the node
#    builds that branch whatever Crave's project happens to be cached at; with
#    --depth=1, which Crave's admins require (full history wastes their storage and
#    bandwidth; the build needs none of it);
#  - one shared local manifest plus a generated device/vendor pair per device: the
#    common projects are listed once and a new device needs no new manifest file;
#  - the zip goes to GoFile right after the build (it is over GitHub's 2 GiB asset
#    limit and Crave clears out/ after the job).
# No `set -e`: build/envsetup.sh is not written for it; critical steps fail explicitly.
VARIANT=${VARIANT:-userdebug}
# DEVICES is the old name of DEVICE; either may be given, with exactly one device.
DEVICE=${DEVICE:-$DEVICES}
BRANCH=${BRANCH:-pixelos-16.2}
COMMON=device/samsung/exynos7885-common
RAW=https://raw.githubusercontent.com/Shaheddan/android_device_samsung_exynos7885-common/$BRANCH

set -- $DEVICE
if [ $# -ne 1 ]; then
  echo "!! give exactly one device, e.g. DEVICE=a30s (got: '${DEVICE}'). Crave allows one device per build."
  exit 1
fi
DEVICE=$1
case " a10 a20 a20e a30 a30s a40 " in
  *" $DEVICE "*) ;;
  *) echo "!! unknown device '$DEVICE' (known: a10 a20 a20e a30 a30s a40)"; exit 1 ;;
esac
echo "== PixelOS 16.2 $VARIANT for $DEVICE"

repo init -u https://github.com/PixelOS-AOSP/android_manifest.git -b sixteen-qpr2 --git-lfs --depth=1 \
  || { echo "!! repo init failed"; exit 1; }
rm -rf .repo/local_manifests && mkdir -p .repo/local_manifests || exit 1
curl -sfL "$RAW/manifests/exynos7885.xml" -o .repo/local_manifests/exynos7885.xml \
  || { echo "!! could not fetch manifests/exynos7885.xml"; exit 1; }
{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo '<manifest>'
  echo "  <project path=\"device/samsung/$DEVICE\" name=\"Shaheddan/android_device_samsung_$DEVICE\" remote=\"github\" revision=\"$BRANCH\" />"
  echo "  <project path=\"vendor/samsung/$DEVICE\" name=\"Shaheddan/proprietary_vendor_samsung_$DEVICE\" remote=\"github\" revision=\"$BRANCH\" />"
  echo '</manifest>'
} > .repo/local_manifests/devices.xml || exit 1
cat .repo/local_manifests/devices.xml

# LineageOS-only apps that PixelOS cannot build, as project:path pairs. AdvancedDisplay and
# Doze in hardware/samsung depend on lineage-sdk (org.lineageos.platform.internal), which
# PixelOS does not ship (it carries its own framework copy of the Lineage health/hardware
# services), and soong refuses the whole tree over one undefined dependency (Crave build
# 307064). Royna's PixelOS fork of hardware/samsung drops the same two ("samsung: Drop
# los-specific stuff"). Removed after the resync; the project is reset before the next one.
TRIMS="hardware/samsung:packages/AdvancedDisplay hardware/samsung:packages/Doze"

# ---- Crave fixes carried over from the kiwi (Honor 5X) and tablet builds ----------------

# Our patches go in uncommitted (below), so a workspace reused from an earlier job still has
# them in its checkouts, and a checkout with local changes can make the resync refuse to
# update that project. Put the projects we patch back to clean before the resync.
if [ -d "$COMMON/patches" ]; then
  for proj in $(cd "$COMMON/patches" && find . -name '*.patch' -printf '%h\n' | sort -u); do
    proj=${proj#./}
    [ -e "$proj/.git" ] || continue
    git -C "$proj" reset -q --hard && git -C "$proj" clean -fdq \
      && echo "== cleaned $proj before the resync"
  done
fi
for proj in $(for t in $TRIMS; do echo "${t%%:*}"; done | sort -u); do
  [ -e "$proj/.git" ] || continue
  git -C "$proj" reset -q --hard && git -C "$proj" clean -fdq \
    && echo "== restored $proj before the resync"
done

# Crave keeps .repo between jobs, and project 82's workspace is re-initialised here from
# whatever PixelOS version it held before. The tablet's first LineageOS 20 job after its 19.1
# ones died in the resync with "You are attempting to fetch <sha>, which is in the commit
# graph file but not in the object database": a project's commit-graph (an index git writes
# during fetch and gc) still listed commits whose objects were gone; Crave's git refuses to
# fetch then, and resync.sh's retry deletes only the checkout, not the shared object store.
# A commit-graph is only an index: deleting it loses nothing, and the next fetch brings back
# whatever is really missing. The find skips loose-object and pack directories to stay quick.
if [ -d .repo ]; then
  CG=$(find .repo/project-objects .repo/projects \
      -path '*/objects/??' -prune -o -path '*/objects/pack' -prune -o \
      \( -name commit-graph -o -name commit-graphs \) -path '*/objects/info/*' -print 2>/dev/null)
  printf '%s\n' "$CG" | grep . | xargs -r rm -rf
  echo "== removed $(printf '%s\n' "$CG" | grep -c .) commit-graph indexes under .repo"
  [ -z "$(find .repo/project-objects .repo/projects -path '*/objects/??' -prune -o -path '*/objects/pack' -prune -o \( -name commit-graph -o -name commit-graphs \) -path '*/objects/info/*' -print 2>/dev/null)" ] \
    || { echo "!! commit-graph indexes still present under .repo"; exit 1; }
fi

/opt/crave/resync.sh || { echo "!! resync failed"; exit 1; }

# Our changes to platform projects live in $COMMON/patches/<project path>/*.patch and go
# in after the resync. Each patched project is reset first, so a workspace reused from an
# earlier job (patch already in, or half in) never makes this step fail.
if [ -d "$COMMON/patches" ]; then
  for proj in $(cd "$COMMON/patches" && find . -name '*.patch' -printf '%h\n' | sort -u); do
    proj=${proj#./}
    { git -C "$proj" reset -q --hard && git -C "$proj" clean -fdq; } \
      || { echo "!! cannot reset $proj"; exit 1; }
    for p in "$COMMON/patches/$proj"/*.patch; do
      git -C "$proj" apply "$PWD/$p" || { echo "!! patch does not apply: $p"; exit 1; }
      echo "== patched $proj: $(basename "$p")"
    done
  done
fi
for t in $TRIMS; do
  proj=${t%%:*}
  [ -e "$proj/.git" ] || { echo "!! project missing for trim: $proj"; exit 1; }
  rm -rf "${proj:?}/${t#*:}"
  echo "== removed $proj/${t#*:} (LineageOS-only, needs lineage-sdk)"
done

# Crave wraps compilers by renaming each one to mbt-bin-<name> (kiwi build 17). LineageOS's
# gcc-4.9 "gcc" is itself a python wrapper that execs real-<its own file name>, so after the
# rename it looks for real-mbt-bin-<name> and a kernel build that uses GCC dies. Our kernel
# builds with clang, and Android 16 trees may not ship gcc-4.9 at all: this only links names
# that exist, so it is a no-op then.
for t in aarch64/aarch64-linux-android-4.9:aarch64-linux-android arm/arm-linux-androideabi-4.9:arm-linux-androideabi; do
  dir=prebuilts/gcc/linux-x86/${t%%:*}/bin
  pre=${t##*:}
  [ -d "$dir" ] || continue
  for c in gcc g++; do
    [ -e "$dir/real-$pre-$c" ] && ln -sfn "real-$pre-$c" "$dir/real-mbt-bin-$pre-$c"
  done
  echo "== toolchain wrappers in $dir:"
  ls -la "$dir" | grep -E "mbt-bin|real-|$pre-gcc\$" | sed 's/^/==   /'
done

# Android 11 to 13 trees run old host tools that need libncurses.so.5 / libtinfo.so.5, which
# Crave's Debian 13 image only has as .so.6 (tablet builds 305623, 305713, 305971; kiwi
# 18.1-20). The ncurses functions they import are unversioned, so .so.5 names pointing at the
# .so.6 libraries are enough, placed in each tree's lib64 (soong tools reach it through
# RUNPATH $ORIGIN/../lib64; LD_LIBRARY_PATH does not reach ninja). It must run after the
# start of the build (`m nothing`), which can rm -rf out/host/linux-x86 and out/soong/host.
# Android 16 has none of these tools (no clang-3289846 / RenderScript), so it only switches
# itself on when that old toolchain is present.
RSCLANG=prebuilts/clang/host/linux-x86/clang-3289846
ncurses5_links() {
  [ -d "$RSCLANG" ] || { echo "== no $RSCLANG: ncurses 5 links not needed"; return 0; }
  m nothing || { echo "!! m nothing failed"; return 1; }
  local o=${OUT_DIR:-out} need=0 n t f l tinfo6 ncurses6 trees dirs= bad= warn= count=0
  for n in libncurses.so.5 libtinfo.so.5; do
    ls "/lib/x86_64-linux-gnu/$n" "/usr/lib/x86_64-linux-gnu/$n" "/lib64/$n" "/usr/lib64/$n" \
      >/dev/null 2>&1 || need=1
  done
  [ "$need" = 1 ] || return 0
  tinfo6=$(ls /usr/lib/x86_64-linux-gnu/libtinfo.so.6 /lib/x86_64-linux-gnu/libtinfo.so.6 2>/dev/null | head -1)
  ncurses6=$(ls /usr/lib/x86_64-linux-gnu/libncurses.so.6 /lib/x86_64-linux-gnu/libncurses.so.6 2>/dev/null | head -1)
  [ -n "$tinfo6" ] || { echo "!! no libtinfo.so.6 on this host"; return 1; }
  trees="$RSCLANG prebuilts/sdk/tools/linux $o/host/linux-x86 $o/soong/host/linux-x86"
  for t in $trees; do
    case "$t" in
      "$o"/*) mkdir -p "$t/lib64" || { echo "!! cannot create $t/lib64"; return 1; } ;;
      *) [ -d "$t/lib64" ] || continue ;;
    esac
    ln -sfn "$tinfo6" "$t/lib64/libtinfo.so.5"
    ln -sfn "${ncurses6:-$tinfo6}" "$t/lib64/libncurses.so.5"
    dirs="$dirs $t/lib64"
  done
  echo "== libncurses.so.5 -> ${ncurses6:-$tinfo6}, libtinfo.so.5 -> $tinfo6 in:$dirs"
  for t in $trees; do
    for f in "$t"/bin/*; do
      [ -f "$f" ] && [ -x "$f" ] || continue
      count=$((count + 1))
      l=$(ldd "$f" 2>/dev/null) || true
      case "$l" in
        *"libncurses.so.5 => not found"*|*"libtinfo.so.5 => not found"*)
          readelf -d "$f" 2>&1 | grep -E 'NEEDED|RPATH|RUNPATH' | sed "s|^|== $f: |"
          case "$f" in prebuilts/sdk/*) warn="$warn $f" ;; *) bad="$bad $f" ;; esac ;;
      esac
    done
  done
  [ -z "$warn" ] || echo "== WARNING, still no ncurses 5 for:$warn"
  [ -z "$bad" ] || { echo "!! still no libncurses.so.5/libtinfo.so.5 for:$bad"; return 1; }
  echo "== checked $count host tools"
}

source build/envsetup.sh
LOG=$(mktemp)
OK=; FAIL=
echo "==================== $DEVICE"
# PixelOS's breakfast lunches custom_<device>-<its current release>-$VARIANT
if breakfast "$DEVICE" "$VARIANT" && ncurses5_links && m pixelos; then
  bash "$COMMON/crave/upload-gofile.sh" "$DEVICE" | tee -a "$LOG"
  if [ "${PIPESTATUS[0]}" = 0 ]; then OK=" $DEVICE"; else FAIL=" $DEVICE(upload)"; fi
else
  echo "!! $DEVICE: build failed"
  FAIL=" $DEVICE(build)"
fi

# Repeat the result at the very end so it is the last lines of the log.
echo "===================="
grep '^GOFILE ' "$LOG"
echo "BUILT:${OK:- none}"
echo "FAILED:${FAIL:- none}"
[ -z "$FAIL" ]
