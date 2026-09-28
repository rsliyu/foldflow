package io.github.rsliyu.foldflow;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** 列表里的状态小标签：圆角半透明底 + 同色文字。 */
final class TagSpan extends ReplacementSpan {
    private final int color;
    private final float padding;
    private final float margin;
    private final float radius;
    private final RectF rect = new RectF();

    TagSpan(int color, float density) {
        this.color = color;
        this.padding = 6 * density;
        this.margin = 5 * density;
        this.radius = 6 * density;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            fm.ascent = metrics.ascent - Math.round(padding / 2);
            fm.top = fm.ascent;
            fm.descent = metrics.descent + Math.round(padding / 2);
            fm.bottom = fm.descent;
        }
        return Math.round(paint.measureText(text, start, end) + padding * 2 + margin);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom,
                     Paint paint) {
        float width = paint.measureText(text, start, end);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        rect.set(x, y + metrics.ascent - padding / 3, x + width + padding * 2, y + metrics.descent + padding / 3);
        int oldColor = paint.getColor();
        paint.setColor((color & 0x00FFFFFF) | 0x2E000000);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setColor(color);
        canvas.drawText(text, start, end, x + padding, y, paint);
        paint.setColor(oldColor);
    }
}
