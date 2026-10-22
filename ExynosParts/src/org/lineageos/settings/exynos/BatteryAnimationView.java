/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.exynos;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * The battery on the Charging page status card.
 *
 *  - CHARGING:   the charge is liquid with a moving wave on top and a pulsing bolt.
 *  - BYPASS:     the charge stands still (the battery is held) while a dashed ring of
 *                current flows AROUND the battery: the charger powers the phone directly.
 *  - DISCHARGING / FULL: still picture, no animation (no wasted frames on battery).
 *
 * Level changes glide instead of jumping. Animations stop when the view is off screen
 * and follow the system "remove animations" setting.
 */
public class BatteryAnimationView extends View {

    public enum State { DISCHARGING, CHARGING, BYPASS, FULL }

    // M3 emphasized-decelerate, used for level changes.
    private static final PathInterpolator EMPHASIZED = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private State mState = State.DISCHARGING;
    private float mLevel;          // 0..1, what is drawn
    private float mTargetLevel;    // 0..1, where it is going
    private float mPhase;          // 0..1, loop position of the wave / current ring

    private int mTrackColor, mFillColor, mWaveColor, mGlyphColor, mFlowColor;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mBody = new RectF();
    private final RectF mCap = new RectF();
    private final RectF mRing = new RectF();
    private final Path mBodyPath = new Path();
    private final Path mWavePath = new Path();
    private final Path mRingPath = new Path();
    private final Path mBolt = new Path();

    private ValueAnimator mLoop;
    private ValueAnimator mLevelAnim;
    private boolean mVisible;

    public BatteryAnimationView(Context context) {
        this(context, null);
    }

    public BatteryAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mFillPaint.setStyle(Paint.Style.FILL);
        mWavePaint.setStyle(Paint.Style.FILL);
        mTrackPaint.setStyle(Paint.Style.FILL);
        mGlyphPaint.setStyle(Paint.Style.FILL);
        mFlowPaint.setStyle(Paint.Style.STROKE);
        mFlowPaint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Colours come from the card so the battery always matches it. */
    public void setColors(int track, int fill, int wave, int glyph, int flow) {
        mTrackColor = track;
        mFillColor = fill;
        mWaveColor = wave;
        mGlyphColor = glyph;
        mFlowColor = flow;
        invalidate();
    }

    /** @param percent 0..100, or negative when unknown (drawn empty). */
    public void setBattery(State state, int percent) {
        final float target = Math.max(0, Math.min(100, percent)) / 100f;
        final boolean stateChanged = state != mState;
        mState = state;

        if (!isLaidOut() || !ValueAnimator.areAnimatorsEnabled()) {
            if (mLevelAnim != null) mLevelAnim.cancel();
            mLevel = mTargetLevel = target;
        } else if (target != mTargetLevel) {
            mTargetLevel = target;
            if (mLevelAnim != null) mLevelAnim.cancel();
            mLevelAnim = ValueAnimator.ofFloat(mLevel, target);
            mLevelAnim.setDuration(700);
            mLevelAnim.setInterpolator(EMPHASIZED);
            mLevelAnim.addUpdateListener(a -> {
                mLevel = (float) a.getAnimatedValue();
                invalidate();
            });
            mLevelAnim.start();
        }
        if (stateChanged) updateLoop();
        invalidate();
    }

    // ---- loop animation life cycle --------------------------------------------------

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        mVisible = isVisible;
        updateLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        updateLoop();
        if (mLevelAnim != null) mLevelAnim.cancel();
    }

    private void updateLoop() {
        final boolean animated = mState == State.CHARGING || mState == State.BYPASS;
        final boolean run = animated && mVisible && isAttachedToWindow()
                && ValueAnimator.areAnimatorsEnabled();
        if (!run) {
            if (mLoop != null) {
                mLoop.cancel();
                mLoop = null;
            }
            return;
        }
        final long duration = mState == State.BYPASS ? 1600 : 2600;
        if (mLoop != null && mLoop.getDuration() == duration) return;
        if (mLoop != null) mLoop.cancel();
        mLoop = ValueAnimator.ofFloat(0f, 1f);
        mLoop.setDuration(duration);
        mLoop.setRepeatCount(ValueAnimator.INFINITE);
        mLoop.setInterpolator(new LinearInterpolator());
        mLoop.addUpdateListener(a -> {
            mPhase = (float) a.getAnimatedValue();
            invalidate();
        });
        mLoop.start();
    }

    // ---- geometry -------------------------------------------------------------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        final float pad = ExynosStyle.dp(getContext(), 10);    // room for the current ring
        final float availW = w - getPaddingLeft() - getPaddingRight() - 2 * pad;
        final float availH = h - getPaddingTop() - getPaddingBottom() - 2 * pad;

        // Battery proportions: body 1 : 1.75, cap on top.
        float bodyW = Math.min(availW, availH / 1.9f);
        float bodyH = bodyW * 1.75f;
        final float capH = bodyW * 0.1f;
        final float cx = getPaddingLeft() + pad + availW / 2f;
        final float top = getPaddingTop() + pad + (availH - bodyH - capH) / 2f + capH;

        mBody.set(cx - bodyW / 2f, top, cx + bodyW / 2f, top + bodyH);
        final float capW = bodyW * 0.38f;
        mCap.set(cx - capW / 2f, top - capH, cx + capW / 2f, top + capH * 0.6f);

        final float radius = bodyW * 0.24f;
        mBodyPath.reset();
        mBodyPath.addRoundRect(mBody, radius, radius, Path.Direction.CW);

        // The current ring follows the body at a distance, cap included.
        final float gap = ExynosStyle.dp(getContext(), 6);
        mRing.set(mBody.left - gap, mCap.top - gap, mBody.right + gap, mBody.bottom + gap);
        mRingPath.reset();
        mRingPath.addRoundRect(mRing, radius + gap, radius + gap, Path.Direction.CW);
        mFlowPaint.setStrokeWidth(ExynosStyle.dp(getContext(), 3));

        buildBolt(cx, mBody.centerY(), bodyW * 0.42f);
    }

    /** Material "bolt" glyph, centred at (cx, cy), height h. */
    private void buildBolt(float cx, float cy, float h) {
        final float s = h / 18f;   // glyph drawn on an 11 x 18 grid
        final float x0 = cx - 5.5f * s;
        final float y0 = cy - 9f * s;
        mBolt.reset();
        mBolt.moveTo(x0 + 7f * s, y0);
        mBolt.lineTo(x0, y0 + 10.5f * s);
        mBolt.lineTo(x0 + 5f * s, y0 + 10.5f * s);
        mBolt.lineTo(x0 + 4f * s, y0 + 18f * s);
        mBolt.lineTo(x0 + 11f * s, y0 + 7.5f * s);
        mBolt.lineTo(x0 + 6f * s, y0 + 7.5f * s);
        mBolt.close();
    }

    // ---- drawing --------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBody.isEmpty()) return;

        // Empty battery and its cap.
        mTrackPaint.setColor(mTrackColor);
        canvas.drawRoundRect(mCap, mCap.height() / 2f, mCap.height() / 2f, mTrackPaint);
        canvas.drawPath(mBodyPath, mTrackPaint);

        // Charge: clipped to the body so the wave and corners stay inside it.
        canvas.save();
        canvas.clipPath(mBodyPath);
        final float fillTop = mBody.bottom - mBody.height() * mLevel;
        mFillPaint.setColor(mFillColor);
        if (mState == State.CHARGING && mLevel < 0.995f) {
            final float amp = ExynosStyle.dp(getContext(), 3.5f);
            // A softer second wave behind, half a cycle apart, gives depth.
            mWavePaint.setColor(mWaveColor);
            buildWave(fillTop, amp, mPhase + 0.5f);
            canvas.drawPath(mWavePath, mWavePaint);
            buildWave(fillTop, amp, mPhase);
            canvas.drawPath(mWavePath, mFillPaint);
        } else {
            canvas.drawRect(mBody.left, fillTop, mBody.right, mBody.bottom, mFillPaint);
        }
        canvas.restore();

        if (mState == State.CHARGING) {
            // Bolt breathes gently with the wave.
            final float pulse = 0.9f + 0.1f * (float) Math.sin(mPhase * 2 * Math.PI);
            canvas.save();
            canvas.scale(pulse, pulse, mBody.centerX(), mBody.centerY());
            mGlyphPaint.setColor(mGlyphColor);
            canvas.drawPath(mBolt, mGlyphPaint);
            canvas.restore();
        } else if (mState == State.BYPASS) {
            // Current running around the battery instead of into it.
            final float dash = ExynosStyle.dp(getContext(), 14);
            final float space = ExynosStyle.dp(getContext(), 10);
            mFlowPaint.setPathEffect(new DashPathEffect(new float[] { dash, space },
                    -mPhase * (dash + space) * 3f));
            mFlowPaint.setColor(mFlowColor);
            canvas.drawPath(mRingPath, mFlowPaint);
        }
    }

    /** Liquid surface across the body at height top, one wavelength per body width. */
    private void buildWave(float top, float amp, float phase) {
        final float left = mBody.left;
        final float width = mBody.width();
        final int steps = 24;
        mWavePath.reset();
        mWavePath.moveTo(left, mBody.bottom);
        for (int i = 0; i <= steps; i++) {
            final float x = left + width * i / steps;
            final double angle = 2 * Math.PI * ((double) i / steps + phase);
            mWavePath.lineTo(x, top + amp * (float) Math.sin(angle));
        }
        mWavePath.lineTo(mBody.right, mBody.bottom);
        mWavePath.close();
    }
}
