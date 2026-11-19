package com.vrcdevicecheck.osc.weather;

import org.junit.Test;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.Assert.assertEquals;

/**
 * 「失败原因」的归类规则。
 *
 * 这类代码最容易悄悄错：UnknownHost 和 ConnectException 长得像，但用户该做的事完全不同。
 *
 * 注意：这里只测 kind / code 这些纯逻辑，不测 text() / hint()——
 * 那两句话来自字符串资源，需要 Android Context，纯 JVM 单测拿不到。
 */
public class WeatherErrorTest {

    @Test
    public void unknownHostIsDnsFailure() {
        WeatherError error = WeatherError.fromException(new UnknownHostException("wttr.in"));
        assertEquals(WeatherError.Kind.DNS, error.kind);
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
    }

    @Test
    public void httpStatusKeepsTheCode() {
        WeatherError error = WeatherError.http(500, "Internal Server Error");
        assertEquals(WeatherError.Kind.HTTP_STATUS, error.kind);
        assertEquals(500, error.httpStatus);
    }

    @Test
    public void qweatherBusinessCodeIsKept() {
        WeatherError error = WeatherError.qweather("401");
        assertEquals(WeatherError.Kind.SERVER_CODE, error.kind);
        assertEquals("401", error.code);
    }

    @Test
    public void unknownExceptionStillCarriesTheClassName() {
        WeatherError error = WeatherError.fromException(new IllegalStateException("boom"));
        assertEquals(WeatherError.Kind.UNKNOWN, error.kind);
    }

    @Test
    public void nullExceptionDoesNotCrash() {
        WeatherError error = WeatherError.fromException(null);
        assertEquals(WeatherError.Kind.UNKNOWN, error.kind);
    }
}
