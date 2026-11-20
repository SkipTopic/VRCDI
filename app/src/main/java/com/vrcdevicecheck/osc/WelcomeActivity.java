package com.vrcdevicecheck.osc;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.theme.ThemeManager;
import com.vrcdevicecheck.osc.util.LocaleHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 首次启动引导：先选语言，再看一遍「怎么在 VRChat 里打开 OSC」。
 *
 * 两个入口：
 * - {@link #intentForOnboarding(Context)}：第一次打开 App 时由 MainActivity 拉起，
 *   走完（或返回）会把「已引导」标记写进 AppSettings，之后不再出现；
 * - {@link #intentForGuide(Context)}：设置 → OSC 使用教程，随时可以再看一遍，不影响标记。
 *
 * 选完语言不是就地刷新，而是带着 extra 重新拉起自己：这样 attachBaseContext 会按新语言
 * 重建 Resource，所有 getString 立刻是目标语言，不用赌 updateConfiguration 生效。
 *
 * 截图是实拍的 VRChat 界面（中文 / English / 日本語 / Русский / 한국어 各一套），
 * 放在 res/drawable-nodpi；第 1 步「打开快捷菜单」各语言界面一样，共用一张。
 */
public class WelcomeActivity extends BaseActivity {

    private static final String EXTRA_GUIDE = "guide";
    private static final String EXTRA_STEP = "step";
    /** 0 = 选语言（首次启动），1 = 直接看教程 */
    private static final int STEP_LANGUAGE = 0;
    private static final int STEP_TUTORIAL = 1;
    /** 教程固定 5 步：打开快捷菜单 → 设置 → 展开 → 虚拟形象 → OSC 开关 */
    private static final int STEP_COUNT = 5;

    private static final int[] STEP_TEXT = {
            R.string.guide_step1,
            R.string.guide_step2,
            R.string.guide_step3,
            R.string.guide_step4,
            R.string.guide_step5,
    };

    /** 语言名用各语言自己的写法 —— 选语言这一步不能依赖当前界面语言 */
    private static final String[] LANGUAGE_LABELS = {
            "简体中文", "English", "日本語", "Русский", "한국어"};

    /**
     * 第 2~5 步的截图，行序与 {@link LocaleHelper#ALL} 一致（zh / en / ja / ru / ko）。
     * 第 1 步共用 guide_open_menu。
     */
    private static final int[][] STEP_IMAGES = {
            {R.drawable.guide_settings_zh, R.drawable.guide_expand_zh,
                    R.drawable.guide_avatar_zh, R.drawable.guide_osc_zh},
            {R.drawable.guide_settings_en, R.drawable.guide_expand_en,
                    R.drawable.guide_avatar_en, R.drawable.guide_osc_en},
            {R.drawable.guide_settings_ja, R.drawable.guide_expand_ja,
                    R.drawable.guide_avatar_ja, R.drawable.guide_osc_ja},
            {R.drawable.guide_settings_ru, R.drawable.guide_expand_ru,
                    R.drawable.guide_avatar_ru, R.drawable.guide_osc_ru},
            {R.drawable.guide_settings_ko, R.drawable.guide_expand_ko,
                    R.drawable.guide_avatar_ko, R.drawable.guide_osc_ko},
    };

    private boolean guideOnly;
    private AppSettings settings;

    public static Intent intentForOnboarding(Context context) {
        return new Intent(context, WelcomeActivity.class);
    }

    public static Intent intentForGuide(Context context) {
        return new Intent(context, WelcomeActivity.class).putExtra(EXTRA_GUIDE, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(this);
        guideOnly = getIntent().getBooleanExtra(EXTRA_GUIDE, false);
        int step = getIntent().getIntExtra(EXTRA_STEP, STEP_LANGUAGE);
        // 从设置进来的是纯教程；首次引导选完语言后也是直接进教程，但那次带着 guideOnly=false
        if (guideOnly || step == STEP_TUTORIAL) {
            showGuide();
        } else {
            showLanguagePicker();
        }
    }

    // ---------------- 第一步：选语言 ----------------

    private void showLanguagePicker() {
        LinearLayout root = newRoot();

        TextView title = text("VRCDI", 30, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = text(getString(R.string.onboarding_language_subtitle), 14,
                R.color.text_secondary);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = dp(10);
        root.addView(subtitle, subParams);

        for (int i = 0; i < LocaleHelper.ALL.length; i++) {
            final String language = LocaleHelper.ALL[i];
            Button button = new Button(this);
            button.setText(LANGUAGE_LABELS[i]);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            button.setTextColor(getColor(R.color.text_primary));
            button.setAllCaps(false);
            // 平台 Button 会按 colorButtonNormal 给背景上 tint，会把圆角描边一起染掉
            button.setBackgroundTintList(null);
            button.setBackground(languageButtonBackground(language));
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectLanguage(language);
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            params.topMargin = dp(10);
            root.addView(button, params);
        }

        TextView hint = text(getString(R.string.onboarding_language_hint), 12,
                R.color.text_secondary);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(18);
        root.addView(hint, hintParams);

        setContentView(scroll(root));
        ThemeManager.apply(this);
    }

    /**
     * 选完语言：写偏好，再带着「直接看教程」重新拉起自己。
     * 新实例的 attachBaseContext 会按新 locale 取资源，界面文字和教程截图一起换掉。
     */
    private void selectLanguage(String language) {
        LocaleHelper.setLanguage(this, language);
        Intent intent = new Intent(this, WelcomeActivity.class);
        intent.putExtra(EXTRA_STEP, STEP_TUTORIAL);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        finish();
    }

    /** 当前语言在 {@link #STEP_IMAGES} 里的行号；语言异常时回退中文那套 */
    private int languageRow() {
        String language = LocaleHelper.getLanguage(this);
        for (int i = 0; i < LocaleHelper.ALL.length; i++) {
            if (LocaleHelper.ALL[i].equals(language)) return i;
        }
        return 0;
    }

    // ---------------- 第二步：OSC 图文教程 ----------------

    private void showGuide() {
        LinearLayout root = newRoot();

        TextView title = text(getString(R.string.guide_title), 22, R.color.text_primary);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView intro = text(getString(R.string.guide_intro), 14, R.color.text_secondary);
        intro.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams introParams = matchWrap();
        introParams.topMargin = dp(8);
        introParams.bottomMargin = dp(6);
        root.addView(intro, introParams);

        int row = languageRow();
        List<TextView> badges = new ArrayList<>(STEP_COUNT);
        for (int step = 1; step <= STEP_COUNT; step++) {
            TextView[] badge = new TextView[1];
            root.addView(stepCard(step, row, badge));
            badges.add(badge[0]);
        }

        TextView pcNote = text(getString(R.string.guide_pc_note), 12, R.color.text_secondary);
        pcNote.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.topMargin = dp(4);
        root.addView(pcNote, noteParams);

        final Button start = new Button(this);
        start.setText(R.string.guide_start);
        start.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        start.setTextColor(getColor(android.R.color.white));
        start.setAllCaps(false);
        start.setBackgroundTintList(null);
        start.setBackground(primaryButtonBackground());
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishGuide();
            }
        });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        startParams.topMargin = dp(20);
        root.addView(start, startParams);

        setContentView(scroll(root));
        ThemeManager.apply(this);

        // 主题会给所有 TextView 上色，这里把强调色块上的白字补回来，别让主题拉成正文色
        start.setTextColor(getColor(android.R.color.white));
        for (TextView badge : badges) {
            badge.setTextColor(getColor(android.R.color.white));
        }
    }

    /** 一步：序号徽标 + 截图 + 一句说明 */
    private View stepCard(int step, int row, TextView[] badgeOut) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        // ThemeManager 靠这个 tag 给卡片换底色
        card.setTag("card");

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(String.valueOf(step), 12, android.R.color.white);
        badge.setGravity(Gravity.CENTER);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setBackground(badgeBackground());
        header.addView(badge, new LinearLayout.LayoutParams(dp(22), dp(22)));
        badgeOut[0] = badge;

        TextView label = text(getString(R.string.guide_step_label, step, STEP_COUNT), 12,
                R.color.text_secondary);
        LinearLayout.LayoutParams labelParams = wrapWrap();
        labelParams.leftMargin = dp(8);
        header.addView(label, labelParams);

        card.addView(header, matchWrap());

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(false);
        image.setImageResource(imageFor(step, row));
        // 第 1 步是竖屏截图（整屏），给高一点；后面几步是横屏，300dp 足够看清按钮
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(step == 1 ? 380 : 300));
        imageParams.topMargin = dp(10);
        card.addView(image, imageParams);

        TextView caption = text(getString(STEP_TEXT[step - 1]), 14, R.color.text_primary);
        caption.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams captionParams = matchWrap();
        captionParams.topMargin = dp(10);
        card.addView(caption, captionParams);

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(12);
        card.setLayoutParams(cardParams);
        return card;
    }

    private int imageFor(int step, int row) {
        if (step == 1) return R.drawable.guide_open_menu;
        int[] images = STEP_IMAGES[row];
        int index = step - 2;
        if (index < 0) index = 0;
        if (index >= images.length) index = images.length - 1;
        return images[index];
    }

    /** 结束教程：首次引导时写标记再进主界面；从设置进来的直接退出 */
    private void finishGuide() {
        if (!guideOnly) {
            settings.setOnboarded();
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        if (!guideOnly) {
            // 首次引导里按返回也得放人进应用，否则会一直卡在引导
            finishGuide();
            return;
        }
        super.onBackPressed();
    }

    // ---------------- 小工具 ----------------

    private LinearLayout newRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        return root;
    }

    private ScrollView scroll(View content) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.bg));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private TextView text(String value, int sizeSp, int colorRes) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(getColor(colorRes));
        return view;
    }

    private GradientDrawable languageButtonBackground(String language) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(12));
        drawable.setColor(getColor(R.color.card_bg));
        int stroke = LocaleHelper.getLanguage(this).equals(language) ? dp(2) : dp(1);
        drawable.setStroke(stroke, getColor(R.color.accent));
        return drawable;
    }

    private GradientDrawable primaryButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(12));
        drawable.setColor(getColor(R.color.accent));
        return drawable;
    }

    private GradientDrawable badgeBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(getColor(R.color.accent));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
