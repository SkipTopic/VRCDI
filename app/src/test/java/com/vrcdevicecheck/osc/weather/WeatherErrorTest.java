package com.vrcdevicecheck.osc.weather;

import org.junit.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「失败原因」的翻译规则。
 * 这类代码最容易悄悄错：UnknownHost 和 ConnectException 长得像，但用户该做的事完全不同。
 */
public class WeatherErrorTest {

    @Test
    public void unknownHostIsDnsFailure() {
        WeatherError error = WeatherError.fromException(new UnknownHostException("wttr.in"));
        assertEquals(WeatherError.Kind.DNS, error.kind);
        assertTrue(error.text().contains("DNS"));
        assertTrue(error.hint().contains("加速器"));
    }

    @Test
    public void connectTimeoutIsDistinguishedFromReadTimeout() {
        WeatherError connect = WeatherError.fromException(
                new SocketTimeoutException("connect timed out"));
        assertEquals(WeatherError.Kind.CONNECT_TIMEOUT, connect.kind);

        WeatherError read = WeatherError.fromException(
                new SocketTimeoutException("Read timed out"));
        assertEquals(WeatherError.Kind.READ_TIMEOUT, read.kind);
    }

    @Test
    public void connectionRefusedIsNotReportedAsTimeout() {
        WeatherError error = WeatherError.fromException(new ConnectException("Connection refused"));
        assertEquals(WeatherError.Kind.CONNECTION_REFUSED, error.kind);
    }

    @Test
    public void sslHandshakeIsCalledOut() {
        WeatherError error = WeatherError.fromException(new SSLHandshakeException("handshake failed"));
        assertEquals(WeatherError.Kind.SSL, error.kind);
        assertTrue(error.hint().contains("代理"));
    }

    @Test
    public void httpStatusKeepsTheCode() {
        WeatherError error = WeatherError.http(500, "Internal Server Error");
        assertEquals(WeatherError.Kind.HTTP_STATUS, error.kind);
        assertTrue(error.text().contains("500"));
    }

    @Test
    public void qweatherAuthErrorExplainsWhatToCheck() {
        WeatherError error = WeatherError.qweather("401");
        assertEquals(WeatherError.Kind.SERVER_CODE, error.kind);
        assertTrue(error.text().contains("401"));
        assertTrue(error.text().contains("Key"));
        assertTrue(error.hint().contains("API Host"));
    }

    @Test
    public void qweatherQuotaErrorIsRecognizable() {
        WeatherError error = WeatherError.qweather("402");
        assertTrue(error.text().contains("额度"));
    }

    @Test
    public void unknownExceptionStillCarriesTheClassName() {
        WeatherError error = WeatherError.fromException(new IllegalStateException("boom"));
        assertEquals(WeatherError.Kind.UNKNOWN, error.kind);
        assertTrue(error.text().contains("IllegalStateException"));
    }
}
