package com.vrcdevicecheck.osc.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * 界面语言。
 *
 * 为什么不用 AppCompatDelegate.setApplicationLocales()：
 * 本工程刻意不引入 AndroidX（保持 AGP 4.2.2 + compileSdk 30 的依赖面最小），
 * 所以自己用 Configuration 覆盖 locale——Activity 在 attachBaseContext 里包一层，
 * 之后 getString() 就会按选定语言取 values/ 或 values-en/。
 *
 * 约定：
 * - 默认中文（不管系统语言是什么）；
 * - 只影响界面文案，不影响发往 VRChat 的 Chatbox 文本（那由「仅发送 ASCII」决定）。
 */
public final class LocaleHelper {

    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    private static final String PREFS = "vrcdi_locale";
    private static final String KEY_LANGUAGE = "language";

    /** 语言读得很频繁（日志、提示），缓存一份；setLanguage() 会立刻刷新 */
    private static volatile String cachedLanguage;
    /** 已经套好 locale 的 application context，避免每次都新建 */
    private static volatile Context cachedContext;
    private static volatile String cachedContextLanguage;

    private LocaleHelper() {
    }

    public static String getLanguage(Context context) {
        String value = cachedLanguage;
        if (value != null) return value;
        value = LANG_ZH;
        if (context != null) {
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            SharedPreferences sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String stored = sp.getString(KEY_LANGUAGE, LANG_ZH);
            if (LANG_EN.equals(stored)) value = LANG_EN;
        }
        cachedLanguage = value;
        return value;
    }

    public static boolean isEnglish(Context context) {
        return LANG_EN.equals(getLanguage(context));
    }

    public static void setLanguage(Context context, String language) {
        String value = LANG_EN.equals(language) ? LANG_EN : LANG_ZH;
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, value).apply();
        cachedLanguage = value;
        cachedContext = null;
        cachedContextLanguage = null;
    }

    public static Locale localeOf(String language) {
        return LANG_EN.equals(language) ? Locale.US : Locale.SIMPLIFIED_CHINESE;
    }

    /** 给 Activity / Service 的 attachBaseContext 用：不改缓存，只套一层 locale */
    public static Context wrap(Context context) {
        if (context == null) return null;
        try {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.setLocale(localeOf(getLanguage(context)));
            return context.createConfigurationContext(configuration);
        } catch (Throwable t) {
            // 极端情况下（Context 还没准备好）宁可回退到原 Context，也不要启动就崩
            return context;
        }
    }

    /**
     * 非 Activity 环境（天气仓库、发送器、更新检查）取文案用。
     * 返回的是套好 locale 的 application context，可缓存复用。
     */
    public static Context localized(Context context) {
        if (context == null) return null;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        String language = getLanguage(app);
        Context result = cachedContext;
        if (result != null && language.equals(cachedContextLanguage)) return result;
        try {
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.setLocale(localeOf(language));
            result = app.createConfigurationContext(configuration);
        } catch (Throwable t) {
            result = app;
        }
        cachedContext = result;
        cachedContextLanguage = language;
        return result;
    }

    /** 取一条本地化文案；context 为 null 时退回中文默认资源 */
    public static String str(Context context, int resId) {
        Context localized = localized(context);
        return localized == null
                ? String.valueOf(resId)
                : localized.getString(resId);
    }

    public static String str(Context context, int resId, Object... args) {
        Context localized = localized(context);
        return localized == null
                ? String.valueOf(resId)
                : localized.getString(resId, args);
    }

    /** 切换语言后重启应用，让所有已打开的界面按新语言重建 */
    public static void applyAndRestart(Activity activity) {
        try {
            Intent intent = activity.getPackageManager()
                    .getLaunchIntentForPackage(activity.getPackageName());
            if (intent == null) return;
            // CLEAR_TASK：把现有界面全部清掉再拉起，确保没有旧语言的界面残留在返回栈里
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finishAffinity();
        } catch (Throwable ignored) {
            // 重启失败就留在当前页面，下次进入应用时也会是新语言
        }
    }
}
