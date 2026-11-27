# Background part of the exynos_caveart install: NeoZygisk and Vector, then root for
# CaveArt. A module the user had already installed is left alone, and is not removed
# when the switch is turned off again (only the ones listed in $S/ours_* are).
S=/data/adb/exynos_caveart
E=/system_ext/etc/caveart
KSUD=/data/adb/ksud
PKG=com.android.CaveArt
log() { echo "$(date '+%m-%d %H:%M:%S') $*"; }

# ksud is still finishing this module's own install
sleep 3
state=restart
for m in zygisksu:NeoZygisk.zip zygisk_vector:Vector.zip; do
    id=${m%%:*}
    zip=${m#*:}
    if [ -f "/data/adb/modules/$id/module.prop" ] && [ ! -f "/data/adb/modules/$id/remove" ]; then
        log "$id is already installed, keeping it"
        continue
    fi
    log "installing $zip"
    if $KSUD module install "$E/$zip"; then
        touch "$S/ours_$id"
    else
        log "!! $zip did not install"
        state=failed
    fi
done

# CaveArt uses su to read the current lock screen wallpaper and to set live wallpapers.
printf '{"key":"%s","allow_su":true,"root":{"use_default":true,"uid":0,"gid":0,"groups":[],"capabilities":[],"selinux_domain":"u:r:ksu:s0","namespace":"inherited","flags":[]}}\n' \
    "$PKG" > "$S/profile.json"
$KSUD profile set "$S/profile.json" || log "!! could not give $PKG root (grant it in the KernelSU-Next manager)"

log "done: $state"
setprop persist.exynos.caveart.state "$state"
