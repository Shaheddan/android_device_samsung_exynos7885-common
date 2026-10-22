/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Charging speed as a Material 3 Expressive connected button group inside a grouped row:
 * one button per speed the charger supports, the chosen one filled with the primary
 * colour, fully rounded and ticked. Stores nothing; the fragment applies the choice
 * through the fast charge service, exactly like the stock "Charging speed" list did.
 */
public class ChargingSpeedPreference extends Preference implements ExynosRows.GroupedRow {

    /** Called with the FastChargeMode value of the button the user tapped. */
    public interface OnSpeedSelectedListener {
        void onSpeedSelected(int mode);
    }

    private int mGroupPosition = ExynosStyle.GROUP_SINGLE;
    private int[] mModes = new int[0];
    private CharSequence[] mLabels = new CharSequence[0];
    private int mSelectedMode = -1;
    private OnSpeedSelectedListener mListener;

    public ChargingSpeedPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.charging_speed_row);
        setSelectable(false);
        setPersistent(false);
    }

    @Override
    public void setGroupPosition(int position) {
        if (position != mGroupPosition) {
            mGroupPosition = position;
            notifyChanged();
        }
    }

    /** @param modes FastChargeMode values, in the order the buttons appear */
    public void setOptions(int[] modes, CharSequence[] labels) {
        mModes = modes;
        mLabels = labels;
        notifyChanged();
    }

    public void setSelectedMode(int mode) {
        if (mode != mSelectedMode) {
            mSelectedMode = mode;
            notifyChanged();
        }
    }

    public void setOnSpeedSelectedListener(OnSpeedSelectedListener listener) {
        mListener = listener;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final Context c = getContext();

        holder.itemView.setBackground(
                ExynosStyle.groupedRowBackground(c, mGroupPosition, false, false));
        final int side = Math.round(ExynosStyle.dp(c, 32));
        final int vertical = Math.round(ExynosStyle.dp(c, 16));
        holder.itemView.setPaddingRelative(side, vertical, side, vertical);
        ExynosRows.styleIcon(c, holder.findViewById(android.R.id.icon));

        final LinearLayout group = (LinearLayout) holder.findViewById(R.id.speed_buttons);
        group.removeAllViews();
        final int count = Math.min(mModes.length, mLabels.length);
        for (int i = 0; i < count; i++) {
            group.addView(makeButton(c, i, count));
        }
    }

    private TextView makeButton(Context c, int index, int count) {
        final int mode = mModes[index];
        final boolean selected = mode == mSelectedMode;
        final boolean enabled = isEnabled();

        final TextView button = new TextView(c);
        button.setText(mLabels[index]);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(selected ? ExynosStyle.onPrimary(c) : ExynosStyle.onSurfaceVariant(c));
        button.setBackground(buttonShape(c, index, count, selected));
        button.setSelected(selected);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.38f);

        if (selected) {
            final Drawable check = c.getDrawable(R.drawable.ic_check).mutate();
            final int size = Math.round(ExynosStyle.dp(c, 18));
            check.setBounds(0, 0, size, size);
            check.setTint(ExynosStyle.onPrimary(c));
            button.setCompoundDrawablesRelative(check, null, null, null);
            button.setCompoundDrawablePadding(Math.round(ExynosStyle.dp(c, 6)));
        }

        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        if (index > 0) lp.setMarginStart(Math.round(ExynosStyle.dp(c, 2)));
        button.setLayoutParams(lp);

        button.setOnClickListener(v -> {
            if (!isEnabled() || mode == mSelectedMode) return;
            mSelectedMode = mode;
            notifyChanged();
            if (mListener != null) mListener.onSpeedSelected(mode);
        });
        return button;
    }

    /**
     * Connected group shape: fully round on the outside of the group, small corners where
     * buttons meet. The selected button morphs to fully round, as in Material 3 Expressive.
     */
    private static Drawable buttonShape(Context c, int index, int count, boolean selected) {
        final float full = ExynosStyle.dp(c, 24);
        final float inner = ExynosStyle.dp(c, 8);
        final boolean first = index == 0;
        final boolean last = index == count - 1;
        final boolean rtl = c.getResources().getConfiguration().getLayoutDirection()
                == android.view.View.LAYOUT_DIRECTION_RTL;
        float start = selected || first ? full : inner;
        float end = selected || last ? full : inner;
        final float left = rtl ? end : start;
        final float right = rtl ? start : end;

        final GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadii(new float[] { left, left, right, right, right, right, left, left });
        shape.setColor(selected ? ExynosStyle.primary(c) : ExynosStyle.surfaceContainerHighest(c));
        return shape;
    }
}
