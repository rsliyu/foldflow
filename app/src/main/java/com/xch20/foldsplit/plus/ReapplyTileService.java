package com.xch20.foldsplit.plus;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;

/** 下拉栏快捷开关：重启并启动 Shizuku 后，一键重新应用分屏/小窗设置。 */
public final class ReapplyTileService extends TileService {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile(null);
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!RUNNING.compareAndSet(false, true)) {
            toast("正在重新应用，请稍候");
            return;
        }
        updateTile("正在应用…");
        new Thread(this::runReapply, "foldsplit-tile").start();
    }

    private void runReapply() {
        try {
            if (!waitForShizuku()) {
                mainHandler.post(() -> {
                    toast("Shizuku 未运行或未授权，请先启动 Shizuku");
                    openApp();
                });
                return;
            }
            List<AppChoice> apps = AppChoice.loadLaunchable(this, false);
            FoldOps.Result result = FoldOps.reapply(this, apps, message -> { });
            mainHandler.post(() -> toast((result.success ? "已重新应用：" : "未完成：") + result.message));
        } catch (Throwable error) {
            mainHandler.post(() -> toast("重新应用失败：" + FoldOps.safeMessage(error)));
        } finally {
            RUNNING.set(false);
            mainHandler.post(() -> updateTile(null));
        }
    }

    /** 快捷开关可能在 App 进程刚启动时被点击，Shizuku binder 需要一点时间才能收到。 */
    private boolean waitForShizuku() throws InterruptedException {
        if (ShizukuShell.isReady()) {
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Shizuku.OnBinderReceivedListener listener = latch::countDown;
        Shizuku.addBinderReceivedListenerSticky(listener, mainHandler);
        try {
            latch.await(3, TimeUnit.SECONDS);
        } finally {
            Shizuku.removeBinderReceivedListener(listener);
        }
        return ShizukuShell.isReady();
    }

    private void updateTile(String subtitleOverride) {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        boolean needed = FoldOps.needsReapply(this);
        tile.setState(needed ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(subtitleOverride != null ? subtitleOverride
                    : needed ? "重启后需重新应用" : new Store(this).hasReapplyableState() ? "已是最新" : "暂无设置");
        }
        tile.updateTile();
    }

    @SuppressWarnings("deprecation")
    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE));
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
