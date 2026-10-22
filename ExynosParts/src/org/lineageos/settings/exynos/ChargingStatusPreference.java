/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Material You hero card at the top of the Charging page: an animated
 * battery, a state chip, the level and what the charger and battery are doing right now
 * Display only; it changes no setting.
 */
public class ChargingStatusPreference extends Preference {

    private BatteryAnimationView.State mState = BatteryAnimationView.State.DISCHARGING;
    private int mPercent = -1;
    private boolean mLow;
    private CharSequence mChip = "";
    private CharSequence mStatusTitle = "";
    private CharSequence mStatusSummary = "";

    public ChargingStatusPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.charging_status_card);
        setSelectable(false);
        setPersistent(false);
    }

    /**
     * @param state   drives the battery animation and the card colour
     * @param percent battery level 0..100, or -1 when not known yet
     * @param low     draw the charge in the error colour (low battery, on battery)
     */
    public void setStatus(BatteryAnimationView.State state, int percent, boolean low,
            CharSequence chip, CharSequence title, CharSequence summary) {
        mState = state;
        mPercent = percent;
        mLow = low;
        mChip = chip;
        mStatusTitle = title;
        mStatusSummary = summary;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final Context c = getContext();
        final boolean active = mState == BatteryAnimationView.State.BYPASS;

        // Primary container while bypass holds the battery, secondary container otherwise.
        final int cardColor = active ? ExynosStyle.primaryContainer(c)
                : ExynosStyle.secondaryContainer(c);
        final int onCard = active ? ExynosStyle.onPrimaryContainer(c)
                : ExynosStyle.onSecondaryContainer(c);
        final int track = active ? ExynosStyle.primaryContainerHigh(c)
                : ExynosStyle.secondaryContainerHigh(c);
        final int fill = mLow ? ExynosStyle.error(c) : ExynosStyle.primary(c);
        final int glyph = mLow ? ExynosStyle.onError(c) : ExynosStyle.onPrimary(c);
        // The back wave is the fill colour at half strength over the empty track.
        final int wave = (fill & 0x00FFFFFF) | 0x80000000;

        final GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(ExynosStyle.dp(c, 32));
        card.setColor(cardColor);
        holder.findViewById(R.id.status_card).setBackground(card);

        final BatteryAnimationView battery =
                (BatteryAnimationView) holder.findViewById(R.id.battery_view);
        battery.setColors(track, fill, wave, glyph, ExynosStyle.primary(c));
        battery.setBattery(mState, mPercent);

        final TextView chip = (TextView) holder.findViewById(R.id.status_chip);
        final GradientDrawable chipBg = new GradientDrawable();
        chipBg.setCornerRadius(ExynosStyle.dp(c, 100));
        chipBg.setColor(fill);
        chip.setBackground(chipBg);
        chip.setTextColor(glyph);
        chip.setText(mChip);
        chip.setVisibility(mChip.length() > 0 ? View.VISIBLE : View.GONE);

        final TextView level = (TextView) holder.findViewById(R.id.status_level);
        level.setText(mPercent >= 0 ? c.getString(R.string.percent, mPercent) : "");
        level.setTextColor(onCard);

        final TextView title = (TextView) holder.findViewById(R.id.status_title);
        title.setText(mStatusTitle);
        title.setTextColor(onCard);

        final TextView summary = (TextView) holder.findViewById(R.id.status_summary);
        summary.setText(mStatusSummary);
        summary.setTextColor(onCard);
        summary.setAlpha(0.8f);

        // One spoken summary for the whole card.
        holder.itemView.setContentDescription(mChip + ". " + level.getText() + ". "
                + mStatusTitle + ". " + mStatusSummary);
    }
}
