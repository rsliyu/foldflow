package io.github.rsliyu.foldflow;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AppChoice {
    final String label;
    final String packageName;
    final boolean system;
    final Drawable icon;

    private AppChoice(String label, String packageName, boolean system, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.system = system;
        this.icon = icon;
    }

    /** 界面预览和测试用。 */
    static AppChoice of(String label, String packageName, boolean system, Drawable icon) {
        return new AppChoice(label, packageName, system, icon);
    }

    boolean matches(String normalizedQuery) {
        return normalizedQuery.isEmpty()
                || label.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || packageName.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    /** 读取桌面上所有可启动的应用（排除本 App），按名称排序。 */
    static List<AppChoice> loadLaunchable(Context context, boolean withIcons) {
        PackageManager pm = context.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, AppChoice> unique = new LinkedHashMap<>();
        for (ResolveInfo info : pm.queryIntentActivities(query, PackageManager.MATCH_ALL)) {
            if (info.activityInfo == null || info.activityInfo.applicationInfo == null) {
                continue;
            }
            String packageName = info.activityInfo.packageName;
            if (context.getPackageName().equals(packageName) || unique.containsKey(packageName)) {
                continue;
            }
            CharSequence rawLabel = info.loadLabel(pm);
            String label = TextUtils.isEmpty(rawLabel) ? packageName : rawLabel.toString();
            int flags = info.activityInfo.applicationInfo.flags;
            boolean system = (flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
            unique.put(packageName, new AppChoice(label, packageName, system, withIcons ? info.loadIcon(pm) : null));
        }
        List<AppChoice> apps = new ArrayList<>(unique.values());
        Collator collator = Collator.getInstance(Locale.CHINA);
        apps.sort((a, b) -> collator.compare(a.label, b.label));
        return apps;
    }

    @Override
    public String toString() {
        return label + "  (" + packageName + ")";
    }
}
