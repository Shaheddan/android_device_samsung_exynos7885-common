/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;

import androidx.preference.PreferenceViewHolder;

/** Shared styling for the grouped rows on the Charging page (same look as KiwiParts). */
final class ExynosRows {

    /** A row that is drawn as part of a rounded group and needs to know its place in it. */
    interface GroupedRow {
        void setGroupPosition(int position);
    }

    private ExynosRows() {}

    /**
     * Turns a stock SettingsLib row into a grouped card row. Only background, padding and
     * the icon are touched, so the row keeps its own widgets (switch, seek bar, radio).
     */
    static void styleRow(Context c, PreferenceViewHolder holder, int position,
            boolean clickable, boolean selected) {
        final View row = holder.itemView;
        row.setBackground(ExynosStyle.groupedRowBackground(c, position, clickable, selected));
        // 16dp card margin + 16dp inside the card on both sides.
        final int side = Math.round(ExynosStyle.dp(c, 32));
        final int vertical = Math.round(ExynosStyle.dp(c, 6));
        row.setPaddingRelative(side, vertical, side, vertical);
        styleIcon(c, holder.findViewById(android.R.id.icon));
    }

    /** Icon in a tonal circle, tinted to match it. */
    static void styleIcon(Context c, View icon) {
        if (icon instanceof ImageView) {
            final int pad = Math.round(ExynosStyle.dp(c, 8));
            icon.setBackground(ExynosStyle.iconCircle(c));
            icon.setPadding(pad, pad, pad, pad);
            ((ImageView) icon).setImageTintList(
                    ColorStateList.valueOf(ExynosStyle.onPrimaryContainer(c)));
        }
    }

    /** First SeekBar under a row, found by type so no library resource id is needed. */
    static SeekBar findSeekBar(View view) {
        if (view instanceof SeekBar) return (SeekBar) view;
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                final SeekBar found = findSeekBar(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
