package io.github.rsliyu.foldflow;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

/**
 * 所有多窗操作的同步实现，界面和快捷开关都调用这里。必须在后台线程执行。
 */
final class FoldOps {
    interface Progress {
        void log(String message);
    }

    static final class Result {
        final boolean success;
        final String message;

        private Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static Result ok(String message) {
            return new Result(true, message);
        }

        static Result fail(String message) {
            return new Result(false, message);
        }
    }

    enum SplitState {
        BOTH("已加入分屏"),
        PARTIAL("分屏资格不完整"),
        NONE("未加入分屏列表"),
        UNKNOWN("分屏状态未知");

        final String label;

        SplitState(String label) {
            this.label = label;
        }
    }

    static final String ALLOW_SPLIT_APPS = "AllowSplitAppsList";
    static final String ALLOW_VERTICAL_SPLIT_APPS = "UnfoldAllowSplitVerticalApp";
    private static final String SPLIT_LISTS = ALLOW_SPLIT_APPS + "-" + ALLOW_VERTICAL_SPLIT_APPS;
    private static final String CMD = "/system/bin/cmd";
    private static final String SETTINGS = "/system/bin/settings";

    /** 开启全应用小窗时改动的系统开关：{命名空间, 键, 目标值}。 */
    static final String[][] SMALL_WINDOW_SWITCHES = {
            {"global", "enable_freeform_support", "1"},
            {"global", "disable_small_window", "0"},
            {"global", "force_resizable_activities", "1"},
            {"global", "enable_non_resizable_multi_window", "1"},
            {"system", "freeform_jump_enable", "1"},
            {"system", "freeform_share_enable", "1"},
            {"system", "freeform_support_multi_freeform", "1"},
            {"system", "freeform_inside_enable", "1"},
    };

    private FoldOps() {
    }

    // ================= 原生分屏 =================

    static Result repairSplit(Context context, List<AppChoice> apps, boolean coversAll, Progress progress) throws Exception {
        if (apps.isEmpty()) {
            return Result.fail("没有要处理的应用。");
        }
        Store store = new Store(context);
        List<String> repaired = new ArrayList<>();
        int failed = 0;
        for (AppChoice app : apps) {
            progress.log("修复分屏资格：" + app.label);
            Result result = applyNativeSplitPolicy(app.packageName);
            if (result.success) {
                repaired.add(app.packageName);
            } else {
                failed++;
                progress.log(app.label + "：" + result.message);
            }
        }
        store.addRepairedSplit(repaired);
        if (coversAll) {
            store.setRepairedAll(true);
        }
        if (!repaired.isEmpty()) {
            store.markApplied(currentBootCount(context));
        }
        if (apps.size() == 1 && failed == 0) {
            return Result.ok("已加入 OriginOS 原生分屏和上下分屏兼容列表。请回到最近任务，继续使用系统的分屏入口。");
        }
        String message = "已处理 " + repaired.size() + " 个应用的原生分屏/上下分屏资格"
                + (failed == 0 ? "。" : "，失败 " + failed + " 个。");
        return failed == 0 ? Result.ok(message) : Result.fail(message);
    }

    private static Result applyNativeSplitPolicy(String packageName) throws Exception {
        ShizukuShell.ShellResult add = ShizukuShell.run(CMD, "activity", "splitconfig", "add", packageName, SPLIT_LISTS);
        if (!add.isSuccess()) {
            return Result.fail("写入 OriginOS 分屏列表失败：" + add.compactOutput());
        }
        if (querySplitState(packageName, null) != SplitState.BOTH) {
            return Result.fail("写入后未能从 system_server 读回完整配置。");
        }
        return Result.ok("");
    }

    static SplitState querySplitState(String packageName, StringBuilder rawOutput) throws Exception {
        ShizukuShell.ShellResult dump = ShizukuShell.run(CMD, "activity", "splitconfig", "dump", packageName, "verify");
        if (rawOutput != null) {
            rawOutput.append(dump.output(1200));
        }
        if (!dump.isSuccess()) {
            return SplitState.UNKNOWN;
        }
        String output = dump.stdout + "\n" + dump.stderr;
        boolean allow = output.contains(ALLOW_SPLIT_APPS);
        boolean vertical = output.contains(ALLOW_VERTICAL_SPLIT_APPS);
        if (allow && vertical) {
            return SplitState.BOTH;
        }
        return allow || vertical ? SplitState.PARTIAL : SplitState.NONE;
    }

    static Map<String, SplitState> querySplit(List<AppChoice> apps, Progress progress) throws Exception {
        Map<String, SplitState> states = new LinkedHashMap<>();
        for (AppChoice app : apps) {
            StringBuilder raw = apps.size() == 1 ? new StringBuilder() : null;
            SplitState state = querySplitState(app.packageName, raw);
            states.put(app.packageName, state);
            progress.log(app.label + "：" + state.label);
            if (raw != null && raw.length() > 0) {
                progress.log("系统返回：\n" + raw);
            }
        }
        return states;
    }

    static Result removeSplit(Context context, List<AppChoice> apps, Progress progress) throws Exception {
        if (apps.isEmpty()) {
            return Result.fail("没有要处理的应用。");
        }
        String verb = splitRemoveVerb();
        if (verb == null) {
            return Result.fail("当前固件的 splitconfig 命令没有提供移除子命令，无法移除。可以在「诊断报告」里查看它支持的命令。");
        }
        Store store = new Store(context);
        List<String> removed = new ArrayList<>();
        int failed = 0;
        for (AppChoice app : apps) {
            progress.log("移除分屏兼容：" + app.label);
            ShizukuShell.ShellResult result = ShizukuShell.run(CMD, "activity", "splitconfig", verb, app.packageName, SPLIT_LISTS);
            SplitState state = result.isSuccess() ? querySplitState(app.packageName, null) : SplitState.UNKNOWN;
            if (state == SplitState.NONE) {
                removed.add(app.packageName);
            } else {
                failed++;
                progress.log(app.label + "：" + (result.isSuccess()
                        ? "命令已执行，但系统仍报告「" + state.label + "」"
                        : "系统拒绝：" + result.compactOutput()));
            }
        }
        store.removeRepairedSplit(removed);
        store.setRepairedAll(false);
        String message = "已移除 " + removed.size() + " 个应用的分屏兼容" + (failed == 0 ? "。" : "，失败 " + failed + " 个。");
        return failed == 0 ? Result.ok(message) : Result.fail(message);
    }

    /** 从 splitconfig 的帮助文本里找移除子命令，找不到返回 null。 */
    private static String splitRemoveVerb() throws Exception {
        ShizukuShell.ShellResult help = ShizukuShell.run(CMD, "activity", "splitconfig", "help");
        String text = help.stdout + "\n" + help.stderr;
        for (String verb : new String[]{"remove", "delete", "del", "rm"}) {
            if (Pattern.compile("(^|[\\s|\\[<(])" + verb + "([\\s|\\]>)]|$)", Pattern.MULTILINE).matcher(text).find()) {
                return verb;
            }
        }
        return null;
    }

    // ================= 小窗 =================

    static Result enableAllSmallWindows(Context context, List<AppChoice> apps, Progress progress) throws Exception {
        if (apps.isEmpty()) {
            return Result.fail("没有找到可启动应用。");
        }
        Store store = new Store(context);
        if (!store.hasSwitchBackup()) {
            progress.log("备份当前系统小窗开关…");
            store.saveSwitchBackup(readSwitches());
        }
        progress.log("打开 Android/OriginOS 的小窗总开关…");
        String settingsMessage = writeSmallWindowSwitches();

        List<String> packageNames = new ArrayList<>();
        List<String> repaired = new ArrayList<>();
        int splitFailed = 0;
        for (AppChoice app : apps) {
            packageNames.add(app.packageName);
            if (applyNativeSplitPolicy(app.packageName).success) {
                repaired.add(app.packageName);
            } else {
                splitFailed++;
            }
        }
        progress.log("已处理 " + repaired.size() + " 个应用的原生分屏资格。");
        store.addRepairedSplit(repaired);
        store.setRepairedAll(true);

        Bundle freeform = ShizukuShell.runVivoFreeformConfig(packageNames.toArray(new String[0]));
        store.setSmallWindowEnabled(true);
        store.markApplied(currentBootCount(context));
        if (!freeform.getBoolean("success", false)) {
            return Result.fail("分屏兼容列表已处理，但小窗允许列表刷新失败："
                    + freeform.getString("error", "系统没有接受小窗允许列表")
                    + (settingsMessage.isEmpty() ? "" : "；" + settingsMessage));
        }
        return Result.ok("已将 " + packageNames.size() + " 个可启动应用加入 OriginOS 小窗允许列表。以后仍从系统的小窗入口启动，窗口外观和拖动由 OriginOS 管理。"
                + (splitFailed == 0 ? "" : " 原生分屏资格有 " + splitFailed + " 个未写入。")
                + (settingsMessage.isEmpty() ? "" : " 部分系统开关未写入：" + settingsMessage));
    }

    static Result restoreSmallWindowSwitches(Context context, Progress progress) throws Exception {
        Store store = new Store(context);
        Map<String, String> backup = store.switchBackup();
        if (backup.isEmpty()) {
            return Result.fail("没有找到备份：只有用本 App「开启所有应用小窗」之后才能还原。");
        }
        StringBuilder failures = new StringBuilder();
        int restored = 0;
        for (Map.Entry<String, String> entry : backup.entrySet()) {
            String[] parts = entry.getKey().split("/", 2);
            String value = entry.getValue();
            ShizukuShell.ShellResult result = "null".equals(value)
                    ? ShizukuShell.run(SETTINGS, "delete", parts[0], parts[1])
                    : ShizukuShell.run(SETTINGS, "put", parts[0], parts[1], value);
            if (result.isSuccess()) {
                restored++;
                progress.log("还原 " + entry.getKey() + " = " + value);
            } else {
                appendFailure(failures, parts[1] + " 未还原");
            }
        }
        ShizukuShell.ShellResult reset = ShizukuShell.run(CMD, "window", "reset-multi-window-config");
        if (!reset.isSuccess()) {
            appendFailure(failures, "系统多窗口兼容参数未能立即重置（重启手机后会恢复）");
        }
        if (failures.length() == 0) {
            store.clearSwitchBackup();
        }
        store.setSmallWindowEnabled(false);
        String message = "已把 " + restored + " 个系统小窗开关还原为开启前的值。小窗允许列表是整体写入的，本 App 读不到它原来的内容，所以无法精确还原这一项。";
        return failures.length() == 0 ? Result.ok(message) : Result.fail(message + " 未完成：" + failures);
    }

    private static Map<String, String> readSwitches() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String[] item : SMALL_WINDOW_SWITCHES) {
            ShizukuShell.ShellResult result = ShizukuShell.run(SETTINGS, "get", item[0], item[1]);
            if (result.isSuccess()) {
                values.put(item[0] + "/" + item[1], result.stdout.trim());
            }
        }
        return values;
    }

    private static String writeSmallWindowSwitches() throws Exception {
        StringBuilder failures = new StringBuilder();
        for (String[] item : SMALL_WINDOW_SWITCHES) {
            if (!ShizukuShell.run(SETTINGS, "put", item[0], item[1], item[2]).isSuccess()) {
                appendFailure(failures, item[1] + " 未写入");
            }
        }
        ShizukuShell.ShellResult multiWindow = ShizukuShell.run(CMD, "window", "set-multi-window-config",
                "--supportsNonResizable", "1", "--respectsActivityMinWidthHeight", "-1");
        if (!multiWindow.isSuccess()) {
            appendFailure(failures, "系统多窗口兼容参数未写入：" + multiWindow.compactOutput());
        }
        return failures.toString();
    }

    // ================= 内屏显示比例 =================

    static String ratioLabel(int storedRatio, boolean optimized) {
        switch (storedRatio) {
            case 0:
                return optimized ? "全屏布局优化" : "全屏";
            case 1:
                return "4:3";
            case 2:
                return "16:9";
            case 3:
                return "与外屏相同";
            default:
                return "比例 " + storedRatio;
        }
    }

    static Map<String, Integer> readRatioMap() throws Exception {
        Bundle result = ShizukuShell.readSecureSetting(InnerRatioConfig.SETTING_KEY);
        if (!result.getBoolean("success", false)) {
            throw new IllegalStateException("读取内屏比例配置失败：" + result.getString("error", ""));
        }
        return InnerRatioConfig.parse(result.getString("value"));
    }

    static Result applyRatio(Context context, List<AppChoice> apps, int displayRatio, String ratioLabel,
                             boolean relaunch, Progress progress) throws Exception {
        if (apps.isEmpty()) {
            return Result.fail("请先选择应用。");
        }
        Store store = new Store(context);
        int succeeded = 0;
        int failed = 0;
        boolean adaptiveMissing = false;
        for (AppChoice app : apps) {
            progress.log("正在设置「" + app.label + "」的内屏显示比例…");
            Bundle result = ShizukuShell.setInnerDisplayRatio(app.packageName, displayRatio);
            if (!result.getBoolean("success", false)) {
                failed++;
                progress.log(app.label + "：" + result.getString("error", "系统未接受该设置"));
                continue;
            }
            if (!store.hasRatioPrevious(app.packageName)) {
                store.setRatioPrevious(app.packageName, result.getInt("previous_entry", -1));
            }
            boolean adaptiveEnabled = result.getBoolean("adaptive_enabled", false);
            store.setOptimized(app.packageName, adaptiveEnabled);
            if (displayRatio == PrivilegedShellService.RATIO_FULL_OPTIMIZATION && !adaptiveEnabled) {
                adaptiveMissing = true;
            }
            succeeded++;
        }
        if (relaunch && apps.size() == 1 && succeeded == 1) {
            relaunch(context, apps.get(0), progress);
        }
        String message = apps.size() == 1
                ? "已将「" + apps.get(0).label + "」的内屏显示比例设为「" + ratioLabel + "」，并重启该应用使其生效。"
                : "已为 " + succeeded + " 个应用设置内屏显示比例「" + ratioLabel + "」" + (failed == 0 ? "。" : "，失败 " + failed + " 个。");
        message += "进入 OriginOS 原生分屏或小窗后，比例由系统窗口容器管理。";
        if (adaptiveMissing) {
            message += "当前固件未提供自适应布局服务，已按全屏模式处理。";
        }
        return failed == 0 && succeeded > 0 ? Result.ok(message) : Result.fail(message);
    }

    static Result restoreRatio(Context context, List<AppChoice> apps, Progress progress) throws Exception {
        if (apps.isEmpty()) {
            return Result.fail("请先选择应用。");
        }
        Store store = new Store(context);
        int succeeded = 0;
        int failed = 0;
        for (AppChoice app : apps) {
            int previous = store.hasRatioPrevious(app.packageName) ? store.ratioPrevious(app.packageName) : -1;
            progress.log("恢复「" + app.label + "」为" + (previous < 0 ? "系统默认比例" : "原比例「" + ratioLabel(previous, false) + "」"));
            Bundle result = ShizukuShell.setInnerDisplayRatio(app.packageName, previous);
            if (result.getBoolean("success", false)) {
                store.clearRatio(app.packageName);
                succeeded++;
            } else {
                failed++;
                progress.log(app.label + "：" + result.getString("error", "系统未接受该设置"));
            }
        }
        String message = "已恢复 " + succeeded + " 个应用的内屏比例" + (failed == 0 ? "。" : "，失败 " + failed + " 个。");
        return failed == 0 ? Result.ok(message) : Result.fail(message);
    }

    private static void relaunch(Context context, AppChoice app, Progress progress) {
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(app.packageName);
        if (launch == null) {
            return;
        }
        try {
            Thread.sleep(400);
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            progress.log("已重新打开「" + app.label + "」。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            progress.log("重新打开「" + app.label + "」失败：" + safeMessage(error));
        }
    }

    // ================= 重启后重新应用 =================

    static int currentBootCount(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    /** 手机重启过、且之前用本 App 做过重启后会失效的设置时返回 true。 */
    static boolean needsReapply(Context context) {
        Store store = new Store(context);
        int bootCount = currentBootCount(context);
        return store.hasReapplyableState() && bootCount >= 0 && bootCount != store.appliedBootCount();
    }

    static Result reapply(Context context, List<AppChoice> apps, Progress progress) throws Exception {
        Store store = new Store(context);
        if (store.smallWindowEnabled()) {
            progress.log("重新开启所有应用小窗…");
            return enableAllSmallWindows(context, apps, progress);
        }
        if (store.repairedAll()) {
            progress.log("重新修复全部应用的分屏资格…");
            return repairSplit(context, apps, true, progress);
        }
        Set<String> repaired = store.repairedSplit();
        List<AppChoice> targets = new ArrayList<>();
        for (AppChoice app : apps) {
            if (repaired.contains(app.packageName)) {
                targets.add(app);
            }
        }
        if (targets.isEmpty()) {
            return Result.fail("还没有需要重新应用的设置。内屏比例保存在系统设置里，重启后不会丢失。");
        }
        progress.log("重新修复 " + targets.size() + " 个应用的分屏资格…");
        return repairSplit(context, targets, false, progress);
    }

    // ================= 诊断 =================

    static Result diagnostics(Context context, Progress progress) throws Exception {
        Store store = new Store(context);
        StringBuilder report = new StringBuilder("==== 诊断报告 ====\n");
        report.append("App：").append(BuildConfig.VERSION_NAME).append('\n');
        report.append("机型：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("（").append(Build.DEVICE).append("）\n");
        report.append("Android：").append(Build.VERSION.RELEASE).append("（SDK ").append(Build.VERSION.SDK_INT).append("）\n");
        for (String prop : new String[]{"ro.vivo.os.build.display.id", "ro.vivo.os.version", "ro.build.version.bbk"}) {
            ShizukuShell.ShellResult value = ShizukuShell.run("/system/bin/getprop", prop);
            report.append(prop).append("：").append(value.stdout.trim()).append('\n');
        }
        int uid = Shizuku.getUid();
        report.append("Shizuku：").append(uid == 0 ? "Root" : uid == 2000 ? "ADB" : "UID " + uid)
                .append(" 模式，服务版本 ").append(Shizuku.getVersion()).append('\n');
        report.append("开机次数：").append(currentBootCount(context))
                .append("，上次应用设置时：").append(store.appliedBootCount()).append('\n');
        report.append("已开启全应用小窗：").append(store.smallWindowEnabled())
                .append("，已修复全部分屏：").append(store.repairedAll())
                .append("，单独修复过分屏：").append(store.repairedSplit().size()).append(" 个\n");

        progress.log("读取系统开关…");
        report.append("\n-- 小窗相关系统开关（当前值 / 开启前备份） --\n");
        Map<String, String> backup = store.switchBackup();
        for (String[] item : SMALL_WINDOW_SWITCHES) {
            String key = item[0] + "/" + item[1];
            ShizukuShell.ShellResult value = ShizukuShell.run(SETTINGS, "get", item[0], item[1]);
            report.append(key).append(" = ").append(value.stdout.trim());
            if (backup.containsKey(key)) {
                report.append(" / ").append(backup.get(key));
            }
            report.append('\n');
        }

        report.append("\n-- 内屏比例配置 --\n");
        try {
            Map<String, Integer> ratios = readRatioMap();
            report.append("共 ").append(ratios.size()).append(" 条记录\n");
            for (Map.Entry<String, Integer> entry : ratios.entrySet()) {
                report.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
            }
        } catch (Exception error) {
            report.append(safeMessage(error)).append('\n');
        }

        progress.log("读取 splitconfig 帮助…");
        report.append("\n-- splitconfig 帮助 --\n")
                .append(ShizukuShell.run(CMD, "activity", "splitconfig", "help").output(2000)).append('\n');

        progress.log("检查 vivo 系统接口…");
        report.append("\n-- vivo 多窗接口 --\n").append(ShizukuShell.inspectVivoApis().getString("text", ""));
        return Result.ok(report.toString());
    }

    // ================= 工具 =================

    private static void appendFailure(StringBuilder failures, String message) {
        if (failures.length() > 0) {
            failures.append("；");
        }
        failures.append(message);
    }

    static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
