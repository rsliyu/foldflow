package com.xch20.foldsplit.plus;

import android.content.AttributionSource;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.provider.Settings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * 运行在 Shizuku 拉起的 shell 身份进程里（uid 2000 或 root），所有需要系统权限的操作都在这里执行。
 */
public final class PrivilegedShellService extends IPrivilegedShell.Stub {
    static final String VIVO_ADAPTIVE_UI_MANAGER_CLASS = "vivo.app.adaptiveui.manager.AdaptiveUiManager";
    static final String VIVO_FREEFORM_MANAGER_CLASS = "vivo.app.vivofreeform.VivoFreeformManager";
    static final int RATIO_SYSTEM_DEFAULT = -1;
    static final int RATIO_FULL_OPTIMIZATION = 4;

    private static final long COMMAND_TIMEOUT_SECONDS = 30;

    private interface ProviderOperation<T> {
        T run(Object provider, AttributionSource attribution) throws Exception;
    }

    public PrivilegedShellService() {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public Bundle execute(String[] command) {
        Bundle result = new Bundle();
        if (command == null || command.length == 0) {
            result.putInt("exit_code", 2);
            result.putString("stderr", "empty command");
            return result;
        }
        java.lang.Process process = null;
        try {
            final java.lang.Process started = Runtime.getRuntime().exec(command);
            process = started;
            FutureTask<String> stdout = new FutureTask<>(() -> read(started.getInputStream()));
            FutureTask<String> stderr = new FutureTask<>(() -> read(started.getErrorStream()));
            new Thread(stdout, "foldsplit-stdout").start();
            new Thread(stderr, "foldsplit-stderr").start();
            if (!started.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                result.putInt("exit_code", 124);
                result.putString("stderr", "command timed out: " + String.join(" ", command));
                return result;
            }
            result.putInt("exit_code", started.exitValue());
            result.putString("stdout", stdout.get(2, TimeUnit.SECONDS));
            result.putString("stderr", stderr.get(2, TimeUnit.SECONDS));
        } catch (Throwable error) {
            result.putInt("exit_code", 1);
            result.putString("stderr", unwrap(error).toString());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return result;
    }

    @Override
    public Bundle executeVivoFreeformConfig(String[] packageNames) {
        Bundle result = new Bundle();
        try {
            ArrayList<String> enabledPackages = new ArrayList<>();
            if (packageNames != null) {
                for (String packageName : packageNames) {
                    validatePackageName(packageName);
                    if (!enabledPackages.contains(packageName)) {
                        enabledPackages.add(packageName);
                    }
                }
            }
            if (enabledPackages.isEmpty()) {
                throw new IllegalArgumentException("freeform package list is empty");
            }
            allowHiddenApiReflection();
            Class<?> managerClass = Class.forName(VIVO_FREEFORM_MANAGER_CLASS);
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Method update = findSingleArgMethod(managerClass, "updateVivoFreeformConfig");
            if (update == null) {
                throw new NoSuchMethodException("VivoFreeformManager.updateVivoFreeformConfig");
            }
            update.setAccessible(true);
            HashMap<String, ArrayList<String>> config = new HashMap<>();
            config.put("FreeFormEnabledApp", enabledPackages);
            update.invoke(manager, config);
            result.putBoolean("success", true);
            result.putInt("package_count", enabledPackages.size());
        } catch (Throwable error) {
            result.putBoolean("success", false);
            result.putString("error", unwrap(error).toString());
        }
        return result;
    }

    @Override
    public Bundle setInnerDisplayRatio(String packageName, int displayRatio) {
        Bundle result = new Bundle();
        try {
            validatePackageName(packageName);
            if (displayRatio < RATIO_SYSTEM_DEFAULT || displayRatio > RATIO_FULL_OPTIMIZATION) {
                throw new IllegalArgumentException("inner display ratio must be between -1 and 4");
            }
            allowHiddenApiReflection();
            Boolean adaptiveSupported = updateDisplayedAdaptiveMap(packageName, displayRatio == RATIO_FULL_OPTIMIZATION);

            String current = readSecure(InnerRatioConfig.SETTING_KEY);
            int previous = InnerRatioConfig.find(current, packageName);
            int storedRatio = displayRatio == RATIO_FULL_OPTIMIZATION ? 0 : displayRatio;
            String updated = displayRatio == RATIO_SYSTEM_DEFAULT
                    ? InnerRatioConfig.without(current, packageName).toString()
                    : InnerRatioConfig.with(current, packageName, storedRatio);
            if (previous != storedRatio) {
                writeSecure(InnerRatioConfig.SETTING_KEY, updated);
            }
            String verified = readSecure(InnerRatioConfig.SETTING_KEY);
            if (InnerRatioConfig.find(verified, packageName) != storedRatio) {
                throw new IllegalStateException("写入后系统未读回目标内屏显示比例");
            }

            Bundle stop = execute(new String[]{"/system/bin/am", "force-stop", packageName});
            if (stop.getInt("exit_code", 1) != 0) {
                throw new IllegalStateException("重启应用使显示比例生效失败：" + stop.getString("stderr", ""));
            }
            result.putBoolean("success", true);
            result.putInt("previous_entry", previous);
            result.putInt("stored_ratio", storedRatio);
            result.putBoolean("adaptive_supported", adaptiveSupported != null);
            result.putBoolean("adaptive_enabled",
                    displayRatio == RATIO_FULL_OPTIMIZATION && Boolean.TRUE.equals(adaptiveSupported));
        } catch (Throwable error) {
            result.putBoolean("success", false);
            result.putString("error", unwrap(error).toString());
        }
        return result;
    }

    @Override
    public Bundle readSecureSetting(String key) {
        Bundle result = new Bundle();
        try {
            result.putString("value", readSecure(key));
            result.putBoolean("success", true);
        } catch (Throwable error) {
            result.putBoolean("success", false);
            result.putString("error", unwrap(error).toString());
        }
        return result;
    }

    /** 只读：列出 vivo 多窗相关系统类的方法签名，方便以后针对固件做适配。 */
    @Override
    public Bundle inspectVivoApis() {
        Bundle result = new Bundle();
        StringBuilder text = new StringBuilder();
        allowHiddenApiReflection();
        for (String className : new String[]{VIVO_FREEFORM_MANAGER_CLASS, VIVO_ADAPTIVE_UI_MANAGER_CLASS}) {
            text.append(className).append('\n');
            try {
                Method[] methods = Class.forName(className).getDeclaredMethods();
                Arrays.sort(methods, Comparator.comparing(Method::getName));
                int shown = 0;
                for (Method method : methods) {
                    if (shown++ >= 150) {
                        text.append("  …共 ").append(methods.length).append(" 个方法\n");
                        break;
                    }
                    text.append("  ");
                    if (Modifier.isStatic(method.getModifiers())) {
                        text.append("static ");
                    }
                    text.append(method.getName()).append('(');
                    Class<?>[] params = method.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        text.append(i == 0 ? "" : ", ").append(params[i].getSimpleName());
                    }
                    text.append(") → ").append(method.getReturnType().getSimpleName()).append('\n');
                }
            } catch (Throwable error) {
                text.append("  不可用：").append(unwrap(error)).append('\n');
            }
        }
        result.putBoolean("success", true);
        result.putString("text", text.toString());
        return result;
    }

    private static String readSecure(final String key) throws Exception {
        return withSettingsProvider((provider, attribution) -> {
            Bundle queryArgs = new Bundle();
            queryArgs.putString("android:query-arg-sql-selection", "name=?");
            queryArgs.putStringArray("android:query-arg-sql-selection-args", new String[]{key});
            queryArgs.putStringArray("android:query-arg-sql-projection", new String[]{"value"});
            Method query = provider.getClass().getMethod("query", AttributionSource.class, Uri.class,
                    String[].class, Bundle.class, Class.forName("android.os.ICancellationSignal"));
            Cursor cursor = (Cursor) query.invoke(provider, attribution, Settings.Secure.CONTENT_URI,
                    new String[]{"value"}, queryArgs, null);
            if (cursor == null) {
                return null;
            }
            try {
                if (!cursor.moveToFirst()) {
                    return null;
                }
                int valueColumn = cursor.getColumnIndex("value");
                return valueColumn >= 0 ? cursor.getString(valueColumn) : null;
            } finally {
                cursor.close();
            }
        });
    }

    private static void writeSecure(final String key, final String value) throws Exception {
        withSettingsProvider((provider, attribution) -> {
            ContentValues values = new ContentValues();
            values.put("name", key);
            values.put("value", value);
            Bundle updateArgs = new Bundle();
            updateArgs.putString("android:query-arg-sql-selection", "name=?");
            updateArgs.putStringArray("android:query-arg-sql-selection-args", new String[]{key});
            Method update = provider.getClass().getMethod("update", AttributionSource.class, Uri.class,
                    ContentValues.class, Bundle.class);
            int updated = (Integer) update.invoke(provider, attribution, Settings.Secure.CONTENT_URI, values, updateArgs);
            if (updated == 0) {
                Method insert = provider.getClass().getMethod("insert", AttributionSource.class, Uri.class,
                        ContentValues.class, Bundle.class);
                if (insert.invoke(provider, attribution, Settings.Secure.CONTENT_URI, values, null) == null) {
                    throw new IllegalStateException("系统拒绝写入内屏显示比例");
                }
            }
            return null;
        });
    }

    private static <T> T withSettingsProvider(ProviderOperation<T> operation) throws Exception {
        allowHiddenApiReflection();
        Object activityManager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        IBinder token = new Binder();
        Method getProvider = activityManager.getClass().getMethod("getContentProviderExternal",
                String.class, int.class, IBinder.class, String.class);
        Object holder = getProvider.invoke(activityManager, "settings", 0, token, "com.android.shell");
        if (holder == null) {
            throw new IllegalStateException("无法取得 SettingsProvider");
        }
        Object provider = holder.getClass().getField("provider").get(holder);
        if (provider == null) {
            throw new IllegalStateException("SettingsProvider 没有返回可用接口");
        }
        AttributionSource attribution = new AttributionSource.Builder(Process.myUid())
                .setPid(Process.myPid())
                .setPackageName("com.android.shell")
                .build();
        try {
            return operation.run(provider, attribution);
        } finally {
            try {
                activityManager.getClass().getMethod("removeContentProviderExternal", String.class, IBinder.class)
                        .invoke(activityManager, "settings", token);
            } catch (Throwable ignored) {
                // 释放失败不影响结果，进程退出时系统会回收
            }
        }
    }

    private static Boolean updateDisplayedAdaptiveMap(String packageName, boolean enabled) throws Exception {
        try {
            Class<?> managerClass = Class.forName(VIVO_ADAPTIVE_UI_MANAGER_CLASS);
            Object manager = managerClass.getMethod("getInstance").invoke(null);
            Method update = managerClass.getMethod("updateDisplayedAdaptiveMap", String.class, boolean.class);
            Object value = update.invoke(manager, packageName, enabled);
            return value instanceof Boolean ? (Boolean) value : Boolean.TRUE;
        } catch (ClassNotFoundException | NoSuchMethodException unsupported) {
            return null;
        }
    }

    private static void validatePackageName(String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) {
            throw new IllegalArgumentException("invalid package name: " + packageName);
        }
    }

    private static Method findSingleArgMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        return null;
    }

    private static void allowHiddenApiReflection() {
        try {
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Object vmRuntime = getRuntime.invoke(null);
            Method setExemptions = vmRuntimeClass.getDeclaredMethod("setHiddenApiExemptions", String[].class);
            setExemptions.setAccessible(true);
            setExemptions.invoke(vmRuntime, (Object) new String[]{"L"});
        } catch (Throwable ignored) {
            // shell 进程通常本身就不受隐藏 API 限制
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null
                && (cause instanceof InvocationTargetException || cause instanceof UndeclaredThrowableException)) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String read(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8").trim();
        }
    }
}
