package io.github.rsliyu.foldflow;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.ide.common.rendering.api.SessionParams;

import org.junit.Rule;
import org.junit.Test;

import app.cash.paparazzi.EnvironmentKt;
import app.cash.paparazzi.Paparazzi;

/** README 顶部的横幅：图标 + 标题 + 折叠屏插画。 */
public class BannerTest {
    @Rule
    public final Paparazzi paparazzi = new Paparazzi(EnvironmentKt.detectEnvironment(), Fixtures.BANNER,
            Fixtures.THEME, SessionParams.RenderingMode.NORMAL, false, 0.1);

    @Test
    public void banner() {
        Context context = paparazzi.getContext();
        float d = context.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackgroundResource(R.drawable.bg_app);
        root.setPadding(px(56, d), 0, px(32, d), 0);

        root.addView(new Fixtures.IconBadge(context), new LinearLayout.LayoutParams(px(132, d), px(132, d)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginStart(px(32, d));
        root.addView(text, textParams);

        TextView eyebrow = new TextView(context);
        eyebrow.setText("FOLDFLOW · 多窗工作台");
        eyebrow.setTextColor(context.getColor(R.color.cyan));
        eyebrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        eyebrow.setLetterSpacing(0.18f);
        eyebrow.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        text.addView(eyebrow);

        GradientTextView title = new GradientTextView(context);
        title.setText("折叠多窗");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setTextColor(0xFFFFFFFF);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = px(10, d);
        text.addView(title, titleParams);

        TextView subtitle = new TextView(context);
        subtitle.setText("全应用分屏 · 全应用小窗 · 内屏比例");
        subtitle.setTextColor(context.getColor(R.color.text_secondary));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = px(14, d);
        text.addView(subtitle, subtitleParams);

        root.addView(new FoldHeroView(context), new LinearLayout.LayoutParams(px(210, d), px(220, d)));
        paparazzi.snapshot(root, "banner");
    }

    private static int px(int dp, float density) {
        return Math.round(dp * density);
    }
}
