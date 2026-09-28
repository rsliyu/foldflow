package io.github.rsliyu.foldflow;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把 UiState 渲染到当前布局上，并把用户操作转给 Listener。
 * 外屏用单栏布局（底部导航），内屏展开后用 layout-w600dp 的双栏布局，两者使用相同的控件 id。
 */
final class MainScreen {
    interface Listener {
        void onTabSelected(int tab);

        void onQueryChanged(String query);

        void onFilterChanged(UiState.Filter filter);

        void onToggleApp(String packageName);

        void onSelectVisible(List<AppChoice> visible);

        void onClearSelection();

        void onRatioChoice(int index);

        void onManualRatioChanged(String text);

        void onRelaunchChanged(boolean enabled);

        void onAutoReapplyChanged(boolean enabled);

        void onAction(int actionId);

        void onDismissTask();
    }

    private static final int[] TAB_IDS = {R.id.navApps, R.id.navRatio, R.id.navSplit, R.id.navWindow, R.id.navTools};
    private static final int[] PANEL_IDS = {R.id.panelApps, R.id.panelRatio, R.id.panelSplit, R.id.panelWindow, R.id.panelTools};
    private static final int[] ACTION_IDS = {
            R.id.applyInnerRatioButton, R.id.restoreRatioButton, R.id.repairSelectedButton, R.id.repairAllButton,
            R.id.checkSplitButton, R.id.removeSplitButton, R.id.enableSmallWindowsButton,
            R.id.restoreSmallWindowsButton, R.id.reapplyButton, R.id.bannerReapplyButton, R.id.diagnosticsButton,
    };
    private static final int[] PLAIN_ACTION_IDS = {
            R.id.shizukuPill, R.id.copyLogButton, R.id.shareLogButton, R.id.clearLogButton, R.id.repoButton,
            R.id.taskShowLog,
    };

    private final Context context;
    private final View root;
    private final Listener listener;
    private final boolean twoPane;
    private final float density;
    private boolean binding;

    private final StatusDotView shizukuDot;
    private final TextView shizukuText;
    private final View rebootBanner;
    private final EditText appFilter;
    private final RadioGroup filterGroup;
    private final TextView appCountText;
    private final ListView appList;
    private final TextView appEmpty;
    private final AppListAdapter adapter;
    private final List<AppChoice> visibleApps = new ArrayList<>();
    private final RadioGroup tabBar;
    private final View[] panels = new View[PANEL_IDS.length];
    private final List<View> ratioCards = new ArrayList<>();
    private final EditText manualRatioInput;
    private final CompoundButton relaunchSwitch;
    private final CompoundButton autoReapplySwitch;
    private final View targetRatio;
    private final View targetSplit;
    private final TextView windowStateTitle;
    private final TextView windowStateBody;
    private final TextView logText;
    private final TextView aboutTitle;
    private final View taskHud;
    private final ImageView taskIcon;
    private final TextView taskTitle;
    private final TextView taskMessage;
    private final GlowProgressView taskProgress;
    private final List<View> actionButtons = new ArrayList<>();

    private UiState state;
    private int shownTab = -1;
    private boolean hudShown;
    private int renderedLogLength = -1;

    MainScreen(Context context, View root, Listener listener) {
        this.context = context;
        this.root = root;
        this.listener = listener;
        this.twoPane = context.getResources().getBoolean(R.bool.two_pane);
        this.density = context.getResources().getDisplayMetrics().density;

        shizukuDot = root.findViewById(R.id.shizukuDot);
        shizukuText = root.findViewById(R.id.shizukuText);
        rebootBanner = root.findViewById(R.id.rebootBanner);
        appFilter = root.findViewById(R.id.appFilter);
        filterGroup = root.findViewById(R.id.filterGroup);
        appCountText = root.findViewById(R.id.appCountText);
        appList = root.findViewById(R.id.appList);
        appEmpty = root.findViewById(R.id.appEmpty);
        tabBar = root.findViewById(R.id.tabBar);
        manualRatioInput = root.findViewById(R.id.manualRatioInput);
        relaunchSwitch = root.findViewById(R.id.relaunchSwitch);
        autoReapplySwitch = root.findViewById(R.id.autoReapplySwitch);
        targetRatio = root.findViewById(R.id.targetRatio);
        targetSplit = root.findViewById(R.id.targetSplit);
        windowStateTitle = root.findViewById(R.id.windowStateTitle);
        windowStateBody = root.findViewById(R.id.windowStateBody);
        logText = root.findViewById(R.id.logText);
        aboutTitle = root.findViewById(R.id.aboutTitle);
        taskHud = root.findViewById(R.id.taskHud);
        taskIcon = root.findViewById(R.id.taskIcon);
        taskTitle = root.findViewById(R.id.taskTitle);
        taskMessage = root.findViewById(R.id.taskMessage);
        taskProgress = root.findViewById(R.id.taskProgress);
        for (int i = 0; i < PANEL_IDS.length; i++) {
            panels[i] = root.findViewById(PANEL_IDS[i]);
        }

        adapter = new AppListAdapter(LayoutInflater.from(context), visibleApps, new AppListAdapter.Binder() {
            @Override
            public boolean isSelected(AppChoice app) {
                return state != null && state.selected.contains(app.packageName);
            }

            @Override
            public CharSequence tagsFor(AppChoice app) {
                return tags(app);
            }
        });
        appList.setAdapter(adapter);
        appList.setEmptyView(appEmpty);
        appList.setOnItemClickListener((parent, view, position, id) -> {
            pop(view.findViewById(R.id.appCheck));
            listener.onToggleApp(visibleApps.get(position).packageName);
        });

        buildRatioCards();
        bindInputs();
    }

    // ================= 绑定 =================

    private void bindInputs() {
        appFilter.addTextChangedListener(new SimpleWatcher(text -> listener.onQueryChanged(text)));
        manualRatioInput.addTextChangedListener(new SimpleWatcher(text -> listener.onManualRatioChanged(text)));
        filterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding || checkedId == View.NO_ID) {
                return;
            }
            listener.onFilterChanged(checkedId == R.id.filterAll ? UiState.Filter.ALL
                    : checkedId == R.id.filterModified ? UiState.Filter.MODIFIED
                    : checkedId == R.id.filterSelected ? UiState.Filter.SELECTED : UiState.Filter.USER);
        });
        tabBar.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding || checkedId == View.NO_ID) {
                return;
            }
            for (int i = 0; i < TAB_IDS.length; i++) {
                if (TAB_IDS[i] == checkedId) {
                    listener.onTabSelected(i);
                }
            }
        });
        relaunchSwitch.setOnCheckedChangeListener((v, checked) -> {
            if (!binding) {
                listener.onRelaunchChanged(checked);
            }
        });
        autoReapplySwitch.setOnCheckedChangeListener((v, checked) -> {
            if (!binding) {
                listener.onAutoReapplyChanged(checked);
            }
        });
        root.findViewById(R.id.selectVisibleButton).setOnClickListener(v -> listener.onSelectVisible(new ArrayList<>(visibleApps)));
        root.findViewById(R.id.clearSelectionButton).setOnClickListener(v -> listener.onClearSelection());
        root.findViewById(R.id.taskClose).setOnClickListener(v -> listener.onDismissTask());
        for (View strip : new View[]{targetRatio, targetSplit}) {
            strip.findViewById(R.id.targetAction).setOnClickListener(v -> listener.onTabSelected(UiState.TAB_APPS));
        }
        for (int id : ACTION_IDS) {
            View button = root.findViewById(id);
            button.setOnClickListener(v -> listener.onAction(id));
            actionButtons.add(button);
        }
        for (int id : PLAIN_ACTION_IDS) {
            root.findViewById(id).setOnClickListener(v -> listener.onAction(id));
        }
    }

    private void buildRatioCards() {
        GridLayout grid = root.findViewById(R.id.ratioGrid);
        LayoutInflater inflater = LayoutInflater.from(context);
        int gap = Math.round(5 * density);
        for (int i = 0; i < UiState.RATIO_CHOICES.length; i++) {
            UiState.RatioChoice choice = UiState.RATIO_CHOICES[i];
            View card = inflater.inflate(R.layout.item_ratio_card, grid, false);
            ((DiagramView) card.findViewById(R.id.ratioDiagram)).setMode(choice.diagram);
            ((TextView) card.findViewById(R.id.ratioLabel)).setText(choice.label);
            ((TextView) card.findViewById(R.id.ratioCaption)).setText(choice.caption);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
            params.width = 0;
            params.setMargins(gap, gap, gap, gap);
            card.setLayoutParams(params);
            int index = i;
            card.setOnClickListener(v -> {
                pop(v);
                listener.onRatioChoice(index);
            });
            grid.addView(card);
            ratioCards.add(card);
        }
        ViewGroup.MarginLayoutParams gridParams = (ViewGroup.MarginLayoutParams) grid.getLayoutParams();
        gridParams.setMarginStart(gridParams.getMarginStart() - gap);
        gridParams.setMarginEnd(gridParams.getMarginEnd() - gap);
        grid.setLayoutParams(gridParams);
    }

    List<AppChoice> visibleApps() {
        return new ArrayList<>(visibleApps);
    }

    // ================= 渲染 =================

    void render(UiState s) {
        state = s;
        binding = true;
        try {
            renderHeader(s);
            renderApps(s);
            renderTabs(s);
            renderRatio(s);
            renderTarget(targetRatio, s);
            renderTarget(targetSplit, s);
            renderWindow(s);
            renderTools(s);
            renderTask(s);
            for (View button : actionButtons) {
                button.setEnabled(!s.busy());
            }
            root.findViewById(R.id.restoreSmallWindowsButton).setEnabled(!s.busy() && s.switchesBackedUp);
        } finally {
            binding = false;
        }
    }

    /** 任务进行中的轻量刷新：只更新进度浮层和日志，不重建列表。 */
    void renderProgress(UiState s) {
        state = s;
        binding = true;
        try {
            renderTask(s);
            renderLog(s);
        } finally {
            binding = false;
        }
    }

    private void renderHeader(UiState s) {
        int color;
        String text;
        switch (s.shizuku) {
            case READY:
                color = context.getColor(R.color.lime);
                text = "Shizuku 已连接 · " + s.shizukuMode;
                break;
            case NOT_GRANTED:
                color = context.getColor(R.color.amber);
                text = "Shizuku 未授权 · 点此授权";
                break;
            default:
                color = context.getColor(R.color.red);
                text = "Shizuku 未运行 · 点此启动";
                break;
        }
        shizukuDot.setStatus(color, s.shizuku == UiState.Shizuku.READY);
        shizukuText.setText(text);
        rebootBanner.setVisibility(s.rebootPending ? View.VISIBLE : View.GONE);
    }

    private void renderApps(UiState s) {
        if (!appFilter.getText().toString().equals(s.query)) {
            appFilter.setText(s.query);
        }
        int filterId = s.filter == UiState.Filter.ALL ? R.id.filterAll
                : s.filter == UiState.Filter.MODIFIED ? R.id.filterModified
                : s.filter == UiState.Filter.SELECTED ? R.id.filterSelected : R.id.filterUser;
        if (filterGroup.getCheckedRadioButtonId() != filterId) {
            filterGroup.check(filterId);
        }

        String query = s.query.trim().toLowerCase(Locale.ROOT);
        visibleApps.clear();
        for (AppChoice app : s.allApps) {
            boolean keep;
            switch (s.filter) {
                case USER:
                    keep = !app.system;
                    break;
                case MODIFIED:
                    keep = s.isModified(app);
                    break;
                case SELECTED:
                    keep = s.selected.contains(app.packageName);
                    break;
                default:
                    keep = true;
                    break;
            }
            if (keep && app.matches(query)) {
                visibleApps.add(app);
            }
        }
        adapter.notifyDataSetChanged();
        if (!s.appsLoaded) {
            appCountText.setText("正在读取应用…");
            appEmpty.setText("正在读取应用…");
        } else {
            appCountText.setText("共 " + s.allApps.size() + " 个 · 列出 " + visibleApps.size() + " · 已选 " + s.selected.size());
            appEmpty.setText(s.filter == UiState.Filter.SELECTED ? "还没有选择应用" : "没有符合条件的应用");
        }
    }

    private void renderTabs(UiState s) {
        int tab = twoPane && s.tab == UiState.TAB_APPS ? UiState.TAB_RATIO : s.tab;
        if (tabBar.getCheckedRadioButtonId() != TAB_IDS[tab]) {
            tabBar.check(TAB_IDS[tab]);
        }
        if (tab == shownTab) {
            return;
        }
        boolean animate = shownTab >= 0;
        shownTab = tab;
        for (int i = 0; i < panels.length; i++) {
            if (twoPane && i == UiState.TAB_APPS) {
                continue; // 双栏时应用列表常驻左侧
            }
            View panel = panels[i];
            if (i == tab) {
                panel.setVisibility(View.VISIBLE);
                if (animate) {
                    panel.setAlpha(0f);
                    panel.setTranslationY(18 * density);
                    panel.animate().alpha(1f).translationY(0).setDuration(220)
                            .setInterpolator(new DecelerateInterpolator(1.6f)).start();
                }
            } else {
                panel.animate().cancel();
                panel.setVisibility(View.GONE);
            }
        }
    }

    private void renderRatio(UiState s) {
        boolean manual = !s.manualRatio.trim().isEmpty();
        for (int i = 0; i < ratioCards.size(); i++) {
            ratioCards.get(i).setActivated(!manual && i == s.ratioChoice);
        }
        if (!manualRatioInput.getText().toString().equals(s.manualRatio)) {
            manualRatioInput.setText(s.manualRatio);
        }
        relaunchSwitch.setChecked(s.relaunchAfterRatio);
    }

    private void renderTarget(View strip, UiState s) {
        List<AppChoice> apps = s.selectedApps();
        LinearLayout icons = strip.findViewById(R.id.targetIcons);
        icons.removeAllViews();
        int size = Math.round(34 * density);
        int overlap = Math.round(-12 * density);
        int shown = Math.min(4, apps.size());
        for (int i = 0; i < shown; i++) {
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(copyOf(apps.get(i).icon));
            icon.setBackgroundResource(R.drawable.icon_ring);
            int pad = Math.round(2 * density);
            icon.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginStart(i == 0 ? 0 : overlap);
            icon.setElevation(shown - i);
            icons.addView(icon, params);
        }
        if (apps.isEmpty()) {
            ImageView placeholder = new ImageView(context);
            placeholder.setImageResource(R.drawable.ic_nav_apps);
            placeholder.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.text_muted)));
            placeholder.setBackgroundResource(R.drawable.check_off);
            int pad = Math.round(8 * density);
            placeholder.setPadding(pad, pad, pad, pad);
            icons.addView(placeholder, new LinearLayout.LayoutParams(size, size));
        }
        icons.setVisibility(View.VISIBLE);

        TextView text = strip.findViewById(R.id.targetText);
        if (apps.isEmpty()) {
            text.setText(twoPane ? "在左侧列表里勾选应用" : "还没有选择应用");
            text.setTextColor(context.getColor(R.color.text_secondary));
        } else if (apps.size() == 1) {
            text.setText(apps.get(0).label);
            text.setTextColor(context.getColor(R.color.text_primary));
        } else {
            text.setText(apps.get(0).label + "、" + apps.get(1).label
                    + (apps.size() > 2 ? " 等 " + apps.size() + " 个应用" : ""));
            text.setTextColor(context.getColor(R.color.text_primary));
        }
        Button action = strip.findViewById(R.id.targetAction);
        action.setVisibility(twoPane ? View.GONE : View.VISIBLE);
        action.setText(apps.isEmpty() ? "去选择" : "修改");
    }

    private void renderWindow(UiState s) {
        if (s.smallWindowEnabled) {
            windowStateTitle.setText("已开启");
            windowStateTitle.setTextColor(context.getColor(R.color.lime));
            windowStateBody.setText("开启前的系统开关已备份，随时可以还原。手机重启后如失效，重新应用即可。");
        } else if (s.switchesBackedUp) {
            windowStateTitle.setText("已还原或部分还原");
            windowStateTitle.setTextColor(context.getColor(R.color.amber));
            windowStateBody.setText("还保留着开启前的开关备份，可以再点一次「还原」。");
        } else {
            windowStateTitle.setText("尚未开启");
            windowStateTitle.setTextColor(context.getColor(R.color.text_primary));
            windowStateBody.setText("第一次开启前，会先备份要改动的系统开关。");
        }
    }

    private void renderTools(UiState s) {
        autoReapplySwitch.setChecked(s.autoReapply);
        aboutTitle.setText("关于 · 折叠多窗 " + s.versionName);
        renderLog(s);
    }

    /** 日志可能很长，只在工具页可见时才写进 TextView。 */
    private void renderLog(UiState s) {
        if (panels[UiState.TAB_TOOLS].getVisibility() != View.VISIBLE || renderedLogLength == s.log.length()) {
            return;
        }
        renderedLogLength = s.log.length();
        logText.setText(s.log.length() == 0 ? "$ 等待操作…" : s.log);
    }

    private void renderTask(UiState s) {
        boolean visible = s.task == UiState.Task.RUNNING || s.resultVisible;
        if (!visible) {
            if (hudShown) {
                hudShown = false;
                taskHud.animate().alpha(0f).translationY(24 * density).setDuration(160)
                        .withEndAction(() -> taskHud.setVisibility(View.GONE)).start();
            }
            return;
        }
        int iconRes;
        int color;
        int progress;
        switch (s.task) {
            case SUCCESS:
                iconRes = R.drawable.ic_check;
                color = R.color.lime;
                progress = GlowProgressView.SUCCESS;
                break;
            case FAILURE:
                iconRes = R.drawable.ic_close;
                color = R.color.red;
                progress = GlowProgressView.FAILURE;
                break;
            default:
                iconRes = R.drawable.ic_bolt;
                color = R.color.cyan;
                progress = GlowProgressView.RUNNING;
                break;
        }
        taskIcon.setImageResource(iconRes);
        taskIcon.setImageTintList(ColorStateList.valueOf(context.getColor(color)));
        taskTitle.setText(s.taskTitle);
        taskMessage.setText(s.taskMessage);
        taskProgress.setState(progress);
        root.findViewById(R.id.taskShowLog).setVisibility(
                s.task == UiState.Task.RUNNING || shownTab == UiState.TAB_TOOLS ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.taskClose).setVisibility(s.task == UiState.Task.RUNNING ? View.GONE : View.VISIBLE);
        if (!hudShown) {
            hudShown = true;
            taskHud.animate().cancel();
            taskHud.setVisibility(View.VISIBLE);
            taskHud.setAlpha(0f);
            taskHud.setTranslationY(24 * density);
            taskHud.animate().alpha(1f).translationY(0).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
    }

    /** 日志更新后把终端滚到底部（只在工具页可见时）。 */
    void scrollLogToBottom() {
        View panel = panels[UiState.TAB_TOOLS];
        if (panel instanceof android.widget.ScrollView && panel.getVisibility() == View.VISIBLE) {
            panel.post(() -> ((android.widget.ScrollView) panel).smoothScrollTo(0, logText.getBottom()));
        }
    }

    // ================= 工具 =================

    private CharSequence tags(AppChoice app) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        Integer ratio = state.ratioMap.get(app.packageName);
        if (ratio != null) {
            appendTag(text, "内屏 " + FoldOps.ratioLabel(ratio, state.optimized.contains(app.packageName)), R.color.violet_soft);
        }
        FoldOps.SplitState split = state.splitStates.get(app.packageName);
        if (split == FoldOps.SplitState.BOTH) {
            appendTag(text, "分屏 ✓", R.color.cyan);
        } else if (split == FoldOps.SplitState.PARTIAL) {
            appendTag(text, "分屏不完整", R.color.amber);
        } else if (split == FoldOps.SplitState.NONE) {
            appendTag(text, "未加入分屏", R.color.text_muted);
        } else if (split == FoldOps.SplitState.UNKNOWN) {
            appendTag(text, "分屏未知", R.color.text_muted);
        } else if (state.repairedSplit.contains(app.packageName)) {
            appendTag(text, "已修复分屏", R.color.cyan);
        }
        if (app.system && state.filter != UiState.Filter.USER) {
            appendTag(text, "系统", R.color.text_muted);
        }
        return text;
    }

    private void appendTag(SpannableStringBuilder text, String tag, int colorRes) {
        int start = text.length();
        text.append(tag);
        text.setSpan(new TagSpan(context.getColor(colorRes), density), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static Drawable copyOf(Drawable icon) {
        if (icon == null || icon.getConstantState() == null) {
            return icon;
        }
        return icon.getConstantState().newDrawable().mutate();
    }

    private static void pop(View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setScaleX(0.86f);
        view.setScaleY(0.86f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
    }

    private final class SimpleWatcher implements TextWatcher {
        private final java.util.function.Consumer<String> onChange;

        SimpleWatcher(java.util.function.Consumer<String> onChange) {
            this.onChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (!binding) {
                onChange.accept(s.toString());
            }
        }
    }
}
