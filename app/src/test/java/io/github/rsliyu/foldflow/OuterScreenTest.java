package io.github.rsliyu.foldflow;

import android.view.View;
import android.widget.FrameLayout;

import com.android.ide.common.rendering.api.SessionParams;

import org.junit.Rule;
import org.junit.Test;

import app.cash.paparazzi.EnvironmentKt;
import app.cash.paparazzi.Paparazzi;

/** 外屏（单栏 + 底部导航）截图。 */
public class OuterScreenTest {
    @Rule
    public final Paparazzi paparazzi = new Paparazzi(EnvironmentKt.detectEnvironment(), Fixtures.OUTER,
            Fixtures.THEME, SessionParams.RenderingMode.NORMAL, false, 0.1);

    private View render(UiState state) {
        View root = paparazzi.inflate(R.layout.activity_main);
        new MainScreen(paparazzi.getContext(), root, Fixtures.noop()).render(state);
        return root;
    }

    @Test
    public void apps() {
        paparazzi.snapshot(render(Fixtures.sample(UiState.TAB_APPS)), "apps");
    }

    @Test
    public void ratio() {
        paparazzi.snapshot(render(Fixtures.sample(UiState.TAB_RATIO)), "ratio");
    }

    @Test
    public void split() {
        paparazzi.snapshot(render(Fixtures.sample(UiState.TAB_SPLIT)), "split");
    }

    @Test
    public void window() {
        UiState state = Fixtures.sample(UiState.TAB_WINDOW);
        state.smallWindowEnabled = true;
        state.switchesBackedUp = true;
        paparazzi.snapshot(render(state), "window");
    }

    @Test
    public void tools() {
        paparazzi.snapshot(render(Fixtures.sample(UiState.TAB_TOOLS)), "tools");
    }

    @Test
    public void running() {
        UiState state = Fixtures.sample(UiState.TAB_SPLIT);
        state.task = UiState.Task.RUNNING;
        state.taskTitle = "修复全部 128 个应用的分屏";
        state.taskMessage = "修复分屏资格：哔哩哔哩";
        paparazzi.snapshot(render(state), "running", 500);
    }

    @Test
    public void notReady() {
        UiState state = Fixtures.sample(UiState.TAB_RATIO);
        state.shizuku = UiState.Shizuku.NOT_GRANTED;
        state.rebootPending = true;
        state.selected.clear();
        paparazzi.snapshot(render(state), "not_ready");
    }

    @Test
    public void hero() {
        FrameLayout frame = new FrameLayout(paparazzi.getContext());
        frame.setBackgroundResource(R.drawable.bg_app);
        FoldHeroView hero = new FoldHeroView(paparazzi.getContext());
        float d = paparazzi.getContext().getResources().getDisplayMetrics().density;
        frame.addView(hero, new FrameLayout.LayoutParams(Math.round(340 * d), Math.round(280 * d),
                android.view.Gravity.CENTER));
        paparazzi.snapshot(frame, "hero");
    }

    @Test
    public void launcherIcon() {
        paparazzi.snapshot(new Fixtures.IconPreview(paparazzi.getContext()), "icon");
    }
}
