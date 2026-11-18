package com.vrcdevicecheck.osc.weather;

import java.util.HashMap;
import java.util.Map;

/**
 * 三套天气编码到中英文文案的映射：
 * - Open-Meteo 用 WMO Weather interpretation codes；
 * - wttr.in 用 WWO（World Weather Online）weatherCode；
 * - 和风天气用 icon 码（100 / 305 这类字符串）。
 *
 * 和风天气的中文文案直接由接口返回（text 字段），这里只维护英文，
 * 因为它只返回一种语言，不额外发第二次请求省额度。
 *
 * 只映射常见天气；未知编码回退到「未知 / Unknown」。
 */
public final class WeatherCodes {

    private static final Map<Integer, String> WMO_ZH = new HashMap<>();
    private static final Map<Integer, String> WMO_EN = new HashMap<>();
    private static final Map<Integer, String> WWO_ZH = new HashMap<>();
    private static final Map<Integer, String> WWO_EN = new HashMap<>();
    private static final Map<String, String> QW_EN = new HashMap<>();

    static {
        wmo(0, "晴", "Clear sky");
        wmo(1, "大部晴朗", "Mainly clear");
        wmo(2, "多云", "Partly cloudy");
        wmo(3, "阴", "Overcast");
        wmo(45, "雾", "Fog");
        wmo(48, "雾凇", "Depositing rime fog");
        wmo(51, "小毛毛雨", "Light drizzle");
        wmo(53, "毛毛雨", "Moderate drizzle");
        wmo(55, "大毛毛雨", "Dense drizzle");
        wmo(56, "冻毛毛雨", "Light freezing drizzle");
        wmo(57, "强冻毛毛雨", "Dense freezing drizzle");
        wmo(61, "小雨", "Slight rain");
        wmo(63, "中雨", "Moderate rain");
        wmo(65, "大雨", "Heavy rain");
        wmo(66, "冻雨", "Light freezing rain");
        wmo(67, "强冻雨", "Heavy freezing rain");
        wmo(71, "小雪", "Slight snow fall");
        wmo(73, "中雪", "Moderate snow fall");
        wmo(75, "大雪", "Heavy snow fall");
        wmo(77, "雪粒", "Snow grains");
        wmo(80, "小阵雨", "Slight rain showers");
        wmo(81, "中阵雨", "Moderate rain showers");
        wmo(82, "强阵雨", "Violent rain showers");
        wmo(85, "小阵雪", "Slight snow showers");
        wmo(86, "强阵雪", "Heavy snow showers");
        wmo(95, "雷阵雨", "Thunderstorm");
        wmo(96, "雷阵雨伴小冰雹", "Thunderstorm with slight hail");
        wmo(99, "强雷雨伴大冰雹", "Thunderstorm with heavy hail");

        wwo(113, "晴", "Sunny");
        wwo(116, "多云", "Partly cloudy");
        wwo(119, "阴", "Cloudy");
        wwo(122, "阴", "Overcast");
        wwo(143, "薄雾", "Mist");
        wwo(176, "局部有雨", "Patchy rain nearby");
        wwo(179, "局部有雪", "Patchy snow nearby");
        wwo(182, "局部雨夹雪", "Patchy sleet nearby");
        wwo(185, "局部冻毛毛雨", "Patchy freezing drizzle nearby");
        wwo(200, "雷阵雨", "Thundery outbreaks nearby");
        wwo(227, "风吹雪", "Blowing snow");
        wwo(230, "暴风雪", "Blizzard");
        wwo(248, "雾", "Fog");
        wwo(260, "冻雾", "Freezing fog");
        wwo(263, "小毛毛雨", "Patchy light drizzle");
        wwo(266, "毛毛雨", "Light drizzle");
        wwo(281, "冻毛毛雨", "Freezing drizzle");
        wwo(284, "强冻毛毛雨", "Heavy freezing drizzle");
        wwo(293, "小阵雨", "Patchy light rain");
        wwo(296, "小雨", "Light rain");
        wwo(299, "中阵雨", "Moderate rain at times");
        wwo(302, "中雨", "Moderate rain");
        wwo(305, "大阵雨", "Heavy rain at times");
        wwo(308, "大雨", "Heavy rain");
        wwo(311, "冻雨", "Light freezing rain");
        wwo(314, "强冻雨", "Moderate or heavy freezing rain");
        wwo(317, "雨夹雪", "Light sleet");
        wwo(320, "强雨夹雪", "Moderate or heavy sleet");
        wwo(323, "小阵雪", "Patchy light snow");
        wwo(326, "小雪", "Light snow");
        wwo(329, "中阵雪", "Patchy moderate snow");
        wwo(332, "中雪", "Moderate snow");
        wwo(335, "大阵雪", "Patchy heavy snow");
        wwo(338, "大雪", "Heavy snow");
        wwo(350, "冰粒", "Ice pellets");
        wwo(353, "小阵雨", "Light rain shower");
        wwo(356, "大阵雨", "Moderate or heavy rain shower");
        wwo(359, "暴雨", "Torrential rain shower");
        wwo(362, "小阵雨夹雪", "Light sleet showers");
        wwo(365, "强阵雨夹雪", "Moderate or heavy sleet showers");
        wwo(368, "小阵雪", "Light snow showers");
        wwo(371, "强阵雪", "Moderate or heavy snow showers");
        wwo(374, "小冰粒", "Light showers of ice pellets");
        wwo(377, "强冰粒", "Moderate or heavy showers of ice pellets");
        wwo(386, "雷阵雨", "Patchy light rain with thunder");
        wwo(389, "强雷雨", "Moderate or heavy rain with thunder");
        wwo(392, "雷雪", "Patchy light snow with thunder");
        wwo(395, "强雷雪", "Moderate or heavy snow with thunder");

        // 和风天气 icon 码（数值区间是有规律的：1xx 晴/云，3xx 雨，4xx 雪，5xx 雾霾）
        qw("100", "Sunny");
        qw("101", "Cloudy");
        qw("102", "Few clouds");
        qw("103", "Partly cloudy");
        qw("104", "Overcast");
        qw("150", "Clear");
        qw("151", "Cloudy");
        qw("152", "Few clouds");
        qw("153", "Partly cloudy");
        qw("300", "Shower rain");
        qw("301", "Heavy shower rain");
        qw("302", "Thundershower");
        qw("303", "Heavy thunderstorm");
        qw("304", "Thundershower with hail");
        qw("305", "Light rain");
        qw("306", "Moderate rain");
        qw("307", "Heavy rain");
        qw("308", "Extreme rain");
        qw("309", "Drizzle");
        qw("310", "Storm");
        qw("311", "Heavy storm");
        qw("312", "Severe storm");
        qw("313", "Freezing rain");
        qw("314", "Light to moderate rain");
        qw("315", "Moderate to heavy rain");
        qw("316", "Heavy rain to Storm");
        qw("317", "Storm to heavy storm");
        qw("318", "Heavy to severe storm");
        qw("399", "Rain");
        qw("400", "Light snow");
        qw("401", "Moderate snow");
        qw("402", "Heavy snow");
        qw("403", "Snowstorm");
        qw("404", "Sleet");
        qw("405", "Rain and snow");
        qw("406", "Shower rain and snow");
        qw("407", "Shower snow");
        qw("408", "Light to moderate snow");
        qw("409", "Moderate to heavy snow");
        qw("410", "Heavy snow to snowstorm");
        qw("456", "Shower rain and snow");
        qw("457", "Shower snow");
        qw("499", "Snow");
        qw("500", "Mist");
        qw("501", "Fog");
        qw("502", "Haze");
        qw("503", "Sand");
        qw("504", "Dust");
        qw("507", "Sandstorm");
        qw("508", "Severe sandstorm");
        qw("509", "Dense fog");
        qw("510", "Strong dense fog");
        qw("511", "Moderate haze");
        qw("512", "Heavy haze");
        qw("513", "Severe haze");
        qw("514", "Heavy fog");
        qw("515", "Extra strong dense fog");
        qw("900", "Hot");
        qw("901", "Cold");
        qw("999", "Unknown");
    }

    private WeatherCodes() {
    }

    private static void wmo(int code, String zh, String en) {
        WMO_ZH.put(code, zh);
        WMO_EN.put(code, en);
    }

    private static void wwo(int code, String zh, String en) {
        WWO_ZH.put(code, zh);
        WWO_EN.put(code, en);
    }

    private static void qw(String icon, String en) {
        QW_EN.put(icon, en);
    }

    public static String zhForWmo(int code) {
        String value = WMO_ZH.get(code);
        return value == null ? "未知" : value;
    }

    public static String enForWmo(int code) {
        String value = WMO_EN.get(code);
        return value == null ? "Unknown" : value;
    }

    public static String zhForWwo(int code) {
        String value = WWO_ZH.get(code);
        return value == null ? "未知" : value;
    }

    public static String enForWwo(int code, String fallback) {
        String value = WWO_EN.get(code);
        if (value != null) return value;
        return fallback == null || fallback.trim().isEmpty() ? "Unknown" : fallback.trim();
    }

    /** 和风天气的 icon 是字符串，例如 "100"、"305" */
    public static String enForQWeather(String icon) {
        if (icon == null) return "Unknown";
        String value = QW_EN.get(icon.trim());
        return value == null ? "Unknown" : value;
    }
}
