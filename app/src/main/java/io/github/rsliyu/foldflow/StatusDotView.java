package io.github.rsliyu.foldflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** 状态指示灯：实心圆点，连接正常时向外扩散一圈光晕。动画随可见性自动启停。 */
public final class StatusDotView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = 0xFFF87171;
    private boolean pulsing;
    private float phase;
    private ValueAnimator animator;

    public StatusDotView(Context context) {
        super(context);
    }

    public StatusDotView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setStatus(int color, boolean pulsing) {
        this.color = color;
        if (this.pulsing != pulsing) {
            this.pulsing = pulsing;
            if (pulsing) {
                start();
            } else {
                stop();
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float max = Math.min(cx, cy);
        float core = max * 0.42f;
        if (pulsing) {
            paint.setColor(color);
            paint.setAlpha(Math.round(110 * (1f - phase)));
            canvas.drawCircle(cx, cy, core + (max - core) * phase, paint);
        }
        paint.setColor(color);
        paint.setAlpha(255);
        canvas.drawCircle(cx, cy, core, paint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (pulsing) {
            start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible && pulsing) {
            start();
        } else {
            stop();
        }
    }

    private void start() {
        if (animator != null || !isAttachedToWindow()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        phase = 0f;
    }
}
