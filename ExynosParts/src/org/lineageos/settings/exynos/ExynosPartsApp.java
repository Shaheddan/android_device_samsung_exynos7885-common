/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/** The app process is persistent, so it is up for the whole session. */
public class ExynosPartsApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // A clean shutdown or restart is not a crash: keep an overclock profile that is
        // still on trial. ACTION_SHUTDOWN only reaches receivers registered at runtime.
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                PerfProfiles.confirmTrial();
            }
        }, new IntentFilter(Intent.ACTION_SHUTDOWN), Context.RECEIVER_EXPORTED);
    }
}
