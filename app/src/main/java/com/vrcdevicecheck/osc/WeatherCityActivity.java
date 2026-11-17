package com.vrcdevicecheck.osc;

import android.app.Activity;
import android.app.ProgressDialog;
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
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.theme.ThemeManager;
import com.vrcdevicecheck.osc.weather.ChinaCities;
import com.vrcdevicecheck.osc.weather.WeatherInfo;
import com.vrcdevicecheck.osc.weather.WeatherRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 天气城市设置：省份 → 城市两级选择。
 *
 * 选完后立即尝试取一次天气（受 1 小时缓存限制，同城市重复选择会直接读缓存），
 * 让用户能马上看到结果；真正的定时刷新由上报服务负责。
 */
public class WeatherCityActivity extends Activity {

    private AppSettings settings;
    private List<ChinaCities.Province> provinces;
    private ListView list;
    private TextView header;
    private TextView hint;
    private Button clearButton;
    private int provinceIndex = -1;

    private final Handler main = new Handler(Looper.getMainLooper());

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

        hint = new TextView(this);
        hint.setTextSize(12f);
        hint.setTextColor(getColor(R.color.text_secondary));
        hint.setLineSpacing(dp(2), 1f);
        hint.setText("先选省份，再选具体城市。\n"
                + "天气数据来自 Open-Meteo，网络不通时自动切换 wttr.in；"
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

        clearButton = new Button(this);
        clearButton.setText("清除已选城市");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WeatherRepository.clearCity(WeatherCityActivity.this);
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
        refreshWeather();
    }

    /** 选完城市立刻取一次，让用户看到结果；服务后续会按 1 小时缓存复用 */
    private void refreshWeather() {
        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("正在获取天气…");
        dialog.setCancelable(true);
        dialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final WeatherInfo info = WeatherRepository.getForReport(getApplicationContext());
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (dialog.isShowing()) dialog.dismiss();
                        if (info != null) {
                            toast("当前天气：" + info.summary());
                        } else {
                            toast("城市已保存；天气获取失败，下次上报会自动重试");
                        }
                        showProvinces();
                    }
                });
            }
        }, "vrcdi-weather-select").start();
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
                    .append("（").append(cached.timeText()).append(" 更新）");
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
