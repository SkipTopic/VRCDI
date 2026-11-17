package com.vrcdevicecheck.osc.weather;

import java.util.HashMap;
import java.util.Map;

/**
 * 两套天气编码到中英文文案的映射：
 * - Open-Meteo 用 WMO Weather interpretation codes；
 * - wttr.in 用 WWO（World Weather Online）weatherCode。
 *
 * 只映射常见天气；未知编码回退到「未知 / Unknown」。
 */
public final class WeatherCodes {

    private static final Map<Integer, String> WMO_ZH = new HashMap<>();
    private static final Map<Integer, String> WMO_EN = new HashMap<>();
    private static final Map<Integer, String> WWO_ZH = new HashMap<>();
    private static final Map<Integer, String> WWO_EN = new HashMap<>();

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
}
