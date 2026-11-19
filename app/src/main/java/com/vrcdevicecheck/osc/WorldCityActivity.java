package com.vrcdevicecheck.osc;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.theme.ThemeManager;
import com.vrcdevicecheck.osc.weather.WeatherRepository;
import com.vrcdevicecheck.osc.weather.WorldCities;

import java.util.ArrayList;
import java.util.List;

/**
 * 「选择国外」：国家 → 城市两级浏览。
 *
 * 数据是内置的（每个国家按人口取前 15 个城市），不联网也能翻；
 * 选中后直接把该城市的经纬度写进设置，之后按坐标查天气。
 */
public class WorldCityActivity extends BaseActivity {

    private List<WorldCities.Country> countries;
    private WorldCities.Country current;
    private ListView list;
    private TextView header;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.world_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        root.setPadding(padding, padding, padding, padding);

        header = new TextView(this);
        header.setTextSize(12f);
        header.setTextColor(getColor(R.color.text_secondary));
        header.setLineSpacing(dp(2), 1f);
        header.setText(R.string.world_hint);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerParams.bottomMargin = dp(6);
        header.setLayoutParams(headerParams);
        root.addView(header);

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        ThemeManager.apply(this);

        countries = WorldCities.load(this);
        if (countries.isEmpty()) {
            header.setText(R.string.world_empty);
            return;
        }
        showCountries();
    }

    private void showCountries() {
        current = null;
        setTitle(R.string.world_title);
        header.setText(R.string.world_hint);

        List<String> names = new ArrayList<>();
        for (WorldCities.Country country : countries) names.add(country.displayName(this));
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= countries.size()) return;
                showCities(countries.get(position));
            }
        });
    }

    private void showCities(WorldCities.Country country) {
        current = country;
        setTitle(getString(R.string.world_city_title, country.displayName(this)));
        header.setText(getString(R.string.world_city_count, country.cities.size()));

        List<String> names = new ArrayList<>();
        for (WorldCities.City city : country.cities) names.add(city.displayName(this));
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (current == null || position < 0 || position >= current.cities.size()) return;
                select(current, current.cities.get(position));
            }
        });
    }

    private void select(WorldCities.Country country, WorldCities.City city) {
        String cityName = city.displayName(this);
        String countryName = country.displayName(this);
        // province 留空：国外城市没有「省」这一级，标签会显示成「东京 · 日本」
        WeatherRepository.setPlace(this, "", cityName, countryName, country.code,
                city.latitude, city.longitude);
        Toast.makeText(this, getString(R.string.weather_city_selected, cityName + " · " + countryName),
                Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (current != null) {
            showCountries();
            return;
        }
        super.onBackPressed();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
