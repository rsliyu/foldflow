package io.github.rsliyu.foldflow;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

/**
 * 持有状态和后台任务。界面由 MainScreen 按 UiState 渲染；折叠/展开时 Activity 不重建，
 * 只重新加载对应尺寸的布局，正在执行的任务不受影响。
 */
public final class MainActivity extends Activity implements MainScreen.Listener {
    private static final int SHIZUKU_PERMISSION_REQUEST = 1001;
    private static final int LOG_LIMIT = 40000;
    private static final long RESULT_AUTO_HIDE_MS = 6000;
    private static final String REPO_URL = "https://github.com/rsliyu/foldflow";

    private interface Task {
        FoldOps.Result run(FoldOps.Progress progress) throws Exception;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final UiState state = new UiState();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss", Locale.ROOT);

    private Store store;
    private MainScreen screen;
    private boolean autoReapplyTried;
    private int resultToken;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> runOnUiThread(this::onShizukuChanged);
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> runOnUiThread(this::onShizukuChanged);
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        if (requestCode == SHIZUKU_PERMISSION_REQUEST) {
            runOnUiThread(this::onShizukuChanged);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        state.versionName = BuildConfig.VERSION_NAME;
        state.relaunchAfterRatio = store.relaunchAfterRatio();
        state.autoReapply = store.autoReapply();
        readStore();
        buildScreen();
        loadApps();
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        refreshShizuku();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        buildScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        onShizukuChanged();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        ShizukuShell.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (state.tab != UiState.TAB_APPS && !getResources().getBoolean(R.bool.two_pane)) {
            onTabSelected(UiState.TAB_APPS);
            return;
        }
        super.onBackPressed();
    }

    /** 按当前屏幕尺寸加载布局：外屏单栏，内屏展开后双栏。 */
    private void buildScreen() {
        View root = getLayoutInflater().inflate(R.layout.activity_main, null);
        setContentView(root);
        screen = new MainScreen(this, root, this);
        render();
    }

    private void render() {
        if (screen != null) {
            screen.render(state);
        }
    }

    // ================= 数据 =================

    private void loadApps() {
        executor.execute(() -> {
            List<AppChoice> apps = AppChoice.loadLaunchable(this, true);
            runOnUiThread(() -> {
                state.allApps.clear();
                state.allApps.addAll(apps);
                state.appsLoaded = true;
                if (apps.isEmpty()) {
                    appendLog("没有找到可启动应用。");
                }
                render();
                maybeAutoReapply();
            });
        });
    }

    private void readStore() {
        state.repairedSplit = store.repairedSplit();
        state.optimized = store.optimizedPackages();
        state.switchesBackedUp = store.hasSwitchBackup();
        state.smallWindowEnabled = store.smallWindowEnabled();
        state.rebootPending = FoldOps.needsReapply(this);
    }

    /** 从系统读取内屏比例配置，刷新列表上的标签。 */
    private void refreshRatios() {
        if (!ShizukuShell.isReady()) {
            return;
        }
        executor.execute(() -> {
            try {
                Map<String, Integer> ratios = FoldOps.readRatioMap();
                runOnUiThread(() -> {
                    state.ratioMap.clear();
                    state.ratioMap.putAll(ratios);
                    render();
                });
            } catch (Exception error) {
                runOnUiThread(() -> appendLog("读取内屏比例失败：" + FoldOps.safeMessage(error)));
            }
        });
    }

    // ================= 界面回调 =================

    @Override
    public void onTabSelected(int tab) {
        state.tab = tab;
        if (tab == UiState.TAB_TOOLS) {
            state.resultVisible = false;
        }
        render();
        if (tab == UiState.TAB_TOOLS) {
            screen.scrollLogToBottom();
        }
    }

    @Override
    public void onQueryChanged(String query) {
        state.query = query;
        render();
    }

    @Override
    public void onFilterChanged(UiState.Filter filter) {
        state.filter = filter;
        render();
    }

    @Override
    public void onToggleApp(String packageName) {
        if (!state.selected.remove(packageName)) {
            state.selected.add(packageName);
        }
        render();
    }

    @Override
    public void onSelectVisible(List<AppChoice> visible) {
        for (AppChoice app : visible) {
            state.selected.add(app.packageName);
        }
        render();
    }

    @Override
    public void onClearSelection() {
        state.selected.clear();
        render();
    }

    @Override
    public void onRatioChoice(int index) {
        state.ratioChoice = index;
        state.manualRatio = "";
        render();
    }

    @Override
    public void onManualRatioChanged(String text) {
        state.manualRatio = text;
        render();
    }

    @Override
    public void onRelaunchChanged(boolean enabled) {
        state.relaunchAfterRatio = enabled;
        store.setRelaunchAfterRatio(enabled);
    }

    @Override
    public void onAutoReapplyChanged(boolean enabled) {
        state.autoReapply = enabled;
        store.setAutoReapply(enabled);
    }

    @Override
    public void onDismissTask() {
        state.resultVisible = false;
        render();
    }

    @Override
    public void onAction(int id) {
        if (id == R.id.applyInnerRatioButton) {
            applyRatio();
        } else if (id == R.id.restoreRatioButton) {
            restoreRatio();
        } else if (id == R.id.repairSelectedButton) {
            repairSelected();
        } else if (id == R.id.repairAllButton) {
            repairAll();
        } else if (id == R.id.checkSplitButton) {
            checkSplit();
        } else if (id == R.id.removeSplitButton) {
            removeSplit();
        } else if (id == R.id.enableSmallWindowsButton) {
            enableSmallWindows();
        } else if (id == R.id.restoreSmallWindowsButton) {
            restoreSmallWindows();
        } else if (id == R.id.reapplyButton || id == R.id.bannerReapplyButton) {
            reapply();
        } else if (id == R.id.diagnosticsButton) {
            diagnostics();
        } else if (id == R.id.shizukuPill) {
            requestShizukuPermission();
        } else if (id == R.id.copyLogButton) {
            copyLog();
        } else if (id == R.id.shareLogButton) {
            shareLog();
        } else if (id == R.id.clearLogButton) {
            state.log.setLength(0);
            render();
        } else if (id == R.id.repoButton) {
            openUrl(REPO_URL);
        } else if (id == R.id.taskShowLog) {
            onTabSelected(UiState.TAB_TOOLS);
        }
    }

    // ================= 操作 =================

    private List<AppChoice> requireSelection() {
        List<AppChoice> apps = state.selectedApps();
        if (apps.isEmpty()) {
            Toast.makeText(this, "请先在应用列表里勾选应用", Toast.LENGTH_SHORT).show();
        }
        return apps;
    }

    private void applyRatio() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        int ratio;
        String label;
        String manual = state.manualRatio.trim();
        if (!manual.isEmpty()) {
            Integer parsed = parseManualRatio(manual);
            if (parsed == null) {
                Toast.makeText(this, "请输入有效比例，例如 4:3、16:9 或 2.33", Toast.LENGTH_SHORT).show();
                return;
            }
            ratio = parsed;
            label = manual;
        } else {
            UiState.RatioChoice choice = UiState.RATIO_CHOICES[state.ratioChoice];
            ratio = choice.value;
            label = choice.label;
        }
        if (ratio == UiState.RATIO_OPEN_SYSTEM_SETTINGS) {
            openSystemRatioSettings(label);
            return;
        }
        boolean relaunch = state.relaunchAfterRatio;
        Runnable run = () -> runTask("设置内屏比例「" + label + "」",
                progress -> FoldOps.applyRatio(this, apps, ratio, label, relaunch, progress));
        if (apps.size() > 1) {
            confirm("设置内屏比例", "将把 " + apps.size() + " 个应用设为「" + label + "」，这些应用会被强制停止一次。", run);
        } else {
            run.run();
        }
    }

    private void restoreRatio() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        confirm("恢复原比例", "将把 " + apps.size() + " 个应用恢复为本 App 修改前的内屏比例（没有修改记录的恢复为系统默认），这些应用会被强制停止一次。",
                () -> runTask("恢复内屏比例", progress -> FoldOps.restoreRatio(this, apps, progress)));
    }

    private void repairSelected() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        runTask("修复 " + apps.size() + " 个应用的分屏", progress -> FoldOps.repairSplit(this, apps, false, progress));
    }

    private void repairAll() {
        List<AppChoice> apps = new ArrayList<>(state.allApps);
        confirm("修复全部应用", "将把全部 " + apps.size() + " 个可启动应用（含系统应用）加入 OriginOS 分屏允许名单。",
                () -> runTask("修复全部 " + apps.size() + " 个应用的分屏",
                        progress -> FoldOps.repairSplit(this, apps, true, progress)));
    }

    private void checkSplit() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        runTask("检测分屏状态", progress -> {
            Map<String, FoldOps.SplitState> states = FoldOps.querySplit(apps, progress);
            runOnUiThread(() -> state.splitStates.putAll(states));
            return FoldOps.Result.ok("检测完成，状态已标在应用列表里。");
        });
    }

    private void removeSplit() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        confirm("移除分屏（实验）", "会尝试把 " + apps.size() + " 个应用移出 OriginOS 分屏允许名单。这依赖固件提供移除命令，不支持时会直接提示失败，不会改动其他设置。",
                () -> runTask("移除分屏", progress -> {
                    FoldOps.Result result = FoldOps.removeSplit(this, apps, progress);
                    Map<String, FoldOps.SplitState> states = FoldOps.querySplit(apps, message -> { });
                    runOnUiThread(() -> state.splitStates.putAll(states));
                    return result;
                }));
    }

    private void enableSmallWindows() {
        List<AppChoice> apps = new ArrayList<>(state.allApps);
        confirm("开启所有应用小窗", "会修改 " + FoldOps.SMALL_WINDOW_SWITCHES.length + " 个系统多窗开关，并把全部 " + apps.size()
                        + " 个应用加入小窗和分屏名单。第一次开启前会自动备份这些开关，之后可以一键还原。",
                () -> runTask("开启全应用小窗", progress -> FoldOps.enableAllSmallWindows(this, apps, progress)));
    }

    private void restoreSmallWindows() {
        confirm("还原系统小窗开关", "把系统多窗开关恢复成第一次开启全应用小窗之前的值。",
                () -> runTask("还原系统小窗开关", progress -> FoldOps.restoreSmallWindowSwitches(this, progress)));
    }

    private void reapply() {
        List<AppChoice> apps = new ArrayList<>(state.allApps);
        runTask("重新应用上次设置", progress -> FoldOps.reapply(this, apps, progress));
    }

    private void diagnostics() {
        runTask("生成诊断报告", progress -> {
            FoldOps.Result report = FoldOps.diagnostics(this, progress);
            progress.log(report.message);
            return FoldOps.Result.ok("报告已写入执行记录，可以复制或分享给开发者。");
        });
    }

    private void runTask(String title, Task task) {
        if (state.busy()) {
            Toast.makeText(this, "上一个操作还在进行", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ensureShizukuReady()) {
            return;
        }
        resultToken++;
        state.task = UiState.Task.RUNNING;
        state.taskTitle = title;
        state.taskMessage = "准备中…";
        state.resultVisible = false;
        appendLog("▶ " + title);
        render();
        executor.execute(() -> {
            FoldOps.Result result;
            try {
                result = task.run(message -> runOnUiThread(() -> {
                    appendLog(message);
                    state.taskMessage = message;
                    if (screen != null) {
                        screen.renderProgress(state);
                    }
                }));
            } catch (Throwable error) {
                result = FoldOps.Result.fail(FoldOps.safeMessage(error));
            }
            FoldOps.Result finished = result;
            runOnUiThread(() -> finishTask(finished));
        });
    }

    private void finishTask(FoldOps.Result result) {
        state.task = result.success ? UiState.Task.SUCCESS : UiState.Task.FAILURE;
        state.taskTitle = result.success ? "完成" : "未完成";
        state.taskMessage = result.message;
        state.resultVisible = true;
        appendLog((result.success ? "✓ " : "✗ ") + result.message);
        readStore();
        render();
        refreshRatios();
        if (result.success) {
            int token = resultToken;
            handler.postDelayed(() -> {
                if (token == resultToken && state.task == UiState.Task.SUCCESS) {
                    state.resultVisible = false;
                    render();
                }
            }, RESULT_AUTO_HIDE_MS);
        }
    }

    private void confirm(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("继续", (dialog, which) -> onConfirm.run())
                .setNegativeButton("取消", null)
                .show();
    }

    private void openSystemRatioSettings(String requestedRatio) {
        try {
            startActivity(new Intent("com.vivo.settings.action.APP_DISPLAY_RATIO"));
            appendLog("已打开系统“应用显示比例”，请在内屏页面手动选择「" + requestedRatio + "」。");
        } catch (ActivityNotFoundException error) {
            appendLog("打开系统显示比例设置失败：当前系统没有这个页面。");
        }
        render();
    }

    /** 解析手动输入的比例：系统开放的比例返回对应值，其他比例返回跳转系统设置。 */
    static Integer parseManualRatio(String input) {
        String normalized = input.replace('：', ':').replace(" ", "");
        double ratio;
        try {
            if (normalized.contains(":")) {
                String[] parts = normalized.split(":");
                if (parts.length != 2) {
                    return null;
                }
                double width = Double.parseDouble(parts[0]);
                double height = Double.parseDouble(parts[1]);
                if (width <= 0 || height <= 0) {
                    return null;
                }
                ratio = width / height;
            } else {
                ratio = Double.parseDouble(normalized);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (ratio < 0.5 || ratio > 3.5) {
            return null;
        }
        if (Math.abs(ratio - 4.0 / 3.0) < 0.02) {
            return 1;
        }
        if (Math.abs(ratio - 16.0 / 9.0) < 0.02) {
            return 2;
        }
        if (Math.abs(ratio - 21.0 / 9.0) < 0.02) {
            return 3;
        }
        return UiState.RATIO_OPEN_SYSTEM_SETTINGS;
    }

    // ================= Shizuku 与重启检测 =================

    private void onShizukuChanged() {
        refreshShizuku();
        readStore();
        render();
        refreshRatios();
        maybeAutoReapply();
    }

    private void maybeAutoReapply() {
        if (autoReapplyTried || state.busy() || !state.appsLoaded || !state.autoReapply
                || !ShizukuShell.isReady() || !FoldOps.needsReapply(this)) {
            return;
        }
        autoReapplyTried = true;
        appendLog("检测到手机重启过，自动重新应用上次设置。");
        reapply();
    }

    private boolean ensureShizukuReady() {
        if (ShizukuShell.isReady()) {
            return true;
        }
        Toast.makeText(this, "请先启动并授权 Shizuku", Toast.LENGTH_LONG).show();
        refreshShizuku();
        render();
        return false;
    }

    private void requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "请先在 Shizuku 中启动服务", Toast.LENGTH_LONG).show();
                Intent launch = getPackageManager().getLaunchIntentForPackage(ShizukuProvider.MANAGER_APPLICATION_ID);
                if (launch != null) {
                    startActivity(launch);
                }
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                onShizukuChanged();
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
            }
        } catch (RuntimeException error) {
            appendLog("请求 Shizuku 权限失败：" + FoldOps.safeMessage(error));
            render();
        }
    }

    private void refreshShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                state.shizuku = UiState.Shizuku.NOT_RUNNING;
            } else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                state.shizuku = UiState.Shizuku.NOT_GRANTED;
            } else {
                int uid = Shizuku.getUid();
                state.shizuku = UiState.Shizuku.READY;
                state.shizukuMode = uid == 0 ? "Root" : uid == 2000 ? "ADB" : "UID " + uid;
            }
        } catch (RuntimeException e) {
            state.shizuku = UiState.Shizuku.NOT_RUNNING;
        }
    }

    // ================= 日志 =================

    private void appendLog(String message) {
        if (state.log.length() > 0) {
            state.log.append('\n');
        }
        state.log.append('[').append(clock.format(new Date())).append("] ").append(message);
        if (state.log.length() > LOG_LIMIT) {
            state.log.delete(0, state.log.length() - LOG_LIMIT);
        }
        if (screen != null) {
            screen.scrollLogToBottom();
        }
    }

    private void copyLog() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("执行记录", state.log.toString()));
        Toast.makeText(this, "已复制执行记录", Toast.LENGTH_SHORT).show();
    }

    private void shareLog() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, state.log.toString());
        startActivity(Intent.createChooser(send, "分享执行记录"));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "没有可以打开链接的应用", Toast.LENGTH_SHORT).show();
        }
    }
}
