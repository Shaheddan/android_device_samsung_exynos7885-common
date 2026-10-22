/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

/**
 * A switch drawn as a grouped row (rounded card, icon in a tonal circle). Still an
 * androidx SwitchPreference, not SwitchPreferenceCompat: the SettingsLib preference theme
 * styles SwitchPreference, and the Compat variant rendered without a switch on kiwi.
 */
public class ChargingSwitchPreference extends SwitchPreference
        implements ExynosRows.GroupedRow {

    private int mGroupPosition = ExynosStyle.GROUP_SINGLE;

    public ChargingSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setGroupPosition(int position) {
        if (position != mGroupPosition) {
            mGroupPosition = position;
            notifyChanged();
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ExynosRows.styleRow(getContext(), holder, mGroupPosition, true, false);
    }
}
