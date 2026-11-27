/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemProperties;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Settings > Display > Lock screen styles: CaveArt, built into the ROM, on Vector and
 * NeoZygisk (KernelSU-Next modules). The switch only sets properties: init runs ksud for
 * it (init.caveart.rc), and the exynos_caveart module that ksud installs reports how far
 * it got in persist.exynos.caveart.state.
 */
public class LockscreenSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    static final String PROP_ENABLED = "persist.exynos.caveart";
    static final String PROP_STATE = "persist.exynos.caveart.state";
    // set to the time of the request, so init sees a new value every time
    private static final String PROP_ON = "exynos.caveart.on";
    private static final String PROP_OFF = "exynos.caveart.off";
    private static final String CAVEART = "com.android.CaveArt";

    // ksud starts the module install within a few seconds; nothing by then means there is
    // no ksud to run (KernelSU-Next manager never opened).
    private static final long NO_KSUD_MS = 20000;
    private static final long POLL_MS = 1000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private ChargingSwitchPreference mSwitch;
    private VolteStatusPreference mStatus;
    private VolteStatusPreference mOpen;
    private boolean mAskedRestart;

    static boolean isEnabled() {
        return SystemProperties.getInt(PROP_ENABLED, 0) == 1;
    }

    /** A removal goes through while the phone restarts (the module's uninstall.sh). */
    static void onBootCompleted() {
        if (!isEnabled() && "removing".equals(SystemProperties.get(PROP_STATE))) {
            SystemProperties.set(PROP_STATE, "off");
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.lockscreen_settings, rootKey);
        mSwitch = findPreference("caveart_enabled");
        mStatus = findPreference("caveart_status");
        mOpen = findPreference("caveart_open");
        if (mSwitch != null) {
            mSwitch.setChecked(isEnabled());
            mSwitch.setOnPreferenceChangeListener(this);
            mSwitch.setGroupPosition(ExynosStyle.GROUP_TOP);
        }
        if (mStatus != null) {
            mStatus.setGroupPosition(ExynosStyle.GROUP_MIDDLE);
        }
        if (mOpen != null) {
            mOpen.setGroupPosition(ExynosStyle.GROUP_BOTTOM);
            mOpen.setOnPreferenceClickListener(p -> {
                openCaveArt();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mAskedRestart = false;
        tick();
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean on = Boolean.TRUE.equals(newValue);
        SystemProperties.set(PROP_ENABLED, on ? "1" : "0");
        SystemProperties.set(PROP_STATE, on ? "requested" : "removing");
        SystemProperties.set(on ? PROP_ON : PROP_OFF, Long.toString(System.currentTimeMillis()));
        mAskedRestart = false;
        mHandler.removeCallbacksAndMessages(null);
        if (on) {
            mHandler.postDelayed(this::tick, POLL_MS);
        } else {
            askRestart(R.string.lockscreen_restart_off);
        }
        updateViews(state());
        return true;
    }

    private static String state() {
        return SystemProperties.get(PROP_STATE, "off");
    }

    /** Follows the setup while it runs, and offers the restart it ends with. */
    private void tick() {
        if (!isAdded()) return;
        String s = state();
        if ("requested".equals(s) && sinceRequest() > NO_KSUD_MS) {
            SystemProperties.set(PROP_ENABLED, "0");
            SystemProperties.set(PROP_STATE, "no_ksu");
            if (mSwitch != null) mSwitch.setChecked(false);
            s = "no_ksu";
        }
        updateViews(s);
        if ("restart".equals(s) && isEnabled() && !mAskedRestart) {
            mAskedRestart = true;
            askRestart(R.string.lockscreen_restart_on);
        }
        if ("requested".equals(s) || "installing".equals(s)) {
            mHandler.postDelayed(this::tick, POLL_MS);
        }
    }

    private static long sinceRequest() {
        try {
            return System.currentTimeMillis() - Long.parseLong(SystemProperties.get(PROP_ON, "0"));
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private void updateViews(String s) {
        if (mStatus != null) mStatus.setSummary(stateText(s));
        if (mOpen != null) mOpen.setEnabled(launchIntent() != null);
    }

    private static int stateText(String s) {
        switch (s) {
            case "requested": return R.string.lockscreen_state_requested;
            case "installing": return R.string.lockscreen_state_installing;
            case "restart": return R.string.lockscreen_state_restart;
            case "active": return R.string.lockscreen_state_active;
            case "failed": return R.string.lockscreen_state_failed;
            case "no_vector": return R.string.lockscreen_state_no_vector;
            case "no_ksu": return R.string.lockscreen_state_no_ksu;
            case "removing": return R.string.lockscreen_state_removing;
            default: return R.string.lockscreen_state_off;
        }
    }

    private Intent launchIntent() {
        final Context c = getContext();
        return c == null ? null : c.getPackageManager().getLaunchIntentForPackage(CAVEART);
    }

    private void openCaveArt() {
        final Intent i = launchIntent();
        if (i != null) startActivity(i);
    }

    private void askRestart(int message) {
        final Context c = getContext();
        if (c == null) return;
        new AlertDialog.Builder(c)
                .setTitle(R.string.lockscreen_restart_title)
                .setMessage(message)
                .setPositiveButton(R.string.volte_restart_now, (d, w) -> {
                    final PowerManager pm = c.getSystemService(PowerManager.class);
                    // reboot() blocks until the phone goes down: not on the UI thread.
                    if (pm != null) {
                        new Thread(() -> pm.reboot(null), "lockscreen-reboot").start();
                    }
                })
                .setNegativeButton(R.string.volte_restart_later, null)
                .show();
    }
}
