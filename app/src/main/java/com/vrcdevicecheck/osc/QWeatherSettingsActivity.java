package com.vrcdevicecheck.osc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.theme.ThemeManager;
import com.vrcdevicecheck.osc.weather.WeatherFetchListener;
import com.vrcdevicecheck.osc.weather.WeatherRepository;

/**
 * 和风天气兜底设置。
 *
 * 和风天气 2026 年起逐步停用公共地址（devapi.qweather.com），
 * 每个账号在控制台有独立的 API Host，所以这里要填两样东西：
 * API Host（形如 h2a9cf3mhs.xy.qweatherapi.com）和 API KEY。
 */
public class QWeatherSettingsActivity extends Activity {

    private AppSettings settings;
    private TextView intro;
    private EditText hostInput;
    private EditText keyInput;
    private TextView logView;
    private ScrollView logScroll;
    private Button testButton;
    private boolean testing;

    private final Handler main = new Handler(Looper.getMainLooper());

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
        setTitle("和风天气兜底");
        settings = new AppSettings(this);
        buildUi();
        loadValues();
        ThemeManager.apply(this);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));

        intro = new TextView(this);
        intro.setTextSize(12f);
        intro.setTextColor(getColor(R.color.text_secondary));
        intro.setLineSpacing(dp(2), 1f);
        intro.setText("和风天气是国内的天气服务，服务器在境内，不需要翻墙，作为最后一个兜底源。\n\n"
                + "申请步骤：\n"
                + "1. 打开 console.qweather.com 注册并完成实名认证\n"
                + "2. 「项目管理」→ 创建项目\n"
                + "3. 项目里「+ 添加凭据」→ 认证方式选 API KEY\n"
                + "4. 回到「设置」页，复制你的 API Host（形如 xxxx.qweatherapi.com）\n"
                + "5. 把 Host 和 Key 填到下面，点「测试连接」\n\n"
                + "免费订阅每天 1000 次，本应用同一城市每小时最多问一次，用不完。");
        root.addView(intro);

        TextView hostLabel = new TextView(this);
        hostLabel.setTextSize(12f);
        hostLabel.setTextColor(getColor(R.color.text_primary));
        hostLabel.setText("API Host");
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(14);
        root.addView(hostLabel, labelParams);

        hostInput = new EditText(this);
        hostInput.setSingleLine(true);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT);
        hostInput.setTextSize(13f);
        hostInput.setHint("xxxx.qweatherapi.com");
        root.addView(hostInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView keyLabel = new TextView(this);
        keyLabel.setTextSize(12f);
        keyLabel.setTextColor(getColor(R.color.text_primary));
        keyLabel.setText("API KEY");
        root.addView(keyLabel, labelParams);

        keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT);
        keyInput.setTextSize(13f);
        keyInput.setHint("控制台里那一串 32 位十六进制");
        root.addView(keyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);

        Button saveButton = new Button(this);
        saveButton.setText("保存");
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                toast("已保存");
            }
        });
        row.addView(saveButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        testButton = new Button(this);
        testButton.setText("测试连接");
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                runTest();
            }
        });
        row.addView(testButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row, rowParams);

        logView = new TextView(this);
        logView.setTextSize(11f);
        logView.setTextColor(getColor(R.color.text_secondary));
        logView.setLineSpacing(dp(2), 1f);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setPadding(dp(8), dp(6), dp(8), dp(6));

        logScroll = new ScrollView(this);
        logScroll.addView(logView);
        logScroll.setBackgroundColor(getColor(R.color.card_bg));
        logScroll.setVisibility(View.GONE);
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        logParams.topMargin = dp(10);
        root.addView(logScroll, logParams);

        setContentView(root);
    }

    private void loadValues() {
        // Host 不是机密，可以回显；Key 只回显用户自己填过的那个，内置的不显示
        String host = settings.getQWeatherHost();
        if (!AppSettings.QWEATHER_LEGACY_HOST.equals(host)) hostInput.setText(host);
        keyInput.setText(settings.getUserQWeatherKey());

        if (AppSettings.hasBuiltInQWeather()) {
            intro.append("\n\n这个安装包已经内置了一套兜底凭据，不用填也能用；"
                    + "下面的输入框只用于覆盖它（留空即用内置）。");
        }
    }

    private void save() {
        settings.setQWeatherHost(hostInput.getText().toString());
        settings.setQWeatherKey(keyInput.getText().toString());
    }

    private void runTest() {
        if (testing) return;
        if (settings.getWeatherCity().isEmpty()) {
            toast("先回上一页选一个城市，才能测");
            return;
        }
        if (!settings.isQWeatherConfigured()) {
            toast("Key 是空的，测不了");
            return;
        }
        testing = true;
        testButton.setEnabled(false);
        logView.setText("");
        logScroll.setVisibility(View.VISIBLE);
        appendLog("——— 测试和风天气 ———");
        appendLog("· API Host：" + settings.getQWeatherHost());

        new Thread(new Runnable() {
            @Override
            public void run() {
                final WeatherRepository.Result result =
                        WeatherRepository.testQWeather(getApplicationContext(), listener);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        testing = false;
                        testButton.setEnabled(true);
                        appendLog("——— " + result.summaryLine() + " ———");
                    }
                });
            }
        }, "vrcdi-qweather-test").start();
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

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
