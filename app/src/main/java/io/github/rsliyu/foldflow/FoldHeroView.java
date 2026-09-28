package io.github.rsliyu.foldflow;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;

/**
 * 顶部插画：一台展开的折叠屏，带透视。左屏紫色，右屏上下分成青、粉两块（分屏），
 * 中间折痕发光。屏幕会缓慢"呼吸式"开合，一道高光从左往右扫过。
 */
public final class FoldHeroView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path left = new Path();
    private final Path rightTop = new Path();
    private final Path rightBottom = new Path();
    private final Path panels = new Path();

    /** 0 = 微微合拢，1 = 完全展开。 */
    private float openness = 0.7f;
    /** 高光扫过的位置，0..1。 */
    private float sweep = 0.35f;
    private ValueAnimator foldAnimator;
    private ValueAnimator sweepAnimator;

    public FoldHeroView(Context context) {
        super(context);
    }

    public FoldHeroView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FoldHeroView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float cx = w / 2f;
        float top = h * 0.12f;
        float bottom = h * 0.86f;
        float panelHeight = bottom - top;
        float panelWidth = Math.min(w * 0.4f, panelHeight * 0.52f);
        float gap = Math.max(1.5f, w * 0.018f);

        // 展开程度决定外侧边的收缩：越合拢，外侧越窄、上下越往里收
        float outerWidth = panelWidth * (0.8f + 0.2f * openness);
        float inset = panelHeight * (0.1f - 0.06f * openness);
        float r = w * 0.02f;

        // 底部光晕
        glow.setShader(new RadialGradient(cx, bottom, w * 0.46f,
                new int[]{0x668B5CF6, 0x2222D3EE, 0x00000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawOval(cx - w * 0.46f, bottom - h * 0.12f, cx + w * 0.46f, bottom + h * 0.12f, glow);

        // 左屏
        buildQuad(left, cx - gap, top, cx - gap - outerWidth, top + inset, bottom - inset, bottom, r);
        fill.setShader(new LinearGradient(cx - gap - outerWidth, top, cx - gap, bottom,
                new int[]{0xFFDDD6FE, 0xFF9D74FA, 0xFF6D28D9}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(left, fill);

        // 右屏上下两块
        float mid = (top + bottom) / 2f;
        float splitGap = gap * 0.9f;
        float rx = cx + gap;
        float farX = rx + outerWidth;
        buildHalf(rightTop, rx, top, farX, top + inset, mid - splitGap, true, r);
        fill.setShader(new LinearGradient(rx, top, farX, mid,
                new int[]{0xFFCFFAFE, 0xFF22D3EE, 0xFF0891B2}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(rightTop, fill);
        buildHalf(rightBottom, rx, bottom, farX, bottom - inset, mid + splitGap, false, r);
        fill.setShader(new LinearGradient(rx, mid, farX, bottom,
                new int[]{0xFFFBCFE8, 0xFFF472B6, 0xFFBE185D}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawPath(rightBottom, fill);

        // 扫光：只画在屏幕区域内
        panels.reset();
        panels.addPath(left);
        panels.addPath(rightTop);
        panels.addPath(rightBottom);
        canvas.save();
        canvas.clipPath(panels);
        float bandCenter = (cx - gap - outerWidth) + (2 * gap + 2 * outerWidth + w * 0.3f) * sweep - w * 0.15f;
        float band = w * 0.16f;
        fill.setShader(new LinearGradient(bandCenter - band, 0, bandCenter + band, 0,
                new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, fill);
        canvas.restore();

        // 折痕：由宽到窄三层叠出发光效果
        float creaseTop = top - h * 0.05f;
        float creaseBottom = bottom + h * 0.05f;
        int[] creaseColors = {0x0022D3EE, 0x1A22D3EE, 0x0022D3EE};
        line.setShader(new LinearGradient(0, creaseTop, 0, creaseBottom,
                new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        line.setStrokeCap(Paint.Cap.ROUND);
        glow.setShader(new LinearGradient(cx - gap * 4, 0, cx + gap * 4, 0,
                new int[]{creaseColors[0], 0x8822D3EE, creaseColors[2]}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(cx - gap * 4, creaseTop, cx + gap * 4, creaseBottom, glow);
        line.setStrokeWidth(Math.max(1f, gap * 0.8f));
        canvas.drawLine(cx, creaseTop, cx, creaseBottom, line);

        // 右上角的小星点
        float spark = 0.5f + 0.5f * openness;
        drawSpark(canvas, w * 0.9f, h * 0.14f, w * 0.035f * spark, 0xFFFFFFFF);
        drawSpark(canvas, w * 0.1f, h * 0.8f, w * 0.022f * (1.4f - spark), 0xFFA5F3FC);
    }

    private void drawSpark(Canvas canvas, float x, float y, float size, int color) {
        fill.setShader(null);
        fill.setColor(color);
        Path p = new Path();
        p.moveTo(x, y - size * 2);
        p.quadTo(x, y, x + size * 2, y);
        p.quadTo(x, y, x, y + size * 2);
        p.quadTo(x, y, x - size * 2, y);
        p.quadTo(x, y, x, y - size * 2);
        canvas.drawPath(p, fill);
    }

    /** 四边形：内侧边完整高度，外侧边上下各收 inset，四角做小圆角。 */
    private static void buildQuad(Path path, float innerX, float innerTop, float outerX, float outerTop,
                                  float outerBottom, float innerBottom, float r) {
        path.reset();
        path.moveTo(innerX, innerTop + r);
        path.lineTo(innerX, innerBottom - r);
        path.quadTo(innerX, innerBottom, innerX - Math.signum(innerX - outerX) * r, innerBottom - r * 0.2f);
        path.lineTo(outerX + Math.signum(innerX - outerX) * r, outerBottom);
        path.quadTo(outerX, outerBottom, outerX, outerBottom - r);
        path.lineTo(outerX, outerTop + r);
        path.quadTo(outerX, outerTop, outerX + Math.signum(innerX - outerX) * r, outerTop);
        path.lineTo(innerX - Math.signum(innerX - outerX) * r, innerTop + r * 0.2f);
        path.quadTo(innerX, innerTop, innerX, innerTop + r);
        path.close();
    }

    /** 右屏的一半：upper 时从顶边到中线，否则从中线到底边。 */
    private static void buildHalf(Path path, float innerX, float innerEdgeY, float outerX, float outerEdgeY,
                                  float midY, boolean upper, float r) {
        float dir = upper ? 1 : -1;
        path.reset();
        path.moveTo(innerX, innerEdgeY + dir * r);
        path.quadTo(innerX, innerEdgeY, innerX + r, innerEdgeY + dir * r * 0.2f);
        path.lineTo(outerX - r, outerEdgeY);
        path.quadTo(outerX, outerEdgeY, outerX, outerEdgeY + dir * r);
        path.lineTo(outerX, midY);
        path.lineTo(innerX, midY);
        path.close();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    private void startAnimation() {
        if (foldAnimator != null) {
            return;
        }
        foldAnimator = ValueAnimator.ofFloat(0.55f, 1f);
        foldAnimator.setDuration(3200);
        foldAnimator.setRepeatMode(ValueAnimator.REVERSE);
        foldAnimator.setRepeatCount(ValueAnimator.INFINITE);
        foldAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        foldAnimator.addUpdateListener(a -> {
            openness = (float) a.getAnimatedValue();
            invalidate();
        });
        sweepAnimator = ValueAnimator.ofFloat(-0.2f, 1.4f);
        sweepAnimator.setDuration(4200);
        sweepAnimator.setRepeatCount(ValueAnimator.INFINITE);
        sweepAnimator.setInterpolator(new LinearInterpolator());
        sweepAnimator.addUpdateListener(a -> sweep = (float) a.getAnimatedValue());
        foldAnimator.start();
        sweepAnimator.start();
    }

    private void stopAnimation() {
        if (foldAnimator != null) {
            foldAnimator.cancel();
            sweepAnimator.cancel();
            foldAnimator = null;
            sweepAnimator = null;
        }
    }
}
