/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.widget.RadioButton;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * One charging control mode, as a grouped row with a radio button. The chosen row also
 * takes the secondary container colour. The row stores nothing; the fragment reads and
 * writes the mode in the system's charging control settings.
 */
public class ChargingChoicePreference extends Preference implements ExynosRows.GroupedRow {

    private int mGroupPosition = ExynosStyle.GROUP_MIDDLE;
    private boolean mChecked;

    public ChargingChoicePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWidgetLayoutResource(R.layout.charging_radio);
        setPersistent(false);
    }

    @Override
    public void setGroupPosition(int position) {
        if (position != mGroupPosition) {
            mGroupPosition = position;
            notifyChanged();
        }
    }

    public void setChecked(boolean checked) {
        if (checked != mChecked) {
            mChecked = checked;
            notifyChanged();
        }
    }

    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final Context c = getContext();
        ExynosRows.styleRow(c, holder, mGroupPosition, true, mChecked && isEnabled());

        final RadioButton radio = (RadioButton) holder.findViewById(R.id.choice_radio);
        if (radio != null) {
            radio.setChecked(mChecked);
            radio.setButtonTintList(ColorStateList.valueOf(mChecked
                    ? ExynosStyle.primary(c) : ExynosStyle.onSurfaceVariant(c)));
        }
        // Spoken as a radio button, since the RadioButton itself is hidden from TalkBack.
        holder.itemView.setAccessibilityDelegate(new android.view.View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(android.view.View host,
                    android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(RadioButton.class.getName());
                info.setCheckable(true);
                info.setChecked(mChecked);
            }
        });
    }
}
