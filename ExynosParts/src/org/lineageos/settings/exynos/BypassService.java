/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * Keeps "Bypass now" applied: while it is on and a charger is plugged in, charging stays
 * paused at the current level.
 *
 * The kernel clears the pause on every unplug, so it is set again on each plug-in and
 * re-checked on every battery update while the mode is on. That re-check also wins the
 * race with charging control: switching charging control off makes the system re-enable
 * charging once, shortly after, and the next battery update pauses it again.
 */
public class BypassService extends Service {

    private static final String TAG = "ExynosCharging";

    static final String PREF_BYPASS_NOW = "bypass_now";
    // Charging control was on when bypass now started, so it is switched back on after.
    static final String PREF_RESUME_CONTROL = "resume_control";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mWatchingBattery;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            apply(intent);
        }
    };

    static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext()
                .getSharedPreferences("charging", Context.MODE_PRIVATE);
    }

    static boolean isBypassNow(Context context) {
        return ChargerNode.isBypassSupported() && prefs(context).getBoolean(PREF_BYPASS_NOW, false);
    }

    /** Re-evaluates now and once more after charging control has had time to react. */
    static void refresh(Context context) {
        context.startService(new Intent(context, BypassService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final IntentFilter power = new IntentFilter();
        power.addAction(Intent.ACTION_POWER_CONNECTED);
        power.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mReceiver, power, Context.RECEIVER_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        apply(null);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(() -> apply(null), 1500);
        return START_STICKY;
    }

    private void apply(Intent event) {
        final boolean on = isBypassNow(this);
        watchBattery(on);
        if (!on) return;

        final Intent battery = event != null && Intent.ACTION_BATTERY_CHANGED.equals(
                event.getAction()) ? event : registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        final boolean plugged = battery != null
                && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        if (event != null && Intent.ACTION_POWER_DISCONNECTED.equals(event.getAction())) {
            return;     // the kernel has already given charging back
        }
        if (plugged && !Boolean.TRUE.equals(ChargerNode.isPaused())) {
            if (ChargerNode.setPaused(true)) Log.i(TAG, "Bypass now: charging paused");
        }
    }

    /** Battery updates are only needed while bypass now is on. */
    private void watchBattery(boolean watch) {
        if (watch == mWatchingBattery) return;
        mWatchingBattery = watch;
        if (watch) {
            registerReceiver(mBatteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    Context.RECEIVER_EXPORTED);
        } else {
            unregisterReceiver(mBatteryReceiver);
        }
    }

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            apply(intent);
        }
    };

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        unregisterReceiver(mReceiver);
        if (mWatchingBattery) unregisterReceiver(mBatteryReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
