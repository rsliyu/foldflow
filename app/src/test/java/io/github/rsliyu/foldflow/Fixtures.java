package io.github.rsliyu.foldflow;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.android.resources.Density;
import com.android.resources.ScreenOrientation;

import app.cash.paparazzi.DeviceConfig;

/** 截图测试共用的设备参数和示例数据。 */
final class Fixtures {
    /**
     * vivo X Fold3：外屏 1172×2748，内屏 2200×2480。
     * 按 2 倍密度渲染（图片小一些），dp 尺寸与真机 3 倍密度时相同：外屏约 390dp 宽，内屏约 733dp 宽。
     */
    static final DeviceConfig OUTER = device(780, 1832);
    static final DeviceConfig INNER = device(1466, 1652);
    static final String THEME = "AppTheme";

    private Fixtures() {
    }

    private static DeviceConfig device(int widthPx, int heightPx) {
        DeviceConfig base = DeviceConfig.PIXEL_9;
        ScreenOrientation orientation = widthPx > heightPx ? ScreenOrientation.LANDSCAPE : ScreenOrientation.PORTRAIT;
        return new DeviceConfig(heightPx, widthPx, 320, 320, orientation, base.getUiMode(),
                base.getNightMode(), Density.XHIGH, 1f, base.getLayoutDirection(), "zh-rCN", base.getRatio(),
                base.getSize(), base.getKeyboard(), base.getTouchScreen(), base.getKeyboardState(),
                base.getSoftButtons(), base.getNavigation(), base.getScreenRound(), base.getReleased());
    }

    static UiState sample(int tab) {
        UiState s = new UiState();
        s.versionName = "3.0.0";
        s.appsLoaded = true;
        s.shizuku = UiState.Shizuku.READY;
        s.shizukuMode = "ADB";
        app(s, "番茄小说", "com.dragon.read", false, "番", 0xFFFF8A3D, 0xFFE8363C);
        app(s, "哔哩哔哩", "tv.danmaku.bili", false, "B", 0xFF4FC3F7, 0xFFFB7299);
        app(s, "小红书", "com.xingin.xhs", false, "红", 0xFFFF5A6E, 0xFFD6204A);
        app(s, "微信读书", "com.tencent.weread", false, "读", 0xFF3D8BFF, 0xFF1F5FD6);
        app(s, "网易云音乐", "com.netease.cloudmusic", false, "云", 0xFFFF4B4B, 0xFFB31B1B);
        app(s, "高德地图", "com.autonavi.minimap", false, "高", 0xFF3FC1FF, 0xFF1D7CF2);
        app(s, "知乎", "com.zhihu.android", false, "知", 0xFF1E90FF, 0xFF0A5BD6);
        app(s, "美团", "com.sankuai.meituan", false, "美", 0xFFFFD43B, 0xFFF5A300);
        app(s, "设置", "com.android.settings", true, "设", 0xFF9AA5B8, 0xFF5B6478);
        app(s, "相机", "com.android.camera", true, "相", 0xFF7C8799, 0xFF3E4656);

        s.selected.add("com.dragon.read");
        s.selected.add("com.xingin.xhs");
        s.ratioMap.put("com.dragon.read", 0);
        s.optimized.add("com.dragon.read");
        s.ratioMap.put("tv.danmaku.bili", 1);
        s.ratioMap.put("com.netease.cloudmusic", 3);
        s.splitStates.put("com.dragon.read", FoldOps.SplitState.BOTH);
        s.splitStates.put("com.xingin.xhs", FoldOps.SplitState.PARTIAL);
        s.repairedSplit.add("com.dragon.read");
        s.repairedSplit.add("com.tencent.weread");

        s.log.append("[21:08:12] ▶ 修复 2 个应用的分屏\n")
                .append("[21:08:12] 修复分屏资格：番茄小说\n")
                .append("[21:08:13] 修复分屏资格：小红书\n")
                .append("[21:08:13] ✓ 已处理 2 个应用的原生分屏/上下分屏资格。\n")
                .append("[21:09:40] ▶ 设置内屏比例「全屏布局优化」\n")
                .append("[21:09:41] 正在设置「番茄小说」的内屏显示比例…\n")
                .append("[21:09:42] ✓ 已将「番茄小说」的内屏显示比例设为「全屏布局优化」，并重启该应用使其生效。");
        s.tab = tab;
        return s;
    }

    private static void app(UiState s, String label, String pkg, boolean system, String letter, int start, int end) {
        s.allApps.add(AppChoice.of(label, pkg, system, new LetterIcon(letter, start, end)));
    }

    static MainScreen.Listener noop() {
        return new MainScreen.Listener() {
            @Override
            public void onTabSelected(int tab) {
            }

            @Override
            public void onQueryChanged(String query) {
            }

            @Override
            public void onFilterChanged(UiState.Filter filter) {
            }

            @Override
            public void onToggleApp(String packageName) {
            }

            @Override
            public void onSelectVisible(java.util.List<AppChoice> visible) {
            }

            @Override
            public void onClearSelection() {
            }

            @Override
            public void onRatioChoice(int index) {
            }

            @Override
            public void onManualRatioChanged(String text) {
            }

            @Override
            public void onRelaunchChanged(boolean enabled) {
            }

            @Override
            public void onAutoReapplyChanged(boolean enabled) {
            }

            @Override
            public void onAction(int actionId) {
            }

            @Override
            public void onDismissTask() {
            }
        };
    }

    /** 示例应用图标：渐变圆角方块 + 一个字。 */
    static final class LetterIcon extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String letter;
        private final int start;
        private final int end;

        LetterIcon(String letter, int start, int end) {
            this.letter = letter;
            this.start = start;
            this.end = end;
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float r = b.width() * 0.26f;
            paint.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, start, end, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(b), r, r, paint);
            paint.setShader(null);
            paint.setColor(0xFFFFFFFF);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(b.height() * 0.46f);
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(letter, b.exactCenterX(), b.exactCenterY() - (fm.ascent + fm.descent) / 2f, paint);
        }

        @Override
        public int getIntrinsicWidth() {
            return 144;
        }

        @Override
        public int getIntrinsicHeight() {
            return 144;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public ConstantState getConstantState() {
            return new ConstantState() {
                @Override
                public Drawable newDrawable() {
                    return new LetterIcon(letter, start, end);
                }

                @Override
                public int getChangingConfigurations() {
                    return 0;
                }
            };
        }
    }

    /** 横幅：宽 800dp、高 320dp，放在 README 顶部。 */
    static final DeviceConfig BANNER = device(1600, 640);

    /** 单个圆角方形的启动图标。 */
    static final class IconBadge extends View {
        private final Drawable background;
        private final Drawable foreground;

        IconBadge(android.content.Context context) {
            super(context);
            background = context.getDrawable(R.drawable.ic_launcher_background);
            foreground = context.getDrawable(R.drawable.ic_launcher_foreground);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float size = Math.min(getWidth(), getHeight());
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float layer = size * 108f / 72f;
            android.graphics.Path mask = new android.graphics.Path();
            mask.addRoundRect(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f,
                    size * 0.3f, size * 0.3f, android.graphics.Path.Direction.CW);
            canvas.save();
            canvas.clipPath(mask);
            Rect bounds = new Rect(Math.round(cx - layer / 2f), Math.round(cy - layer / 2f),
                    Math.round(cx + layer / 2f), Math.round(cy + layer / 2f));
            background.setBounds(bounds);
            background.draw(canvas);
            foreground.setBounds(bounds);
            foreground.draw(canvas);
            canvas.restore();
        }
    }

    /** 把自适应图标的前景/背景按不同桌面遮罩画出来，模拟桌面上的样子。 */
    static final class IconPreview extends View {
        private final Drawable background;
        private final Drawable foreground;
        private final Drawable monochrome;
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconPreview(android.content.Context context) {
            super(context);
            background = context.getDrawable(R.drawable.ic_launcher_background);
            foreground = context.getDrawable(R.drawable.ic_launcher_foreground);
            monochrome = context.getDrawable(R.drawable.ic_launcher_monochrome);
            text.setColor(0xFFA6AED3);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            Paint bg = new Paint();
            bg.setShader(new LinearGradient(0, 0, w, h, 0xFF1D2A5A, 0xFF3A1F5C, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, bg);

            // 大图标（圆角方形遮罩，接近 OriginOS 的图标形状）
            float big = Math.min(w * 0.42f, h * 0.46f);
            drawIcon(canvas, w / 2f, h * 0.33f, big, false, false);
            text.setTextSize(big * 0.13f);
            text.setColor(0xFFF2F4FF);
            canvas.drawText("折叠多窗", w / 2f, h * 0.33f + big * 0.5f + big * 0.2f, text);

            // 三种桌面形状：圆形、圆角方形、主题图标
            float small = w * 0.17f;
            float y = h * 0.78f;
            String[] labels = {"圆形", "圆角方形", "主题图标"};
            for (int i = 0; i < 3; i++) {
                float cx = w * (0.22f + 0.28f * i);
                drawIcon(canvas, cx, y, small, i == 0, i == 2);
                text.setTextSize(small * 0.17f);
                text.setColor(0xFFA6AED3);
                canvas.drawText(labels[i], cx, y + small * 0.5f + small * 0.3f, text);
            }
        }

        private void drawIcon(Canvas canvas, float cx, float cy, float size, boolean circle, boolean themed) {
            // 自适应图标：108 单位的图层里，中间 72 单位是可见区域
            float layer = size * 108f / 72f;
            android.graphics.Path mask = new android.graphics.Path();
            if (circle) {
                mask.addCircle(cx, cy, size / 2f, android.graphics.Path.Direction.CW);
            } else {
                mask.addRoundRect(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f,
                        size * 0.3f, size * 0.3f, android.graphics.Path.Direction.CW);
            }
            canvas.save();
            canvas.clipPath(mask);
            Rect bounds = new Rect(Math.round(cx - layer / 2f), Math.round(cy - layer / 2f),
                    Math.round(cx + layer / 2f), Math.round(cy + layer / 2f));
            if (themed) {
                Paint p = new Paint();
                p.setColor(0xFF2B2F45);
                canvas.drawRect(bounds, p);
                monochrome.setBounds(bounds);
                monochrome.setTint(0xFFC9D2FF);
                monochrome.draw(canvas);
            } else {
                background.setBounds(bounds);
                background.draw(canvas);
                foreground.setBounds(bounds);
                foreground.draw(canvas);
            }
            canvas.restore();
        }
    }
}
