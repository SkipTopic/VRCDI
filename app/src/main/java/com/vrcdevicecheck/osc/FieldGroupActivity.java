package com.vrcdevicecheck.osc;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.data.FieldGroup;
import com.vrcdevicecheck.osc.data.ModelDisplayMode;
import com.vrcdevicecheck.osc.data.ReportField;
import com.vrcdevicecheck.osc.device.VrSupport;
import com.vrcdevicecheck.osc.media.NowPlayingRepository;
import com.vrcdevicecheck.osc.service.ReporterService;
import com.vrcdevicecheck.osc.theme.ThemeManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 字段分组的二级页面：点主页的「手机与硬件 / 天气 / 音乐播放 / 其它」进来，
 * 在这里勾选本组的细项。勾选即时写入配置，返回主页后摘要会刷新。
 */
public class FieldGroupActivity extends BaseActivity {

    public static final String EXTRA_GROUP = "group";

    private AppSettings settings;
    private FieldGroup group;
    private LinearLayout container;
    private TextView weatherHint;
    private TextView musicHint;
    private Spinner spinnerModelMode;
    private final List<CheckBox> boxes = new ArrayList<>();
    private boolean weatherPromptShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        group = FieldGroup.fromKey(getIntent().getStringExtra(EXTRA_GROUP));
        if (group == null) {
            finish();
            return;
        }
        settings = new AppSettings(this);
        setTitle(group.labelRes);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        container.setPadding(padding, padding, padding, padding);
        scroll.addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        buildFields();
        if (group == FieldGroup.HARDWARE) buildModelModeRow();
        if (group == FieldGroup.WEATHER) buildWeatherHint();
        if (group == FieldGroup.MUSIC) buildMusicHint();
        buildGroupButtons();
        refreshHints();

        ThemeManager.apply(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHints();
    }

    private void buildFields() {
        for (final ReportField field : group.fields()) {
            CheckBox box = new CheckBox(this);
            box.setText(field.label(this));
            box.setTag(field);
            box.setTextColor(getColor(R.color.text_primary));
            box.setTextSize(15f);
            box.setChecked(settings.getEnabledFields().contains(field));
            if (VrSupport.isVrDevice()) {
                int minHeight = Math.round(getResources().getDisplayMetrics().density * 48);
                box.setMinHeight(minHeight);
                int vertical = dp(6);
                box.setPadding(box.getPaddingLeft(), vertical, box.getPaddingRight(), vertical);
            }
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (!buttonView.isPressed()) return;

                    Set<ReportField> current = EnumSet.noneOf(ReportField.class);
                    current.addAll(settings.getEnabledFields());
                    if (isChecked) current.add(field);
                    else current.remove(field);
                    settings.setEnabledFields(current);
                    ReporterService.wake();

                    if (isChecked && field.isMusic()
                            && !NowPlayingRepository.hasAccess(FieldGroupActivity.this)) {
                        promptMusicAccess();
                    }
                    if (isChecked && field.isWeather() && settings.getWeatherCity().isEmpty()) {
                        promptWeatherCity();
                    }
                    refreshHints();
                }
            });
            boxes.add(box);
            container.addView(box);
        }
    }

    private void buildModelModeRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(12), 0, dp(4));

        TextView label = new TextView(this);
        label.setText(R.string.label_model_mode);
        label.setTextSize(12f);
        label.setTextColor(getColor(R.color.text_secondary));
        wrap.addView(label);

        spinnerModelMode = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{ModelDisplayMode.REAL_MODEL.label(this),
                        ModelDisplayMode.PRODUCT_NAME.label(this)});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerModelMode.setAdapter(adapter);
        spinnerModelMode.setSelection(
                settings.getModelDisplayMode() == ModelDisplayMode.REAL_MODEL ? 0 : 1);
        spinnerModelMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.setModelDisplayMode(position == 0
                        ? ModelDisplayMode.REAL_MODEL
                        : ModelDisplayMode.PRODUCT_NAME);
                ReporterService.wake();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 无需处理
            }
        });
        wrap.addView(spinnerModelMode, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        container.addView(wrap);
    }

    private void buildWeatherHint() {
        weatherHint = new TextView(this);
        weatherHint.setTextSize(12f);
        weatherHint.setPadding(0, dp(8), 0, dp(4));
        weatherHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FieldGroupActivity.this, WeatherCityActivity.class));
            }
        });
        container.addView(weatherHint);
    }

    private void buildMusicHint() {
        musicHint = new TextView(this);
        musicHint.setTextSize(12f);
        musicHint.setPadding(0, dp(8), 0, dp(4));
        musicHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNotificationAccess();
            }
        });
        container.addView(musicHint);
    }

    private void buildGroupButtons() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(16), 0, 0);

        Button all = new Button(this);
        all.setText(R.string.action_select_all);
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<ReportField> current = EnumSet.noneOf(ReportField.class);
                current.addAll(settings.getEnabledFields());
                for (ReportField field : group.fields()) current.add(field);
                settings.setEnabledFields(current);
                setAllChecked(true);
                ReporterService.wake();
                refreshHints();
                if (group == FieldGroup.MUSIC
                        && !NowPlayingRepository.hasAccess(FieldGroupActivity.this)) {
                    promptMusicAccess();
                }
                if (group == FieldGroup.WEATHER && settings.getWeatherCity().isEmpty()) {
                    promptWeatherCity();
                }
            }
        });
        row.addView(all, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button none = new Button(this);
        none.setText(R.string.action_select_none);
        none.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Set<ReportField> current = EnumSet.noneOf(ReportField.class);
                current.addAll(settings.getEnabledFields());
                for (ReportField field : group.fields()) current.remove(field);
                settings.setEnabledFields(current);
                setAllChecked(false);
                ReporterService.wake();
                refreshHints();
            }
        });
        LinearLayout.LayoutParams noneParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        noneParams.setMarginStart(dp(8));
        row.addView(none, noneParams);

        container.addView(row);
    }

    private void setAllChecked(boolean checked) {
        for (CheckBox box : boxes) box.setChecked(checked);
    }

    private void refreshHints() {
        if (weatherHint != null) {
            String city = settings.getWeatherCityLabel();
            weatherHint.setText(city.isEmpty()
                    ? getString(R.string.weather_hint_no_city)
                    : getString(R.string.weather_hint_city, city));
            weatherHint.setTextColor(getColor(city.isEmpty() ? R.color.warn : R.color.text_secondary));
        }
        if (musicHint != null) {
            boolean granted = NowPlayingRepository.hasAccess(this);
            musicHint.setText(granted
                    ? R.string.music_access_granted_hint
                    : R.string.music_access_hint);
            musicHint.setTextColor(getColor(granted ? R.color.text_secondary : R.color.warn));
        }
    }

    private void promptWeatherCity() {
        if (weatherPromptShown || isFinishing()) return;
        weatherPromptShown = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.weather_prompt_title)
                .setMessage(R.string.weather_prompt_message)
                .setPositiveButton(R.string.go_choose, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(FieldGroupActivity.this, WeatherCityActivity.class));
                    }
                })
                .setNegativeButton(R.string.common_later, null)
                .show();
    }

    private void promptMusicAccess() {
        if (isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.music_access_dialog_title)
                .setMessage(R.string.music_access_dialog_message)
                .setPositiveButton(R.string.music_access_go, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openNotificationAccess();
                    }
                })
                .setNegativeButton(R.string.common_later, null)
                .show();
    }

    private void openNotificationAccess() {
        if (NowPlayingRepository.hasAccess(this)) {
            toast(getString(R.string.music_access_granted_hint));
            refreshHints();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (Throwable t) {
            toast(getString(R.string.music_access_failed));
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}