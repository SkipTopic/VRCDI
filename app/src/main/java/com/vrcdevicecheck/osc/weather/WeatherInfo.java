package com.vrcdevicecheck.osc.weather;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 一次天气查询的结果。字段可为 null，表示该 provider 没返回这项数据。
 */
public final class WeatherInfo {

    public final String cityLabel;
    public final Float temperatureC;
    public final Integer humidityPct;
    public final String conditionZh;
    public final String conditionEn;
    public final long fetchedAtMs;
    /** 数据来源：Open-Meteo / wttr.in */
    public final String provider;

    public WeatherInfo(String cityLabel, Float temperatureC, Integer humidityPct,
                       String conditionZh, String conditionEn, long fetchedAtMs, String provider) {
        this.cityLabel = cityLabel;
        this.temperatureC = temperatureC;
        this.humidityPct = humidityPct;
        this.conditionZh = conditionZh == null ? "未知" : conditionZh;
        this.conditionEn = conditionEn == null ? "Unknown" : conditionEn;
        this.fetchedAtMs = fetchedAtMs;
        this.provider = provider == null ? "" : provider;
    }

    public String timeText() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(fetchedAtMs));
    }

    /** 设置页/Toast 用的一行摘要 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (conditionZh != null && !conditionZh.isEmpty()) sb.append(conditionZh);
        if (temperatureC != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format(Locale.US, "%.1f℃", temperatureC));
        }
        if (humidityPct != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("湿度 ").append(humidityPct).append('%');
        }
        if (sb.length() == 0) sb.append("不可用");
        return sb.toString();
    }
}
