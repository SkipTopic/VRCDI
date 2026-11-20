package com.vrcdevicecheck.osc.weather;

import android.content.Context;

import com.vrcdevicecheck.osc.R;
import com.vrcdevicecheck.osc.util.LocaleHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 一次天气查询的结果。字段可为 null，表示该 provider 没返回这项数据。
 *
 * 天气描述存了五种语言：发往 Chatbox 的文本按界面语言取一条（见 ReportBuilder），
 * 中文和英文是原有文案，日文、俄文、韩文是后加的。
 */
public final class WeatherInfo {

    public final String cityLabel;
    public final Float temperatureC;
    public final Integer humidityPct;
    public final String conditionZh;
    public final String conditionEn;
    public final String conditionJa;
    public final String conditionRu;
    public final String conditionKo;
    public final long fetchedAtMs;
    /** 数据来源：Open-Meteo / wttr.in / 和风天气 */
    public final String provider;

    public final Float pressureHpa;
    /** 风向角度：0=北，90=东，180=南，270=西 */
    public final Integer windDirectionDeg;
    public final Float windSpeedMs;
    public final Integer uvIndex;
    /** 当地日落时间，HH:mm */
    public final String sunset;

    public WeatherInfo(String cityLabel, Float temperatureC, Integer humidityPct,
                       String conditionZh, String conditionEn, String conditionJa, String conditionRu,
                       String conditionKo, long fetchedAtMs, String provider,
                       Float pressureHpa, Integer windDirectionDeg, Float windSpeedMs,
                       Integer uvIndex, String sunset) {
        this.cityLabel = cityLabel;
        this.temperatureC = temperatureC;
        this.humidityPct = humidityPct;
        this.conditionZh = conditionZh == null ? "未知" : conditionZh;
        this.conditionEn = conditionEn == null ? "Unknown" : conditionEn;
        this.conditionJa = conditionJa == null ? "" : conditionJa;
        this.conditionRu = conditionRu == null ? "" : conditionRu;
        this.conditionKo = conditionKo == null ? "" : conditionKo;
        this.fetchedAtMs = fetchedAtMs;
        this.provider = provider == null ? "" : provider;
        this.pressureHpa = pressureHpa;
        this.windDirectionDeg = windDirectionDeg;
        this.windSpeedMs = windSpeedMs;
        this.uvIndex = uvIndex;
        this.sunset = sunset;
    }

    /** 按语言取天气描述：zh / en / ja / ru / ko，缺日俄韩时退回英文 */
    public String condition(String lang) {
        if ("en".equals(lang)) return conditionEn;
        if ("ja".equals(lang)) return conditionJa.isEmpty() ? conditionEn : conditionJa;
        if ("ru".equals(lang)) return conditionRu.isEmpty() ? conditionEn : conditionRu;
        if ("ko".equals(lang)) return conditionKo.isEmpty() ? conditionEn : conditionKo;
        return conditionZh;
    }

    public String timeText() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(fetchedAtMs));
    }

    /** 设置页/Toast 用的一行摘要，天气文案跟着界面语言走 */
    public String summary(Context context) {
        String lang = LocaleHelper.getLanguage(context);
        String condition = condition(lang);
        StringBuilder sb = new StringBuilder();
        if (condition != null && !condition.isEmpty()) sb.append(condition);
        if (temperatureC != null) {
            if (sb.length() > 0) sb.append(' ');
            if ("ja".equals(lang) || "zh".equals(lang) || "ko".equals(lang)) {
                sb.append(String.format(Locale.US, "%.1f℃", temperatureC));
            } else {
                sb.append(String.format(Locale.US, "%.1f°C", temperatureC));
            }
        }
        if (humidityPct != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(LocaleHelper.str(context, R.string.wsummary_humidity, humidityPct));
        }
        if (sb.length() == 0) {
            sb.append(LocaleHelper.str(context, R.string.not_available));
        }
        return sb.toString();
    }

    /** 数据源名字。和风天气在非中文界面显示 QWeather，其余用服务自己的品牌名 */
    public String providerLabel(Context context) {
        if (!LocaleHelper.isChinese(context) && "和风天气".equals(provider)) {
            return LocaleHelper.str(context, R.string.wprovider_qweather_en);
        }
        return provider;
    }
}
