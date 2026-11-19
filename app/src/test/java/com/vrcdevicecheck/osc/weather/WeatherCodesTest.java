package com.vrcdevicecheck.osc.weather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 天气编码 → 四语文案。纯查表，不依赖 Android 运行时。
 */
public class WeatherCodesTest {

    @Test
    public void mapsWmoCodes() {
        assertEquals("多云", WeatherCodes.forWmo(2, "zh"));
        assertEquals("Partly cloudy", WeatherCodes.forWmo(2, "en"));
        assertEquals("晴れ時々曇り", WeatherCodes.forWmo(2, "ja"));
        assertEquals("Переменная облачность", WeatherCodes.forWmo(2, "ru"));
        assertEquals("雷阵雨", WeatherCodes.forWmo(95, "zh"));
        assertEquals("Гроза", WeatherCodes.forWmo(95, "ru"));
    }

    @Test
    public void mapsWwoCodes() {
        assertEquals("小雨", WeatherCodes.forWwo(296, "zh", ""));
        assertEquals("Light rain", WeatherCodes.forWwo(296, "en", ""));
        assertEquals("小雨", WeatherCodes.forWwo(296, "ja", ""));
        assertEquals("局部有雨", WeatherCodes.forWwo(176, "zh", ""));
    }

    @Test
    public void unknownCodesFallBackSafely() {
        assertEquals("未知", WeatherCodes.forWmo(999, "zh"));
        assertEquals("Unknown", WeatherCodes.forWmo(999, "en"));
        assertEquals("不明", WeatherCodes.forWmo(999, "ja"));
        assertEquals("Неизвестно", WeatherCodes.forWmo(999, "ru"));

        // 编码没收录时：英文界面用接口给的描述兜底，其它语言给出「未知」
        assertEquals("Patchy rain nearby", WeatherCodes.forWwo(999, "en", "Patchy rain nearby"));
        assertEquals("不明", WeatherCodes.forWwo(999, "ja", "Patchy rain nearby"));
    }

    @Test
    public void mapsQWeatherIconCodes() {
        assertEquals("Sunny", WeatherCodes.forQWeather("100", "en"));
        assertEquals("晴れ", WeatherCodes.forQWeather("100", "ja"));
        assertEquals("Солнечно", WeatherCodes.forQWeather("100", "ru"));
        assertEquals("Light rain", WeatherCodes.forQWeather("305", "en"));
        assertEquals("Heavy snow", WeatherCodes.forQWeather("402", "en"));
        // 和风的中文来自接口 text，查表只负责 en / ja / ru
        assertEquals("Unknown", WeatherCodes.forQWeather("12345", "en"));
        assertEquals("Unknown", WeatherCodes.forQWeather(null, "en"));
    }
}
