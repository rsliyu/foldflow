package com.xch20.foldsplit.plus;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/** App 进程这一侧：负责绑定 Shizuku UserService，并把调用转给 PrivilegedShellService。 */
public final class ShizukuShell {
    private static final Object LOCK = new Object();
    private static final Shizuku.UserServiceArgs USER_SERVICE_ARGS = new Shizuku.UserServiceArgs(
            new ComponentName(BuildConfig.APPLICATION_ID, PrivilegedShellService.class.getName()))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE);

    private static IPrivilegedShell activeService;
    private static IBinder activeBinder;
    private static CountDownLatch connectionLatch;

    private static final ServiceConnection SERVICE_CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (LOCK) {
                activeService = IPrivilegedShell.Stub.asInterface(binder);
                activeBinder = binder;
                if (connectionLatch != null) {
                    connectionLatch.countDown();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (LOCK) {
                activeService = null;
                activeBinder = null;
            }
        }
    };

    private ShizukuShell() {
    }

    public static boolean isReady() {
        try {
            return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static ShellResult run(String... command) throws Exception {
        Bundle result = connect().execute(command);
        return new ShellResult(result.getInt("exit_code", -1),
                result.getString("stdout", ""), result.getString("stderr", ""));
    }

    public static Bundle runVivoFreeformConfig(String[] packageNames) throws Exception {
        return connect().executeVivoFreeformConfig(packageNames);
    }

    public static Bundle setInnerDisplayRatio(String packageName, int displayRatio) throws Exception {
        return connect().setInnerDisplayRatio(packageName, displayRatio);
    }

    public static Bundle readSecureSetting(String key) throws Exception {
        return connect().readSecureSetting(key);
    }

    public static Bundle inspectVivoApis() throws Exception {
        return connect().inspectVivoApis();
    }

    private static IPrivilegedShell connect() throws Exception {
        if (!isReady()) {
            throw new IllegalStateException("Shizuku 未运行或未授权");
        }
        CountDownLatch latch;
        synchronized (LOCK) {
            if (activeService != null && activeBinder != null && activeBinder.pingBinder()) {
                return activeService;
            }
            connectionLatch = new CountDownLatch(1);
            latch = connectionLatch;
            Shizuku.bindUserService(USER_SERVICE_ARGS, SERVICE_CONNECTION);
        }
        if (!latch.await(8, TimeUnit.SECONDS)) {
            throw new IllegalStateException("连接 Shizuku 特权服务超时");
        }
        synchronized (LOCK) {
            if (activeService == null) {
                throw new IllegalStateException("Shizuku 特权服务没有返回接口");
            }
            return activeService;
        }
    }

    public static void close() {
        synchronized (LOCK) {
            if (activeService != null) {
                try {
                    Shizuku.unbindUserService(USER_SERVICE_ARGS, SERVICE_CONNECTION, true);
                } catch (Throwable ignored) {
                    // 服务已经不在了
                }
                activeService = null;
                activeBinder = null;
            }
        }
    }

    public static final class ShellResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        ShellResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public boolean isSuccess() {
            String combined = (stdout + "\n" + stderr).toLowerCase(Locale.ROOT);
            return exitCode == 0 && !combined.contains("error:") && !combined.contains("exception");
        }

        public String output(int limit) {
            String combined = stdout;
            if (!stderr.isEmpty()) {
                combined = combined.isEmpty() ? stderr : combined + "\n" + stderr;
            }
            return combined.length() > limit ? combined.substring(0, limit) + "…" : combined;
        }

        public String compactOutput() {
            return output(500);
        }
    }
}
