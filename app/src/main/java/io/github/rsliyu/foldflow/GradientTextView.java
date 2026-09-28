package io.github.rsliyu.foldflow;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.TextView;

/** 文字填充紫→青渐变，用于标题。 */
public final class GradientTextView extends TextView {
    public GradientTextView(Context context) {
        super(context);
    }

    public GradientTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GradientTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        float textWidth = getPaint().measureText(getText().toString());
        float end = Math.max(1f, Math.min(getWidth(), textWidth + getPaddingLeft()));
        getPaint().setShader(new LinearGradient(getPaddingLeft(), 0, end, 0,
                new int[]{0xFFF5F3FF, 0xFFC4B5FD, 0xFF67E8F9},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }
}
