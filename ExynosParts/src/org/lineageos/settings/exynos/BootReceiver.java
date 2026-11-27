/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Starts the bypass service at boot, so "Bypass now" survives a reboot, and starts the
 * 3 minute count of an overclock profile on trial.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            BypassService.refresh(context);
            PerfProfiles.onBootCompleted(context);
            LockscreenSettingsFragment.onBootCompleted();
        }
    }
}
