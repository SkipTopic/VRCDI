package com.vrcdevicecheck.osc;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.theme.ThemeManager;
import com.vrcdevicecheck.osc.weather.ChinaCities;
import com.vrcdevicecheck.osc.weather.WeatherFetchListener;
import com.vrcdevicecheck.osc.weather.WeatherInfo;
import com.vrcdevicecheck.osc.weather.WeatherRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气城市设置：省份 → 城市两级选择，另外提供一个全球城市搜索入口。
 *
 * 选完城市立刻跑一次完整查询（无视缓存），过程中把每一步都打在下面的日志区：
 * 连哪个源、失败的具体原因、切到哪个备用源。取到后写进缓存，供上报服务复用。
 *
 * 国内外走同一套流程：都是先拿到经纬度，之后按坐标向三个源要天气。
 */
public class WeatherCityActivity extends BaseActivity {

    private static final int REQUEST_CODE_SEARCH = 71;

    private AppSettings settings;
    private List<ChinaCities.Province> provinces;
    private ListView list;
    private TextView header;
    private TextView hint;
    private TextView logView;
    private ScrollView logScroll;
    private Button clearButton;
    private Button fetchButton;
    private Button qweatherButton;
    private int provinceIndex = -1;
    private boolean fetching;

    private final Handler main = new Handler(Looper.getMainLooper());

    /** 查询过程在后台线程，回调切回主线程再动界面 */
    private final WeatherFetchListener listener = new WeatherFetchListener() {
        @Override
        public void onStep(final String line) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    if (isFinishing() || isDestroyed()) return;
                    appendLog(line);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.weather_title);
        settings = new AppSettings(this);
        provinces = ChinaCities.load(this);
        buildUi();
        showProvinces();
        ThemeManager.apply(this);
        if (provinces.isEmpty()) {
            header.setText(R.string.weather_load_failed);
            toast(getString(R.string.weather_load_failed_toast));
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        header = new TextView(this);
        header.setTextSize(14f);
        header.setTextColor(getColor(R.color.text_primary));
        header.setLineSpacing(dp(2), 1f);
        root.addView(header);

        logView = new TextView(this);
        logView.setTextSize(11f);
        logView.setTextColor(getColor(R.color.text_secondary));
        logView.setLineSpacing(dp(2), 1f);
        logView.setPadding(dp(8), dp(6), dp(8), dp(6));
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);

        logScroll = new ScrollView(this);
        logScroll.addView(logView);
        logScroll.setBackgroundColor(getColor(R.color.card_bg));
        logScroll.setVisibility(View.GONE);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        logParams.topMargin = dp(6);
        root.addView(logScroll, logParams);

        hint = new TextView(this);
        hint.setTextSize(12f);
        hint.setTextColor(getColor(R.color.text_secondary));
        hint.setLineSpacing(dp(2), 1f);
        hint.setText(R.string.weather_hint);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(6);
        hintParams.bottomMargin = dp(6);
        hint.setLayoutParams(hintParams);
        root.addView(hint);

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        fetchButton = new Button(this);
        fetchButton.setText(R.string.weather_fetch_now);
        fetchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (settings.getWeatherCity().isEmpty()) {
                    toast(getString(R.string.weather_pick_city_first));
                    return;
                }
                runFetch(false);
            }
        });
        row.addView(fetchButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        qweatherButton = new Button(this);
        qweatherButton.setText(R.string.weather_qweather);
        qweatherButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(WeatherCityActivity.this,
                        QWeatherSettingsActivity.class));
            }
        });
        row.addView(qweatherButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(row);

        clearButton = new Button(this);
        clearButton.setText(R.string.weather_clear_city);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WeatherRepository.clearCity(WeatherCityActivity.this);
                logView.setText("");
                logScroll.setVisibility(View.GONE);
                toast(getString(R.string.weather_cleared));
                showProvinces();
            }
        });
        root.addView(clearButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void showProvinces() {
        provinceIndex = -1;
        setTitle(R.string.weather_title);
        updateHeader();

        // 第 0 项永远是「搜索城市」，国外城市从这里进；下面才是国内省市。
        // 省市数据读取失败时也保留这一项，至少还能靠搜索选城市。
        final List<String> names = new ArrayList<>();
        names.add(getString(R.string.weather_search_entry));
        for (ChinaCities.Province province : provinces) names.add(province.name);

        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    startActivityForResult(
                            new Intent(WeatherCityActivity.this, CitySearchActivity.class),
                            REQUEST_CODE_SEARCH);
                    return;
                }
                int index = position - 1;
                if (index < 0 || index >= provinces.size()) return;
                ChinaCities.Province province = provinces.get(index);
                if (province.cities.size() == 1) {
                    selectCity(province, province.cities.get(0));
                } else {
                    showCities(index);
                }
            }
        });
    }

    private void showCities(int index) {
        provinceIndex = index;
        ChinaCities.Province province = provinces.get(index);
        setTitle(getString(R.string.weather_pick_province_title, province.name));
        header.setText(getString(R.string.weather_province_header, province.name));

        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                province.cities));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ChinaCities.Province selected = provinces.get(provinceIndex);
                selectCity(selected, selected.cities.get(position));
            }
        });
    }

    private void selectCity(ChinaCities.Province province, String city) {
        WeatherRepository.setCity(this, province.name, city);
        toast(getString(R.string.weather_city_selected, settings.getWeatherCityLabel()));
        showProvinces();
        runFetch(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_SEARCH) return;
        if (resultCode != RESULT_OK) return;
        // 搜索页已经把城市和坐标写进设置，这里只负责回到列表并立刻取一次天气
        showProvinces();
        runFetch(false);
    }

    /**
     * 跑一次完整查询。qweatherOnly=true 时只测和风天气这一条。
     */
    private void runFetch(final boolean qweatherOnly) {
        if (fetching) return;
        fetching = true;
        fetchButton.setEnabled(false);
        qweatherButton.setEnabled(false);

        logView.setText("");
        logScroll.setVisibility(View.VISIBLE);
        appendLog("——— " + getString(qweatherOnly
                ? R.string.weather_log_test : R.string.weather_log_start) + " ———");

        new Thread(new Runnable() {
            @Override
            public void run() {
                final WeatherRepository.Result result = qweatherOnly
                        ? WeatherRepository.testQWeather(getApplicationContext(), listener)
                        : WeatherRepository.refresh(getApplicationContext(), listener);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        fetching = false;
                        fetchButton.setEnabled(true);
                        qweatherButton.setEnabled(true);
                        appendLog("——— " + result.summaryLine(WeatherCityActivity.this) + " ———");
                        updateHeader();
                    }
                });
            }
        }, "vrcdi-weather-select").start();
    }

    private void appendLog(String line) {
        logScroll.setVisibility(View.VISIBLE);
        logView.append(line + "\n");
        logScroll.post(new Runnable() {
            @Override
            public void run() {
                logScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void updateHeader() {
        String label = settings.getWeatherCityLabel();
        if (label.isEmpty()) {
            header.setText(R.string.weather_no_city);
            clearButton.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder(getString(R.string.weather_current_city, label));
        WeatherInfo cached = WeatherRepository.getCached(this);
        if (cached != null) {
            sb.append('\n').append(getString(R.string.weather_current_value,
                    cached.summary(this), cached.timeText(), cached.providerLabel(this)));
        } else {
            sb.append('\n').append(getString(R.string.weather_never_fetched));
        }
        String lastFail = WeatherRepository.getLastFailText(this);
        if (!lastFail.isEmpty()) {
            sb.append('\n').append(getString(R.string.weather_last_fail, lastFail));
        }
        header.setText(sb);
        clearButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (provinceIndex >= 0) {
            showProvinces();
            return;
        }
        super.onBackPressed();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
