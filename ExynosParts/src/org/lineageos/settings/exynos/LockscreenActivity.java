/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/** Settings > Display > Lock screen styles (CaveArt). */
public class LockscreenActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG_LOCKSCREEN = "lockscreen";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    new LockscreenSettingsFragment(), TAG_LOCKSCREEN).commit();
        }
    }
}
