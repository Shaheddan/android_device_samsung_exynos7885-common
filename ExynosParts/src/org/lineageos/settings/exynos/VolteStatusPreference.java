/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/** A plain information row drawn as part of a grouped card, like the charging rows. */
public class VolteStatusPreference extends Preference implements ExynosRows.GroupedRow {

    private int mGroupPosition = ExynosStyle.GROUP_SINGLE;

    public VolteStatusPreference(Context context, AttributeSet attrs) {
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
        ExynosRows.styleRow(getContext(), holder, mGroupPosition, false, false);
    }
}
