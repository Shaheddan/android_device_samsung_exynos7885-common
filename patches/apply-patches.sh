#!/bin/bash
# Applies the Exynos7885 phones' platform patches (A10, A20, A20e, A30, A30s, A40) to a
# synced LineageOS tree. Run from the top of the tree:
#   bash device/samsung/exynos7885-common/patches/apply-patches.sh --check   only test
#   bash device/samsung/exynos7885-common/patches/apply-patches.sh           apply
# An optional last argument picks the patch set (default lineage-22.2, the folder of the
# same name next to this script).
# Patches already present (same subject in the project's recent history) are skipped, so
# running it twice is safe. --check applies everything in a throwaway worktree of each
# project and reports what would happen; the tree itself is not touched.
# WHY a fixed git identity: git am refuses to commit without one. The patch author is kept.
# WHY no --3way: it needs the patches' pre-image blobs, which shallow (--depth=1) trees do
#   not have; each set is made against its own LineageOS branch.
check=
if [ "$1" = "--check" ]; then check=1; shift; fi
ver=${1:-lineage-22.2}
top=$(pwd)
base="$(cd "$(dirname "$0")" && pwd)/$ver"
[ -d "$top/.repo" ] || { echo "run this from the top of the LineageOS tree"; exit 1; }
[ -d "$base" ] || { echo "no patch set: $base"; exit 1; }
id=(-c user.name=exynos7885-patches -c user.email=exynos7885-patches@localhost)
projects=$(cd "$base" && find . -name '*.patch' -printf '%h\n' | sed 's|^\./||' | sort -u)

# Every project must be there and clean before anything is applied.
bad=0
for proj in $projects; do
  if [ ! -e "$top/$proj/.git" ]; then
    echo "!! project missing from the tree: $proj"; bad=1
  elif [ -n "$(git --no-optional-locks -C "$top/$proj" status --porcelain --untracked-files=no)" ]; then
    echo "!! $proj has uncommitted changes; commit or reset them first"; bad=1
  fi
done
[ "$bad" = 0 ] || { echo "STOPPED: nothing was changed"; exit 1; }

for proj in $projects; do
  echo "== $proj"
  wt="$top/$proj"
  if [ -n "$check" ]; then
    wt=$(mktemp -d)
    git -C "$top/$proj" worktree add -q --detach "$wt" HEAD || { echo "!! no worktree for $proj"; bad=1; continue; }
  fi
  for p in "$base/$proj"/*.patch; do
    subj=$(git mailinfo /dev/null /dev/null < "$p" | sed -n 's/^Subject: //p')
    if git -C "$wt" log --format=%s -n 300 | grep -Fqx -- "$subj"; then
      echo "   already applied: $subj"; continue
    fi
    if ! git "${id[@]}" -C "$wt" am -q "$p"; then
      git -C "$wt" am --abort >/dev/null 2>&1
      echo "!! does not apply: ${p#$base/}"; bad=1
      [ -n "$check" ] && break
      echo "STOPPED at this patch; the ones above it are applied"; exit 1
    fi
    if [ -n "$check" ]; then echo "   would apply: $subj"; else echo "   applied: $subj"; fi
  done
  if [ -n "$check" ]; then
    git -C "$top/$proj" worktree remove --force "$wt" 2>/dev/null; rm -rf "$wt"
    git -C "$top/$proj" worktree prune
  fi
done
if [ -n "$check" ]; then
  [ "$bad" = 0 ] && echo "CHECK OK: every $ver patch applies (nothing was changed)" \
    || { echo "CHECK FAILED: see the lines marked !! (nothing was changed)"; exit 1; }
else
  echo "All Exynos7885 $ver platform patches are in place."
fi
