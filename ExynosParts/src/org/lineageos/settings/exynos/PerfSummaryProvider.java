/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * The summary under Settings > System > Performance (the active profile's name). Settings
 * asks for it through the tile's com.android.settings.summary_uri meta-data.
 */
public class PerfSummaryProvider extends ContentProvider {

    private static final String METHOD_GET_DYNAMIC_SUMMARY = "getDynamicSummary";
    private static final String KEY_SUMMARY = "com.android.settings.summary";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String uri, Bundle extras) {
        if (!METHOD_GET_DYNAMIC_SUMMARY.equals(method)) return null;
        final PerfProfiles.Profile profile = PerfProfiles.soc().get(PerfProfiles.active());
        final Bundle bundle = new Bundle();
        bundle.putString(KEY_SUMMARY, getContext().getString(profile.title));
        return bundle;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
