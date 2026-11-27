# The switch was turned off: ksud runs this while it removes the module at boot. Take out
# the modules this module installed (not ones the user had already) and CaveArt's root.
S=/data/adb/exynos_caveart
PKG=com.android.CaveArt
for id in zygisksu zygisk_vector; do
    [ -f "$S/ours_$id" ] && rm -rf "/data/adb/modules/$id" "/data/adb/modules_update/$id"
done
printf '{"key":"%s","allow_su":false,"non_root":{"use_default":true,"umount_modules":true}}\n' \
    "$PKG" > /data/adb/exynos_caveart_profile.json
/data/adb/ksud profile set /data/adb/exynos_caveart_profile.json
rm -rf "$S" /data/adb/exynos_caveart_profile.json
