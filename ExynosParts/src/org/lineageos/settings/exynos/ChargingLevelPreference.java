/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.SeekBar;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SeekBarPreference;

/**
 * The charge limit as a grouped row with the KiwiParts Material 3 Expressive slider: a
 * thick rounded track and a slim vertical handle with a gap around it. Not persisted;
 * the fragment hands the value to charging control when the finger lifts.
 */
public class ChargingLevelPreference extends SeekBarPreference implements ExynosRows.GroupedRow {

    private int mGroupPosition = ExynosStyle.GROUP_BOTTOM;

    public ChargingLevelPreference(Context context, AttributeSet attrs) {
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
        final Context c = getContext();
        ExynosRows.styleRow(c, holder, mGroupPosition, false, false);

        final SeekBar seekBar = ExynosRows.findSeekBar(holder.itemView);
        if (seekBar == null) return;

        final float radius = ExynosStyle.dp(c, 8);
        final GradientDrawable track = new GradientDrawable();
        track.setCornerRadius(radius);
        track.setColor(ExynosStyle.secondaryContainer(c));

        final GradientDrawable active = new GradientDrawable();
        active.setCornerRadius(radius);
        active.setColor(ExynosStyle.primary(c));
        final ClipDrawable progress = new ClipDrawable(active, Gravity.START,
                ClipDrawable.HORIZONTAL);

        final LayerDrawable layers = new LayerDrawable(new Drawable[] { track, progress });
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        seekBar.setProgressDrawable(layers);

        final int height = Math.round(ExynosStyle.dp(c, 16));
        seekBar.setMinHeight(height);
        seekBar.setMaxHeight(height);

        // Slim handle; the transparent inset is cut out of the track (split track),
        // which gives the expressive gap on both sides of the handle.
        final GradientDrawable handle = new GradientDrawable();
        handle.setCornerRadius(ExynosStyle.dp(c, 2));
        handle.setColor(ExynosStyle.primary(c));
        handle.setSize(Math.round(ExynosStyle.dp(c, 4)), Math.round(ExynosStyle.dp(c, 36)));
        final int gap = Math.round(ExynosStyle.dp(c, 5));
        seekBar.setThumb(new InsetDrawable(handle, gap, 0, gap, 0));
        seekBar.setSplitTrack(true);
        seekBar.setAlpha(isEnabled() ? 1f : 0.38f);
    }
}
