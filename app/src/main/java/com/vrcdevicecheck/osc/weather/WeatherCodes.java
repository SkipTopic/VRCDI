package com.vrcdevicecheck.osc.weather;

import java.util.HashMap;
import java.util.Map;

/**
 * 三套天气编码到四种语言文案的映射：
 * - Open-Meteo 用 WMO Weather interpretation codes；
 * - wttr.in 用 WWO（World Weather Online）weatherCode；
 * - 和风天气用 icon 码（100 / 305 这类字符串）。
 *
 * 每种编码存一个数组：{中文, English, 日本語, Русский}。
 * 中文和英文是原有文案，逐字保持不变；日文、俄文是后加的。
 *
 * 和风天气的中文文案仍以接口返回的 text 为准（这里只提供 en / ja / ru 兜底），
 * 因为它只请求一次，不为了省额度再发第二次。
 */
public final class WeatherCodes {

    /** 下标与语言顺序对应，改这里要同步 forLang() */
    public static final int ZH = 0;
    public static final int EN = 1;
    public static final int JA = 2;
    public static final int RU = 3;

    private static final Map<Integer, String[]> WMO = new HashMap<>();
    private static final Map<Integer, String[]> WWO = new HashMap<>();
    private static final Map<String, String[]> QW = new HashMap<>();

    static {
        wmo(0, "晴", "Clear sky", "快晴", "Ясно");
        wmo(1, "大部晴朗", "Mainly clear", "晴れ", "Преимущественно ясно");
        wmo(2, "多云", "Partly cloudy", "晴れ時々曇り", "Переменная облачность");
        wmo(3, "阴", "Overcast", "曇り", "Пасмурно");
        wmo(45, "雾", "Fog", "霧", "Туман");
        wmo(48, "雾凇", "Depositing rime fog", "着氷性の霧", "Изморозь");
        wmo(51, "小毛毛雨", "Light drizzle", "弱い霧雨", "Слабая морось");
        wmo(53, "毛毛雨", "Moderate drizzle", "霧雨", "Умеренная морось");
        wmo(55, "大毛毛雨", "Dense drizzle", "強い霧雨", "Сильная морось");
        wmo(56, "冻毛毛雨", "Light freezing drizzle", "弱い着氷性霧雨", "Слабая ледяная морось");
        wmo(57, "强冻毛毛雨", "Dense freezing drizzle", "強い着氷性霧雨", "Сильная ледяная морось");
        wmo(61, "小雨", "Slight rain", "小雨", "Небольшой дождь");
        wmo(63, "中雨", "Moderate rain", "雨", "Умеренный дождь");
        wmo(65, "大雨", "Heavy rain", "大雨", "Сильный дождь");
        wmo(66, "冻雨", "Light freezing rain", "弱い着氷性の雨", "Слабый ледяной дождь");
        wmo(67, "强冻雨", "Heavy freezing rain", "強い着氷性の雨", "Сильный ледяной дождь");
        wmo(71, "小雪", "Slight snow fall", "小雪", "Небольшой снег");
        wmo(73, "中雪", "Moderate snow fall", "雪", "Умеренный снег");
        wmo(75, "大雪", "Heavy snow fall", "大雪", "Сильный снег");
        wmo(77, "雪粒", "Snow grains", "霧雪", "Снежная крупа");
        wmo(80, "小阵雨", "Slight rain showers", "弱いにわか雨", "Небольшие ливни");
        wmo(81, "中阵雨", "Moderate rain showers", "にわか雨", "Умеренные ливни");
        wmo(82, "强阵雨", "Violent rain showers", "強いにわか雨", "Сильные ливни");
        wmo(85, "小阵雪", "Slight snow showers", "弱いにわか雪", "Небольшие снегопады");
        wmo(86, "强阵雪", "Heavy snow showers", "強いにわか雪", "Сильные снегопады");
        wmo(95, "雷阵雨", "Thunderstorm", "雷雨", "Гроза");
        wmo(96, "雷阵雨伴小冰雹", "Thunderstorm with slight hail", "雷雨と雹",
                "Гроза с небольшим градом");
        wmo(99, "强雷雨伴大冰雹", "Thunderstorm with heavy hail", "強い雷雨と大粒の雹",
                "Сильная гроза с крупным градом");

        wwo(113, "晴", "Sunny", "晴れ", "Солнечно");
        wwo(116, "多云", "Partly cloudy", "晴れ時々曇り", "Переменная облачность");
        wwo(119, "阴", "Cloudy", "曇り", "Облачно");
        wwo(122, "阴", "Overcast", "曇天", "Пасмурно");
        wwo(143, "薄雾", "Mist", "もや", "Дымка");
        wwo(176, "局部有雨", "Patchy rain nearby", "所により雨", "Местами дождь");
        wwo(179, "局部有雪", "Patchy snow nearby", "所により雪", "Местами снег");
        wwo(182, "局部雨夹雪", "Patchy sleet nearby", "所によりみぞれ", "Местами мокрый снег");
        wwo(185, "局部冻毛毛雨", "Patchy freezing drizzle nearby", "所により着氷性霧雨",
                "Местами ледяная морось");
        wwo(200, "雷阵雨", "Thundery outbreaks nearby", "所により雷雨", "Местами грозы");
        wwo(227, "风吹雪", "Blowing snow", "地吹雪", "Позёмок");
        wwo(230, "暴风雪", "Blizzard", "猛吹雪", "Метель");
        wwo(248, "雾", "Fog", "霧", "Туман");
        wwo(260, "冻雾", "Freezing fog", "着氷性の霧", "Ледяной туман");
        wwo(263, "小毛毛雨", "Patchy light drizzle", "所により弱い霧雨", "Местами слабая морось");
        wwo(266, "毛毛雨", "Light drizzle", "弱い霧雨", "Слабая морось");
        wwo(281, "冻毛毛雨", "Freezing drizzle", "着氷性霧雨", "Ледяная морось");
        wwo(284, "强冻毛毛雨", "Heavy freezing drizzle", "強い着氷性霧雨", "Сильная ледяная морось");
        wwo(293, "小阵雨", "Patchy light rain", "所により弱い雨", "Местами слабый дождь");
        wwo(296, "小雨", "Light rain", "小雨", "Небольшой дождь");
        wwo(299, "中阵雨", "Moderate rain at times", "時々雨", "Временами умеренный дождь");
        wwo(302, "中雨", "Moderate rain", "雨", "Умеренный дождь");
        wwo(305, "大阵雨", "Heavy rain at times", "時々強い雨", "Временами сильный дождь");
        wwo(308, "大雨", "Heavy rain", "大雨", "Сильный дождь");
        wwo(311, "冻雨", "Light freezing rain", "弱い着氷性の雨", "Слабый ледяной дождь");
        wwo(314, "强冻雨", "Moderate or heavy freezing rain", "着氷性の雨",
                "Умеренный или сильный ледяной дождь");
        wwo(317, "雨夹雪", "Light sleet", "弱いみぞれ", "Слабый мокрый снег");
        wwo(320, "强雨夹雪", "Moderate or heavy sleet", "みぞれ",
                "Умеренный или сильный мокрый снег");
        wwo(323, "小阵雪", "Patchy light snow", "所により弱い雪", "Местами слабый снег");
        wwo(326, "小雪", "Light snow", "小雪", "Небольшой снег");
        wwo(329, "中阵雪", "Patchy moderate snow", "所により雪", "Местами умеренный снег");
        wwo(332, "中雪", "Moderate snow", "雪", "Умеренный снег");
        wwo(335, "大阵雪", "Patchy heavy snow", "所により大雪", "Местами сильный снег");
        wwo(338, "大雪", "Heavy snow", "大雪", "Сильный снег");
        wwo(350, "冰粒", "Ice pellets", "氷粒", "Ледяные иглы");
        wwo(353, "小阵雨", "Light rain shower", "弱いにわか雨", "Небольшой ливень");
        wwo(356, "大阵雨", "Moderate or heavy rain shower", "にわか雨",
                "Умеренный или сильный ливень");
        wwo(359, "暴雨", "Torrential rain shower", "激しいにわか雨", "Сильнейший ливень");
        wwo(362, "小阵雨夹雪", "Light sleet showers", "弱いみぞれ", "Небольшой мокрый снег");
        wwo(365, "强阵雨夹雪", "Moderate or heavy sleet showers", "みぞれ",
                "Умеренный или сильный мокрый снег");
        wwo(368, "小阵雪", "Light snow showers", "弱いにわか雪", "Небольшой снегопад");
        wwo(371, "强阵雪", "Moderate or heavy snow showers", "にわか雪",
                "Умеренный или сильный снегопад");
        wwo(374, "小冰粒", "Light showers of ice pellets", "弱い氷粒",
                "Небольшой ледяной дождь");
        wwo(377, "强冰粒", "Moderate or heavy showers of ice pellets", "氷粒",
                "Умеренный или сильный ледяной дождь");
        wwo(386, "雷阵雨", "Patchy light rain with thunder", "雷を伴う雨",
                "Местами дождь с грозой");
        wwo(389, "强雷雨", "Moderate or heavy rain with thunder", "雷を伴う強い雨",
                "Умеренный или сильный дождь с грозой");
        wwo(392, "雷雪", "Patchy light snow with thunder", "雷を伴う雪",
                "Местами снег с грозой");
        wwo(395, "强雷雪", "Moderate or heavy snow with thunder", "雷を伴う大雪",
                "Умеренный или сильный снег с грозой");

        // 和风天气 icon 码（数值区间是有规律的：1xx 晴/云，3xx 雨，4xx 雪，5xx 雾霾）
        qw("100", "Sunny", "晴れ", "Солнечно");
        qw("101", "Cloudy", "曇り", "Облачно");
        qw("102", "Few clouds", "晴れ時々曇り", "Небольшая облачность");
        qw("103", "Partly cloudy", "晴れ時々曇り", "Переменная облачность");
        qw("104", "Overcast", "曇天", "Пасмурно");
        qw("150", "Clear", "晴れ", "Ясно");
        qw("151", "Cloudy", "曇り", "Облачно");
        qw("152", "Few clouds", "晴れ時々曇り", "Небольшая облачность");
        qw("153", "Partly cloudy", "晴れ時々曇り", "Переменная облачность");
        qw("300", "Shower rain", "にわか雨", "Ливень");
        qw("301", "Heavy shower rain", "強いにわか雨", "Сильный ливень");
        qw("302", "Thundershower", "雷雨", "Гроза");
        qw("303", "Heavy thunderstorm", "強い雷雨", "Сильная гроза");
        qw("304", "Thundershower with hail", "雷雨と雹", "Гроза с градом");
        qw("305", "Light rain", "小雨", "Небольшой дождь");
        qw("306", "Moderate rain", "雨", "Умеренный дождь");
        qw("307", "Heavy rain", "大雨", "Сильный дождь");
        qw("308", "Extreme rain", "激しい雨", "Чрезвычайно сильный дождь");
        qw("309", "Drizzle", "霧雨", "Морось");
        qw("310", "Storm", "暴風雨", "Шторм");
        qw("311", "Heavy storm", "強い暴風雨", "Сильный шторм");
        qw("312", "Severe storm", "激しい暴風雨", "Жестокий шторм");
        qw("313", "Freezing rain", "着氷性の雨", "Ледяной дождь");
        qw("314", "Light to moderate rain", "弱い雨から雨", "От слабого до умеренного дождя");
        qw("315", "Moderate to heavy rain", "雨から大雨", "От умеренного до сильного дождя");
        qw("316", "Heavy rain to Storm", "大雨から暴風雨", "От сильного дождя до шторма");
        qw("317", "Storm to heavy storm", "暴風雨から強い暴風雨", "От шторма до сильного шторма");
        qw("318", "Heavy to severe storm", "強い暴風雨から激しい暴風雨",
                "От сильного до жестокого шторма");
        qw("399", "Rain", "雨", "Дождь");
        qw("400", "Light snow", "小雪", "Небольшой снег");
        qw("401", "Moderate snow", "雪", "Умеренный снег");
        qw("402", "Heavy snow", "大雪", "Сильный снег");
        qw("403", "Snowstorm", "吹雪", "Метель");
        qw("404", "Sleet", "みぞれ", "Мокрый снег");
        qw("405", "Rain and snow", "雨と雪", "Дождь со снегом");
        qw("406", "Shower rain and snow", "にわか雨と雪", "Ливень со снегом");
        qw("407", "Shower snow", "にわか雪", "Снегопад");
        qw("408", "Light to moderate snow", "弱い雪から雪", "От небольшого до умеренного снега");
        qw("409", "Moderate to heavy snow", "雪から大雪", "От умеренного до сильного снега");
        qw("410", "Heavy snow to snowstorm", "大雪から吹雪", "От сильного снега до метели");
        qw("456", "Shower rain and snow", "にわか雨と雪", "Ливень со снегом");
        qw("457", "Shower snow", "にわか雪", "Снегопад");
        qw("499", "Snow", "雪", "Снег");
        qw("500", "Mist", "もや", "Дымка");
        qw("501", "Fog", "霧", "Туман");
        qw("502", "Haze", "霞", "Мгла");
        qw("503", "Sand", "砂じん", "Песчаная буря");
        qw("504", "Dust", "ちり", "Пыль");
        qw("507", "Sandstorm", "砂あらし", "Песчаная буря");
        qw("508", "Severe sandstorm", "強い砂あらし", "Сильная песчаная буря");
        qw("509", "Dense fog", "濃霧", "Плотный туман");
        qw("510", "Strong dense fog", "強い濃霧", "Сильный плотный туман");
        qw("511", "Moderate haze", "中程度の霞", "Умеренная мгла");
        qw("512", "Heavy haze", "強い霞", "Сильная мгла");
        qw("513", "Severe haze", "激しい霞", "Очень сильная мгла");
        qw("514", "Heavy fog", "強い霧", "Сильный туман");
        qw("515", "Extra strong dense fog", "非常に強い濃霧", "Очень сильный плотный туман");
        qw("900", "Hot", "高温", "Жара");
        qw("901", "Cold", "低温", "Холод");
        qw("999", "Unknown", "不明", "Неизвестно");
    }

    private WeatherCodes() {
    }

    private static void wmo(int code, String zh, String en, String ja, String ru) {
        WMO.put(code, new String[]{zh, en, ja, ru});
    }

    private static void wwo(int code, String zh, String en, String ja, String ru) {
        WWO.put(code, new String[]{zh, en, ja, ru});
    }

    private static void qw(String icon, String en, String ja, String ru) {
        // 和风天气的中文由接口返回，这里只存 en / ja / ru
        QW.put(icon, new String[]{en, ja, ru});
    }

    /** 语言字符串（zh/en/ja/ru）转成数组下标 */
    private static int forLang(String lang) {
        if ("en".equals(lang)) return EN;
        if ("ja".equals(lang)) return JA;
        if ("ru".equals(lang)) return RU;
        return ZH;
    }

    /** 未知天气的写法 */
    public static String unknown(String lang) {
        switch (forLang(lang)) {
            case EN:
                return "Unknown";
            case JA:
                return "不明";
            case RU:
                return "Неизвестно";
            default:
                return "未知";
        }
    }

    public static String forWmo(int code, String lang) {
        String[] text = WMO.get(code);
        return text == null ? unknown(lang) : text[forLang(lang)];
    }

    /**
     * @param fallbackEn wttr.in 返回的英文描述，编码没收录时用于英文界面
     */
    public static String forWwo(int code, String lang, String fallbackEn) {
        String[] text = WWO.get(code);
        if (text != null) return text[forLang(lang)];
        if (EN == forLang(lang) && fallbackEn != null && !fallbackEn.trim().isEmpty()) {
            return fallbackEn.trim();
        }
        return unknown(lang);
    }

    /** 和风天气的 icon 是字符串，例如 "100"、"305"；中文由接口 text 提供 */
    public static String forQWeather(String icon, String lang) {
        String[] text = icon == null ? null : QW.get(icon.trim());
        if (text == null) return unknown(lang);
        int index = forLang(lang);
        if (index == JA) return text[1];
        if (index == RU) return text[2];
        // EN 或者 ZH（ZH 时调用方会优先用接口返回的中文 text）
        return text[0];
    }
}
