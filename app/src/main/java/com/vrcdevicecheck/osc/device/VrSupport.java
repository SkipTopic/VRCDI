package com.vrcdevicecheck.osc.device;

import android.os.Build;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VR 一体机（Quest / Pico）相关的小工具。
 *
 * 这里不引入任何 XR/OpenXR 依赖，只做“现有功能在 VR 上更好用”的适配：
 * - 识别设备类型，界面上给出合适的权限提示；
 * - 品牌归一化（oculus/facebook -> Meta，bytedance/pico -> Pico）；
 * - 解析 dumpsys activity 里的前台包名，作为 UsageStats 的兜底。
 */
public final class VrSupport {

    /** ActivityRecord{... u0 com.vrchat.oculus.quest/com.unity3d.player.UnityPlayerActivity ...} */
    private static final Pattern FOREGROUND_PATTERN =
            Pattern.compile("([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+)/");

    private VrSupport() {
    }

    public static boolean isVrDevice() {
        String text = (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL
                + " " + Build.DEVICE + " " + Build.PRODUCT).toLowerCase(java.util.Locale.US);
        return text.contains("oculus")
                || text.contains("quest")
                || text.contains("pico")
                || text.contains("bytedance")
                || text.contains("meta");
    }

    /** 品牌显示名归一化，避免出现 "oculus" 这种小写厂商名 */
    public static String normalizeBrand(String brand) {
        if (brand == null) return null;
        String value = brand.trim();
        if (value.isEmpty()) return value;
        if ("oculus".equalsIgnoreCase(value)
                || "facebook".equalsIgnoreCase(value)
                || "meta".equalsIgnoreCase(value)) {
            return "Meta";
        }
        if ("pico".equalsIgnoreCase(value) || "bytedance".equalsIgnoreCase(value)) {
            return "Pico";
        }
        return value;
    }

    /**
     * 从 dumpsys activity 的输出里取前台包名；取不到返回 null。
     * 兼容 "mResumedActivity: ActivityRecord{... u0 com.x/.Y ...}" 和
     * "topResumedActivity=ActivityRecord{...}" 两种写法。
     */
    public static String parseForegroundPackage(String dump) {
        if (dump == null || dump.trim().isEmpty()) return null;
        Matcher matcher = FOREGROUND_PATTERN.matcher(dump);
        if (!matcher.find()) return null;
        String pkg = matcher.group(1);
        if (pkg.startsWith("android.") || pkg.startsWith("com.android.")) return null;
        return pkg;
    }
}
