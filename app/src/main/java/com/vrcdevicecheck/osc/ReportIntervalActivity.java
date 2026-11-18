package com.vrcdevicecheck.osc;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.service.ReporterService;
import com.vrcdevicecheck.osc.theme.ThemeManager;

/**
 * 上报时间设置。
 *
 * 原来间隔只能在首页的输入框里改，这里给一个更明确的入口：
 * - 预设 2 / 5 / 10 / 30 / 60 秒，也可以填任意 2–3600 秒；
 * - 「启动上报时立即发送一次」开关，控制点启动后要不要先补一条；
 * - 保存后若服务正在上报，立刻按新间隔重排，不用等旧周期走完。
 */
public class ReportIntervalActivity extends BaseActivity {

    private static final int[] PRESETS = {2, 5, 10, 30, 60};
    private static final int[] PRESET_LABELS = {
            R.string.interval_preset_2,
            R.string.interval_preset_5,
            R.string.interval_preset_10,
            R.string.interval_preset_30,
            R.string.interval_preset_60
    };

    private AppSettings settings;

    private RadioGroup group;
    private EditText customInput;
    private CheckBox sendOnStart;
    private TextView preview;

    private boolean updatingUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new AppSettings(this);
        setTitle(R.string.interval_title);
        buildUi();
        loadValues();
        ThemeManager.apply(this);
    }

    // ---------------- 界面 ----------------

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 将要生效的设置（随选择实时变化）
        LinearLayout previewCard = newCard();
        previewCard.addView(cardTitle(getString(R.string.interval_preview_card)));
        preview = body("");
        previewCard.addView(preview);
        root.addView(previewCard);

        // 间隔
        LinearLayout intervalCard = newCard();
        intervalCard.addView(cardTitle(getString(R.string.interval_card)));
        intervalCard.addView(hint(getString(R.string.interval_card_hint)));

        group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < PRESETS.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setTag(PRESETS[i]);
            button.setText(PRESET_LABELS[i]);
            button.setTextSize(14f);
            button.setTextColor(getColor(R.color.text_primary));
            button.setPadding(0, dp(6), 0, dp(6));
            group.addView(button, new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        RadioButton custom = new RadioButton(this);
        custom.setId(View.generateViewId());
        custom.setText(R.string.interval_custom);
        custom.setTextSize(14f);
        custom.setTextColor(getColor(R.color.text_primary));
        custom.setPadding(0, dp(6), 0, dp(6));
        group.addView(custom, new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        intervalCard.addView(group);

        customInput = new EditText(this);
        customInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        customInput.setHint(R.string.interval_custom_hint);
        customInput.setTextSize(14f);
        customInput.setTextColor(getColor(R.color.text_primary));
        customInput.setHintTextColor(getColor(R.color.text_secondary));
        customInput.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        customInput.setMaxLines(1);
        intervalCard.addView(customInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        intervalCard.addView(hint(getString(R.string.interval_range_hint)));
        root.addView(intervalCard);

        // 启动行为
        LinearLayout startCard = newCard();
        startCard.addView(cardTitle(getString(R.string.interval_start_card)));
        sendOnStart = new CheckBox(this);
        sendOnStart.setText(R.string.interval_send_on_start);
        sendOnStart.setTextSize(14f);
        sendOnStart.setTextColor(getColor(R.color.text_primary));
        startCard.addView(sendOnStart);
        startCard.addView(hint(getString(R.string.interval_send_on_start_hint)));
        root.addView(startCard);

        // 操作按钮
        Button save = new Button(this);
        save.setText(R.string.common_save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        root.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button reset = new Button(this);
        reset.setText(R.string.interval_reset);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settings.setIntervalSec(AppSettings.DEFAULT_INTERVAL);
                settings.setSendOnStart(true);
                ReporterService.onIntervalChanged();
                loadValues();
                toast(getString(R.string.interval_reset_done));
            }
        });
        root.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
    }

    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setTag("card");
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private TextView cardTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15f);
        view.setTextColor(getColor(R.color.text_primary));
        view.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        view.setLayoutParams(params);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14f);
        view.setTextColor(getColor(R.color.text_primary));
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12f);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(6);
        view.setLayoutParams(params);
        return view;
    }

    // ---------------- 状态 ----------------

    private void loadValues() {
        updatingUi = true;
        try {
            int current = settings.getIntervalSec();
            int presetIndex = -1;
            for (int i = 0; i < PRESETS.length; i++) {
                if (PRESETS[i] == current) {
                    presetIndex = i;
                    break;
                }
            }

            if (presetIndex >= 0) {
                ((RadioButton) group.getChildAt(presetIndex)).setChecked(true);
                customInput.setText(String.valueOf(current));
                customInput.setEnabled(false);
            } else {
                ((RadioButton) group.getChildAt(PRESETS.length)).setChecked(true);
                customInput.setText(String.valueOf(current));
                customInput.setEnabled(true);
            }
            sendOnStart.setChecked(settings.isSendOnStart());
        } finally {
            updatingUi = false;
        }

        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                if (updatingUi) return;
                View checked = radioGroup.findViewById(checkedId);
                boolean isCustom = checked != null && checked.getTag() == null;
                customInput.setEnabled(isCustom);
                if (isCustom) {
                    customInput.requestFocus();
                    customInput.setSelection(customInput.getText().length());
                } else if (checked != null) {
                    updatingUi = true;
                    customInput.setText(String.valueOf((Integer) checked.getTag()));
                    updatingUi = false;
                }
                updatePreview();
            }
        });

        customInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingUi || !customInput.hasFocus()) return;
                View custom = group.getChildAt(PRESETS.length);
                if (custom != null && group.getCheckedRadioButtonId() != custom.getId()) {
                    updatingUi = true;
                    group.check(custom.getId());
                    updatingUi = false;
                }
                updatePreview();
            }
        });

        sendOnStart.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (updatingUi) return;
                updatePreview();
            }
        });

        updatePreview();
    }

    /** 当前界面选择对应的秒数；非法返回 null */
    private Integer selectedIntervalOrNull() {
        int checkedId = group.getCheckedRadioButtonId();
        View checked = group.findViewById(checkedId);
        if (checked == null) return null;
        Object tag = checked.getTag();
        if (tag instanceof Integer) return (Integer) tag;

        try {
            int value = Integer.parseInt(customInput.getText().toString().trim());
            if (value < AppSettings.MIN_INTERVAL || value > AppSettings.MAX_INTERVAL) return null;
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private void updatePreview() {
        Integer value = selectedIntervalOrNull();
        if (value == null) {
            preview.setText(getString(R.string.interval_invalid_preview,
                    AppSettings.MIN_INTERVAL, AppSettings.MAX_INTERVAL));
            return;
        }
        String tail = sendOnStart.isChecked()
                ? getString(R.string.interval_tail_immediate)
                : getString(R.string.interval_tail_wait);
        preview.setText(getString(R.string.interval_preview_ok, value, tail));
    }

    private void save() {
        Integer value = selectedIntervalOrNull();
        if (value == null) {
            toast(getString(R.string.interval_invalid_toast,
                    AppSettings.MIN_INTERVAL, AppSettings.MAX_INTERVAL));
            customInput.requestFocus();
            return;
        }

        settings.setIntervalSec(value);
        settings.setSendOnStart(sendOnStart.isChecked());
        // 服务在跑就立即按新间隔重排；没跑则只保存，下次启动生效
        ReporterService.onIntervalChanged();

        String suffix = ReporterService.isRunning()
                ? getString(R.string.interval_saved_immediate) : "";
        toast(getString(R.string.interval_saved, value, suffix));
        finish();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
