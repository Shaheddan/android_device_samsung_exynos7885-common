/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The battery driver's hmt_ta_charge node: the bypass switch on these phones.
 *
 * Writing 0 sets SEC_BAT_CURRENT_EVENT_CHARGE_DISABLE, so every "charge" request becomes
 * SEC_BAT_CHG_MODE_CHARGING_OFF: the charger (S2MU106 on the A20/A20e/A30/A30s/A40,
 * S2MU005 on the A10) drops to buck-only mode, keeps powering the phone and leaves the
 * battery alone (the same state as "Full" on a charger). Measured on the A30: battery
 * current went from about +420 mA to about 0 mA with USB still online, and back on
 * writing 1. The kernel clears it by itself when the cable is unplugged. EurekaV3 gives
 * the A10's micro-USB build the same behaviour (Samsung's used the node for Gear VR OTG).
 *
 * Charging control (the Lineage health HAL) drives the same node; this app only writes it
 * for "Bypass now", while charging control is switched off.
 */
final class ChargerNode {

    private static final String TAG = "ExynosCharging";

    // The real sysfs path first: system_app is not allowed to follow the
    // /sys/class/power_supply symlinks.
    private static final String[] PATHS = {
        "/sys/devices/platform/battery/power_supply/battery/hmt_ta_charge",
        "/sys/class/power_supply/battery/hmt_ta_charge",
    };

    private ChargerNode() {}

    private static String path() {
        for (String p : PATHS) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    static boolean isBypassSupported() {
        return path() != null;
    }

    /** @return true when charging is paused (bypass), false when allowed, null if unknown */
    static Boolean isPaused() {
        if (!isBypassSupported()) return null;
        try {
            final String v = new String(Files.readAllBytes(new File(path()).toPath()),
                    StandardCharsets.US_ASCII).trim();
            if ("0".equals(v)) return true;
            if ("1".equals(v)) return false;
        } catch (Exception e) {
            Log.w(TAG, "Cannot read the bypass node", e);
        }
        return null;
    }

    /** Pause (true) or allow (false) charging. Only takes effect with a cable attached. */
    static boolean setPaused(boolean paused) {
        if (!isBypassSupported()) return false;
        try (FileOutputStream out = new FileOutputStream(path())) {
            out.write((paused ? "0" : "1").getBytes(StandardCharsets.US_ASCII));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Cannot write the bypass node", e);
            return false;
        }
    }
}
