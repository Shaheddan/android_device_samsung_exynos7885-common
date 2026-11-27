# Runs while ksud installs this module (Settings > Display > Lock screen styles).
# A module can't install other modules from inside its own install, so NeoZygisk and
# Vector go in from setup.sh in the background once ksud is done with this one.
S=/data/adb/exynos_caveart
mkdir -p "$S"
setprop persist.exynos.caveart.state installing
sh "$MODPATH/setup.sh" > "$S/setup.log" 2>&1 &
# init kills everything left in the cgroup of the ksud it started once ksud exits, so
# move setup.sh out to the root cgroup before returning.
for c in /sys/fs/cgroup/cgroup.procs /acct/cgroup.procs; do echo $! > "$c" 2>/dev/null; done
ui_print "- NeoZygisk and Vector follow in the background"
