package io.github.rsliyu.foldflow;

import android.view.View;

import com.android.ide.common.rendering.api.SessionParams;

import org.junit.Rule;
import org.junit.Test;

import app.cash.paparazzi.EnvironmentKt;
import app.cash.paparazzi.Paparazzi;

/** 内屏展开（左右双栏）截图。 */
public class InnerScreenTest {
    @Rule
    public final Paparazzi paparazzi = new Paparazzi(EnvironmentKt.detectEnvironment(), Fixtures.INNER,
            Fixtures.THEME, SessionParams.RenderingMode.NORMAL, false, 0.1);

    private View render(UiState state) {
        View root = paparazzi.inflate(R.layout.activity_main);
        new MainScreen(paparazzi.getContext(), root, Fixtures.noop()).render(state);
        return root;
    }

    @Test
    public void ratio() {
        paparazzi.snapshot(render(Fixtures.sample(UiState.TAB_RATIO)), "ratio");
    }

    @Test
    public void split() {
        UiState state = Fixtures.sample(UiState.TAB_SPLIT);
        state.task = UiState.Task.SUCCESS;
        state.taskTitle = "完成";
        state.taskMessage = "已处理 2 个应用的原生分屏/上下分屏资格。";
        state.resultVisible = true;
        paparazzi.snapshot(render(state), "split", 500);
    }

    @Test
    public void window() {
        paparazzi.snapshot(render(Fixtures.sample(UiState.TAB_WINDOW)), "window");
    }

    @Test
    public void tools() {
        paparazzi.snapshot(render(Fixtures.sample(UiState.TAB_TOOLS)), "tools");
    }
}
