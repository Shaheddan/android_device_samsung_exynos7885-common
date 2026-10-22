/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.util.Log;

import com.android.internal.lineage.health.HealthInterface;

/**
 * The system's charging control and fast charge service (LineageOS health, built into
 * PixelOS's framework), which the stock "Charging control" and "Charging speed" entries
 * used. This page drives the same settings, so the schedules, the limit, the
 * notification and the HAL behave exactly as they did there; only the backend node of
 * the HAL changed (hmt_ta_charge, true bypass).
 *
 * Every call is guarded: if the service is missing the page simply hides those groups.
 */
final class ChargingEngine {

    private static final String TAG = "ExynosCharging";

    static final int MODE_AUTO = HealthInterface.MODE_AUTO;
    static final int MODE_MANUAL = HealthInterface.MODE_MANUAL;
    static final int MODE_LIMIT = HealthInterface.MODE_LIMIT;

    // FastChargeMode values from vendor.lineage.health.
    static final int SPEED_SLOW = 1;
    static final int SPEED_FAST = 2;
    static final int SPEED_SUPER_FAST = 4;

    private final HealthInterface mHealth;

    ChargingEngine(Context context) {
        HealthInterface health = null;
        try {
            health = HealthInterface.getInstance(context);
        } catch (RuntimeException e) {
            Log.w(TAG, "No health service", e);
        }
        mHealth = health;
    }

    boolean isControlSupported() {
        return mHealth != null && mHealth.isChargingControlSupported();
    }

    /** Limit mode is offered only when the HAL can toggle charging (as in stock). */
    boolean isLimitSupported() {
        return mHealth != null && mHealth.allowFineGrainedSettings();
    }

    boolean isEnabled() {
        return mHealth != null && mHealth.getEnabled();
    }

    void setEnabled(boolean enabled) {
        if (mHealth != null) mHealth.setEnabled(enabled);
    }

    /** The mode, falling back to automatic for "none" or anything unknown. */
    int getMode() {
        final int mode = mHealth != null ? mHealth.getMode() : MODE_AUTO;
        return mode == MODE_MANUAL || mode == MODE_LIMIT ? mode : MODE_AUTO;
    }

    void setMode(int mode) {
        if (mHealth != null) mHealth.setMode(mode);
    }

    int getLimit() {
        return mHealth != null ? mHealth.getLimit() : 80;
    }

    void setLimit(int limit) {
        if (mHealth != null) mHealth.setLimit(limit);
    }

    /** Seconds after midnight. */
    int getStartTime() {
        return mHealth != null ? mHealth.getStartTime() : 22 * 3600;
    }

    void setStartTime(int secondOfDay) {
        if (mHealth != null) mHealth.setStartTime(secondOfDay);
    }

    /** Seconds after midnight. */
    int getTargetTime() {
        return mHealth != null ? mHealth.getTargetTime() : 6 * 3600;
    }

    void setTargetTime(int secondOfDay) {
        if (mHealth != null) mHealth.setTargetTime(secondOfDay);
    }

    boolean isSpeedSupported() {
        return mHealth != null && mHealth.isFastChargeSupported()
                && getSpeeds().length > 1;
    }

    int[] getSpeeds() {
        return mHealth != null ? mHealth.getSupportedFastChargeModes() : new int[0];
    }

    int getSpeed() {
        return mHealth != null ? mHealth.getFastChargeMode() : 0;
    }

    void setSpeed(int mode) {
        if (mHealth != null) mHealth.setFastChargeMode(mode);
    }
}
