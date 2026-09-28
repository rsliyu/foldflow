package io.github.rsliyu.foldflow;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * 示意图：在折叠屏内屏轮廓里画出应用窗口的样子。
 * 用于内屏比例卡片（各种比例）、分屏说明（左右/上下）和小窗说明。
 */
public final class DiagramView extends View {
    public static final int FULL_OPTIMIZED = 0;
    public static final int FULL = 1;
    public static final int RATIO_4_3 = 2;
    public static final int RATIO_16_9 = 3;
    public static final int RATIO_21_9 = 4;
    public static final int RATIO_20_9 = 5;
    public static final int SPLIT_LEFT_RIGHT = 6;
    public static final int SPLIT_TOP_BOTTOM = 7;
    public static final int FREEFORM = 8;

    /** X Fold3 内屏竖握时的宽高比（2200 × 2480）。 */
    private static final float SCREEN_ASPECT = 2200f / 2480f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF screen = new RectF();
    private final RectF box = new RectF();
    private final float dp;
    private int mode = FULL;

    public DiagramView(Context context) {
        this(context, null);
    }

    public DiagramView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = context.getResources().getDisplayMetrics().density;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DiagramView);
            mode = a.getInt(R.styleable.DiagramView_diagram, FULL);
            a.recycle();
        }
    }

    public void setMode(int mode) {
        this.mode = mode;
        invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) {
            return;
        }
        float sh = Math.min(h, w / SCREEN_ASPECT);
        float sw = sh * SCREEN_ASPECT;
        float left = getPaddingLeft() + (w - sw) / 2f;
        float top = getPaddingTop() + (h - sh) / 2f;
        screen.set(left, top, left + sw, top + sh);
        boolean lit = isActivated() || !isDuplicateParentStateEnabled();
        int alpha = lit ? 255 : 150;
        float radius = sw * 0.08f;

        // 屏幕底板和折痕
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0D1330);
        canvas.drawRoundRect(screen, radius, radius, paint);

        switch (mode) {
            case FULL_OPTIMIZED:
                drawOptimized(canvas, alpha);
                break;
            case FULL:
                fillApp(canvas, screen, radius, alpha, false);
                break;
            case RATIO_4_3:
                drawCentered(canvas, 3f / 4f, alpha, false);
                break;
            case RATIO_16_9:
                drawCentered(canvas, 9f / 16f, alpha, false);
                break;
            case RATIO_21_9:
                drawCentered(canvas, 9f / 21f, alpha, false);
                break;
            case RATIO_20_9:
                drawCentered(canvas, 9f / 20f, alpha, true);
                break;
            case SPLIT_LEFT_RIGHT:
                drawSplit(canvas, true, alpha);
                break;
            case SPLIT_TOP_BOTTOM:
                drawSplit(canvas, false, alpha);
                break;
            case FREEFORM:
                drawFreeform(canvas, alpha);
                break;
            default:
                break;
        }

        // 折痕虚线
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp * 0.8f));
        paint.setColor(0x33FFFFFF);
        paint.setPathEffect(new DashPathEffect(new float[]{dp * 2, dp * 2.5f}, 0));
        canvas.drawLine(screen.centerX(), screen.top + dp * 2, screen.centerX(), screen.bottom - dp * 2, paint);
        paint.setPathEffect(null);

        // 屏幕边框：选中时是渐变描边
        paint.setStrokeWidth(dp * (lit ? 1.6f : 1f));
        if (lit) {
            paint.setColor(0xFFFFFFFF);
            paint.setShader(new LinearGradient(screen.left, screen.top, screen.right, screen.bottom,
                    0xFFA78BFA, 0xFF22D3EE, Shader.TileMode.CLAMP));
        } else {
            paint.setColor(0x40FFFFFF);
        }
        canvas.drawRoundRect(screen, radius, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    private void fillApp(Canvas canvas, RectF rect, float radius, int alpha, boolean pink) {
        paint.setStyle(Paint.Style.FILL);
        int start = pink ? 0xFFF472B6 : 0xFF8B5CF6;
        int end = pink ? 0xFFFB923C : 0xFF22D3EE;
        paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, start, end, Shader.TileMode.CLAMP));
        paint.setAlpha(alpha);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);
        paint.setAlpha(255);
        // 应用里的"内容行"
        paint.setColor(0x40FFFFFF);
        float lineH = Math.max(dp * 1.5f, rect.height() * 0.035f);
        float x = rect.left + rect.width() * 0.14f;
        float y = rect.top + rect.height() * 0.18f;
        for (int i = 0; i < 4 && y + lineH < rect.bottom - rect.height() * 0.1f; i++) {
            float lw = rect.width() * (i == 0 ? 0.55f : 0.72f - i * 0.08f);
            canvas.drawRoundRect(x, y, x + lw, y + lineH, lineH, lineH, paint);
            y += rect.height() * 0.13f;
        }
    }

    private void drawCentered(Canvas canvas, float aspect, int alpha, boolean dashed) {
        float inset = dp * 3;
        float bh = screen.height() - inset * 2;
        float bw = bh * aspect;
        box.set(screen.centerX() - bw / 2f, screen.top + inset, screen.centerX() + bw / 2f, screen.bottom - inset);
        float r = bw * 0.12f;
        if (dashed) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp * 1.4f);
            paint.setColor(0xB3A5F3FC);
            paint.setPathEffect(new DashPathEffect(new float[]{dp * 3, dp * 2.5f}, 0));
            canvas.drawRoundRect(box, r, r, paint);
            paint.setPathEffect(null);
            // 齿轮位置的小圆点，表示"去系统设置"
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(box.centerX(), box.centerY(), dp * 2.2f, paint);
        } else {
            fillApp(canvas, box, r, alpha, false);
        }
    }

    private void drawOptimized(Canvas canvas, int alpha) {
        float inset = dp * 3;
        box.set(screen.left + inset, screen.top + inset, screen.right - inset, screen.bottom - inset);
        float gap = dp * 2.5f;
        float r = screen.width() * 0.05f;
        // 自适应布局：左边导航栏 + 右边两列内容卡片
        RectF nav = new RectF(box.left, box.top, box.left + box.width() * 0.26f, box.bottom);
        paint.setShader(new LinearGradient(nav.left, nav.top, nav.right, nav.bottom, 0xFF6D28D9, 0xFF8B5CF6, Shader.TileMode.CLAMP));
        paint.setAlpha(alpha);
        canvas.drawRoundRect(nav, r, r, paint);
        float colW = (box.right - nav.right - gap * 2) / 2f;
        float x = nav.right + gap;
        for (int c = 0; c < 2; c++) {
            float y = box.top;
            float[] heights = c == 0 ? new float[]{0.42f, 0.55f} : new float[]{0.6f, 0.37f};
            for (float hFrac : heights) {
                float bh = (box.height() - gap) * hFrac;
                RectF card = new RectF(x, y, x + colW, y + bh);
                paint.setShader(new LinearGradient(card.left, card.top, card.right, card.bottom,
                        c == 0 ? 0xFF8B5CF6 : 0xFF0EA5E9, c == 0 ? 0xFF22D3EE : 0xFF22D3EE, Shader.TileMode.CLAMP));
                paint.setAlpha(alpha);
                canvas.drawRoundRect(card, r, r, paint);
                y += bh + gap;
            }
            x += colW + gap;
        }
        paint.setShader(null);
        paint.setAlpha(255);
    }

    private void drawSplit(Canvas canvas, boolean leftRight, int alpha) {
        float inset = dp * 3;
        float gap = dp * 3;
        float r = screen.width() * 0.06f;
        RectF a;
        RectF b;
        if (leftRight) {
            float mid = screen.centerX();
            a = new RectF(screen.left + inset, screen.top + inset, mid - gap / 2f, screen.bottom - inset);
            b = new RectF(mid + gap / 2f, screen.top + inset, screen.right - inset, screen.bottom - inset);
        } else {
            float mid = screen.centerY();
            a = new RectF(screen.left + inset, screen.top + inset, screen.right - inset, mid - gap / 2f);
            b = new RectF(screen.left + inset, mid + gap / 2f, screen.right - inset, screen.bottom - inset);
        }
        fillApp(canvas, a, r, alpha, false);
        fillApp(canvas, b, r, alpha, true);
        // 分隔条的拖动把手
        paint.setColor(0xFFFFFFFF);
        float handle = dp * 7;
        float thick = dp * 2.2f;
        if (leftRight) {
            canvas.drawRoundRect(screen.centerX() - thick / 2, screen.centerY() - handle, screen.centerX() + thick / 2,
                    screen.centerY() + handle, thick, thick, paint);
        } else {
            canvas.drawRoundRect(screen.centerX() - handle, screen.centerY() - thick / 2, screen.centerX() + handle,
                    screen.centerY() + thick / 2, thick, thick, paint);
        }
    }

    private void drawFreeform(Canvas canvas, int alpha) {
        float inset = dp * 3;
        float r = screen.width() * 0.05f;
        // 背后的全屏应用，压暗
        box.set(screen.left + inset, screen.top + inset, screen.right - inset, screen.bottom - inset);
        fillApp(canvas, box, r, alpha / 3, false);
        // 浮动的小窗
        float fw = screen.width() * 0.52f;
        float fh = screen.height() * 0.5f;
        RectF win = new RectF(screen.right - fw - dp * 8, screen.top + screen.height() * 0.22f,
                screen.right - dp * 8, screen.top + screen.height() * 0.22f + fh);
        paint.setColor(0x66000000);
        canvas.drawRoundRect(win.left + dp * 2, win.top + dp * 3, win.right + dp * 2, win.bottom + dp * 3, r, r, paint);
        fillApp(canvas, win, r, alpha, true);
        // 小窗顶部的把手
        paint.setColor(0xCCFFFFFF);
        float hw = win.width() * 0.22f;
        canvas.drawRoundRect(win.centerX() - hw / 2, win.top + dp * 3, win.centerX() + hw / 2, win.top + dp * 5,
                dp, dp, paint);
    }
}
