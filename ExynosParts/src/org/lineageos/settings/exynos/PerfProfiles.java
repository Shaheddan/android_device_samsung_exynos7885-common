/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.provider.DeviceConfig;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The performance profiles of Settings > System > Performance.
 *
 * How a profile gets applied (all of it outside this app):
 *  - persist.exynos.perf.profile holds the choice. Vendor init copies it once per boot to
 *    exynos.perf.active and restarts the power HAL with /vendor/etc/powerhint-<id>.json.
 *  - That file holds the boosts and the CPU/GPU caps. The HAL writes the caps when boot
 *    completes; the kernel itself always boots at Samsung's clocks.
 *  - Overclock guard: the first boot on oc1/oc2 turns the choice into "oc1-trial". This
 *    app drops "-trial" after 3 good minutes or a clean shutdown. If init still finds
 *    "-trial" at the next boot, the phone restarted by itself: it uses Max instead and
 *    sets exynos.perf.fallback.
 *
 * The clocks shown here come from configs/power/gen_profiles.py: keep them in step.
 */
final class PerfProfiles {

    private static final String TAG = "ExynosPerf";

    static final String PROP_PROFILE = "persist.exynos.perf.profile";
    static final String PROP_ACTIVE = "exynos.perf.active";
    static final String PROP_FALLBACK = "exynos.perf.fallback";
    private static final String TRIAL = "-trial";

    static final String DEFAULT = "smooth";
    static final long GUARD_MS = 3 * 60 * 1000;

    /** One profile; clocks in MHz as {little, big, gpu}. */
    static final class Profile {
        final String id;
        final int title, desc, level;
        final boolean oc;
        final int[] cap;      // the speed limits
        final int[] touch;    // held while touching; null = none (stock) or full speed (max)

        Profile(String id, int title, int desc, int level, boolean oc, int[] cap, int[] touch) {
            this.id = id;
            this.title = title;
            this.desc = desc;
            this.level = level;
            this.oc = oc;
            this.cap = cap;
            this.touch = touch;
        }
    }

    /** The profiles of one SoC, mildest first. */
    static final class Soc {
        final String chip;
        final int[] stock;
        final Profile[] profiles;

        Soc(String chip, int[] stock, int[] oc1, int[] oc2, int[] light, int[] smooth, int[] fast) {
            this.chip = chip;
            this.stock = stock;
            profiles = new Profile[] {
                new Profile("stock", R.string.perf_stock, R.string.perf_stock_desc, 0, false, stock, null),
                new Profile("light", R.string.perf_light, R.string.perf_light_desc, 1, false, stock, light),
                new Profile("smooth", R.string.perf_smooth, R.string.perf_smooth_desc, 2, false, stock, smooth),
                new Profile("fast", R.string.perf_fast, R.string.perf_fast_desc, 3, false, stock, fast),
                new Profile("max", R.string.perf_max, R.string.perf_max_desc, 4, false, stock, null),
                new Profile("oc1", R.string.perf_oc1, R.string.perf_oc1_desc, 5, true, oc1, null),
                new Profile("oc2", R.string.perf_oc2, R.string.perf_oc2_desc, 6, true, oc2, null),
            };
        }

        Profile get(String id) {
            for (Profile p : profiles) {
                if (p.id.equals(id)) return p;
            }
            return get(DEFAULT);
        }
    }

    private static final Soc EXYNOS_7904 = new Soc("Exynos 7904",
            new int[] {1586, 1768, 845},
            new int[] {1690, 1976, 1001},
            new int[] {1690, 2080, 1100},
            new int[] {1014, 1144, 450},
            new int[] {1144, 1352, 676},
            new int[] {1352, 1560, 845});

    private static final Soc EXYNOS_7884B = new Soc("Exynos 7884B",
            new int[] {1352, 1560, 676},
            new int[] {1586, 1768, 845},
            new int[] {1690, 2080, 1001},
            new int[] {1014, 1144, 450},
            new int[] {1144, 1352, 676},
            new int[] {1248, 1560, 676});

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static final Runnable sConfirm = PerfProfiles::confirmTrial;

    private PerfProfiles() {}

    /** A10, A20 and A20e have the Exynos 7884B; A30, A30s and A40 the 7904. */
    static Soc soc() {
        final String device = Build.DEVICE;
        return device.startsWith("a10") || device.startsWith("a20") ? EXYNOS_7884B : EXYNOS_7904;
    }

    private static boolean known(String id) {
        for (Profile p : EXYNOS_7904.profiles) {
            if (p.id.equals(id)) return true;
        }
        return false;
    }

    /** The profile this boot runs with. */
    static String active() {
        final String id = SystemProperties.get(PROP_ACTIVE, "");
        return known(id) ? id : DEFAULT;
    }

    /** The profile the next boot will use. */
    static String chosen() {
        String id = SystemProperties.get(PROP_PROFILE, "");
        if (id.endsWith(TRIAL)) id = id.substring(0, id.length() - TRIAL.length());
        return known(id) ? id : DEFAULT;
    }

    /** The overclock profile the guard turned off this boot, or "". */
    static String fallback() {
        final String id = SystemProperties.get(PROP_FALLBACK, "");
        return known(id) ? id : "";
    }

    static void choose(String id) {
        SystemProperties.set(PROP_PROFILE, id);
    }

    /** The phone ran fine on an overclock profile: keep it for the next boots too. */
    static void confirmTrial() {
        final String id = SystemProperties.get(PROP_PROFILE, "");
        if (id.endsWith(TRIAL)) {
            SystemProperties.set(PROP_PROFILE, id.substring(0, id.length() - TRIAL.length()));
            Log.i(TAG, "overclock profile " + id + " kept");
        }
    }

    /** At boot completed: an overclock trial counts as good after GUARD_MS. */
    static void onBootCompleted(Context context) {
        if (SystemProperties.get(PROP_PROFILE, "").endsWith(TRIAL)) {
            sHandler.removeCallbacks(sConfirm);
            sHandler.postDelayed(sConfirm, GUARD_MS);
        }
    }

    // ---- Memory ----------------------------------------------------------------------

    /**
     * The Memory part of the page.
     *
     *  - Compressed RAM (zram) and swap to storage: persist.exynos.ram.zram holds the zram
     *    size in percent of RAM, persist.exynos.ram.wb the size of the writeback file on
     *    storage in MB (0 = off). Vendor init reads both once per boot and sets up swap from
     *    /vendor/etc/fstab.zram<percent>_wb<MB>, then reports what it used in exynos.ram.zram
     *    and exynos.ram.wb. Nothing chosen means this phone's default
     *    (ro.vendor.exynos.ram.zram_default, set per phone) and no swap to storage.
     *    Android's ZramWriteback job moves idle zram pages to the file once a day.
     *  - Background apps: how many cached processes ActivityManager keeps
     *    (activity_manager/max_cached_processes). Applies right away and stays across
     *    restarts. "balanced" removes the override, so the phone's overlay value
     *    (config_customizedMaxCachedProcesses) counts again.
     */
    static final class Memory {
        static final String PROP_ZRAM = "persist.exynos.ram.zram";
        static final String PROP_WB = "persist.exynos.ram.wb";
        private static final String PROP_ZRAM_ACTIVE = "exynos.ram.zram";
        private static final String PROP_WB_ACTIVE = "exynos.ram.wb";
        private static final String PROP_ZRAM_DEFAULT = "ro.vendor.exynos.ram.zram_default";
        static final int[] ZRAM = {25, 50, 75, 100};
        static final int[] WB = {0, 512, 1024, 2048};
        static final String FEWER = "fewer", BALANCED = "balanced", MORE = "more";
        static final String[] APPS = {FEWER, BALANCED, MORE};
        private static final String NS_AM = "activity_manager";
        private static final String KEY_CACHED = "max_cached_processes";

        private Memory() {}

        private static boolean valid(int[] options, int v) {
            for (int o : options) {
                if (o == v) return true;
            }
            return false;
        }

        static int zramDefault() {
            final int v = SystemProperties.getInt(PROP_ZRAM_DEFAULT, 50);
            return valid(ZRAM, v) ? v : 50;
        }

        /** The zram size the next start uses. */
        static int zramChosen() {
            final int v = SystemProperties.getInt(PROP_ZRAM, 0);
            return valid(ZRAM, v) ? v : zramDefault();
        }

        /** The zram size this start uses (a ROM without the setting: what is chosen). */
        static int zramActive() {
            final int v = SystemProperties.getInt(PROP_ZRAM_ACTIVE, 0);
            return valid(ZRAM, v) ? v : zramChosen();
        }

        static int wbChosen() {
            final int v = SystemProperties.getInt(PROP_WB, 0);
            return valid(WB, v) ? v : 0;
        }

        static int wbActive() {
            final int v = SystemProperties.getInt(PROP_WB_ACTIVE, -1);
            return valid(WB, v) ? v : wbChosen();
        }

        static void choose(int zram, int wb) {
            SystemProperties.set(PROP_ZRAM, String.valueOf(zram));
            SystemProperties.set(PROP_WB, String.valueOf(wb));
        }

        /** Cached processes for fewer / balanced / more: half, this phone's own, 1.5 times. */
        static int[] appsLimits(Context c) {
            int base = 32;
            final int id = c.getResources().getIdentifier("config_customizedMaxCachedProcesses",
                    "integer", "android");
            if (id != 0) base = c.getResources().getInteger(id);
            // Android's own default is 1024 (it freezes cached apps; this kernel can't).
            base = Math.max(16, Math.min(base, 128));
            return new int[] {base / 2, base, base * 3 / 2};
        }

        static String apps(Context c) {
            final String v = DeviceConfig.getProperty(NS_AM, KEY_CACHED);
            if (v == null || v.isEmpty()) return BALANCED;
            final int[] limits = appsLimits(c);
            try {
                final int n = Integer.parseInt(v.trim());
                if (n <= limits[0]) return FEWER;
                if (n >= limits[2]) return MORE;
            } catch (NumberFormatException e) {
                // Not one of ours: show it as the phone's own setting.
            }
            return BALANCED;
        }

        static void chooseApps(Context c, String level) {
            final int[] limits = appsLimits(c);
            if (FEWER.equals(level)) {
                DeviceConfig.setProperty(NS_AM, KEY_CACHED, String.valueOf(limits[0]), false);
            } else if (MORE.equals(level)) {
                DeviceConfig.setProperty(NS_AM, KEY_CACHED, String.valueOf(limits[2]), false);
            } else {
                DeviceConfig.deleteProperty(NS_AM, KEY_CACHED);
            }
            Log.i(TAG, "background apps: " + level);
        }

        /** {MemTotal, MemAvailable, SwapTotal, SwapFree} in kB, from /proc/meminfo. */
        static long[] meminfo() {
            final long[] v = new long[4];
            final String[] keys = {"MemTotal:", "MemAvailable:", "SwapTotal:", "SwapFree:"};
            try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    for (int i = 0; i < keys.length; i++) {
                        if (line.startsWith(keys[i])) {
                            v[i] = Long.parseLong(line.substring(keys[i].length())
                                    .replace("kB", "").trim());
                        }
                    }
                }
            } catch (Exception e) {
                // Leave zeros: the card shows dashes.
            }
            return v;
        }

        /** Numbers of a zram0 sysfs file (mm_stat, bd_stat), or null. */
        static long[] zramFile(String name) {
            try (BufferedReader r = new BufferedReader(new FileReader("/sys/block/zram0/" + name))) {
                final String line = r.readLine();
                if (line == null) return null;
                final String[] f = line.trim().split("\\s+");
                final long[] v = new long[f.length];
                for (int i = 0; i < f.length; i++) v[i] = Long.parseLong(f[i]);
                return v;
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Closes the apps that sit in the background (cached), except the home screen, the
         * keyboard and this app. Returns how many apps it closed. Not on the main thread.
         */
        static int freeUp(Context c) {
            final ActivityManager am = c.getSystemService(ActivityManager.class);
            final Set<String> keep = new HashSet<>();
            keep.add(c.getPackageName());
            final ResolveInfo home = c.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    PackageManager.MATCH_DEFAULT_ONLY);
            if (home != null && home.activityInfo != null) keep.add(home.activityInfo.packageName);
            final String ime = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            final ComponentName imeName = ime == null ? null : ComponentName.unflattenFromString(ime);
            if (imeName != null) keep.add(imeName.getPackageName());

            final Set<String> close = new HashSet<>();
            final List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs != null) {
                for (ActivityManager.RunningAppProcessInfo p : procs) {
                    if (p.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
                            || p.pkgList == null) {
                        continue;
                    }
                    for (String pkg : p.pkgList) {
                        if (!keep.contains(pkg)) close.add(pkg);
                    }
                }
            }
            for (String pkg : close) am.killBackgroundProcesses(pkg);
            Log.i(TAG, "free up RAM: closed " + close.size() + " apps");
            return close.size();
        }
    }
}
