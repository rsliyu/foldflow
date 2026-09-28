package io.github.rsliyu.foldflow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * vivo 内屏显示比例配置 fold_inner_screen_ratio_modified_by_user 的读写工具。
 * 格式为若干个 ":包名,比例:" 首尾相连，例如 ":com.a,1::com.b,0:"。
 * 修改时保留其他条目的原始文本，只替换或删除目标包名的条目。
 */
final class InnerRatioConfig {
    static final String SETTING_KEY = "fold_inner_screen_ratio_modified_by_user";

    private InnerRatioConfig() {
    }

    static Map<String, Integer> parse(String raw) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String entry : entries(raw)) {
            int comma = entry.indexOf(',');
            try {
                result.put(entry.substring(0, comma), Integer.parseInt(entry.substring(comma + 1)));
            } catch (NumberFormatException ignored) {
                // 系统写入了非数字比例时跳过，不影响其他条目显示
            }
        }
        return result;
    }

    /** 返回包名当前记录的比例，没有记录时返回 -1。 */
    static int find(String raw, String packageName) {
        Integer value = parse(raw).get(packageName);
        return value == null ? -1 : value;
    }

    static String with(String raw, String packageName, int storedRatio) {
        StringBuilder updated = without(raw, packageName);
        updated.append(':').append(packageName).append(',').append(storedRatio).append(':');
        return updated.toString();
    }

    static StringBuilder without(String raw, String packageName) {
        StringBuilder updated = new StringBuilder();
        for (String entry : entries(raw)) {
            if (!packageName.equals(entry.substring(0, entry.indexOf(',')))) {
                updated.append(':').append(entry).append(':');
            }
        }
        return updated;
    }

    private static String[] entries(String raw) {
        if (raw == null || raw.trim().isEmpty() || "null".equals(raw.trim())) {
            return new String[0];
        }
        String[] parts = raw.replace(" ", "").trim().split(":");
        int count = 0;
        for (String part : parts) {
            int comma = part.indexOf(',');
            if (!part.isEmpty() && comma > 0 && comma != part.length() - 1) {
                parts[count++] = part;
            }
        }
        String[] valid = new String[count];
        System.arraycopy(parts, 0, valid, 0, count);
        return valid;
    }
}
