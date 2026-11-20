package com.vrcdevicecheck.osc.weather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * 天气编码 → 五语文案。纯查表，不依赖 Android 运行时。
 */
public class WeatherCodesTest {

    @Test
    public void mapsWmoCodes() {
        assertEquals("多云", WeatherCodes.forWmo(2, "zh"));
        assertEquals("Partly cloudy", WeatherCodes.forWmo(2, "en"));
        assertEquals("晴れ時々曇り", WeatherCodes.forWmo(2, "ja"));
        assertEquals("Переменная облачность", WeatherCodes.forWmo(2, "ru"));
        assertEquals("구름 조금", WeatherCodes.forWmo(2, "ko"));
        assertEquals("雷阵雨", WeatherCodes.forWmo(95, "zh"));
        assertEquals("Гроза", WeatherCodes.forWmo(95, "ru"));
        assertEquals("뇌우", WeatherCodes.forWmo(95, "ko"));
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
        assertEquals("알 수 없음", WeatherCodes.forWmo(999, "ko"));

        // 编码没收录时：英文界面用接口给的描述兜底，其它语言给出「未知」
        assertEquals("Patchy rain nearby", WeatherCodes.forWwo(999, "en", "Patchy rain nearby"));
        assertEquals("不明", WeatherCodes.forWwo(999, "ja", "Patchy rain nearby"));
    }

    @Test
    public void mapsQWeatherIconCodes() {
        assertEquals("Sunny", WeatherCodes.forQWeather("100", "en"));
        assertEquals("晴れ", WeatherCodes.forQWeather("100", "ja"));
        assertEquals("Солнечно", WeatherCodes.forQWeather("100", "ru"));
        assertEquals("맑음", WeatherCodes.forQWeather("100", "ko"));
        assertEquals("Light rain", WeatherCodes.forQWeather("305", "en"));
        assertEquals("Heavy snow", WeatherCodes.forQWeather("402", "en"));
        // 和风的中文来自接口 text，查表只负责 en / ja / ru / ko
        assertEquals("Unknown", WeatherCodes.forQWeather("12345", "en"));
        assertEquals("알 수 없음", WeatherCodes.forQWeather("12345", "ko"));
        assertEquals("Unknown", WeatherCodes.forQWeather(null, "en"));
    }
}
