package io.github.rsliyu.foldflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** 细长进度条：执行中是一段渐变光带来回流动，结束后整条变成绿色或红色。 */
public final class GlowProgressView extends View {
    public static final int RUNNING = 0;
    public static final int SUCCESS = 1;
    public static final int FAILURE = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int state = RUNNING;
    private float phase;
    private ValueAnimator animator;

    public GlowProgressView(Context context) {
        super(context);
    }

    public GlowProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setState(int state) {
        if (this.state == state) {
            return;
        }
        this.state = state;
        if (state == RUNNING) {
            start();
        } else {
            stop();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        paint.setShader(null);
        paint.setColor(0x1FFFFFFF);
        canvas.drawRoundRect(0, 0, w, h, r, r, paint);
        if (state == SUCCESS || state == FAILURE) {
            int color = state == SUCCESS ? 0xFF4ADE80 : 0xFFF87171;
            paint.setColor(color);
            canvas.drawRoundRect(0, 0, w, h, r, r, paint);
            return;
        }
        paint.setColor(0xFFFFFFFF);
        float segment = w * 0.38f;
        // 相位错开 0.35：动画刚开始的第一帧光带就在可见区域内
        float start = -segment + (w + segment) * ((phase + 0.35f) % 1f);
        paint.setShader(new LinearGradient(start, 0, start + segment, 0,
                new int[]{0x008B5CF6, 0xFF8B5CF6, 0xFF22D3EE, 0x00F472B6}, new float[]{0f, 0.35f, 0.75f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(Math.max(0, start), 0, Math.min(w, start + segment), h, r, r, paint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (state == RUNNING) {
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
        if (isVisible && state == RUNNING) {
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
        animator.setDuration(1300);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
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
    }
}
