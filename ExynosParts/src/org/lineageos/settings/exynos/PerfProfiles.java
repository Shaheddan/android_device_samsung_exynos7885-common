/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;

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
}
