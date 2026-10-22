/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Settings > Battery > Charging: bypass charging, charging control and charging speed on
 * one page, in the KiwiParts Android 14 design.
 *
 *  - Bypass now      this app pauses charging at the current level (BypassService).
 *  - Charging control the system's own engine (limit, automatic and custom schedule),
 *                    the same settings the stock page used; on these phones its HAL now
 *                    pauses charging with true bypass, so "limit" holds the level.
 *  - Charging speed  the system's fast charge setting (afc_disable underneath).
 *
 * Bypass now and charging control would fight over the same switch, so they exclude each
 * other: turning bypass now on switches charging control off, and back on afterwards if
 * it was on before.
 */
public class ChargingSettingsFragment extends PreferenceFragmentCompat {

    // Battery at or below this is drawn in the error colour while on battery.
    private static final int LOW_LEVEL = 15;
    private static final int LIMIT_MIN = 10;
    private static final int LIMIT_MAX = 100;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private ChargingEngine mEngine;
    private SharedPreferences mPrefs;
    private boolean mBypassSupported;
    private Intent mLastBattery;

    private ChargingStatusPreference mStatus;
    private ChargingSwitchPreference mBypassNow;
    private ChargingSwitchPreference mControl;
    private ChargingChoicePreference mModeLimit;
    private ChargingChoicePreference mModeAuto;
    private ChargingChoicePreference mModeCustom;
    private ChargingLevelPreference mLimit;
    private ChargingTimePreference mStartTime;
    private ChargingTimePreference mTargetTime;
    private ChargingSpeedPreference mSpeed;

    // Keeps the status card live while the page is open.
    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mLastBattery = intent;
            updateStatus();
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.charging_settings, rootKey);
        final Context c = requireContext();
        mEngine = new ChargingEngine(c);
        mPrefs = BypassService.prefs(c);
        mBypassSupported = ChargerNode.isBypassSupported();

        mStatus = findPreference("charging_status");
        mBypassNow = findPreference("bypass_now");
        mControl = findPreference("control_enabled");
        mModeLimit = findPreference("mode_limit");
        mModeAuto = findPreference("mode_auto");
        mModeCustom = findPreference("mode_custom");
        mLimit = findPreference("limit");
        mStartTime = findPreference("start_time");
        mTargetTime = findPreference("target_time");
        mSpeed = findPreference("speed");

        // Only show what this phone can do.
        if (!mBypassSupported) {
            removeCategory("category_bypass");
            mModeLimit.setTitle(R.string.mode_limit_title_no_bypass);
            mModeLimit.setSummary(R.string.mode_limit_summary_no_bypass);
            final Preference footer = findPreference("charging_footer");
            if (footer != null) footer.setTitle(R.string.charging_footer_no_bypass);
        }
        if (!mEngine.isControlSupported()) {
            removeCategory("category_control");
        } else if (!mEngine.isLimitSupported()) {
            mModeLimit.setVisible(false);
        }
        if (!mEngine.isSpeedSupported()) {
            removeCategory("category_speed");
        } else {
            final int[] speeds = mEngine.getSpeeds();
            final CharSequence[] labels = new CharSequence[speeds.length];
            for (int i = 0; i < speeds.length; i++) {
                labels[i] = getString(speeds[i] == ChargingEngine.SPEED_SLOW ? R.string.speed_slow
                        : speeds[i] == ChargingEngine.SPEED_SUPER_FAST ? R.string.speed_super_fast
                        : R.string.speed_fast);
            }
            mSpeed.setOptions(speeds, labels);
        }

        mLimit.setMin(LIMIT_MIN);
        mLimit.setMax(LIMIT_MAX);

        mBypassNow.setOnPreferenceChangeListener((p, value) -> {
            setBypassNow((Boolean) value);
            return true;
        });
        mControl.setOnPreferenceChangeListener((p, value) -> {
            mEngine.setEnabled((Boolean) value);
            refreshSoon();
            return true;
        });
        mModeLimit.setOnPreferenceClickListener(p -> selectMode(ChargingEngine.MODE_LIMIT));
        mModeAuto.setOnPreferenceClickListener(p -> selectMode(ChargingEngine.MODE_AUTO));
        mModeCustom.setOnPreferenceClickListener(p -> selectMode(ChargingEngine.MODE_MANUAL));
        mLimit.setOnPreferenceChangeListener((p, value) -> {
            final int limit = (Integer) value;
            mEngine.setLimit(limit);
            mLimit.setSummary(getString(R.string.limit_summary, limit));
            refreshSoon();
            return true;
        });
        mStartTime.setOnPreferenceClickListener(p -> pickTime(true));
        mTargetTime.setOnPreferenceClickListener(p -> pickTime(false));
        mSpeed.setOnSpeedSelectedListener(mode -> {
            mEngine.setSpeed(mode);
            refreshSoon();
        });
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // The rounded rows and the gaps between them separate items; no dividers.
        setDivider(null);
    }

    @Override
    public void onResume() {
        super.onResume();
        final Intent sticky = requireContext().registerReceiver(mBatteryReceiver,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_EXPORTED);
        if (sticky != null) mLastBattery = sticky;
        BypassService.refresh(requireContext());
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacksAndMessages(null);
        requireContext().unregisterReceiver(mBatteryReceiver);
    }

    private void removeCategory(String key) {
        final PreferenceCategory category = findPreference(key);
        if (category != null) getPreferenceScreen().removePreference(category);
    }

    // ---- actions --------------------------------------------------------------------

    private void setBypassNow(boolean on) {
        final Context c = requireContext();
        if (on) {
            final boolean controlWasOn = mEngine.isControlSupported() && mEngine.isEnabled();
            mPrefs.edit().putBoolean(BypassService.PREF_BYPASS_NOW, true)
                    .putBoolean(BypassService.PREF_RESUME_CONTROL, controlWasOn).commit();
            if (controlWasOn) mEngine.setEnabled(false);
        } else {
            final boolean resume = mPrefs.getBoolean(BypassService.PREF_RESUME_CONTROL, false);
            mPrefs.edit().putBoolean(BypassService.PREF_BYPASS_NOW, false)
                    .remove(BypassService.PREF_RESUME_CONTROL).commit();
            ChargerNode.setPaused(false);
            if (resume) mEngine.setEnabled(true);
        }
        BypassService.refresh(c);
        refreshSoon();
    }

    private boolean selectMode(int mode) {
        if (mEngine.getMode() != mode) {
            mEngine.setMode(mode);
        }
        refreshSoon();
        return true;
    }

    private boolean pickTime(boolean start) {
        final int seconds = start ? mEngine.getStartTime() : mEngine.getTargetTime();
        new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
            final int value = hour * 3600 + minute * 60;
            if (start) {
                mEngine.setStartTime(value);
            } else {
                mEngine.setTargetTime(value);
            }
            refreshSoon();
        }, seconds / 3600, (seconds % 3600) / 60,
                DateFormat.is24HourFormat(requireContext())).show();
        return true;
    }

    /** Now, and again once the system and the kernel have acted on the change. */
    private void refreshSoon() {
        refresh();
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(this::refresh, 1500);
    }

    // ---- state ----------------------------------------------------------------------

    private void refresh() {
        if (!isAdded()) return;
        final boolean bypassNow = mBypassSupported && BypassService.isBypassNow(requireContext());
        if (mBypassNow != null) mBypassNow.setChecked(bypassNow);

        if (mEngine.isControlSupported()) {
            final boolean enabled = mEngine.isEnabled();
            int mode = mEngine.getMode();
            if (mode == ChargingEngine.MODE_LIMIT && !mEngine.isLimitSupported()) {
                mode = ChargingEngine.MODE_AUTO;
            }
            final boolean active = enabled && !bypassNow;

            mControl.setChecked(enabled);
            mControl.setEnabled(!bypassNow);
            mControl.setSummary(bypassNow ? getString(R.string.control_summary_paused)
                    : enabled ? modeTitle(mode) : getString(R.string.control_summary_off));

            mModeLimit.setChecked(mode == ChargingEngine.MODE_LIMIT);
            mModeAuto.setChecked(mode == ChargingEngine.MODE_AUTO);
            mModeCustom.setChecked(mode == ChargingEngine.MODE_MANUAL);
            for (Preference p : new Preference[] { mModeLimit, mModeAuto, mModeCustom,
                    mLimit, mStartTime, mTargetTime }) {
                p.setEnabled(active);
            }

            final int limit = Math.max(LIMIT_MIN, Math.min(LIMIT_MAX, mEngine.getLimit()));
            mLimit.setVisible(mode == ChargingEngine.MODE_LIMIT);
            mLimit.setValue(limit);
            mLimit.setSummary(getString(R.string.limit_summary, limit));

            final boolean custom = mode == ChargingEngine.MODE_MANUAL;
            mStartTime.setVisible(custom);
            mTargetTime.setVisible(custom);
            mStartTime.setSummary(getString(R.string.start_time_summary,
                    formatSecondOfDay(mEngine.getStartTime())));
            mTargetTime.setSummary(getString(R.string.target_time_summary,
                    formatSecondOfDay(mEngine.getTargetTime())));

            layoutGroup(mControl, mModeLimit, mModeAuto, mModeCustom, mLimit,
                    mStartTime, mTargetTime);
        }

        if (mEngine.isSpeedSupported()) {
            mSpeed.setSelectedMode(mEngine.getSpeed());
        }
        updateStatus();
    }

    private String modeTitle(int mode) {
        switch (mode) {
            case ChargingEngine.MODE_LIMIT:
                return getString(mBypassSupported ? R.string.mode_limit_title
                        : R.string.mode_limit_title_no_bypass) + " · "
                        + getString(R.string.percent, mEngine.getLimit());
            case ChargingEngine.MODE_MANUAL:
                return getString(R.string.mode_custom_title);
            default:
                return getString(R.string.mode_auto_title);
        }
    }

    /** Rows that share one rounded card: big outer corners, small corners where they meet. */
    private static void layoutGroup(Preference... rows) {
        final List<Preference> visible = new ArrayList<>();
        for (Preference p : rows) {
            if (p != null && p.isVisible()) visible.add(p);
        }
        final int n = visible.size();
        for (int i = 0; i < n; i++) {
            final int position = n == 1 ? ExynosStyle.GROUP_SINGLE
                    : i == 0 ? ExynosStyle.GROUP_TOP
                    : i == n - 1 ? ExynosStyle.GROUP_BOTTOM
                    : ExynosStyle.GROUP_MIDDLE;
            ((ExynosRows.GroupedRow) visible.get(i)).setGroupPosition(position);
        }
    }

    /**
     * The status card shows what is really happening: "bypass" only when the charger node
     * reports charging paused with a cable in (the battery status alone keeps saying
     * "Charging" on these phones while bypassing).
     */
    private void updateStatus() {
        if (mStatus == null || mLastBattery == null || !isAdded()) return;
        final Context c = requireContext();

        final int level = mLastBattery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = mLastBattery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        final int pct = scale > 0 && level >= 0 ? level * 100 / scale : level;
        final int plugged = mLastBattery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        final int status = mLastBattery.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN);

        final boolean bypassNow = mBypassSupported && BypassService.isBypassNow(c);
        final boolean control = !bypassNow && mEngine.isControlSupported() && mEngine.isEnabled();
        final int mode = mEngine.getMode();
        final int limit = mEngine.getLimit();
        final String target = formatSecondOfDay(mEngine.getTargetTime());

        if (plugged == 0) {
            final String summary;
            if (bypassNow) {
                summary = getString(R.string.status_battery_bypass);
            } else if (control && mode == ChargingEngine.MODE_LIMIT) {
                summary = getString(R.string.status_battery_limit, limit);
            } else if (control && mode == ChargingEngine.MODE_MANUAL) {
                summary = getString(R.string.status_battery_custom, target);
            } else if (control) {
                summary = getString(R.string.status_battery_auto);
            } else {
                summary = getString(R.string.status_battery_normal);
            }
            mStatus.setStatus(BatteryAnimationView.State.DISCHARGING, pct,
                    pct >= 0 && pct <= LOW_LEVEL, getString(R.string.chip_battery),
                    getString(R.string.status_battery_title), summary);
            return;
        }

        if (Boolean.TRUE.equals(ChargerNode.isPaused())) {
            final String summary;
            if (control && mode == ChargingEngine.MODE_LIMIT) {
                summary = getString(R.string.status_bypass_limit, pct);
            } else if (control && mode == ChargingEngine.MODE_MANUAL) {
                summary = getString(R.string.status_bypass_schedule, pct, target);
            } else if (control) {
                summary = getString(R.string.status_bypass_alarm, pct);
            } else {
                summary = getString(R.string.status_bypass_now, pct);
            }
            mStatus.setStatus(BatteryAnimationView.State.BYPASS, pct, false,
                    getString(R.string.chip_bypass), getString(R.string.status_bypass_title),
                    summary);
            return;
        }

        // Plugged in and not held: animate only what the battery really does.
        final boolean full = status == BatteryManager.BATTERY_STATUS_FULL;
        final BatteryAnimationView.State state = full ? BatteryAnimationView.State.FULL
                : status == BatteryManager.BATTERY_STATUS_CHARGING
                        ? BatteryAnimationView.State.CHARGING
                        : BatteryAnimationView.State.DISCHARGING;

        if (bypassNow) {
            // The service applies it with the next battery update.
            mStatus.setStatus(state, pct, false, getString(R.string.chip_starting),
                    getString(R.string.status_starting_title),
                    getString(R.string.status_starting_summary));
            return;
        }

        final String chip;
        if (full) {
            chip = getString(R.string.chip_full);
        } else if (mEngine.isSpeedSupported()
                && mEngine.getSpeed() == ChargingEngine.SPEED_SLOW) {
            chip = getString(R.string.chip_slow);
        } else if (mEngine.isSpeedSupported()
                && plugged == BatteryManager.BATTERY_PLUGGED_AC) {
            // A wall charger in fast mode; a computer's USB port never fast charges.
            chip = getString(R.string.chip_fast);
        } else {
            chip = getString(R.string.chip_charging);
        }

        final String summary;
        if (full) {
            summary = getString(R.string.status_full_summary);
        } else if (control && mode == ChargingEngine.MODE_LIMIT) {
            summary = getString(mBypassSupported ? R.string.status_charging_limit
                    : R.string.status_charging_limit_no_bypass, limit);
        } else if (control && mode == ChargingEngine.MODE_MANUAL) {
            summary = getString(R.string.status_charging_custom, target);
        } else if (control) {
            final String alarm = nextAlarm(c);
            summary = alarm != null ? getString(R.string.status_charging_auto, alarm)
                    : getString(R.string.status_charging_no_alarm);
        } else {
            summary = getString(R.string.status_charging_normal);
        }
        mStatus.setStatus(state, pct, false, chip, getString(full
                ? R.string.status_full_title : R.string.status_charging_title), summary);
    }

    // ---- formatting -----------------------------------------------------------------

    /** A time of day in the user's 12/24-hour format. */
    private String formatSecondOfDay(int seconds) {
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, seconds / 3600);
        cal.set(Calendar.MINUTE, (seconds % 3600) / 60);
        return DateFormat.getTimeFormat(requireContext()).format(cal.getTime());
    }

    private static String nextAlarm(Context c) {
        final AlarmManager am = c.getSystemService(AlarmManager.class);
        final AlarmManager.AlarmClockInfo info = am != null ? am.getNextAlarmClock() : null;
        return info == null ? null
                : DateFormat.getTimeFormat(c).format(new Date(info.getTriggerTime()));
    }
}
