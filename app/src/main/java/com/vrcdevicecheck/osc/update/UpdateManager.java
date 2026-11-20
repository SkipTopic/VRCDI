package com.vrcdevicecheck.osc.update;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.vrcdevicecheck.osc.R;
import com.vrcdevicecheck.osc.util.LocaleHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * 下载并安装新版本。
 *
 * 下载：直接下载发行版附件的直链（GitHub 的 browser_download_url 会 302 到 CDN，
 * HttpURLConnection 默认跟随跳转），保存为 cache/update/VRCDI-update.apk。
 *
 * 安装：通过自带的 {@link ApkProvider} 以 content:// 打开，交给系统安装器；
 * 如果 Gitee 把 APK 命名为 xxx.apk.zip，保存时仍然落在 .apk 上，不影响安装。
 */
public final class UpdateManager {

    private static final String APK_NAME = "VRCDI-update.apk";

    private UpdateManager() {
    }

    public interface Progress {
        void onProgress(int percent);
    }

    public static File download(Context context, UpdateChecker.Asset asset, Progress progress)
            throws Exception {
        File dir = ApkProvider.updateDir(context);
        File target = new File(dir, APK_NAME);
        if (target.exists() && !target.delete()) {
            throw new Exception(LocaleHelper.str(context, R.string.update_err_overwrite));
        }

        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            conn = (HttpURLConnection) new URL(asset.url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", UpdateChecker.USER_AGENT);
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new Exception(LocaleHelper.str(context, R.string.update_err_download_http, code));
            }

            long total = conn.getContentLength();
            in = conn.getInputStream();
            out = new FileOutputStream(target);
            byte[] buffer = new byte[8192];
            long done = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
                done += read;
                if (progress != null && total > 0) {
                    progress.onProgress((int) (done * 100 / total));
                }
            }
            out.flush();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                    // 忽略
                }
            }
            if (conn != null) conn.disconnect();
        }

        if (!target.isFile() || target.length() <= 0) {
            throw new Exception(LocaleHelper.str(context, R.string.update_err_empty_file));
        }
        // 附件名可能是 xxx.apk.zip，内容仍是 APK；保存名固定为 .apk，这里只做个兜底改名
        if (asset.name != null && asset.name.toLowerCase(Locale.US).endsWith(".zip")) {
            File renamed = new File(dir, APK_NAME);
            if (!target.equals(renamed) && target.renameTo(renamed)) {
                return renamed;
            }
        }
        return target;
    }

    public static boolean canInstall(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 打开「允许安装未知应用」设置页 */
    public static void openInstallPermission(Context context) {
        if (context == null) return;
        Intent settings = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName()));
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(settings);
        } catch (Throwable ignored) {
            // 个别机型没有这个页面，忽略
        }
    }

    /** 直接调起安装器（调用方已经确认有安装权限） */
    public static void installNow(Context context, File apk) throws Exception {
        if (apk == null || !apk.isFile()) {
            throw new Exception(LocaleHelper.str(context, R.string.update_err_no_apk_file));
        }
        Uri uri = ApkProvider.uriFor(context, apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void install(Context context, File apk) throws Exception {
        if (apk == null || !apk.isFile()) {
            throw new Exception(LocaleHelper.str(context, R.string.update_err_no_apk_file));
        }
        if (!canInstall(context)) {
            openInstallPermission(context);
            throw new Exception(LocaleHelper.str(context, R.string.update_err_unknown_sources));
        }
        installNow(context, apk);
    }
}
