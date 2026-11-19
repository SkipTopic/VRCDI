package com.vrcdevicecheck.osc.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 关于页里的项目链接。
 *
 * 用 ACTION_VIEW 打开，交给系统按用户的默认浏览器 / 自定义浏览器处理，
 * 不在应用内嵌 WebView，也不做任何浏览器选择逻辑。
 */
public final class AboutLinks {

    public static final String GITHUB = "https://github.com/WanliuStudio/VRCDI";
    public static final String GITEE = "https://gitee.com/WanliuStudio/VRCDI";

    private AboutLinks() {
    }

    public static void bind(final TextView view) {
        if (view == null) return;
        String text = "GitHub：" + GITHUB + "\nGitee：" + GITEE;
        SpannableString span = new SpannableString(text);
        addLink(span, text.indexOf(GITHUB), GITHUB);
        addLink(span, text.indexOf(GITEE), GITEE);

        view.setText(span);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(Color.TRANSPARENT);
        try {
            view.setLinkTextColor(Color.parseColor("#0277BD"));
        } catch (Throwable ignored) {
            // 主题会覆盖颜色，失败无所谓
        }
    }

    private static void addLink(SpannableString span, int start, final String url) {
        if (start < 0) return;
        ClickableSpan clickable = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                open(widget.getContext(), url);
            }
        };
        span.setSpan(clickable, start, start + url.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** 用系统默认浏览器（或用户设置的默认浏览器）打开 */
    public static void open(Context context, String url) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            try {
                Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
                // 忽略
            }
        }
    }
}
