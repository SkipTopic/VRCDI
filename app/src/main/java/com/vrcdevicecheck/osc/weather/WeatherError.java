package com.vrcdevicecheck.osc.weather;

import android.content.Context;

import com.vrcdevicecheck.osc.R;
import com.vrcdevicecheck.osc.util.LocaleHelper;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

/**
 * 一次天气请求失败的具体原因。
 *
 * 这个类的唯一目的：把「失败了」变成「为什么失败」。
 * 网络异常在 Java 里都长得很像，但用户能采取的动作完全不同——
 * DNS 失败要去查加速器/代理，超时要换源，HTTP 401 要去查 Key。
 *
 * 文案跟着界面语言走，所以 text()/hint()/logLine() 都要传 Context。
 */
public final class WeatherError {

    public enum Kind {
        /** 设备压根没有网络 */
        NO_NETWORK,
        /** 域名解析失败 */
        DNS,
        /** TCP 连不上（拒绝、路由不通、被防火墙丢包） */
        CONNECTION_REFUSED,
        /** 连接阶段超时 */
        CONNECT_TIMEOUT,
        /** 已连上，但等不到响应体 */
        READ_TIMEOUT,
        /** TLS 握手失败 */
        SSL,
        /** 连接被重置 */
        CONNECTION_RESET,
        /** 服务器返回非 2xx */
        HTTP_STATUS,
        /** 返回体为空 */
        EMPTY_BODY,
        /** 返回体不是合法 JSON */
        BAD_JSON,
        /** 返回体缺少必要字段 */
        MISSING_FIELD,
        /** 和风天气未配置 */
        NOT_CONFIGURED,
        /** 业务错误码（和风天气的 code 字段） */
        SERVER_CODE,
        UNKNOWN
    }

    public final Kind kind;
    /** 仅 HTTP_STATUS 有效，其余为 0 */
    public final int httpStatus;
    /** 和风天气业务错误码；其余情况为空 */
    public final String code;
    /** 补充信息：异常消息、缺失字段名、服务端原话等 */
    public final String detail;

    private WeatherError(Kind kind, int httpStatus, String code, String detail) {
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.code = code == null ? "" : code;
        this.detail = detail == null ? "" : detail;
    }

    public static WeatherError of(Kind kind) {
        return new WeatherError(kind, 0, "", "");
    }

    public static WeatherError of(Kind kind, String detail) {
        return new WeatherError(kind, 0, "", detail);
    }

    public static WeatherError http(int status, String detail) {
        return new WeatherError(Kind.HTTP_STATUS, status, "", detail);
    }

    /** 把网络异常翻译成人能看懂的一句话 */
    public static WeatherError fromException(Throwable t) {
        if (t == null) return of(Kind.UNKNOWN);
        String message = t.getMessage() == null ? "" : t.getMessage().trim();

        if (t instanceof UnknownHostException) {
            return of(Kind.DNS, message);
        }
        if (t instanceof SocketTimeoutException) {
            String lower = message.toLowerCase();
            if (lower.contains("connect")) {
                return of(Kind.CONNECT_TIMEOUT, message);
            }
            return of(Kind.READ_TIMEOUT, message);
        }
        if (t instanceof ConnectException) {
            return of(Kind.CONNECTION_REFUSED, message);
        }
        if (t instanceof SSLException) {
            return of(Kind.SSL, message);
        }
        if (t instanceof SocketException) {
            return of(Kind.CONNECTION_RESET, message);
        }
        String className = t.getClass().getSimpleName();
        return of(Kind.UNKNOWN, message.isEmpty() ? className : className + ": " + message);
    }

    public static WeatherError qweather(String code) {
        return qweather(code, "");
    }

    /**
     * @param detail 服务端原话，例如 "Authentication failed, check your KEY/Token or Host."
     */
    public static WeatherError qweather(String code, String detail) {
        return new WeatherError(Kind.SERVER_CODE, 0, code, detail);
    }

    /** 和风天气业务码 -> 本地化含义 */
    private static String qweatherMeaning(Context context, String code) {
        int resId;
        if ("204".equals(code)) {
            resId = R.string.qw_code_204;
        } else if ("400".equals(code)) {
            resId = R.string.qw_code_400;
        } else if ("401".equals(code)) {
            resId = R.string.qw_code_401;
        } else if ("402".equals(code)) {
            resId = R.string.qw_code_402;
        } else if ("403".equals(code)) {
            resId = R.string.qw_code_403;
        } else if ("404".equals(code)) {
            resId = R.string.qw_code_404;
        } else if ("429".equals(code)) {
            resId = R.string.qw_code_429;
        } else if ("500".equals(code)) {
            resId = R.string.qw_code_500;
        } else {
            resId = R.string.qw_code_unknown;
        }
        return LocaleHelper.str(context, resId);
    }

    /** 一句话原因，直接显示给用户 */
    public String text(Context context) {
        switch (kind) {
            case NO_NETWORK:
                return LocaleHelper.str(context, R.string.werr_no_network);
            case DNS:
                return LocaleHelper.str(context, R.string.werr_dns);
            case CONNECTION_REFUSED:
                return LocaleHelper.str(context, R.string.werr_connection_refused);
            case CONNECT_TIMEOUT:
                return LocaleHelper.str(context, R.string.werr_connect_timeout);
            case READ_TIMEOUT:
                return LocaleHelper.str(context, R.string.werr_read_timeout);
            case SSL:
                return LocaleHelper.str(context, R.string.werr_ssl);
            case CONNECTION_RESET:
                return LocaleHelper.str(context, R.string.werr_connection_reset);
            case HTTP_STATUS:
                return LocaleHelper.str(context, R.string.werr_http, httpStatus);
            case EMPTY_BODY:
                return LocaleHelper.str(context, R.string.werr_empty_body);
            case BAD_JSON:
                return LocaleHelper.str(context, R.string.werr_bad_json);
            case MISSING_FIELD:
                return LocaleHelper.str(context, R.string.werr_missing_field, detail);
            case NOT_CONFIGURED:
                return LocaleHelper.str(context, R.string.werr_not_configured);
            case SERVER_CODE: {
                String suffix = detail.isEmpty()
                        ? ""
                        : LocaleHelper.str(context, R.string.werr_detail_suffix, detail);
                return LocaleHelper.str(context, R.string.werr_server_code,
                        code + " " + qweatherMeaning(context, code) + suffix);
            }
            default:
                return detail.isEmpty()
                        ? LocaleHelper.str(context, R.string.werr_unknown)
                        : LocaleHelper.str(context, R.string.werr_unknown_detail, detail);
        }
    }

    /** 排查建议；没有建议时返回空串 */
    public String hint(Context context) {
        switch (kind) {
            case DNS:
                return LocaleHelper.str(context, R.string.werr_hint_dns);
            case CONNECT_TIMEOUT:
                return LocaleHelper.str(context, R.string.werr_hint_connect_timeout);
            case CONNECTION_REFUSED:
                return LocaleHelper.str(context, R.string.werr_hint_refused);
            case SSL:
                return LocaleHelper.str(context, R.string.werr_hint_ssl);
            case READ_TIMEOUT:
                return LocaleHelper.str(context, R.string.werr_hint_read_timeout);
            case HTTP_STATUS:
                if (httpStatus == 401 || httpStatus == 403) {
                    return LocaleHelper.str(context, R.string.werr_hint_http_auth);
                }
                if (httpStatus == 404) {
                    return LocaleHelper.str(context, R.string.werr_hint_http_404);
                }
                if (httpStatus == 429) {
                    return LocaleHelper.str(context, R.string.werr_hint_http_429);
                }
                return "";
            case SERVER_CODE:
                if ("402".equals(code) || "429".equals(code)) {
                    return LocaleHelper.str(context, R.string.werr_hint_qw_quota);
                }
                if ("401".equals(code) || "403".equals(code)) {
                    return LocaleHelper.str(context, R.string.werr_hint_qw_auth);
                }
                return "";
            case NOT_CONFIGURED:
                return LocaleHelper.str(context, R.string.werr_hint_not_configured);
            default:
                return "";
        }
    }

    /** 日志里的一行：原因 + 括注细节 */
    public String logLine(Context context) {
        String base = text(context);
        if (detail.isEmpty()) return base;
        if (kind == Kind.SERVER_CODE || kind == Kind.MISSING_FIELD) return base;
        return base + LocaleHelper.str(context, R.string.werr_detail_suffix, detail);
    }
}
