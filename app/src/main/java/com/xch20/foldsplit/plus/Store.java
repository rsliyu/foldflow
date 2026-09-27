package com.xch20.foldsplit.plus;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 本地记录：系统开关备份、改过哪些应用、原来的内屏比例、上次应用时的开机次数。 */
final class Store {
    private static final String PREFS = "foldsplit_plus";
    private static final String SWITCH_BACKUP_PREFIX = "switch_backup/";
    private static final String RATIO_PREVIOUS_PREFIX = "ratio_prev/";
    private static final String RATIO_OPTIMIZED = "ratio_optimized";
    private static final String SPLIT_REPAIRED = "split_repaired";
    private static final String SPLIT_REPAIRED_ALL = "split_repaired_all";
    private static final String SMALL_WINDOW_ENABLED = "small_window_enabled";
    private static final String APPLIED_BOOT_COUNT = "applied_boot_count";
    private static final String AUTO_REAPPLY = "auto_reapply";
    private static final String RELAUNCH_AFTER_RATIO = "relaunch_after_ratio";

    private final SharedPreferences prefs;

    Store(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- 小窗系统开关备份，key 形如 "global/enable_freeform_support"，值为 "null" 表示原本不存在

    boolean hasSwitchBackup() {
        return !switchBackup().isEmpty();
    }

    Map<String, String> switchBackup() {
        Map<String, String> backup = new HashMap<>();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(SWITCH_BACKUP_PREFIX)) {
                backup.put(entry.getKey().substring(SWITCH_BACKUP_PREFIX.length()), String.valueOf(entry.getValue()));
            }
        }
        return backup;
    }

    void saveSwitchBackup(Map<String, String> backup) {
        SharedPreferences.Editor editor = prefs.edit();
        for (Map.Entry<String, String> entry : backup.entrySet()) {
            editor.putString(SWITCH_BACKUP_PREFIX + entry.getKey(), entry.getValue());
        }
        editor.apply();
    }

    void clearSwitchBackup() {
        SharedPreferences.Editor editor = prefs.edit();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(SWITCH_BACKUP_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    // ---- 内屏比例：第一次修改前的原值（-1 表示原本没有记录，即系统默认）

    boolean hasRatioPrevious(String packageName) {
        return prefs.contains(RATIO_PREVIOUS_PREFIX + packageName);
    }

    int ratioPrevious(String packageName) {
        return prefs.getInt(RATIO_PREVIOUS_PREFIX + packageName, -1);
    }

    void setRatioPrevious(String packageName, int ratio) {
        prefs.edit().putInt(RATIO_PREVIOUS_PREFIX + packageName, ratio).apply();
    }

    void clearRatio(String packageName) {
        Set<String> optimized = optimizedPackages();
        optimized.remove(packageName);
        prefs.edit()
                .remove(RATIO_PREVIOUS_PREFIX + packageName)
                .putStringSet(RATIO_OPTIMIZED, optimized)
                .apply();
    }

    Set<String> ratioTouchedPackages() {
        Set<String> packages = new HashSet<>();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(RATIO_PREVIOUS_PREFIX)) {
                packages.add(key.substring(RATIO_PREVIOUS_PREFIX.length()));
            }
        }
        return packages;
    }

    Set<String> optimizedPackages() {
        return new HashSet<>(prefs.getStringSet(RATIO_OPTIMIZED, new HashSet<>()));
    }

    void setOptimized(String packageName, boolean optimized) {
        Set<String> packages = optimizedPackages();
        if (optimized) {
            packages.add(packageName);
        } else {
            packages.remove(packageName);
        }
        prefs.edit().putStringSet(RATIO_OPTIMIZED, packages).apply();
    }

    // ---- 分屏 / 小窗状态（重启后可能被系统清掉，需要重新应用）

    Set<String> repairedSplit() {
        return new HashSet<>(prefs.getStringSet(SPLIT_REPAIRED, new HashSet<>()));
    }

    void addRepairedSplit(Collection<String> packages) {
        Set<String> repaired = repairedSplit();
        repaired.addAll(packages);
        prefs.edit().putStringSet(SPLIT_REPAIRED, repaired).apply();
    }

    void removeRepairedSplit(Collection<String> packages) {
        Set<String> repaired = repairedSplit();
        repaired.removeAll(packages);
        prefs.edit().putStringSet(SPLIT_REPAIRED, repaired).apply();
    }

    boolean repairedAll() {
        return prefs.getBoolean(SPLIT_REPAIRED_ALL, false);
    }

    void setRepairedAll(boolean value) {
        prefs.edit().putBoolean(SPLIT_REPAIRED_ALL, value).apply();
    }

    boolean smallWindowEnabled() {
        return prefs.getBoolean(SMALL_WINDOW_ENABLED, false);
    }

    void setSmallWindowEnabled(boolean value) {
        prefs.edit().putBoolean(SMALL_WINDOW_ENABLED, value).apply();
    }

    boolean hasReapplyableState() {
        return smallWindowEnabled() || repairedAll() || !repairedSplit().isEmpty();
    }

    int appliedBootCount() {
        return prefs.getInt(APPLIED_BOOT_COUNT, -1);
    }

    void markApplied(int bootCount) {
        prefs.edit().putInt(APPLIED_BOOT_COUNT, bootCount).apply();
    }

    // ---- 偏好

    boolean autoReapply() {
        return prefs.getBoolean(AUTO_REAPPLY, false);
    }

    void setAutoReapply(boolean value) {
        prefs.edit().putBoolean(AUTO_REAPPLY, value).apply();
    }

    boolean relaunchAfterRatio() {
        return prefs.getBoolean(RELAUNCH_AFTER_RATIO, true);
    }

    void setRelaunchAfterRatio(boolean value) {
        prefs.edit().putBoolean(RELAUNCH_AFTER_RATIO, value).apply();
    }
}
