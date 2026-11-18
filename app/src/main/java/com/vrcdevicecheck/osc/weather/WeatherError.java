package com.vrcdevicecheck.osc.weather;

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
    /** 补充信息：异常消息、缺失字段名、业务错误码等 */
    public final String detail;

    private WeatherError(Kind kind, int httpStatus, String detail) {
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.detail = detail == null ? "" : detail;
    }

    public static WeatherError of(Kind kind) {
        return new WeatherError(kind, 0, "");
    }

    public static WeatherError of(Kind kind, String detail) {
        return new WeatherError(kind, 0, detail);
    }

    public static WeatherError http(int status, String detail) {
        return new WeatherError(Kind.HTTP_STATUS, status, detail);
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

    /** 和风天气的业务错误码 -> 可读原因 */
    public static WeatherError qweather(String code) {
        String meaning;
        if ("204".equals(code)) {
            meaning = "该地区没有数据";
        } else if ("400".equals(code)) {
            meaning = "请求参数有误（多半是城市或经纬度格式不对）";
        } else if ("401".equals(code)) {
            meaning = "认证失败：API Key 无效，或 Key 与 API Host 不匹配";
        } else if ("402".equals(code)) {
            meaning = "今日免费额度已用完（免费订阅 1000 次/天），明天 0 点恢复";
        } else if ("403".equals(code)) {
            meaning = "这个凭据没有访问该接口的权限";
        } else if ("404".equals(code)) {
            meaning = "查询的地区不存在";
        } else if ("429".equals(code)) {
            meaning = "请求太频繁，被限流了（免费订阅约 10 次/分钟）";
        } else if ("500".equals(code)) {
            meaning = "和风天气服务端出错";
        } else {
            meaning = "未知错误码";
        }
        return new WeatherError(Kind.SERVER_CODE, 0, code + " " + meaning);
    }

    /** 一句话原因，直接显示给用户 */
    public String text() {
        switch (kind) {
            case NO_NETWORK:
                return "设备当前没有网络连接";
            case DNS:
                return "无法解析服务器域名（DNS 查询失败）";
            case CONNECTION_REFUSED:
                return "服务器拒绝连接或无法建立连接";
            case CONNECT_TIMEOUT:
                return "连接超时（对方在规定时间内没有响应）";
            case READ_TIMEOUT:
                return "读取超时（已连上，但服务器迟迟不返回数据）";
            case SSL:
                return "TLS 握手失败";
            case CONNECTION_RESET:
                return "连接被对方重置";
            case HTTP_STATUS:
                return "服务器返回 HTTP " + httpStatus;
            case EMPTY_BODY:
                return "服务器返回了空内容";
            case BAD_JSON:
                return "返回内容不是有效 JSON（可能被登录门户或运营商劫持）";
            case MISSING_FIELD:
                return "返回内容缺少必要字段：" + detail;
            case NOT_CONFIGURED:
                return "未配置和风天气 API Key";
            case SERVER_CODE:
                return "和风天气错误 " + detail;
            default:
                return detail.isEmpty() ? "未知错误" : "未知错误（" + detail + "）";
        }
    }

    /** 排查建议；没有建议时返回空串 */
    public String hint() {
        switch (kind) {
            case DNS:
                return "常见于运营商 DNS 污染，或加速器/代理接管了 DNS";
            case CONNECT_TIMEOUT:
                return "该域名在国内访问本来就不稳定；开着游戏加速器时可能更慢";
            case CONNECTION_REFUSED:
                return "检查飞行模式、VPN、加速器是否把流量拦掉了";
            case SSL:
                return "可能被代理、抓包工具或加速器中间人接管";
            case READ_TIMEOUT:
                return "服务器响应太慢，换个数据源通常就好了";
            case HTTP_STATUS:
                if (httpStatus == 401 || httpStatus == 403) {
                    return "Key 无效、没有权限，或 API Host 填错了";
                }
                if (httpStatus == 404) {
                    return "接口地址或地区参数不对";
                }
                if (httpStatus == 429) {
                    return "请求太频繁，被限流了";
                }
                return "";
            case SERVER_CODE:
                if (detail.startsWith("402") || detail.startsWith("429")) {
                    return "额度或频率限制，等一会儿就恢复";
                }
                if (detail.startsWith("401") || detail.startsWith("403")) {
                    return "去控制台核对 API Host 和 Key，两者必须配对";
                }
                return "";
            case NOT_CONFIGURED:
                return "在「和风天气兜底」里填 API Host 和 Key，它才参与兜底";
            default:
                return "";
        }
    }

    /** 日志里的一行：原因 + 括注细节 */
    public String logLine() {
        String base = text();
        if (detail.isEmpty()) return base;
        if (kind == Kind.SERVER_CODE || kind == Kind.MISSING_FIELD) return base;
        return base + "（" + detail + "）";
    }
}
