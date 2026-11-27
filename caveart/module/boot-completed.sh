# Every boot: make sure CaveArt is on in Vector, with System UI in its scope. Vector keeps
# that in its own database, so after the first boot this only checks.
S=/data/adb/exynos_caveart
CLI=/data/adb/modules/zygisk_vector/cli
PKG=com.android.CaveArt
log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> "$S/boot.log"; }

if [ ! -f "$CLI" ]; then
    log "Vector is not installed"
    setprop persist.exynos.caveart.state no_vector
    exit 0
fi

# Vector's daemon starts with the modules; give it up to a minute.
i=0
until sh "$CLI" status > /dev/null 2>&1; do
    i=$((i + 1))
    if [ $i -ge 30 ]; then
        log "Vector's daemon did not answer"
        setprop persist.exynos.caveart.state failed
        exit 0
    fi
    sleep 2
done

changed=
if ! sh "$CLI" modules ls -e 2>/dev/null | grep -q "^$PKG "; then
    sh "$CLI" modules enable "$PKG" >> "$S/boot.log" 2>&1 && changed=1
fi
# CaveArt's static scope is not applied on its own: add System UI by hand.
if ! sh "$CLI" scope ls "$PKG" 2>/dev/null | grep -q '^com.android.systemui '; then
    sh "$CLI" scope add "$PKG" com.android.systemui/0 >> "$S/boot.log" 2>&1 && changed=1
fi
# System UI started before CaveArt was switched on: restart it once to load the hooks.
if [ -n "$changed" ]; then
    log "CaveArt switched on, restarting System UI"
    pkill -x com.android.systemui
fi
setprop persist.exynos.caveart.state active
