package com.vrcdevicecheck.osc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.theme.ThemeManager;
import com.vrcdevicecheck.osc.weather.WeatherRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 全球城市搜索。
 *
 * 用的是 Open-Meteo 的地理编码，不限制国家，所以国内国外都能查：
 * 输入「东京」「Tokyo」「巴黎」都行，返回结果里带经纬度。
 * 选中后直接把经纬度存进设置，之后天气按坐标查，不再依赖城市名解析。
 */
public class CitySearchActivity extends BaseActivity {

    private EditText queryInput;
    private Button searchButton;
    private TextView statusView;
    private ListView list;
    private ArrayAdapter<String> adapter;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<WeatherRepository.Place> places = new ArrayList<>();
    private boolean searching;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.search_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        root.setPadding(padding, padding, padding, padding);

        queryInput = new EditText(this);
        queryInput.setSingleLine(true);
        queryInput.setHint(R.string.search_hint);
        queryInput.setTextSize(14f);
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    search();
                    return true;
                }
                return false;
            }
        });
        root.addView(queryInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchButton = new Button(this);
        searchButton.setText(R.string.search_button);
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        });
        root.addView(searchButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView tip = new TextView(this);
        tip.setTextSize(12f);
        tip.setTextColor(getColor(R.color.text_secondary));
        tip.setLineSpacing(dp(2), 1f);
        tip.setText(R.string.search_tip);
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipParams.topMargin = dp(6);
        tip.setLayoutParams(tipParams);
        root.addView(tip);

        statusView = new TextView(this);
        statusView.setTextSize(13f);
        statusView.setTextColor(getColor(R.color.text_secondary));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(10);
        statusView.setLayoutParams(statusParams);
        root.addView(statusView);

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                new ArrayList<String>());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= places.size()) return;
                selectPlace(places.get(position));
            }
        });

        setContentView(root);
        ThemeManager.apply(this);
    }

    private void search() {
        if (searching) return;
        final String keyword = queryInput.getText().toString().trim();
        if (keyword.isEmpty()) {
            toast(getString(R.string.search_need_keyword));
            return;
        }

        searching = true;
        searchButton.setEnabled(false);
        statusView.setText(R.string.search_searching);
        places.clear();
        adapter.clear();
        adapter.notifyDataSetChanged();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final WeatherRepository.SearchResult result =
                        WeatherRepository.searchPlaces(getApplicationContext(), keyword);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        searching = false;
                        searchButton.setEnabled(true);
                        showResult(result);
                    }
                });
            }
        }, "vrcdi-city-search").start();
    }

    private void showResult(WeatherRepository.SearchResult result) {
        places.clear();
        adapter.clear();

        if (!result.error.isEmpty() && result.places.isEmpty()) {
            statusView.setText(getString(R.string.search_failed, result.error));
            adapter.notifyDataSetChanged();
            return;
        }
        if (result.places.isEmpty()) {
            statusView.setText(R.string.search_empty);
            adapter.notifyDataSetChanged();
            return;
        }

        places.addAll(result.places);
        List<String> lines = new ArrayList<>();
        for (WeatherRepository.Place place : places) {
            lines.add(place.display(this));
        }
        adapter.addAll(lines);
        adapter.notifyDataSetChanged();
        statusView.setText("");
    }

    private void selectPlace(WeatherRepository.Place place) {
        WeatherRepository.setPlace(this,
                place.admin1, place.name, place.country, place.countryCode,
                place.latitude, place.longitude);
        toast(getString(R.string.weather_city_selected, place.display(this)));
        setResult(RESULT_OK);
        finish();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
