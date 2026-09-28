package io.github.rsliyu.foldflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 界面要显示的全部数据。Activity 修改它，MainScreen 按它渲染；
 * 折叠/展开导致界面重建时，只需用同一份状态重新渲染。
 */
final class UiState {
    static final int TAB_APPS = 0;
    static final int TAB_RATIO = 1;
    static final int TAB_SPLIT = 2;
    static final int TAB_WINDOW = 3;
    static final int TAB_TOOLS = 4;

    enum Filter { USER, ALL, MODIFIED, SELECTED }

    enum Shizuku { NOT_RUNNING, NOT_GRANTED, READY }

    enum Task { IDLE, RUNNING, SUCCESS, FAILURE }

    static final class RatioChoice {
        final String label;
        final String caption;
        final int value;
        final int diagram;

        RatioChoice(String label, String caption, int value, int diagram) {
            this.label = label;
            this.caption = caption;
            this.value = value;
            this.diagram = diagram;
        }
    }

    /** 跳转系统设置手动选择的比例。 */
    static final int RATIO_OPEN_SYSTEM_SETTINGS = -2;

    static final RatioChoice[] RATIO_CHOICES = {
            new RatioChoice("全屏布局优化", "推荐先试", PrivilegedShellService.RATIO_FULL_OPTIMIZATION, DiagramView.FULL_OPTIMIZED),
            new RatioChoice("全屏", "铺满内屏", 0, DiagramView.FULL),
            new RatioChoice("4:3", "居中显示", 1, DiagramView.RATIO_4_3),
            new RatioChoice("16:9", "居中显示", 2, DiagramView.RATIO_16_9),
            new RatioChoice("与外屏相同", "21:9", 3, DiagramView.RATIO_21_9),
            new RatioChoice("20:9", "跳转系统设置", RATIO_OPEN_SYSTEM_SETTINGS, DiagramView.RATIO_20_9),
    };

    final List<AppChoice> allApps = new ArrayList<>();
    final Set<String> selected = new LinkedHashSet<>();
    final Map<String, Integer> ratioMap = new HashMap<>();
    final Map<String, FoldOps.SplitState> splitStates = new HashMap<>();
    Set<String> repairedSplit = new HashSet<>();
    Set<String> optimized = new HashSet<>();
    boolean appsLoaded;

    Filter filter = Filter.USER;
    String query = "";
    int tab = TAB_APPS;

    Shizuku shizuku = Shizuku.NOT_RUNNING;
    String shizukuMode = "";
    boolean rebootPending;

    int ratioChoice;
    String manualRatio = "";
    boolean relaunchAfterRatio = true;
    boolean autoReapply;
    boolean switchesBackedUp;
    boolean smallWindowEnabled;

    Task task = Task.IDLE;
    String taskTitle = "";
    String taskMessage = "";
    boolean resultVisible;

    final StringBuilder log = new StringBuilder();
    String versionName = "";

    boolean busy() {
        return task == Task.RUNNING;
    }

    boolean isModified(AppChoice app) {
        return ratioMap.containsKey(app.packageName) || repairedSplit.contains(app.packageName);
    }

    List<AppChoice> selectedApps() {
        List<AppChoice> apps = new ArrayList<>();
        for (AppChoice app : allApps) {
            if (selected.contains(app.packageName)) {
                apps.add(app);
            }
        }
        return apps;
    }
}
