/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.animation.ArgbEvaluator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.PathParser;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * The drawn parts of the Performance page, in the Material 3 Expressive style: shapes that
 * morph and spin, springy motion, stroke icons. Everything takes its colours from the
 * caller (ExynosStyle), so it follows the wallpaper colours and dark theme.
 */
final class PerfWidgets {

    /** Overshooting "spring" for things that appear, grow or move into place. */
    static final TimeInterpolator SPRING = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);
    /** Softer spring for big moves (the gauge). */
    static final TimeInterpolator SPRING_SOFT = new PathInterpolator(0.34f, 1.25f, 0.64f, 1f);
    static final TimeInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    /** Icons, as 24x24 SVG stroke paths. */
    static final String ICON_STOCK = "M5 19c0-8 6-14 14-14 0 8-6 14-14 14zM5 19l7-7";
    static final String ICON_LIGHT = "M20 4C11 4 6 9 5 18l-1 2M8 14h7M10 10h7";
    static final String ICON_SMOOTH = "M3 9c1.8-2.4 3.6-2.4 5.4 0s3.6 2.4 5.4 0 3.6-2.4 5.4 0"
            + "M3 15c1.8-2.4 3.6-2.4 5.4 0s3.6 2.4 5.4 0 3.6-2.4 5.4 0";
    static final String ICON_FAST = "M13 2L4 14h7l-1 8 9-12h-7z";
    static final String ICON_MAX = "M3.5 17a8.5 8.5 0 1 1 17 0M12 17l4.5-6M7 17h.01M17 17h.01";
    static final String ICON_OC1 = "M12 3c1 4 6 6 6 11a6 6 0 0 1-12 0c0-3 2-4 2-6 1 1 2 2 2 3 1-3 2-5 2-8z";
    static final String ICON_OC2 = "M12 2c3 2.5 5 6 5 10l-2 4H9l-2-4c0-4 2-7.5 5-10z"
            + "M9 16l-2.5 4 3.5-1.5M15 16l2.5 4-3.5-1.5M12 8.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3z";
    static final String ICON_BACK = "M19 12H5M12 19l-7-7 7-7";
    static final String ICON_WARN = "M12 3L2 20h20L12 3zM12 10v4M12 17h.01";
    static final String ICON_RESTART = "M3 12a9 9 0 1 0 2.7-6.4L3 8M3 3v5h5";
    static final String ICON_CLOSE = "M6 6l12 12M18 6L6 18";
    static final String ICON_HEAT = "M14 14.76V4a2 2 0 0 0-4 0v10.76a4 4 0 1 0 4 0z";
    static final String ICON_APP = "M3.8 16.5a8.5 8.5 0 1 1 16.4 0M12.8 7.5l-2.6 4.6h3.6l-2.6 4.6";

    private PerfWidgets() {}

    static String iconFor(String profile) {
        switch (profile) {
            case "stock": return ICON_STOCK;
            case "light": return ICON_LIGHT;
            case "fast": return ICON_FAST;
            case "max": return ICON_MAX;
            case "oc1": return ICON_OC1;
            case "oc2": return ICON_OC2;
            default: return ICON_SMOOTH;
        }
    }

    /** A rounded "cookie" (M3 shape): a circle of radius r with `lobes` soft bumps. */
    static Path cookie(int lobes, float r, float amp, float cx, float cy) {
        final Path p = new Path();
        final int steps = 180;
        for (int i = 0; i <= steps; i++) {
            final double t = i * 2 * Math.PI / steps;
            final double rr = r + amp * Math.cos(lobes * t);
            final float x = (float) (cx + rr * Math.cos(t - Math.PI / 2));
            final float y = (float) (cy + rr * Math.sin(t - Math.PI / 2));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    /** False when "Remove animations" is on. */
    static boolean motionOn() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    /** Loops 0..1 forever (for spinning shapes); paused while the view is detached. */
    static ValueAnimator loop(View v, long ms) {
        final ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(ms);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setInterpolator(new LinearInterpolator());
        a.addUpdateListener(x -> v.invalidate());
        return a;
    }

    // ---------------------------------------------------------------------------------

    /** A 24x24 stroke icon, scaled to its bounds. */
    static final class IconDrawable extends Drawable {
        private final Path mPath;
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mStroke;

        IconDrawable(String data, int color) {
            this(data, color, 2f);
        }

        IconDrawable(String data, int color, float stroke) {
            mPath = PathParser.createPathFromPathData(data);
            mStroke = stroke;
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setColor(color);
        }

        void setColor(int color) {
            mPaint.setColor(color);
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            final float s = Math.min(getBounds().width(), getBounds().height()) / 24f;
            mPaint.setStrokeWidth(mStroke);
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(s, s);
            canvas.drawPath(mPath, mPaint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            mPaint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    // ---------------------------------------------------------------------------------

    /**
     * Leading icon of a profile row: a rounded square that, once the row is selected,
     * spins away and gives way to a cookie shape (spinning slowly for a normal profile).
     */
    static final class LeadIcon extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private final ArgbEvaluator mArgb = new ArgbEvaluator();
        private Path mIcon;
        private Path mCookie;
        private float mProgress;     // 0 = square, 1 = cookie
        private boolean mSelected, mSpin;
        private int mSquare, mShape, mIconOff, mIconOn;
        private ValueAnimator mMorph, mLoop;

        LeadIcon(Context c) {
            super(c);
            mIconPaint.setStyle(Paint.Style.STROKE);
            mIconPaint.setStrokeCap(Paint.Cap.ROUND);
            mIconPaint.setStrokeJoin(Paint.Join.ROUND);
            mLoop = loop(this, 16000);
        }

        void setIcon(String data) {
            mIcon = PathParser.createPathFromPathData(data);
            invalidate();
        }

        /** square: colour when not selected; shape: the cookie; icon colours off and on it. */
        void setColors(int square, int shape, int iconOff, int iconOn) {
            mSquare = square;
            mShape = shape;
            mIconOff = iconOff;
            mIconOn = iconOn;
            invalidate();
        }

        void setSpin(boolean spin) {
            mSpin = spin;
            updateLoop();
        }

        void setSelectedShape(boolean selected, boolean animated) {
            if (mSelected == selected && mMorph == null) {
                mProgress = selected ? 1f : 0f;
                invalidate();
                return;
            }
            mSelected = selected;
            if (mMorph != null) mMorph.cancel();
            mMorph = null;
            if (!animated || !motionOn()) {
                mProgress = selected ? 1f : 0f;
                invalidate();
                updateLoop();
                return;
            }
            mMorph = ValueAnimator.ofFloat(mProgress, selected ? 1f : 0f);
            mMorph.setDuration(selected ? 550 : 300);
            mMorph.setInterpolator(selected ? SPRING : EMPHASIZED);
            mMorph.addUpdateListener(a -> {
                mProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            mMorph.start();
            updateLoop();
        }

        private void updateLoop() {
            if (mSelected && mSpin && isAttachedToWindow() && motionOn()) {
                if (!mLoop.isStarted()) mLoop.start();
            } else {
                mLoop.cancel();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateLoop();
        }

        @Override
        protected void onDetachedFromWindow() {
            mLoop.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            final float r = Math.min(w, h) / 2f;
            mCookie = cookie(9, r * 0.86f, r * 0.095f, w / 2f, h / 2f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth(), h = getHeight(), cx = w / 2f, cy = h / 2f;
            final float p = Math.max(0f, mProgress);
            final float sq = Math.min(1f, p);

            // The square shrinks and turns away.
            if (sq < 1f) {
                canvas.save();
                final float s = 1f - 0.6f * sq;
                canvas.rotate(45f * sq, cx, cy);
                canvas.scale(s, s, cx, cy);
                mPaint.setColor(mSquare);
                mPaint.setAlpha(Math.round(255 * (1f - sq)));
                final float rad = w / 3f;
                mRect.set(0, 0, w, h);
                canvas.drawRoundRect(mRect, rad, rad, mPaint);
                canvas.restore();
            }
            // The cookie grows in, overshooting a little.
            if (p > 0f && mCookie != null) {
                canvas.save();
                final float spin = mLoop.isStarted() ? 360f * (float) mLoop.getAnimatedValue() : 0f;
                canvas.rotate(-90f * (1f - Math.min(1f, p)) + spin, cx, cy);
                canvas.scale(p, p, cx, cy);
                mPaint.setColor(mShape);
                mPaint.setAlpha(Math.round(255 * Math.min(1f, p * 1.5f)));
                canvas.drawPath(mCookie, mPaint);
                canvas.restore();
            }
            if (mIcon != null) {
                final float size = w / 2f;
                final float s = size / 24f;
                canvas.save();
                canvas.translate(cx - size / 2f, cy - size / 2f);
                canvas.scale(s, s);
                mIconPaint.setStrokeWidth(2f);
                mIconPaint.setColor((int) mArgb.evaluate(Math.min(1f, sq), mIconOff, mIconOn));
                canvas.drawPath(mIcon, mIconPaint);
                canvas.restore();
            }
        }
    }

    // ---------------------------------------------------------------------------------

    /**
     * The speed dial of the hero card: a 240 degree arc with one tick per profile, filled
     * up to the active one, a ring on the profile waiting for a restart, and the active
     * profile's icon on a slowly turning shape in the middle.
     */
    static final class Gauge extends View {
        private static final float VW = 200f, VH = 172f, CX = 100f, CY = 100f, R = 76f;
        private static final float START = 150f, SWEEP = 240f;

        private final Paint mTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mArc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mOval = new RectF(CX - R, CY - R, CX + R, CY + R);
        private final Path mSunny = cookie(12, 31f, 2.6f, CX, CY);
        private Path mIcon;
        private float mFrac, mRingAlpha;
        private int mLevel, mPending = -1;
        private boolean mOc;
        private int mOnContainer, mPrimary, mError, mOnPrimary, mOnError;
        private ValueAnimator mFracAnim, mRingAnim;
        private final ValueAnimator mLoop;

        Gauge(Context c) {
            super(c);
            mTrack.setStyle(Paint.Style.STROKE);
            mTrack.setStrokeCap(Paint.Cap.ROUND);
            mTrack.setStrokeWidth(14f);
            mArc.setStyle(Paint.Style.STROKE);
            mArc.setStrokeCap(Paint.Cap.ROUND);
            mArc.setStrokeWidth(14f);
            mRing.setStyle(Paint.Style.STROKE);
            mRing.setStrokeWidth(3f);
            mIconPaint.setStyle(Paint.Style.STROKE);
            mIconPaint.setStrokeCap(Paint.Cap.ROUND);
            mIconPaint.setStrokeJoin(Paint.Join.ROUND);
            mIconPaint.setStrokeWidth(2f);
            mLoop = loop(this, 18000);
        }

        void setColors(int onContainer, int primary, int error, int onPrimary, int onError) {
            mOnContainer = onContainer;
            mPrimary = primary;
            mError = error;
            mOnPrimary = onPrimary;
            mOnError = onError;
            invalidate();
        }

        /** level 0..6; pending: the level after a restart, or -1. */
        void setLevel(int level, boolean oc, String icon, int pending, boolean animated) {
            mLevel = level;
            mOc = oc;
            mIcon = PathParser.createPathFromPathData(icon);
            final float target = (level + 0.6f) / 6.6f;
            if (mFracAnim != null) mFracAnim.cancel();
            if (animated && motionOn()) {
                mFracAnim = ValueAnimator.ofFloat(mFrac, target);
                mFracAnim.setDuration(1100);
                mFracAnim.setInterpolator(SPRING_SOFT);
                mFracAnim.addUpdateListener(a -> {
                    mFrac = (float) a.getAnimatedValue();
                    invalidate();
                });
                mFracAnim.start();
            } else {
                mFrac = target;
            }
            final boolean show = pending >= 0 && pending != level;
            if (show) mPending = pending;
            if (mRingAnim != null) mRingAnim.cancel();
            if (animated && motionOn()) {
                mRingAnim = ValueAnimator.ofFloat(mRingAlpha, show ? 1f : 0f);
                mRingAnim.setDuration(300);
                mRingAnim.addUpdateListener(a -> {
                    mRingAlpha = (float) a.getAnimatedValue();
                    invalidate();
                });
                mRingAnim.start();
            } else {
                mRingAlpha = show ? 1f : 0f;
            }
            updateLoop();
            invalidate();
        }

        private void updateLoop() {
            if (mLevel > 0 && isAttachedToWindow() && motionOn()) {
                if (!mLoop.isStarted()) mLoop.start();
            } else {
                mLoop.cancel();
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateLoop();
        }

        @Override
        protected void onDetachedFromWindow() {
            mLoop.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            final int w = MeasureSpec.getSize(wSpec);
            setMeasuredDimension(w, Math.round(w * VH / VW));
        }

        private float tickX(int level, float r) {
            return CX + r * (float) Math.cos(Math.toRadians(START + 40f * level));
        }

        private float tickY(int level, float r) {
            return CY + r * (float) Math.sin(Math.toRadians(START + 40f * level));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float s = getWidth() / VW;
            canvas.save();
            canvas.scale(s, s);
            final int accent = mOc ? mError : mPrimary;

            mTrack.setColor(withAlpha(mOnContainer, 0.14f));
            canvas.drawArc(mOval, START, SWEEP, false, mTrack);
            mArc.setColor(accent);
            canvas.drawArc(mOval, START, SWEEP * Math.max(0.01f, Math.min(1.08f, mFrac)), false, mArc);

            for (int i = 0; i <= 6; i++) {
                final boolean on = i == mLevel;
                if (i >= 5) {
                    mDot.setColor(withAlpha(mError, on ? 1f : 0.7f));
                } else {
                    mDot.setColor(withAlpha(mOnContainer, on ? 1f : 0.35f));
                }
                canvas.drawCircle(tickX(i, R + 22f), tickY(i, R + 22f), on ? 5f : 3.2f, mDot);
            }
            if (mRingAlpha > 0f && mPending >= 0) {
                mRing.setColor(withAlpha(mOnContainer, mRingAlpha));
                canvas.drawCircle(tickX(mPending, R + 22f), tickY(mPending, R + 22f), 10f, mRing);
            }

            canvas.save();
            if (mLoop.isStarted()) canvas.rotate(360f * (float) mLoop.getAnimatedValue(), CX, CY);
            mFill.setColor(accent);
            canvas.drawPath(mSunny, mFill);
            canvas.restore();

            if (mIcon != null) {
                canvas.save();
                canvas.translate(85f, 85f);
                canvas.scale(1.25f, 1.25f);
                mIconPaint.setColor(mOc ? mOnError : mOnPrimary);
                canvas.drawPath(mIcon, mIconPaint);
                canvas.restore();
            }
            canvas.restore();
        }
    }

    static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(255 * alpha * ((color >>> 24) / 255f)) << 24);
    }

    // ---------------------------------------------------------------------------------

    /** Radio button whose dot springs in. */
    static final class Radio extends View {
        private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float mDot;
        private int mOff, mOn;
        private ValueAnimator mAnim;

        Radio(Context c) {
            super(c);
            mRingPaint.setStyle(Paint.Style.STROKE);
        }

        void setColors(int off, int on) {
            mOff = off;
            mOn = on;
            invalidate();
        }

        void setChecked(boolean checked, boolean animated) {
            if (mAnim != null) mAnim.cancel();
            if (!animated || !motionOn()) {
                mDot = checked ? 1f : 0f;
                invalidate();
                return;
            }
            mAnim = ValueAnimator.ofFloat(mDot, checked ? 1f : 0f);
            mAnim.setDuration(checked ? 350 : 200);
            mAnim.setInterpolator(checked ? SPRING : EMPHASIZED);
            mAnim.addUpdateListener(a -> {
                mDot = (float) a.getAnimatedValue();
                invalidate();
            });
            mAnim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float d = getWidth();
            final float stroke = d / 10f;
            mRingPaint.setStrokeWidth(stroke);
            mRingPaint.setColor(mDot > 0.01f ? mOn : mOff);
            canvas.drawCircle(d / 2f, d / 2f, d / 2f - stroke / 2f, mRingPaint);
            if (mDot > 0f) {
                mDotPaint.setColor(mOn);
                canvas.drawCircle(d / 2f, d / 2f, d / 4f * mDot, mDotPaint);
            }
        }
    }

    // ---------------------------------------------------------------------------------

    /** Five capsules showing how strong a profile is. */
    static final class Pills extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private int mFilled, mOn, mOff;

        Pills(Context c) {
            super(c);
        }

        void set(int filled, int on, int off) {
            mFilled = filled;
            mOn = on;
            mOff = off;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float h = getHeight(), unit = h * 3f, wide = h * 4f, gap = h * 0.67f;
            float x = 0;
            for (int i = 0; i < 5; i++) {
                final boolean on = i < mFilled;
                final float w = on ? wide : unit;
                mPaint.setColor(on ? mOn : mOff);
                mRect.set(x, 0, x + w, h);
                canvas.drawRoundRect(mRect, h / 2f, h / 2f, mPaint);
                x += w + gap;
            }
        }
    }

    // ---------------------------------------------------------------------------------

    /** Thin bar for a live clock; glides to each new value. */
    static final class Bar extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();
        private float mFrac;
        private int mTrack, mFill;
        private ValueAnimator mAnim;

        Bar(Context c) {
            super(c);
        }

        void setColors(int track, int fill) {
            mTrack = track;
            mFill = fill;
            invalidate();
        }

        void setFraction(float frac) {
            frac = Math.max(0.04f, Math.min(1f, frac));
            if (mAnim != null) mAnim.cancel();
            if (!motionOn() || !isAttachedToWindow()) {
                mFrac = frac;
                invalidate();
                return;
            }
            mAnim = ValueAnimator.ofFloat(mFrac, frac);
            mAnim.setDuration(900);
            mAnim.setInterpolator(SPRING_SOFT);
            mAnim.addUpdateListener(a -> {
                mFrac = (float) a.getAnimatedValue();
                invalidate();
            });
            mAnim.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth(), h = getHeight();
            mPaint.setColor(mTrack);
            mRect.set(0, 0, w, h);
            canvas.drawRoundRect(mRect, h / 2f, h / 2f, mPaint);
            mPaint.setColor(mFill);
            mRect.set(0, 0, Math.max(h, w * Math.min(1f, mFrac)), h);
            canvas.drawRoundRect(mRect, h / 2f, h / 2f, mPaint);
        }
    }

    // ---------------------------------------------------------------------------------

    /** M3 Expressive loading indicator: a shape that keeps turning and changing form. */
    static final class Loader extends View {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator mLoop;

        Loader(Context c, int color) {
            super(c);
            mPaint.setColor(color);
            mLoop = loop(this, 1600);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (motionOn()) mLoop.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            mLoop.cancel();
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float t = mLoop.isStarted() ? (float) mLoop.getAnimatedValue() : 0f;
            final float w = getWidth(), r = w / 2f * 0.82f;
            // Bumps come and go twice per turn: circle -> cookie -> circle -> soft burst.
            final float amp = r * 0.14f * (float) Math.abs(Math.sin(2 * Math.PI * t));
            final int lobes = t < 0.5f ? 7 : 5;
            final float scale = 1f + 0.06f * (float) Math.sin(4 * Math.PI * t);
            canvas.save();
            canvas.rotate(360f * t, w / 2f, w / 2f);
            canvas.scale(scale, scale, w / 2f, w / 2f);
            canvas.drawPath(cookie(lobes, r - amp * 0.3f, amp, w / 2f, w / 2f), mPaint);
            canvas.restore();
        }
    }
}
