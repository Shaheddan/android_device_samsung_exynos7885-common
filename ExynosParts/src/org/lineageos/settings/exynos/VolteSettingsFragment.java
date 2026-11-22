/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsManager;
import android.telephony.ims.RegistrationManager;
import android.util.Log;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.List;
import java.util.TreeMap;

/**
 * VoLTE through PhhIms is experimental here: the A-series modem never hands Android the
 * P-CSCF and many SIMs are not provisioned for IMS. The switch only decides whether
 * Android treats VoLTE as available (persist.exynos.volte, mapped by init to Android's own
 * persist.dbg.volte_avail_ovr, which only vendor init may set); the 4G calling switch in
 * the SIM settings and PhhIms do the rest. The status row shows what each SIM really gets.
 */
public class VolteSettingsFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "ExynosVolte";
    static final String PROP_VOLTE = "persist.exynos.volte";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    // slot index -> one status line, so two SIMs always show in the same order
    private final TreeMap<Integer, String> mLines = new TreeMap<>();

    private ChargingSwitchPreference mSwitch;
    private VolteStatusPreference mStatus;

    static boolean isEnabled() {
        return SystemProperties.getInt(PROP_VOLTE, 0) == 1;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.volte_settings, rootKey);
        mSwitch = findPreference("volte_enabled");
        mStatus = findPreference("volte_status");
        if (mSwitch != null) {
            mSwitch.setChecked(isEnabled());
            mSwitch.setOnPreferenceChangeListener(this);
            mSwitch.setGroupPosition(ExynosStyle.GROUP_TOP);
        }
        if (mStatus != null) {
            mStatus.setGroupPosition(ExynosStyle.GROUP_BOTTOM);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final boolean on = Boolean.TRUE.equals(newValue);
        SystemProperties.set(PROP_VOLTE, on ? "1" : "0");
        askRestart();
        return true;
    }

    /** Telephony only looks at the availability switch when it sets IMS up, at boot. */
    private void askRestart() {
        final Context c = getContext();
        if (c == null) return;
        new AlertDialog.Builder(c)
                .setTitle(R.string.volte_restart_title)
                .setMessage(R.string.volte_restart_message)
                .setPositiveButton(R.string.volte_restart_now, (d, w) -> {
                    final PowerManager pm = c.getSystemService(PowerManager.class);
                    if (pm != null) pm.reboot(null);
                })
                .setNegativeButton(R.string.volte_restart_later, null)
                .show();
    }

    /** One line per active SIM: registered, registering or not registered. */
    private void updateStatus() {
        if (mStatus == null) return;
        final Context c = requireContext();
        mLines.clear();
        final SubscriptionManager sm = c.getSystemService(SubscriptionManager.class);
        final List<SubscriptionInfo> subs = sm != null ? sm.getActiveSubscriptionInfoList() : null;
        if (subs == null || subs.isEmpty()) {
            mStatus.setSummary(R.string.volte_status_no_sim);
            return;
        }
        final ImsManager ims = c.getSystemService(ImsManager.class);
        mStatus.setSummary(R.string.volte_status_checking);
        for (SubscriptionInfo info : subs) {
            final int slot = info.getSimSlotIndex();
            final CharSequence name = info.getDisplayName();
            try {
                ims.getImsMmTelManager(info.getSubscriptionId())
                        .getRegistrationState(mHandler::post, state -> {
                            mLines.put(slot, getString(R.string.volte_status_line, slot + 1,
                                    name, getString(stateText(state))));
                            showLines();
                        });
            } catch (RuntimeException e) {  // also no ImsManager at all
                // no IMS service bound for this SIM (yet)
                Log.w(TAG, "IMS state for slot " + slot + ": " + e);
                mLines.put(slot, getString(R.string.volte_status_line, slot + 1, name,
                        getString(R.string.volte_status_not_registered)));
                showLines();
            }
        }
    }

    private void showLines() {
        if (mStatus == null || !isAdded()) return;
        mStatus.setSummary(String.join("\n", mLines.values()));
    }

    private static int stateText(int state) {
        switch (state) {
            case RegistrationManager.REGISTRATION_STATE_REGISTERED:
                return R.string.volte_status_registered;
            case RegistrationManager.REGISTRATION_STATE_REGISTERING:
                return R.string.volte_status_registering;
            default:
                return R.string.volte_status_not_registered;
        }
    }
}
