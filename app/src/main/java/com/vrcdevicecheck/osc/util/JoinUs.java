package com.vrcdevicecheck.osc.util;

import android.app.Activity;
import android.app.AlertDialog;

/**
 * 「加入我们」弹窗：Discord 服务器与 QQ 群。
 * 软件更新设置页和关于页共用同一段文案。
 */
public final class JoinUs {

    public static final String DISCORD = "https://discord.gg/ayjFK4KwC";
    public static final String QQ_GROUP = "1134428559";

    private JoinUs() {
    }

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        String message = "即使是正式版也难免有许多bug，为了让开发者知道bug是什么，"
                + "作者特意创建了Discord服务器和QQ群，加入我们让软件变得更好用。\n\n"
                + "Discord：" + DISCORD + "\n"
                + "QQ群：" + QQ_GROUP;
        new AlertDialog.Builder(activity)
                .setTitle("加入我们")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }
}
