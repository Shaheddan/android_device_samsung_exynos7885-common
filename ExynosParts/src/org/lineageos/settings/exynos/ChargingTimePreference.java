/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * A custom schedule time (start, or full charge by) as a grouped row. Tapping it is
 * handled by the fragment, which opens the time picker and saves the time in charging
 * control; this row only shows it.
 */
public class ChargingTimePreference extends Preference implements ExynosRows.GroupedRow {

    private int mGroupPosition = ExynosStyle.GROUP_MIDDLE;

    public ChargingTimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPersistent(false);
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
