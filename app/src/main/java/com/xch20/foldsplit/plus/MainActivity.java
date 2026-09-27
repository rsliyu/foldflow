package com.xch20.foldsplit.plus;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProvider;

public final class MainActivity extends Activity {
    private static final int SHIZUKU_PERMISSION_REQUEST = 1001;
    private static final int RATIO_OPEN_SYSTEM_SETTINGS = -2;
    private static final int LOG_LIMIT = 20000;

    private static final RatioChoice[] RATIO_CHOICES = {
            new RatioChoice("全屏布局优化（推荐先试）", PrivilegedShellService.RATIO_FULL_OPTIMIZATION),
            new RatioChoice("全屏", 0),
            new RatioChoice("4:3", 1),
            new RatioChoice("16:9", 2),
            new RatioChoice("与外屏相同比例（21:9）", 3),
            new RatioChoice("20:9（跳转系统设置手动选择）", RATIO_OPEN_SYSTEM_SETTINGS),
    };

    private enum Filter { USER, ALL, MODIFIED }

    private interface Task {
        FoldOps.Result run(FoldOps.Progress progress) throws Exception;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<AppChoice> allApps = new ArrayList<>();
    private final List<AppChoice> visibleApps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final Map<String, Integer> ratioMap = new HashMap<>();
    private final Map<String, FoldOps.SplitState> splitStates = new HashMap<>();
    private final StringBuilder logBuffer = new StringBuilder();

    private Store store;
    private Set<String> repairedSplit = new HashSet<>();
    private Set<String> optimized = new HashSet<>();
    private Filter filter = Filter.USER;
    private boolean busy;
    private boolean appsLoaded;
    private boolean autoReapplyTried;

    private AppListAdapter adapter;
    private TextView shizukuStatus;
    private Button grantShizukuButton;
    private View rebootBanner;
    private EditText appFilter;
    private TextView appCountText;
    private Spinner innerRatioSpinner;
    private EditText manualRatioInput;
    private TextView logText;
    private final List<Button> actionButtons = new ArrayList<>();

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
        setContentView(R.layout.activity_main);
        store = new Store(this);
        configureSystemBars();
        bindViews();
        loadApps();
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        refreshShizukuStatus();
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
        executor.shutdownNow();
        ShizukuShell.close();
        super.onDestroy();
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(getColor(R.color.header_navy));
        getWindow().setNavigationBarColor(getColor(R.color.surface));
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(flags);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void bindViews() {
        shizukuStatus = findViewById(R.id.shizukuStatus);
        grantShizukuButton = findViewById(R.id.grantShizukuButton);
        rebootBanner = findViewById(R.id.rebootBanner);
        appFilter = findViewById(R.id.appFilter);
        appCountText = findViewById(R.id.appCountText);
        innerRatioSpinner = findViewById(R.id.innerRatioSpinner);
        manualRatioInput = findViewById(R.id.manualRatioInput);
        logText = findViewById(R.id.logText);

        grantShizukuButton.setOnClickListener(v -> requestShizukuPermission());

        ListView appList = findViewById(R.id.appList);
        adapter = new AppListAdapter(getLayoutInflater(), visibleApps, new AppListAdapter.Binder() {
            @Override
            public boolean isSelected(AppChoice app) {
                return selected.contains(app.packageName);
            }

            @Override
            public String statusFor(AppChoice app) {
                return statusText(app);
            }
        });
        appList.setAdapter(adapter);
        appList.setOnItemClickListener((parent, view, position, id) -> {
            String packageName = visibleApps.get(position).packageName;
            if (!selected.remove(packageName)) {
                selected.add(packageName);
            }
            adapter.notifyDataSetChanged();
            updateCountText();
        });
        // 列表放在整页滚动里：手指在列表上时让列表自己滚动
        appList.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
            }
            return false;
        });

        appFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        RadioGroup filterGroup = findViewById(R.id.filterGroup);
        filterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            filter = checkedId == R.id.filterAll ? Filter.ALL
                    : checkedId == R.id.filterModified ? Filter.MODIFIED : Filter.USER;
            applyFilter();
        });
        findViewById(R.id.selectVisibleButton).setOnClickListener(v -> {
            for (AppChoice app : visibleApps) {
                selected.add(app.packageName);
            }
            adapter.notifyDataSetChanged();
            updateCountText();
        });
        findViewById(R.id.clearSelectionButton).setOnClickListener(v -> {
            selected.clear();
            adapter.notifyDataSetChanged();
            updateCountText();
        });

        ArrayAdapter<RatioChoice> ratioAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, RATIO_CHOICES);
        ratioAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        innerRatioSpinner.setAdapter(ratioAdapter);

        CheckBox relaunchCheck = findViewById(R.id.relaunchCheck);
        relaunchCheck.setChecked(store.relaunchAfterRatio());
        relaunchCheck.setOnCheckedChangeListener((v, checked) -> store.setRelaunchAfterRatio(checked));
        CheckBox autoReapplyCheck = findViewById(R.id.autoReapplyCheck);
        autoReapplyCheck.setChecked(store.autoReapply());
        autoReapplyCheck.setOnCheckedChangeListener((v, checked) -> store.setAutoReapply(checked));

        bindAction(R.id.applyInnerRatioButton, this::applyRatio);
        bindAction(R.id.restoreRatioButton, this::restoreRatio);
        bindAction(R.id.repairSelectedButton, this::repairSelected);
        bindAction(R.id.repairAllButton, this::repairAll);
        bindAction(R.id.checkSplitButton, this::checkSplit);
        bindAction(R.id.removeSplitButton, this::removeSplit);
        bindAction(R.id.enableSmallWindowsButton, this::enableSmallWindows);
        bindAction(R.id.restoreSmallWindowsButton, this::restoreSmallWindows);
        bindAction(R.id.reapplyButton, this::reapply);
        bindAction(R.id.bannerReapplyButton, this::reapply);
        bindAction(R.id.diagnosticsButton, this::diagnostics);

        findViewById(R.id.copyLogButton).setOnClickListener(v -> copyLog());
        findViewById(R.id.shareLogButton).setOnClickListener(v -> shareLog());
        findViewById(R.id.clearLogButton).setOnClickListener(v -> {
            logBuffer.setLength(0);
            logText.setText("日志会显示在这里");
        });
    }

    private void bindAction(int id, Runnable action) {
        Button button = findViewById(id);
        button.setOnClickListener(v -> action.run());
        actionButtons.add(button);
    }

    // ================= 应用列表 =================

    private void loadApps() {
        executor.execute(() -> {
            List<AppChoice> apps = AppChoice.loadLaunchable(this, true);
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(apps);
                appsLoaded = true;
                if (apps.isEmpty()) {
                    appendLog("没有找到可启动应用。");
                }
                applyFilter();
                maybeAutoReapply();
            });
        });
    }

    private void applyFilter() {
        String query = appFilter.getText().toString().trim().toLowerCase(Locale.ROOT);
        visibleApps.clear();
        for (AppChoice app : allApps) {
            if (filter == Filter.USER && app.system) {
                continue;
            }
            if (filter == Filter.MODIFIED && !isModified(app)) {
                continue;
            }
            if (app.matches(query)) {
                visibleApps.add(app);
            }
        }
        adapter.notifyDataSetChanged();
        updateCountText();
    }

    private boolean isModified(AppChoice app) {
        return ratioMap.containsKey(app.packageName) || repairedSplit.contains(app.packageName);
    }

    private void updateCountText() {
        if (!appsLoaded) {
            return;
        }
        appCountText.setText("共 " + allApps.size() + " 个可启动应用，当前列出 " + visibleApps.size()
                + " 个，已选 " + selected.size() + " 个");
    }

    private String statusText(AppChoice app) {
        List<String> parts = new ArrayList<>();
        Integer ratio = ratioMap.get(app.packageName);
        if (ratio != null) {
            parts.add("内屏 " + FoldOps.ratioLabel(ratio, optimized.contains(app.packageName)));
        }
        FoldOps.SplitState state = splitStates.get(app.packageName);
        if (state != null) {
            parts.add(state.label);
        } else if (repairedSplit.contains(app.packageName)) {
            parts.add("已修复分屏");
        }
        if (app.system && filter != Filter.USER) {
            parts.add("系统应用");
        }
        return String.join(" · ", parts);
    }

    private List<AppChoice> selectedApps() {
        List<AppChoice> apps = new ArrayList<>();
        for (AppChoice app : allApps) {
            if (selected.contains(app.packageName)) {
                apps.add(app);
            }
        }
        return apps;
    }

    private List<AppChoice> requireSelection() {
        List<AppChoice> apps = selectedApps();
        if (apps.isEmpty()) {
            Toast.makeText(this, "请先在列表里勾选应用", Toast.LENGTH_SHORT).show();
        }
        return apps;
    }

    /** 在后台读取内屏比例配置，刷新列表上的状态标签。 */
    private void refreshStatus() {
        repairedSplit = store.repairedSplit();
        optimized = store.optimizedPackages();
        if (!ShizukuShell.isReady()) {
            applyFilter();
            return;
        }
        executor.execute(() -> {
            Map<String, Integer> ratios;
            try {
                ratios = FoldOps.readRatioMap();
            } catch (Exception error) {
                runOnUiThread(() -> appendLog("读取内屏比例失败：" + FoldOps.safeMessage(error)));
                return;
            }
            runOnUiThread(() -> {
                ratioMap.clear();
                ratioMap.putAll(ratios);
                applyFilter();
            });
        });
    }

    // ================= 操作 =================

    private void applyRatio() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        int ratio;
        String label;
        String manual = manualRatioInput.getText().toString().trim();
        if (!manual.isEmpty()) {
            Integer parsed = parseManualRatio(manual);
            if (parsed == null) {
                Toast.makeText(this, "请输入有效比例，例如 4:3、16:9 或 2.33", Toast.LENGTH_SHORT).show();
                return;
            }
            ratio = parsed;
            label = manual;
        } else {
            RatioChoice choice = (RatioChoice) innerRatioSpinner.getSelectedItem();
            ratio = choice.value;
            label = choice.label;
        }
        if (ratio == RATIO_OPEN_SYSTEM_SETTINGS) {
            openSystemRatioSettings(label);
            return;
        }
        boolean relaunch = store.relaunchAfterRatio();
        Runnable run = () -> runTask("开始设置 " + apps.size() + " 个应用的内屏显示比例：" + label,
                progress -> FoldOps.applyRatio(this, apps, ratio, label, relaunch, progress));
        if (apps.size() > 1) {
            confirm("设置内屏比例", "将把 " + apps.size() + " 个应用设为「" + label + "」，这些应用会被强制停止一次。继续吗？", run);
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
                () -> runTask("开始恢复内屏比例", progress -> FoldOps.restoreRatio(this, apps, progress)));
    }

    private void repairSelected() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        runTask("开始修复 " + apps.size() + " 个应用的分屏资格",
                progress -> FoldOps.repairSplit(this, apps, false, progress));
    }

    private void repairAll() {
        List<AppChoice> apps = new ArrayList<>(allApps);
        confirm("修复全部应用", "将把全部 " + apps.size() + " 个可启动应用（含系统应用）加入 OriginOS 分屏兼容列表。",
                () -> runTask("开始批量修复 " + apps.size() + " 个可启动应用",
                        progress -> FoldOps.repairSplit(this, apps, true, progress)));
    }

    private void checkSplit() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        runTask("检测 " + apps.size() + " 个应用的分屏状态", progress -> {
            Map<String, FoldOps.SplitState> states = FoldOps.querySplit(apps, progress);
            runOnUiThread(() -> splitStates.putAll(states));
            return FoldOps.Result.ok("检测完成，状态已显示在列表里。");
        });
    }

    private void removeSplit() {
        List<AppChoice> apps = requireSelection();
        if (apps.isEmpty()) {
            return;
        }
        confirm("移除分屏兼容（实验）", "会尝试把 " + apps.size() + " 个应用移出 OriginOS 分屏兼容列表。这个功能依赖固件提供移除命令，不支持时会直接提示失败，不会改动其他设置。",
                () -> runTask("开始移除分屏兼容", progress -> {
                    FoldOps.Result result = FoldOps.removeSplit(this, apps, progress);
                    Map<String, FoldOps.SplitState> states = FoldOps.querySplit(apps, message -> { });
                    runOnUiThread(() -> splitStates.putAll(states));
                    return result;
                }));
    }

    private void enableSmallWindows() {
        List<AppChoice> apps = new ArrayList<>(allApps);
        confirm("开启所有应用小窗", "会修改 " + FoldOps.SMALL_WINDOW_SWITCHES.length + " 个系统多窗开关，并把全部 " + apps.size()
                        + " 个应用加入小窗和分屏列表。第一次开启前会自动备份这些开关，之后可以用「还原系统小窗开关」恢复。",
                () -> runTask("开始开启所有应用小窗，并同步修复上下/左右分屏资格",
                        progress -> FoldOps.enableAllSmallWindows(this, apps, progress)));
    }

    private void restoreSmallWindows() {
        confirm("还原系统小窗开关", "把系统多窗开关恢复成第一次开启全应用小窗之前的值。",
                () -> runTask("开始还原系统小窗开关", progress -> FoldOps.restoreSmallWindowSwitches(this, progress)));
    }

    private void reapply() {
        List<AppChoice> apps = new ArrayList<>(allApps);
        runTask("重新应用上次设置", progress -> FoldOps.reapply(this, apps, progress));
    }

    private void diagnostics() {
        runTask("生成诊断报告…", progress -> FoldOps.diagnostics(this, progress));
    }

    private void runTask(String startMessage, Task task) {
        if (busy) {
            Toast.makeText(this, "上一个操作还在进行", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ensureShizukuReady()) {
            return;
        }
        setBusy(true);
        appendLog(startMessage);
        executor.execute(() -> {
            FoldOps.Result result;
            try {
                result = task.run(message -> runOnUiThread(() -> appendLog(message)));
            } catch (Throwable error) {
                result = FoldOps.Result.fail(FoldOps.safeMessage(error));
            }
            FoldOps.Result finalResult = result;
            runOnUiThread(() -> {
                appendLog((finalResult.success ? "完成：" : "失败：") + finalResult.message);
                setBusy(false);
                updateRebootBanner();
                refreshStatus();
            });
        });
    }

    private void confirm(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("继续", (dialog, which) -> onConfirm.run())
                .setNegativeButton("取消", null)
                .show();
    }

    private void setBusy(boolean value) {
        busy = value;
        for (Button button : actionButtons) {
            button.setEnabled(!value);
        }
    }

    private void openSystemRatioSettings(String requestedRatio) {
        try {
            startActivity(new Intent("com.vivo.settings.action.APP_DISPLAY_RATIO"));
            appendLog("已打开系统“应用显示比例”，请在内屏页面手动选择「" + requestedRatio + "」。");
        } catch (ActivityNotFoundException error) {
            appendLog("打开系统显示比例设置失败：当前系统没有这个页面。");
        }
    }

    /** 解析手动输入的比例：系统开放的比例返回对应值，其他比例返回跳转系统设置。 */
    private static Integer parseManualRatio(String input) {
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
        return RATIO_OPEN_SYSTEM_SETTINGS;
    }

    // ================= Shizuku 与重启检测 =================

    private void onShizukuChanged() {
        refreshShizukuStatus();
        updateRebootBanner();
        refreshStatus();
        maybeAutoReapply();
    }

    private void updateRebootBanner() {
        rebootBanner.setVisibility(FoldOps.needsReapply(this) ? View.VISIBLE : View.GONE);
    }

    private void maybeAutoReapply() {
        if (autoReapplyTried || busy || !appsLoaded || !store.autoReapply()
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
        refreshShizukuStatus();
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
                refreshShizukuStatus();
            } else {
                Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
            }
        } catch (RuntimeException error) {
            appendLog("请求 Shizuku 权限失败：" + FoldOps.safeMessage(error));
        }
    }

    private void refreshShizukuStatus() {
        boolean alive;
        boolean granted = false;
        int uid = -1;
        try {
            alive = Shizuku.pingBinder();
            if (alive) {
                granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                uid = Shizuku.getUid();
            }
        } catch (RuntimeException e) {
            alive = false;
        }
        if (!alive) {
            shizukuStatus.setText("Shizuku：未运行，请先启动 Shizuku 服务");
            grantShizukuButton.setEnabled(true);
            grantShizukuButton.setText("启动 / 授权 Shizuku");
        } else if (!granted) {
            shizukuStatus.setText("Shizuku：已连接，但本 App 尚未授权");
            grantShizukuButton.setEnabled(true);
            grantShizukuButton.setText("授权 Shizuku");
        } else {
            String mode = uid == 0 ? "Root" : uid == 2000 ? "ADB" : "UID " + uid;
            shizukuStatus.setText("Shizuku：已授权（" + mode + " 模式）");
            grantShizukuButton.setEnabled(false);
            grantShizukuButton.setText("Shizuku 已授权");
        }
    }

    // ================= 日志 =================

    private void appendLog(String message) {
        if (logBuffer.length() > 0) {
            logBuffer.append('\n');
        }
        logBuffer.append(message);
        if (logBuffer.length() > LOG_LIMIT) {
            logBuffer.delete(0, logBuffer.length() - LOG_LIMIT);
        }
        logText.setText(logBuffer.toString());
    }

    private void copyLog() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("执行记录", logBuffer.toString()));
        Toast.makeText(this, "已复制执行记录", Toast.LENGTH_SHORT).show();
    }

    private void shareLog() {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, logBuffer.toString());
        startActivity(Intent.createChooser(send, "分享执行记录"));
    }

    private static final class RatioChoice {
        final String label;
        final int value;

        RatioChoice(String label, int value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
