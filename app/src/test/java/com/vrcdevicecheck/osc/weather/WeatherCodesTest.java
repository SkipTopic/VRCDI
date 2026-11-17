package com.vrcdevicecheck.osc.weather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class WeatherCodesTest {

    @Test
    public void mapsWmoCodes() {
        assertEquals("多云", WeatherCodes.zhForWmo(2));
        assertEquals("Partly cloudy", WeatherCodes.enForWmo(2));
        assertEquals("雷阵雨", WeatherCodes.zhForWmo(95));
    }

    @Test
    public void mapsWwoCodes() {
        assertEquals("小雨", WeatherCodes.zhForWwo(296));
        assertEquals("Light rain", WeatherCodes.enForWwo(296, ""));
        assertEquals("局部有雨", WeatherCodes.zhForWwo(176));
    }

    @Test
    public void unknownCodesFallBackSafely() {
        assertEquals("未知", WeatherCodes.zhForWmo(999));
        assertEquals("Unknown", WeatherCodes.enForWmo(999));
        assertEquals("Patchy rain nearby", WeatherCodes.enForWwo(999, "Patchy rain nearby"));
    }
}
