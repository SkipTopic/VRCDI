package com.vrcdevicecheck.osc;

import android.app.Activity;
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
 * 天气城市设置：省份 → 城市两级选择。
 *
 * 选完城市立刻跑一次完整查询（无视缓存），过程中把每一步都打在下面的日志区：
 * 连哪个源、失败的具体原因、切到哪个备用源。取到后写进缓存，供上报服务复用。
 */
public class WeatherCityActivity extends Activity {

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
        setTitle("天气城市设置");
        settings = new AppSettings(this);
        provinces = ChinaCities.load(this);
        buildUi();
        showProvinces();
        ThemeManager.apply(this);
        if (provinces.isEmpty()) {
            header.setText("城市数据加载失败");
            toast("城市数据加载失败，请重新安装应用");
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
        hint.setText("先选省份，再选具体城市。\n"
                + "数据源按顺序兜底：Open-Meteo → wttr.in → 和风天气。\n"
                + "前两个都在境外，国内直连经常超时或被 DNS 污染——如果两个都失败而你没开代理，"
                + "那基本就是网络环境的问题，不是应用的问题。和风天气在国内，最稳，但要自己申请 Key。\n"
                + "同一城市每小时最多请求一次。");
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
        fetchButton.setText("立即获取");
        fetchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (settings.getWeatherCity().isEmpty()) {
                    toast("先选一个城市");
                    return;
                }
                runFetch(false);
            }
        });
        row.addView(fetchButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        qweatherButton = new Button(this);
        qweatherButton.setText("和风天气兜底");
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
        clearButton.setText("清除已选城市");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WeatherRepository.clearCity(WeatherCityActivity.this);
                logView.setText("");
                logScroll.setVisibility(View.GONE);
                toast("已清除天气城市");
                showProvinces();
            }
        });
        root.addView(clearButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void showProvinces() {
        provinceIndex = -1;
        setTitle("天气城市设置");
        updateHeader();
        if (provinces.isEmpty()) {
            list.setAdapter(null);
            clearButton.setVisibility(View.GONE);
            return;
        }

        List<String> names = new ArrayList<>();
        for (ChinaCities.Province province : provinces) names.add(province.name);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ChinaCities.Province province = provinces.get(position);
                if (province.cities.size() == 1) {
                    selectCity(province, province.cities.get(0));
                } else {
                    showCities(position);
                }
            }
        });
    }

    private void showCities(int index) {
        provinceIndex = index;
        ChinaCities.Province province = provinces.get(index);
        setTitle("选择城市 · " + province.name);
        header.setText("省份：" + province.name + "\n请选择具体城市");

        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                province.cities));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectCity(province, province.cities.get(position));
            }
        });
    }

    private void selectCity(ChinaCities.Province province, String city) {
        WeatherRepository.setCity(this, province.name, city);
        toast("已选择：" + province.name + " " + city);
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
        appendLog("——— " + (qweatherOnly ? "测试和风天气" : "开始获取天气") + " ———");

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
                        appendLog("——— " + result.summaryLine() + " ———");
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
            header.setText("当前未选择城市");
            clearButton.setVisibility(View.GONE);
            return;
        }

        StringBuilder sb = new StringBuilder("当前城市：").append(label);
        WeatherInfo cached = WeatherRepository.getCached(this);
        if (cached != null) {
            sb.append("\n当前天气：").append(cached.summary())
                    .append("（").append(cached.timeText()).append(" 更新 · ")
                    .append(cached.provider).append("）");
        } else {
            sb.append("\n当前天气：还没有成功获取过");
        }
        String lastFail = WeatherRepository.getLastFailText(this);
        if (!lastFail.isEmpty()) {
            sb.append("\n上次失败：").append(lastFail);
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
