/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings > System > Performance, also opened from its own app drawer icon.
 *
 * Shows the active profile on a speed dial with the live clocks, lists the profiles (the
 * overclock ones apart, under a warning) and restarts the phone to switch. Built from
 * plain views and the drawn parts in PerfWidgets: no extra libraries, colours from the
 * wallpaper (Material You), and every animation follows "Remove animations".
 */
public class PerformanceActivity extends Activity {

    private static final String TAG = "ExynosPerf";
    private static final String LAUNCHER_ALIAS = "org.lineageos.settings.exynos.PerformanceLauncher";
    private static final String PREF_FALLBACK_SEEN = "perf_fallback_seen_boot";
    private static final long LIVE_MS = 1000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final List<Row> mRows = new ArrayList<>();
    private C mC;
    private PerfProfiles.Soc mSoc;
    private String mActive, mSelected, mFallback;
    private boolean mAck;

    private FrameLayout mRoot;
    private ScrollView mScroll;
    private LinearLayout mContent;
    private View mTopBar;
    private TextView mTopTitle, mBigTitle;
    private View mFallbackCard;
    private PerfWidgets.Gauge mGauge;
    private TextView mActiveName, mActiveStatus;
    private final TextView[] mLiveValue = new TextView[3];
    private final TextView[] mLiveCap = new TextView[3];
    private final PerfWidgets.Bar[] mLiveBar = new PerfWidgets.Bar[3];
    private Switch mDrawerSwitch;

    private LinearLayout mBar;
    private PerfWidgets.LeadIcon mBarIcon;
    private TextView mBarName;
    private TextView mBarRestart;
    private View mScrim;
    private LinearLayout mSheet;
    private PerfWidgets.LeadIcon mSheetIcon;
    private TextView mSheetTitle, mSheetBody, mSheetConfirm;
    private View mAckRow;
    private CheckBox mAckBox;
    private View mOverlay;
    private boolean mBarShown, mSheetShown;
    private int mInsetBottom;

    private final Runnable mLive = new Runnable() {
        @Override
        public void run() {
            updateLive();
            mHandler.postDelayed(this, LIVE_MS);
        }
    };

    // ---- colours ---------------------------------------------------------------------

    /** Material 3 colour roles of the current wallpaper colours and theme (Android 14+). */
    private static final class C {
        final boolean night;
        final int bg, card, cardHigh, onSurface, onSurfaceVariant, outline, outlineVariant;
        final int primary, onPrimary, primaryContainer, onPrimaryContainer;
        final int secondaryContainer, onSecondaryContainer;
        final int error, onError, errorContainer, onErrorContainer;
        final int inverseSurface, inverseOnSurface;

        C(Context c) {
            night = (c.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            bg = pick(c, android.R.color.system_surface_container_light, android.R.color.system_surface_dark);
            card = pick(c, android.R.color.system_surface_container_lowest_light,
                    android.R.color.system_surface_container_dark);
            cardHigh = pick(c, android.R.color.system_surface_container_high_light,
                    android.R.color.system_surface_container_highest_dark);
            onSurface = pick(c, android.R.color.system_on_surface_light, android.R.color.system_on_surface_dark);
            onSurfaceVariant = pick(c, android.R.color.system_on_surface_variant_light,
                    android.R.color.system_on_surface_variant_dark);
            outline = pick(c, android.R.color.system_outline_light, android.R.color.system_outline_dark);
            outlineVariant = pick(c, android.R.color.system_outline_variant_light,
                    android.R.color.system_outline_variant_dark);
            primary = pick(c, android.R.color.system_primary_light, android.R.color.system_primary_dark);
            onPrimary = pick(c, android.R.color.system_on_primary_light, android.R.color.system_on_primary_dark);
            primaryContainer = pick(c, android.R.color.system_primary_container_light,
                    android.R.color.system_primary_container_dark);
            onPrimaryContainer = pick(c, android.R.color.system_on_primary_container_light,
                    android.R.color.system_on_primary_container_dark);
            secondaryContainer = pick(c, android.R.color.system_secondary_container_light,
                    android.R.color.system_secondary_container_dark);
            onSecondaryContainer = pick(c, android.R.color.system_on_secondary_container_light,
                    android.R.color.system_on_secondary_container_dark);
            error = pick(c, android.R.color.system_error_light, android.R.color.system_error_dark);
            onError = pick(c, android.R.color.system_on_error_light, android.R.color.system_on_error_dark);
            errorContainer = pick(c, android.R.color.system_error_container_light,
                    android.R.color.system_error_container_dark);
            onErrorContainer = pick(c, android.R.color.system_on_error_container_light,
                    android.R.color.system_on_error_container_dark);
            inverseSurface = pick(c, android.R.color.system_inverse_surface_light,
                    android.R.color.system_inverse_surface_dark);
            inverseOnSurface = pick(c, android.R.color.system_inverse_on_surface_light,
                    android.R.color.system_inverse_on_surface_dark);
        }

        private int pick(Context c, int light, int dark) {
            return c.getColor(night ? dark : light);
        }
    }

    // ---- lifecycle -------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mC = new C(this);
        mSoc = PerfProfiles.soc();
        mActive = PerfProfiles.active();
        mSelected = PerfProfiles.chosen();
        mFallback = PerfProfiles.fallback();

        getWindow().setBackgroundDrawable(new ColorDrawable(mC.bg));
        final WindowInsetsController wic = getWindow().getInsetsController();
        if (wic != null) {
            final int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            wic.setSystemBarsAppearance(mC.night ? 0 : light, light);
        }

        buildPage();
        setContentView(mRoot);
        refresh(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActive = PerfProfiles.active();
        // Only when it changed: setChecked() runs the listener, which writes the state.
        final boolean shown = isDrawerShown();
        if (mDrawerSwitch.isChecked() != shown) mDrawerSwitch.setChecked(shown);
        refresh(false);
        mHandler.post(mLive);
    }

    @Override
    protected void onPause() {
        mHandler.removeCallbacks(mLive);
        super.onPause();
    }

    /** While the restart sheet is up, back closes it instead of the page. */
    private final OnBackInvokedCallback mCloseSheet = () -> showSheet(false);

    // ---- building --------------------------------------------------------------------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private TextView text(float sp, int color, int weight, float width) {
        final TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        // Roboto on Android 12+ is a variable font: a wider cut for the big titles.
        if (width != 100f) t.setFontVariationSettings("'wdth' " + width);
        return t;
    }

    private static GradientDrawable shape(int color, float radius) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Different radius on the top and bottom corners. */
    private static GradientDrawable shape(int color, float top, float bottom) {
        final GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadii(new float[] {top, top, top, top, bottom, bottom, bottom, bottom});
        return d;
    }

    private Drawable ripple(Drawable content, float radius) {
        final int ripple = PerfWidgets.withAlpha(mC.onSurface, 0.12f);
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, shape(Color.WHITE, radius));
    }

    private ImageView icon(String data, int color, int sizeDp) {
        final ImageView v = new ImageView(this);
        v.setImageDrawable(new PerfWidgets.IconDrawable(data, color));
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams lpMargins(int w, int h, int l, int t, int r, int b) {
        final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private void buildPage() {
        mRoot = new FrameLayout(this);
        mScroll = new ScrollView(this);
        mScroll.setClipToPadding(false);
        mScroll.setVerticalScrollBarEnabled(false);
        mContent = new LinearLayout(this);
        mContent.setOrientation(LinearLayout.VERTICAL);
        mScroll.addView(mContent, new ScrollView.LayoutParams(MATCH, WRAP));
        mRoot.addView(mScroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Space for the top bar, then the big title.
        final View topSpace = new View(this);
        topSpace.setId(View.generateViewId());
        mContent.addView(topSpace, lp(MATCH, dp(64)));
        mBigTitle = text(36, mC.onSurface, 500, 108f);
        mBigTitle.setText(R.string.perf_title);
        mBigTitle.setPadding(dp(24), dp(12), dp(24), dp(20));
        mContent.addView(mBigTitle, lp(MATCH, WRAP));

        buildFallbackCard();
        buildHero();

        mContent.addView(header(R.string.perf_header_profiles, mC.primary));
        final LinearLayout normal = group();
        final LinearLayout oc = group();
        final PerfProfiles.Profile[] all = mSoc.profiles;
        int n = 0, o = 0;
        for (PerfProfiles.Profile p : all) {
            if (p.oc) o++; else n++;
        }
        int ni = 0, oi = 0;
        for (PerfProfiles.Profile p : all) {
            final Row row = p.oc ? new Row(p, oi++, o) : new Row(p, ni++, n);
            mRows.add(row);
            (p.oc ? oc : normal).addView(row.view, lpMargins(MATCH, WRAP, 0, 0, 0, 2));
        }
        mContent.addView(normal);

        mContent.addView(header(R.string.perf_header_beyond, mC.error));
        mContent.addView(warningCard());
        mContent.addView(oc);

        mContent.addView(header(R.string.perf_header_shortcut, mC.primary));
        mContent.addView(drawerRow());
        mContent.addView(footer());

        buildTopBar();
        buildFloatingBar();
        buildSheet();
        buildOverlay();

        mScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            // The small title takes over once the big one has scrolled away.
            final float start = mBigTitle.getTop() + mBigTitle.getHeight() * 0.5f - mTopBar.getHeight();
            final float a = Math.max(0f, Math.min(1f, (y - start) / dp(24)));
            mTopTitle.setAlpha(a);
            mTopBar.setBackgroundColor(PerfWidgets.withAlpha(mC.bg, Math.min(1f, y / (float) dp(16))));
        });

        mRoot.setOnApplyWindowInsetsListener((v, insets) -> {
            final android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            mTopBar.setPadding(bars.left, bars.top, bars.right, 0);
            topSpace.getLayoutParams().height = dp(64) + bars.top;
            topSpace.requestLayout();
            mInsetBottom = bars.bottom;
            mContent.setPadding(bars.left, 0, bars.right, bars.bottom + dp(132));
            ((FrameLayout.LayoutParams) mBar.getLayoutParams()).bottomMargin = bars.bottom + dp(20);
            mBar.requestLayout();
            mSheet.setPadding(dp(24), dp(10), dp(24), bars.bottom + dp(24));
            return WindowInsets.CONSUMED;
        });
    }

    private TextView header(int res, int color) {
        final TextView t = text(14, color, 600, 100f);
        t.setText(res);
        t.setLetterSpacing(0.015f);
        t.setPadding(dp(32), dp(28), dp(32), dp(10));
        return t;
    }

    private LinearLayout group() {
        final LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setPadding(dp(16), 0, dp(16), 0);
        return g;
    }

    private void buildTopBar() {
        final LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        final LinearLayout inner = new LinearLayout(this);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(8), 0, dp(16), 0);
        final ImageView back = icon(PerfWidgets.ICON_BACK, mC.onSurface, 48);
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setBackground(ripple(shape(Color.TRANSPARENT, dp(24)), dp(24)));
        back.setContentDescription(getString(R.string.perf_back));
        back.setOnClickListener(v -> finish());
        inner.addView(back);
        mTopTitle = text(22, mC.onSurface, 500, 100f);
        mTopTitle.setText(R.string.perf_title);
        mTopTitle.setPadding(dp(8), 0, 0, 0);
        mTopTitle.setAlpha(0f);
        inner.addView(mTopTitle);
        bar.addView(inner, lp(MATCH, dp(64)));
        mTopBar = bar;
        mRoot.addView(bar, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));
    }

    private void buildFallbackCard() {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(shape(mC.errorContainer, dp(28)));
        card.setPadding(dp(18), dp(18), dp(18), dp(10));
        final ImageView warn = icon(PerfWidgets.ICON_WARN, mC.onErrorContainer, 24);
        card.addView(warn);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        final TextView title = text(16, mC.onErrorContainer, 600, 100f);
        final TextView body = text(14, mC.onErrorContainer, 400, 100f);
        body.setLineSpacing(dp(2), 1f);
        final TextView ok = text(14, mC.onErrorContainer, 600, 100f);
        ok.setText(R.string.perf_ok);
        ok.setPadding(dp(16), dp(12), dp(16), dp(12));
        ok.setBackground(ripple(shape(Color.TRANSPARENT, dp(20)), dp(20)));
        ok.setOnClickListener(v -> dismissFallback());
        col.addView(title);
        col.addView(body, lpMargins(MATCH, WRAP, 0, 6, 0, 0));
        final LinearLayout.LayoutParams okp = lp(WRAP, WRAP);
        okp.gravity = Gravity.END;
        col.addView(ok, okp);
        card.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        card.setTag(new TextView[] {title, body});
        mFallbackCard = card;
        mContent.addView(card, lpMargins(MATCH, WRAP, 16, 0, 16, 16));
    }

    private void buildHero() {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(shape(mC.primaryContainer, dp(32)));
        card.setPadding(dp(20), dp(20), dp(20), dp(16));

        final LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        mGauge = new PerfWidgets.Gauge(this);
        mGauge.setColors(mC.onPrimaryContainer, mC.primary, mC.error, mC.onPrimary, mC.onError);
        top.addView(mGauge, lp(dp(156), WRAP));
        final LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, 0, 0);
        final TextView label = text(13, PerfWidgets.withAlpha(mC.onPrimaryContainer, 0.85f), 600, 100f);
        label.setText(R.string.perf_active_label);
        label.setLetterSpacing(0.03f);
        mActiveName = text(32, mC.onPrimaryContainer, 600, 112f);
        mActiveStatus = text(14, mC.onPrimaryContainer, 400, 100f);
        texts.addView(label);
        texts.addView(mActiveName, lpMargins(WRAP, WRAP, 0, 2, 0, 2));
        texts.addView(mActiveStatus);
        top.addView(texts, new LinearLayout.LayoutParams(0, WRAP, 1f));
        card.addView(top);

        final LinearLayout tiles = new LinearLayout(this);
        final int[] labels = {R.string.perf_little, R.string.perf_big, R.string.perf_gpu};
        for (int i = 0; i < 3; i++) {
            final LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setBackground(shape(PerfWidgets.withAlpha(mC.card, 0.72f), dp(20)));
            tile.setPadding(dp(12), dp(12), dp(12), dp(10));
            final TextView l = text(12, mC.onPrimaryContainer, 600, 100f);
            l.setText(labels[i]);
            l.setSingleLine();
            l.setEllipsize(TextUtils.TruncateAt.END);
            final LinearLayout val = new LinearLayout(this);
            val.setGravity(Gravity.BOTTOM);
            mLiveValue[i] = text(22, mC.onPrimaryContainer, 600, 100f);
            mLiveValue[i].setFontFeatureSettings("tnum");
            final TextView unit = text(12, mC.onPrimaryContainer, 400, 100f);
            unit.setText(R.string.perf_mhz);
            unit.setPadding(dp(3), 0, 0, dp(3));
            val.addView(mLiveValue[i]);
            val.addView(unit);
            mLiveBar[i] = new PerfWidgets.Bar(this);
            mLiveCap[i] = text(11.5f, PerfWidgets.withAlpha(mC.onPrimaryContainer, 0.85f), 400, 100f);
            mLiveCap[i].setFontFeatureSettings("tnum");
            tile.addView(l);
            tile.addView(val, lpMargins(WRAP, WRAP, 0, 4, 0, 0));
            tile.addView(mLiveBar[i], lpMargins(MATCH, dp(6), 0, 8, 0, 0));
            tile.addView(mLiveCap[i], lpMargins(WRAP, WRAP, 0, 6, 0, 0));
            final LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1f);
            if (i > 0) p.setMarginStart(dp(8));
            tiles.addView(tile, p);
        }
        card.addView(tiles, lpMargins(MATCH, WRAP, 0, 16, 0, 0));
        final TextView hint = text(12, PerfWidgets.withAlpha(mC.onPrimaryContainer, 0.8f), 400, 100f);
        hint.setText(R.string.perf_live_hint);
        card.addView(hint, lpMargins(WRAP, WRAP, 0, 10, 0, 0));
        mContent.addView(card, lpMargins(MATCH, WRAP, 16, 0, 16, 0));
    }

    private View warningCard() {
        final LinearLayout card = new LinearLayout(this);
        card.setBackground(shape(mC.card, dp(28), dp(8)));
        card.setPadding(dp(16), dp(18), dp(18), dp(18));
        final ImageView warn = icon(PerfWidgets.ICON_WARN, mC.onErrorContainer, 40);
        warn.setPadding(dp(9), dp(9), dp(9), dp(9));
        warn.setBackground(shape(mC.errorContainer, dp(14)));
        card.addView(warn);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        final TextView title = text(16, mC.error, 600, 100f);
        title.setText(R.string.perf_warning_title);
        final TextView body = text(14, mC.onSurfaceVariant, 400, 100f);
        body.setText(R.string.perf_warning_body);
        body.setLineSpacing(dp(2), 1f);
        col.addView(title);
        col.addView(body, lpMargins(MATCH, WRAP, 0, 6, 0, 0));
        card.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        final LinearLayout.LayoutParams p = lpMargins(MATCH, WRAP, 16, 0, 16, 2);
        card.setLayoutParams(p);
        return card;
    }

    private View drawerRow() {
        final LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(ripple(shape(mC.card, dp(28)), dp(28)));
        row.setPadding(dp(16), dp(16), dp(18), dp(16));
        final ImageView app = icon(PerfWidgets.ICON_APP, mC.onPrimaryContainer, 48);
        app.setPadding(dp(11), dp(11), dp(11), dp(11));
        app.setBackground(shape(mC.primaryContainer, dp(24)));
        row.addView(app);
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, dp(12), 0);
        final TextView t = text(17, mC.onSurface, 500, 100f);
        t.setText(R.string.perf_drawer_title);
        final TextView s = text(14, mC.onSurfaceVariant, 400, 100f);
        s.setText(R.string.perf_drawer_summary);
        col.addView(t);
        col.addView(s);
        row.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        mDrawerSwitch = new Switch(this);
        mDrawerSwitch.setChecked(isDrawerShown());
        mDrawerSwitch.setOnCheckedChangeListener((b, on) -> setDrawerShown(on));
        mDrawerSwitch.setContentDescription(getString(R.string.perf_drawer_title));
        row.addView(mDrawerSwitch);
        row.setOnClickListener(v -> mDrawerSwitch.toggle());
        row.setLayoutParams(lpMargins(MATCH, WRAP, 16, 0, 16, 0));
        return row;
    }

    private View footer() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(32), dp(28), dp(32), 0);
        final LinearLayout line = new LinearLayout(this);
        line.addView(icon(PerfWidgets.ICON_HEAT, mC.onSurfaceVariant, 20));
        final TextView t = text(13.5f, mC.onSurfaceVariant, 400, 100f);
        t.setText(R.string.perf_footer);
        t.setLineSpacing(dp(2), 1f);
        t.setPadding(dp(12), 0, 0, 0);
        line.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        box.addView(line);
        final TextView chip = text(12.5f, mC.onSurfaceVariant, 400, 100f);
        chip.setFontFeatureSettings("tnum");
        chip.setText(getString(R.string.perf_chip_line, mSoc.chip, mSoc.stock[0], mSoc.stock[1],
                mSoc.stock[2]));
        box.addView(chip, lpMargins(WRAP, WRAP, 0, 14, 0, 0));
        return box;
    }

    private void buildFloatingBar() {
        mBar = new LinearLayout(this);
        mBar.setGravity(Gravity.CENTER_VERTICAL);
        mBar.setPadding(dp(12), 0, dp(10), 0);
        mBar.setElevation(dp(6));
        mBarIcon = new PerfWidgets.LeadIcon(this);
        mBar.addView(mBarIcon, lp(dp(44), dp(44)));
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), 0, dp(4), 0);
        mBarName = text(16, mC.onPrimaryContainer, 600, 100f);
        mBarName.setSingleLine();
        mBarName.setEllipsize(TextUtils.TruncateAt.END);
        final TextView sub = text(12.5f, mC.onPrimaryContainer, 400, 100f);
        sub.setText(R.string.perf_bar_subtitle);
        sub.setTag("sub");
        col.addView(mBarName);
        col.addView(sub);
        mBar.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
        final ImageView keep = icon(PerfWidgets.ICON_CLOSE, mC.onPrimaryContainer, 44);
        keep.setPadding(dp(11), dp(11), dp(11), dp(11));
        keep.setBackground(ripple(shape(Color.TRANSPARENT, dp(22)), dp(22)));
        keep.setContentDescription(getString(R.string.perf_bar_keep));
        keep.setTag("keep");
        keep.setOnClickListener(v -> select(mActive));
        mBar.addView(keep);
        mBarRestart = pillButton(R.string.perf_restart, true);
        mBarRestart.setOnClickListener(v -> showSheet(true));
        final LinearLayout.LayoutParams rp = lp(WRAP, dp(48));
        rp.setMarginStart(dp(4));
        mBar.addView(mBarRestart, rp);
        final FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(MATCH, dp(68),
                Gravity.BOTTOM);
        p.setMargins(dp(16), 0, dp(16), dp(20));
        mRoot.addView(mBar, p);
        mBar.setVisibility(View.INVISIBLE);
    }

    /** Filled pill button with a restart icon; squashes a little while pressed. */
    private TextView pillButton(int res, boolean withIcon) {
        final TextView b = text(15, mC.onPrimary, 600, 100f);
        b.setText(res);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(withIcon ? 14 : 22), 0, dp(20), 0);
        if (withIcon) {
            final PerfWidgets.IconDrawable d = new PerfWidgets.IconDrawable(PerfWidgets.ICON_RESTART,
                    mC.onPrimary, 2.2f);
            d.setBounds(0, 0, dp(20), dp(20));
            b.setCompoundDrawables(d, null, null, null);
            b.setCompoundDrawablePadding(dp(8));
        }
        b.setOnTouchListener((v, e) -> {
            final float s = e.getAction() == android.view.MotionEvent.ACTION_DOWN ? 0.94f : 1f;
            if (e.getAction() == android.view.MotionEvent.ACTION_DOWN
                    || e.getAction() == android.view.MotionEvent.ACTION_UP
                    || e.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(s).scaleY(s).setDuration(180).setInterpolator(PerfWidgets.SPRING).start();
            }
            return false;
        });
        return b;
    }

    private void tintPill(TextView b, int bg, int fg) {
        b.setBackground(ripple(shape(bg, dp(24)), dp(24)));
        b.setTextColor(fg);
        final Drawable d = b.getCompoundDrawables()[0];
        if (d instanceof PerfWidgets.IconDrawable) ((PerfWidgets.IconDrawable) d).setColor(fg);
    }

    private void buildSheet() {
        mScrim = new View(this);
        mScrim.setBackgroundColor(mC.night ? 0x8C000000 : 0x52000000);
        mScrim.setAlpha(0f);
        mScrim.setVisibility(View.GONE);
        mScrim.setOnClickListener(v -> showSheet(false));
        mRoot.addView(mScrim, new FrameLayout.LayoutParams(MATCH, MATCH));

        mSheet = new LinearLayout(this);
        mSheet.setOrientation(LinearLayout.VERTICAL);
        mSheet.setClickable(true);
        mSheet.setBackground(shape(mC.card, dp(32), 0));
        mSheet.setPadding(dp(24), dp(10), dp(24), dp(28));
        final View handle = new View(this);
        handle.setBackground(shape(mC.outlineVariant, dp(2)));
        final LinearLayout.LayoutParams hp = lpMargins(dp(32), dp(4), 0, 0, 0, 22);
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        mSheet.addView(handle, hp);
        mSheetIcon = new PerfWidgets.LeadIcon(this);
        mSheetIcon.setSpin(true);
        final LinearLayout.LayoutParams ip = lp(dp(64), dp(64));
        ip.gravity = Gravity.CENTER_HORIZONTAL;
        mSheet.addView(mSheetIcon, ip);
        mSheetTitle = text(24, mC.onSurface, 550, 100f);
        mSheetTitle.setGravity(Gravity.CENTER);
        mSheet.addView(mSheetTitle, lpMargins(MATCH, WRAP, 0, 18, 0, 10));
        mSheetBody = text(14.5f, mC.onSurfaceVariant, 400, 100f);
        mSheetBody.setGravity(Gravity.CENTER);
        mSheetBody.setLineSpacing(dp(3), 1f);
        mSheet.addView(mSheetBody, lp(MATCH, WRAP));

        final LinearLayout ack = new LinearLayout(this);
        ack.setGravity(Gravity.CENTER_VERTICAL);
        ack.setBackground(shape(mC.errorContainer, dp(20)));
        ack.setPadding(dp(10), dp(4), dp(16), dp(4));
        mAckBox = new CheckBox(this);
        mAckBox.setButtonTintList(ColorStateList.valueOf(mC.onErrorContainer));
        mAckBox.setText(R.string.perf_sheet_ack);
        mAckBox.setTextColor(mC.onErrorContainer);
        mAckBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mAckBox.setOnCheckedChangeListener((b, on) -> {
            mAck = on;
            updateSheetButton();
        });
        ack.addView(mAckBox, new LinearLayout.LayoutParams(0, WRAP, 1f));
        mAckRow = ack;
        mSheet.addView(ack, lpMargins(MATCH, WRAP, 0, 18, 0, 0));

        final LinearLayout buttons = new LinearLayout(this);
        final TextView notNow = text(15, mC.primary, 600, 100f);
        notNow.setText(R.string.perf_not_now);
        notNow.setGravity(Gravity.CENTER);
        final GradientDrawable outline = shape(Color.TRANSPARENT, dp(24));
        outline.setStroke(dp(1), mC.outline);
        notNow.setBackground(ripple(outline, dp(24)));
        notNow.setOnClickListener(v -> showSheet(false));
        buttons.addView(notNow, new LinearLayout.LayoutParams(0, dp(48), 1f));
        mSheetConfirm = pillButton(R.string.perf_restart_now, false);
        mSheetConfirm.setOnClickListener(v -> confirmRestart());
        final LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        cp.setMarginStart(dp(12));
        buttons.addView(mSheetConfirm, cp);
        mSheet.addView(buttons, lpMargins(MATCH, WRAP, 0, 24, 0, 0));

        mRoot.addView(mSheet, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        mSheet.setVisibility(View.INVISIBLE);
    }

    private void buildOverlay() {
        final LinearLayout o = new LinearLayout(this);
        o.setOrientation(LinearLayout.VERTICAL);
        o.setGravity(Gravity.CENTER);
        o.setBackgroundColor(Color.BLACK);
        o.setClickable(true);
        final PerfWidgets.Loader loader = new PerfWidgets.Loader(this,
                getColor(android.R.color.system_primary_dark));
        o.addView(loader, lp(dp(56), dp(56)));
        final TextView t = text(16, getColor(android.R.color.system_on_surface_dark), 400, 100f);
        t.setText(R.string.perf_restarting);
        o.addView(t, lpMargins(WRAP, WRAP, 0, 28, 0, 0));
        o.setAlpha(0f);
        o.setVisibility(View.GONE);
        mOverlay = o;
        mRoot.addView(o, new FrameLayout.LayoutParams(MATCH, MATCH));
    }

    // ---- profile rows ----------------------------------------------------------------

    /** One profile in the list. */
    private final class Row {
        final PerfProfiles.Profile profile;
        final LinearLayout view;
        final GradientDrawable bg = new GradientDrawable();
        final GradientDrawable mask = new GradientDrawable();
        final PerfWidgets.LeadIcon lead;
        final TextView title, desc, meta, badgeDefault, badgeActive;
        final PerfWidgets.Pills pills;
        final PerfWidgets.Radio radio;
        final float outerTop, outerBottom;
        float corner = -1;
        ValueAnimator cornerAnim;
        Boolean selected;

        Row(PerfProfiles.Profile p, int index, int count) {
            profile = p;
            final float big = dp(28), small = dp(8);
            outerTop = index == 0 ? big : small;
            outerBottom = index == count - 1 ? big : small;
            view = new LinearLayout(PerformanceActivity.this);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(dp(16), dp(16), dp(18), dp(16));
            mask.setColor(Color.WHITE);
            view.setBackground(new RippleDrawable(ColorStateList.valueOf(
                    PerfWidgets.withAlpha(mC.onSurface, 0.12f)), bg, mask));
            view.setOnClickListener(v -> select(p.id));
            view.setFocusable(true);

            lead = new PerfWidgets.LeadIcon(PerformanceActivity.this);
            lead.setIcon(PerfWidgets.iconFor(p.id));
            lead.setSpin(!p.oc);
            if (p.oc) {
                lead.setColors(mC.errorContainer, mC.error, mC.onErrorContainer, mC.onError);
            } else {
                lead.setColors(mC.cardHigh, mC.primary, mC.onSurfaceVariant, mC.onPrimary);
            }
            view.addView(lead, lp(dp(48), dp(48)));

            final LinearLayout col = new LinearLayout(PerformanceActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(16), 0, dp(12), 0);
            final LinearLayout titleLine = new LinearLayout(PerformanceActivity.this);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            title = text(17, mC.onSurface, 550, 100f);
            title.setText(p.title);
            titleLine.addView(title);
            if (p.oc) {
                final int plus = Math.round((p.cap[1] / (float) mSoc.stock[1] - 1f) * 100f);
                badgeDefault = badge(getString(R.string.perf_plus, plus), mC.error, mC.onError);
            } else {
                badgeDefault = badge(getString(R.string.perf_badge_default), mC.primaryContainer,
                        mC.onPrimaryContainer);
                badgeDefault.setVisibility(PerfProfiles.DEFAULT.equals(p.id) ? View.VISIBLE : View.GONE);
            }
            titleLine.addView(badgeDefault, lpMargins(WRAP, WRAP, 8, 0, 0, 0));
            badgeActive = badge(getString(R.string.perf_badge_active), mC.primary, mC.onPrimary);
            titleLine.addView(badgeActive, lpMargins(WRAP, WRAP, 8, 0, 0, 0));
            col.addView(titleLine);
            desc = text(14, mC.onSurfaceVariant, 400, 100f);
            desc.setText(p.desc);
            col.addView(desc, lpMargins(MATCH, WRAP, 0, 2, 0, 0));
            meta = text(12.5f, mC.onSurfaceVariant, 400, 100f);
            meta.setFontFeatureSettings("tnum");
            meta.setText(metaText(p));
            col.addView(meta, lpMargins(MATCH, WRAP, 0, 2, 0, 0));
            pills = new PerfWidgets.Pills(PerformanceActivity.this);
            if (!p.oc) col.addView(pills, lpMargins(dp(136), dp(6), 0, 8, 0, 2));
            view.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));

            radio = new PerfWidgets.Radio(PerformanceActivity.this);
            view.addView(radio, lp(dp(20), dp(20)));
        }

        void update(boolean animated) {
            final boolean sel = profile.id.equals(mSelected);
            final boolean changed = selected == null || selected != sel;
            selected = sel;
            final int surface = sel ? (profile.oc ? mC.errorContainer : mC.secondaryContainer) : mC.card;
            final int on = sel ? (profile.oc ? mC.onErrorContainer : mC.onSecondaryContainer) : mC.onSurface;
            final int onVariant = sel ? on : mC.onSurfaceVariant;
            bg.setColor(surface);
            title.setTextColor(on);
            desc.setTextColor(onVariant);
            meta.setTextColor(onVariant);
            badgeActive.setVisibility(profile.id.equals(mActive) ? View.VISIBLE : View.GONE);
            pills.set(profile.level + 1, mC.primary, sel
                    ? PerfWidgets.withAlpha(mC.onSecondaryContainer, 0.18f)
                    : (mC.night ? mC.cardHigh : PerfWidgets.withAlpha(mC.onSurface, 0.1f)));
            radio.setColors(mC.onSurfaceVariant, profile.oc ? mC.error : mC.primary);
            if (changed) {
                lead.setSelectedShape(sel, animated);
                radio.setChecked(sel, animated);
                setCorner(sel ? dp(28) : 0, animated);
            }
            view.setSelected(sel);
        }

        /** 0 = the corners of its place in the group; otherwise all corners this round. */
        private void setCorner(float target, boolean animated) {
            if (cornerAnim != null) cornerAnim.cancel();
            if (corner < 0 || !animated || !PerfWidgets.motionOn()) {
                corner = target;
                applyCorner();
                return;
            }
            cornerAnim = ValueAnimator.ofFloat(corner, target);
            cornerAnim.setDuration(450);
            cornerAnim.setInterpolator(PerfWidgets.SPRING);
            cornerAnim.addUpdateListener(a -> {
                corner = (float) a.getAnimatedValue();
                applyCorner();
            });
            cornerAnim.start();
        }

        private void applyCorner() {
            final float t = Math.max(outerTop, corner), b = Math.max(outerBottom, corner);
            final float[] radii = {t, t, t, t, b, b, b, b};
            bg.setCornerRadii(radii);
            mask.setCornerRadii(radii);
            view.invalidate();
        }
    }

    private TextView badge(String label, int bg, int fg) {
        final TextView b = text(11.5f, fg, 600, 100f);
        b.setText(label);
        b.setLetterSpacing(0.03f);
        b.setPadding(dp(8), dp(2), dp(8), dp(2));
        b.setBackground(shape(bg, dp(12)));
        return b;
    }

    private String ghz(int mhz) {
        return getString(R.string.perf_ghz, String.format(Locale.getDefault(), "%.2f", mhz / 1000f));
    }

    private String gpu(int mhz) {
        return mhz >= 1000 ? ghz(mhz) : getString(R.string.perf_mhz_value, mhz);
    }

    private String metaText(PerfProfiles.Profile p) {
        if (p.oc) return getString(R.string.perf_meta_oc, ghz(p.cap[1]), gpu(p.cap[2]));
        if ("stock".equals(p.id)) return getString(R.string.perf_meta_stock, ghz(mSoc.stock[1]));
        if (p.touch == null) return getString(R.string.perf_meta_max, ghz(mSoc.stock[1]));
        return getString(R.string.perf_meta_touch, ghz(p.touch[1]), gpu(p.touch[2]));
    }

    // ---- state -----------------------------------------------------------------------

    private void select(String id) {
        if (id.equals(mSelected)) return;
        mSelected = id;
        mAck = false;
        mAckBox.setChecked(false);
        refresh(true);
    }

    private void refresh(boolean animated) {
        final PerfProfiles.Profile active = mSoc.get(mActive);
        final PerfProfiles.Profile sel = mSoc.get(mSelected);
        final boolean pending = !sel.id.equals(active.id);
        for (Row r : mRows) r.update(animated);

        mGauge.setLevel(active.level, active.oc, PerfWidgets.iconFor(active.id),
                pending ? sel.level : -1, animated);
        mGauge.setContentDescription(getString(R.string.perf_gauge_desc, active.level,
                getString(active.title)));
        mActiveName.setText(active.title);
        final boolean fallbackShown = fallbackVisible();
        if (fallbackShown) {
            mActiveStatus.setText(getString(R.string.perf_fell_back_status,
                    getString(mSoc.get(mFallback).title)));
        } else if (pending) {
            mActiveStatus.setText(getString(R.string.perf_after_restart, getString(sel.title)));
        } else {
            mActiveStatus.setText(R.string.perf_since_restart);
        }
        mFallbackCard.setVisibility(fallbackShown ? View.VISIBLE : View.GONE);
        if (fallbackShown) {
            final TextView[] tv = (TextView[]) mFallbackCard.getTag();
            final String oc = getString(mSoc.get(mFallback).title);
            tv[0].setText(getString(R.string.perf_fallback_title, oc));
            tv[1].setText(getString(R.string.perf_fallback_body, oc));
        }
        for (int i = 0; i < 3; i++) {
            mLiveBar[i].setColors(PerfWidgets.withAlpha(mC.onPrimaryContainer, 0.14f),
                    active.oc ? mC.error : mC.primary);
            mLiveCap[i].setText(getString(R.string.perf_cap, active.cap[i]));
        }

        // Floating bar: shown while another profile than the active one is picked.
        final int bg = sel.oc ? mC.errorContainer : mC.primaryContainer;
        final int fg = sel.oc ? mC.onErrorContainer : mC.onPrimaryContainer;
        mBar.setBackground(shape(bg, dp(34)));
        mBarIcon.setIcon(PerfWidgets.iconFor(sel.id));
        mBarIcon.setColors(bg, PerfWidgets.withAlpha(fg, 0.12f), fg, fg);
        mBarIcon.setSelectedShape(true, false);
        mBarName.setText(sel.title);
        mBarName.setTextColor(fg);
        ((TextView) mBar.findViewWithTag("sub")).setTextColor(fg);
        ((ImageView) mBar.findViewWithTag("keep")).setImageDrawable(
                new PerfWidgets.IconDrawable(PerfWidgets.ICON_CLOSE, fg));
        tintPill(mBarRestart, sel.oc ? mC.error : mC.primary, sel.oc ? mC.onError : mC.onPrimary);
        showBar(pending && !mSheetShown, animated);
    }

    private void showBar(boolean show, boolean animated) {
        if (show == mBarShown && mBar.getVisibility() == (show ? View.VISIBLE : View.INVISIBLE)) return;
        mBarShown = show;
        mBar.animate().cancel();
        if (!animated || !PerfWidgets.motionOn()) {
            mBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            mBar.setTranslationY(0);
            mBar.setAlpha(1f);
            return;
        }
        if (show) {
            mBar.setVisibility(View.VISIBLE);
            mBar.setTranslationY(dp(120));
            mBar.setAlpha(0f);
            mBar.animate().translationY(0).alpha(1f).setDuration(500)
                    .setInterpolator(PerfWidgets.SPRING).withEndAction(null).start();
        } else {
            mBar.animate().translationY(dp(120)).alpha(0f).setDuration(250)
                    .setInterpolator(PerfWidgets.EMPHASIZED)
                    .withEndAction(() -> mBar.setVisibility(View.INVISIBLE)).start();
        }
    }

    private void showSheet(boolean show) {
        if (show == mSheetShown) return;
        mSheetShown = show;
        if (show) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, mCloseSheet);
        } else {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mCloseSheet);
        }
        final PerfProfiles.Profile sel = mSoc.get(mSelected);
        if (show) {
            final String name = getString(sel.title);
            if (sel.oc) {
                mSheetTitle.setText(R.string.perf_sheet_oc_title);
                mSheetBody.setText(getString(R.string.perf_sheet_oc_body, name, ghz(sel.cap[1]),
                        gpu(sel.cap[2])));
                mSheetIcon.setColors(mC.errorContainer, mC.errorContainer, mC.onErrorContainer,
                        mC.onErrorContainer);
            } else {
                mSheetTitle.setText(getString(R.string.perf_sheet_title, name));
                mSheetBody.setText(getString(R.string.perf_sheet_body, name));
                mSheetIcon.setColors(mC.primaryContainer, mC.primaryContainer,
                        mC.onPrimaryContainer, mC.onPrimaryContainer);
            }
            mSheetIcon.setIcon(PerfWidgets.iconFor(sel.id));
            mSheetIcon.setSelectedShape(false, false);
            mAckRow.setVisibility(sel.oc ? View.VISIBLE : View.GONE);
            updateSheetButton();
        }
        showBar(!show && !sel.id.equals(mActive), true);

        final boolean anim = PerfWidgets.motionOn();
        mScrim.animate().cancel();
        mSheet.animate().cancel();
        if (show) {
            mScrim.setVisibility(View.VISIBLE);
            mScrim.animate().alpha(1f).setDuration(anim ? 300 : 0).start();
            mSheet.setVisibility(View.VISIBLE);
            mSheet.post(() -> {
                mSheet.setTranslationY(mSheet.getHeight() + mInsetBottom);
                mSheet.animate().translationY(0).setDuration(anim ? 500 : 0)
                        .setInterpolator(new android.view.animation.PathInterpolator(0.32f, 1.25f, 0.6f, 1f))
                        .start();
                mSheetIcon.setSelectedShape(true, true);
            });
        } else {
            mScrim.animate().alpha(0f).setDuration(anim ? 250 : 0)
                    .withEndAction(() -> mScrim.setVisibility(View.GONE)).start();
            mSheet.animate().translationY(mSheet.getHeight() + mInsetBottom)
                    .setDuration(anim ? 250 : 0).setInterpolator(PerfWidgets.EMPHASIZED)
                    .withEndAction(() -> mSheet.setVisibility(View.INVISIBLE)).start();
        }
    }

    private void updateSheetButton() {
        final PerfProfiles.Profile sel = mSoc.get(mSelected);
        final boolean enabled = !sel.oc || mAck;
        tintPill(mSheetConfirm, sel.oc ? mC.error : mC.primary, sel.oc ? mC.onError : mC.onPrimary);
        mSheetConfirm.setEnabled(enabled);
        mSheetConfirm.setAlpha(enabled ? 1f : 0.38f);
    }

    private void confirmRestart() {
        final PerfProfiles.Profile sel = mSoc.get(mSelected);
        if (sel.oc && !mAck) return;
        PerfProfiles.choose(sel.id);
        Log.i(TAG, "profile " + sel.id + " chosen, restarting");
        showSheet(false);
        mBar.setVisibility(View.INVISIBLE);
        mOverlay.setVisibility(View.VISIBLE);
        mOverlay.animate().alpha(1f).setDuration(PerfWidgets.motionOn() ? 450 : 0).start();
        mHandler.postDelayed(() -> {
            try {
                getSystemService(PowerManager.class).reboot(null);
            } catch (RuntimeException e) {
                Log.e(TAG, "reboot failed", e);
                mOverlay.animate().alpha(0f).withEndAction(() -> mOverlay.setVisibility(View.GONE)).start();
                Toast.makeText(this, R.string.perf_restart_failed, Toast.LENGTH_LONG).show();
                refresh(false);
            }
        }, 1200);
    }

    // ---- fallback banner -------------------------------------------------------------

    private SharedPreferences prefs() {
        return createDeviceProtectedStorageContext().getSharedPreferences("performance",
                Context.MODE_PRIVATE);
    }

    private int bootCount() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, 0);
    }

    private boolean fallbackVisible() {
        return !mFallback.isEmpty() && prefs().getInt(PREF_FALLBACK_SEEN, -1) != bootCount();
    }

    private void dismissFallback() {
        prefs().edit().putInt(PREF_FALLBACK_SEEN, bootCount()).apply();
        mFallbackCard.animate().alpha(0f).setDuration(PerfWidgets.motionOn() ? 200 : 0)
                .withEndAction(() -> {
                    mFallbackCard.setAlpha(1f);
                    refresh(true);
                }).start();
    }

    // ---- app drawer icon -------------------------------------------------------------

    private ComponentName launcher() {
        return new ComponentName(getPackageName(), LAUNCHER_ALIAS);
    }

    private boolean isDrawerShown() {
        return getPackageManager().getComponentEnabledSetting(launcher())
                != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void setDrawerShown(boolean shown) {
        getPackageManager().setComponentEnabledSetting(launcher(), shown
                ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    // ---- live clocks -----------------------------------------------------------------

    private static int readInt(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            final String line = r.readLine();
            return line == null ? -1 : Integer.parseInt(line.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private void updateLive() {
        final PerfProfiles.Profile active = mSoc.get(mActive);
        final int[] khz = {
            readInt("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"),
            readInt("/sys/devices/system/cpu/cpu6/cpufreq/scaling_cur_freq"),
            readInt("/sys/kernel/gpu/gpu_clock"),
        };
        for (int i = 0; i < 3; i++) {
            final int mhz = khz[i] > 100000 ? khz[i] / 1000 : khz[i];
            if (mhz > 0) {
                mLiveValue[i].setText(String.valueOf(mhz));
                mLiveBar[i].setFraction(mhz / (float) active.cap[i]);
            } else {
                // GPU powered down, or the big cores switched off.
                mLiveValue[i].setText(i == 2 ? R.string.perf_idle : R.string.perf_off);
                mLiveBar[i].setFraction(0f);
            }
        }
    }
}
