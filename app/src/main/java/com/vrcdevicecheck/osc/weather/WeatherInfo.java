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
 * conditionZh / conditionEn 两套文案都留着：发往 Chatbox 的文本用哪一套
 * 只由「仅发送 ASCII」决定，跟界面语言无关（见 ReportBuilder）。
 */
public final class WeatherInfo {

    public final String cityLabel;
    public final Float temperatureC;
    public final Integer humidityPct;
    public final String conditionZh;
    public final String conditionEn;
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
                       String conditionZh, String conditionEn, long fetchedAtMs, String provider) {
        this(cityLabel, temperatureC, humidityPct, conditionZh, conditionEn, fetchedAtMs, provider,
                null, null, null, null, null);
    }

    public WeatherInfo(String cityLabel, Float temperatureC, Integer humidityPct,
                       String conditionZh, String conditionEn, long fetchedAtMs, String provider,
                       Float pressureHpa, Integer windDirectionDeg, Float windSpeedMs,
                       Integer uvIndex, String sunset) {
        this.cityLabel = cityLabel;
        this.temperatureC = temperatureC;
        this.humidityPct = humidityPct;
        this.conditionZh = conditionZh == null ? "未知" : conditionZh;
        this.conditionEn = conditionEn == null ? "Unknown" : conditionEn;
        this.fetchedAtMs = fetchedAtMs;
        this.provider = provider == null ? "" : provider;
        this.pressureHpa = pressureHpa;
        this.windDirectionDeg = windDirectionDeg;
        this.windSpeedMs = windSpeedMs;
        this.uvIndex = uvIndex;
        this.sunset = sunset;
    }

    public String timeText() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(fetchedAtMs));
    }

    /** 设置页/Toast 用的一行摘要，天气文案跟着界面语言走 */
    public String summary(Context context) {
        boolean english = LocaleHelper.isEnglish(context);
        String condition = english ? conditionEn : conditionZh;
        StringBuilder sb = new StringBuilder();
        if (condition != null && !condition.isEmpty()) sb.append(condition);
        if (temperatureC != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format(Locale.US, english ? "%.1f C" : "%.1f℃", temperatureC));
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

    /** 数据源名字。和风天气在英文界面显示 QWeather，其余用服务自己的品牌名 */
    public String providerLabel(Context context) {
        if (LocaleHelper.isEnglish(context) && "和风天气".equals(provider)) {
            return LocaleHelper.str(context, R.string.wprovider_qweather_en);
        }
        return provider;
    }
}
