package com.vrcdevicecheck.osc.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.data.ReportField;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * 天气查询 + 本地缓存。
 *
 * 缓存策略：
 * - 成功拿到数据后记录时间戳，1 小时内同一城市直接读缓存，绝不发请求；
 * - 时间戳存 SharedPreferences，杀进程/重启应用也不会丢；
 * - 失败后 10 分钟内不重试，避免每个上报周期都打一次网络；
 * - 换城市会清空缓存，立即按新城市请求。
 *
 * 数据源（按顺序兜底）：
 * 1) Open-Meteo（免费、无需 key）——国内部分网络访问 api.open-meteo.com 会超时；
 * 2) wttr.in（免费、无需 key）——境外服务，国内可用性不稳定；
 * 3) 和风天气（国内服务、需要自己申请 key）——前两个都失败时的最后兜底。
 *    用天气 API v1（/weather/v1/current/{lat}/{lon}）。旧的 /v7/weather/now 官方已公告
 *    2027-08-01 停服，所以不采用。实测 v1 同样接受 ?key= 的 API KEY 认证，不需要 JWT。
 *
 * 另外两个实测到的细节：
 * - 和风天气即使请求里没有 Accept-Encoding 也会返回 gzip，所以响应体按魔数判断是否解压；
 * - 出错时返回 {@code {"error":{"status":401,"detail":"..."}}}，HTTP 状态码同时就是错误码。
 *
 * 每次失败都会记下具体原因（{@link WeatherError}），
 * 用户能在界面上看到「是 DNS 挂了、超时了，还是 key 不对」，而不是一句「获取失败」。
 */
public final class WeatherRepository {

    private static final String PREFS = "vrcdi_weather";

    /** 缓存结构版本：升级后旧缓存没有气压/风/紫外线/日落，直接丢弃重取 */
    private static final int SCHEMA_VERSION = 2;
    private static final String KEY_SCHEMA = "schema";

    private static final String KEY_LAST_SUCCESS = "last_success";
    private static final String KEY_LAST_ATTEMPT = "last_attempt";
    private static final String KEY_CITY_KEY = "city_key";
    private static final String KEY_TEMP = "temp_c";
    private static final String KEY_HUMIDITY = "humidity";
    private static final String KEY_COND_ZH = "cond_zh";
    private static final String KEY_COND_EN = "cond_en";
    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_PRESSURE = "pressure_hpa";
    private static final String KEY_WIND_DIR = "wind_dir_deg";
    private static final String KEY_WIND_SPEED = "wind_speed_ms";
    private static final String KEY_UV = "uv_index";
    private static final String KEY_SUNSET = "sunset";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_COORD_CITY_KEY = "coord_city_key";
    private static final String KEY_OPEN_METEO_SKIP_UNTIL = "open_meteo_skip_until";
    private static final String KEY_LAST_FAIL = "last_fail";
    private static final String KEY_LAST_FAIL_AT = "last_fail_at";

    /** 成功缓存 1 小时 */
    private static final long CACHE_TTL_MS = 60L * 60L * 1000L;
    /** 失败后至少隔 10 分钟再试 */
    private static final long FAIL_BACKOFF_MS = 10L * 60L * 1000L;
    /** api.open-meteo.com 失败后 6 小时内直接走备用源，避免每次都白等一次超时 */
    private static final long OPEN_METEO_SKIP_MS = 6L * 60L * 60L * 1000L;

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final Object FETCH_LOCK = new Object();

    private WeatherRepository() {
    }

    /** 一次完整查询的结果，带逐条日志，界面直接拿来显示 */
    public static final class Result {
        public final WeatherInfo info;
        public final WeatherError error;
        public final java.util.List<String> log;

        Result(WeatherInfo info, WeatherError error, java.util.List<String> log) {
            this.info = info;
            this.error = error;
            this.log = log == null ? Collections.<String>emptyList() : log;
        }

        public boolean ok() {
            return info != null;
        }

        /** 一句话结论 */
        public String summaryLine() {
            if (info != null) return "成功：" + info.provider + " · " + info.summary();
            if (error != null) return "失败：" + error.text();
            return "失败";
        }
    }

    public static boolean isWeatherEnabled(Set<ReportField> fields) {
        if (fields == null) return false;
        for (ReportField field : fields) {
            if (field.isWeather()) return true;
        }
        return false;
    }

    /** 上报服务调用：允许网络请求，但受 1 小时缓存/10 分钟失败退避限制 */
    public static WeatherInfo getForReport(Context context) {
        return get(context, null);
    }

    /** 界面预览调用：只读缓存，绝不联网 */
    public static WeatherInfo getCached(Context context) {
        AppSettings settings = new AppSettings(context);
        String city = settings.getWeatherCity();
        if (city.isEmpty()) return null;
        String cityKey = settings.getWeatherProvince() + "/" + city;
        SharedPreferences sp = prefs(context);
        if (sp.getInt(KEY_SCHEMA, 0) < SCHEMA_VERSION) return null;
        return readCache(sp, cityKey, settings.getWeatherCityLabel());
    }

    /**
     * 界面上的「立即获取」：无视缓存与退避，把三个源完整跑一遍，
     * 每一步都通过 listener 实时回调，失败时给出具体原因。
     */
    public static Result refresh(Context context, WeatherFetchListener listener) {
        AppSettings settings = new AppSettings(context);
        if (settings.getWeatherCity().isEmpty()) {
            if (listener != null) listener.onStep("还没选城市，先在下面选一个");
            return new Result(null, WeatherError.of(WeatherError.Kind.UNKNOWN, "未选择城市"),
                    Collections.<String>emptyList());
        }
        SharedPreferences sp = prefs(context);
        synchronized (FETCH_LOCK) {
            Session session = new Session(context, settings, sp, listener, true, false);
            WeatherInfo info = session.run();
            return new Result(info, session.lastError, session.log);
        }
    }

    /** 只测和风天气这一条链路，给设置页的「测试连接」用 */
    public static Result testQWeather(Context context, WeatherFetchListener listener) {
        AppSettings settings = new AppSettings(context);
        SharedPreferences sp = prefs(context);
        synchronized (FETCH_LOCK) {
            if (settings.getWeatherCity().isEmpty()) {
                if (listener != null) listener.onStep("先选一个城市，才能测出这条链路通不通");
                return new Result(null, WeatherError.of(WeatherError.Kind.UNKNOWN, "未选择城市"),
                        Collections.<String>emptyList());
            }
            Session session = new Session(context, settings, sp, listener, true, true);
            WeatherInfo info = session.run();
            return new Result(info, session.lastError, session.log);
        }
    }

    /** 「上次失败：Open-Meteo 连接超时（10:32）」；没有失败记录时返回空串 */
    public static String getLastFailText(Context context) {
        SharedPreferences sp = prefs(context);
        String text = sp.getString(KEY_LAST_FAIL, "");
        if (text == null || text.isEmpty()) return "";
        long at = sp.getLong(KEY_LAST_FAIL_AT, 0L);
        String time = at > 0
                ? new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(at))
                : "";
        return time.isEmpty() ? text : text + "（" + time + "）";
    }

    /** 选择/切换城市；城市变了才清缓存 */
    public static void setCity(Context context, String province, String city) {
        AppSettings settings = new AppSettings(context);
        String oldKey = settings.getWeatherProvince() + "/" + settings.getWeatherCity();
        String newKey = province + "/" + city;
        settings.setWeatherCity(province, city);
        if (!oldKey.equals(newKey)) clearCache(context);
    }

    public static void clearCity(Context context) {
        new AppSettings(context).clearWeatherCity();
        clearCache(context);
    }

    public static void clearCache(Context context) {
        prefs(context).edit()
                .remove(KEY_LAST_SUCCESS)
                .remove(KEY_LAST_ATTEMPT)
                .remove(KEY_CITY_KEY)
                .remove(KEY_TEMP)
                .remove(KEY_HUMIDITY)
                .remove(KEY_COND_ZH)
                .remove(KEY_COND_EN)
                .remove(KEY_PROVIDER)
                .remove(KEY_PRESSURE)
                .remove(KEY_WIND_DIR)
                .remove(KEY_WIND_SPEED)
                .remove(KEY_UV)
                .remove(KEY_SUNSET)
                .remove(KEY_LAT)
                .remove(KEY_LON)
                .remove(KEY_COORD_CITY_KEY)
                .remove(KEY_LAST_FAIL)
                .remove(KEY_LAST_FAIL_AT)
                .apply();
    }

    private static WeatherInfo get(Context context, WeatherFetchListener listener) {
        AppSettings settings = new AppSettings(context);
        String city = settings.getWeatherCity();
        if (city.isEmpty()) return null;

        String cityKey = settings.getWeatherProvince() + "/" + city;
        String label = settings.getWeatherCityLabel();
        SharedPreferences sp = prefs(context);

        // 旧版本缓存里没有新字段，先清掉，避免升级后一小时都显示不出气压/风/紫外线/日落
        if (sp.getInt(KEY_SCHEMA, 0) < SCHEMA_VERSION) {
            sp.edit().clear().putInt(KEY_SCHEMA, SCHEMA_VERSION).apply();
        }

        synchronized (FETCH_LOCK) {
            long now = System.currentTimeMillis();
            long lastSuccess = sp.getLong(KEY_LAST_SUCCESS, 0L);
            if (lastSuccess > 0
                    && cityKey.equals(sp.getString(KEY_CITY_KEY, ""))
                    && now - lastSuccess < CACHE_TTL_MS) {
                return readCache(sp, cityKey, label);
            }

            long lastAttempt = sp.getLong(KEY_LAST_ATTEMPT, 0L);
            if (now - lastAttempt < FAIL_BACKOFF_MS) {
                return cityKey.equals(sp.getString(KEY_CITY_KEY, ""))
                        ? readCache(sp, cityKey, label)
                        : null;
            }

            sp.edit().putLong(KEY_LAST_ATTEMPT, now).apply();
            Session session = new Session(context, settings, sp, listener, false, false);
            WeatherInfo info = session.run();
            if (info != null) return info;
            // 失败时如果还有旧数据，宁可显示旧数据也不留空
            return cityKey.equals(sp.getString(KEY_CITY_KEY, ""))
                    ? readCache(sp, cityKey, label)
                    : null;
        }
    }

    /**
     * 一次查询会话。持有日志、最后一个错误，负责把三个源按顺序跑完。
     */
    private static final class Session {

        private final Context context;
        private final AppSettings settings;
        private final SharedPreferences sp;
        private final String province;
        private final String city;
        private final String label;
        private final String cityKey;
        private final WeatherFetchListener listener;
        /** 用户手动触发：忽略缓存、忽略 6 小时冷却 */
        private final boolean force;
        /** 只跑和风天气 */
        private final boolean qweatherOnly;

        final java.util.List<String> log = new ArrayList<>();
        WeatherError lastError;

        Session(Context context, AppSettings settings, SharedPreferences sp,
                WeatherFetchListener listener, boolean force, boolean qweatherOnly) {
            this.context = context;
            this.settings = settings;
            this.sp = sp;
            this.listener = listener;
            this.force = force;
            this.qweatherOnly = qweatherOnly;
            this.province = settings.getWeatherProvince();
            this.city = settings.getWeatherCity();
            this.label = settings.getWeatherCityLabel();
            this.cityKey = province + "/" + city;
        }

        private void step(String text) {
            String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                    + "  " + text;
            log.add(line);
            if (listener != null) listener.onStep(line);
        }

        WeatherInfo run() {
            if (!hasNetwork(context)) {
                lastError = WeatherError.of(WeatherError.Kind.NO_NETWORK);
                step("✗ 设备当前没有网络连接（飞行模式 / 移动数据关闭）");
                recordFail("网络", lastError);
                return null;
            }

            step("开始获取天气：" + (label.isEmpty() ? "未选择城市" : label));

            Float[] coords = readCoords(sp, cityKey);
            if (coords == null) {
                step("· 解析城市坐标（geocoding-api.open-meteo.com）…");
                coords = geocode(sp, province, city, cityKey, this);
            }
            if (coords != null) {
                step(String.format(Locale.US, "· 已定位：%.4f, %.4f", coords[0], coords[1]));
            } else {
                step("· 没拿到坐标，和风天气将改用城市名查询");
            }

            if (qweatherOnly) {
                return runQWeather(coords);
            }

            // ① Open-Meteo
            long skipUntil = sp.getLong(KEY_OPEN_METEO_SKIP_UNTIL, 0L);
            if (!force && System.currentTimeMillis() < skipUntil) {
                step("① Open-Meteo：上次失败后处于 6 小时冷却，跳过");
            } else {
                step("① 连接 Open-Meteo（api.open-meteo.com）…");
                WeatherError[] err = new WeatherError[1];
                WeatherInfo info = coords == null
                        ? null
                        : fetchOpenMeteo(coords[0], coords[1], label, err);
                if (info != null) return succeed(info);
                if (coords == null) err[0] = WeatherError.of(WeatherError.Kind.UNKNOWN, "没有坐标");
                fail("Open-Meteo", err[0]);
                sp.edit().putLong(KEY_OPEN_METEO_SKIP_UNTIL,
                        System.currentTimeMillis() + OPEN_METEO_SKIP_MS).apply();
            }

            // ② wttr.in
            step("② 切换备用源 wttr.in（wttr.in）…");
            WeatherError[] err2 = new WeatherError[1];
            WeatherInfo info2 = fetchWttr(coords, province, city, label, err2);
            if (info2 != null) return succeed(info2);
            fail("wttr.in", err2[0]);

            // ③ 和风天气
            return runQWeather(coords);
        }

        private WeatherInfo runQWeather(Float[] coords) {
            String host = settings.getQWeatherHost();
            step("③ 切换到兜底源 和风天气（" + host + "）…");

            String key = settings.getQWeatherKey();
            if (key.isEmpty()) {
                fail("和风天气", WeatherError.of(WeatherError.Kind.NOT_CONFIGURED));
                step("· 三个数据源全部失败。去「和风天气兜底」填一套 Key 就能补上");
                recordFail("全部数据源", lastError);
                return null;
            }

            Float[] point = coords;
            if (point == null) {
                // v1 用路径里的经纬度，没有坐标时先让 GeoAPI 把城市名换成坐标
                step("· 用城市名查经纬度（GeoAPI）…");
                point = geoLookup(host, key, city);
                if (point == null) {
                    fail("和风天气", WeatherError.of(WeatherError.Kind.UNKNOWN, "城市名解析失败"));
                    recordFail("全部数据源", lastError);
                    return null;
                }
                step(String.format(Locale.US, "· 城市库定位：%.4f, %.4f", point[0], point[1]));
            }

            WeatherError[] err = new WeatherError[1];
            WeatherInfo info = fetchQWeather(host, key, point[0], point[1], label, err);
            if (info != null) return succeed(info);
            fail("和风天气", err[0]);
            recordFail("全部数据源", lastError);
            step("· 三个数据源全部失败，本次没有拿到天气");
            return null;
        }

        private WeatherInfo succeed(WeatherInfo info) {
            step("✓ " + info.provider + " 成功：" + info.summary());
            save(sp, info, cityKey);
            clearFail();
            return info;
        }

        private void fail(String provider, WeatherError error) {
            WeatherError value = error == null
                    ? WeatherError.of(WeatherError.Kind.UNKNOWN) : error;
            lastError = value;
            step("✗ " + provider + " 失败：" + value.logLine());
            String hint = value.hint();
            if (!hint.isEmpty()) step("   ↳ " + hint);
            recordFail(provider, value);
        }

        private void recordFail(String provider, WeatherError error) {
            if (error == null) return;
            sp.edit()
                    .putString(KEY_LAST_FAIL, provider + " " + error.text())
                    .putLong(KEY_LAST_FAIL_AT, System.currentTimeMillis())
                    .apply();
        }

        private void clearFail() {
            sp.edit().remove(KEY_LAST_FAIL).remove(KEY_LAST_FAIL_AT).apply();
        }
    }

    // ---------------- 各数据源 ----------------

    private static WeatherInfo fetchOpenMeteo(float lat, float lon, String label, WeatherError[] out) {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code,"
                        + "pressure_msl,wind_direction_10m,wind_speed_10m,uv_index"
                        + "&daily=sunset&timezone=auto&wind_speed_unit=ms",
                lat, lon);
        HttpResult result = httpGet(url, 5000);
        if (!result.ok()) {
            out[0] = result.error;
            return null;
        }
        try {
            JSONObject root = new JSONObject(result.body);
            JSONObject current = root.optJSONObject("current");
            if (current == null) {
                out[0] = WeatherError.of(WeatherError.Kind.MISSING_FIELD, "current");
                return null;
            }
            if (!current.has("temperature_2m")) {
                out[0] = WeatherError.of(WeatherError.Kind.MISSING_FIELD, "current.temperature_2m");
                return null;
            }

            float temp = (float) current.getDouble("temperature_2m");
            Integer humidity = current.has("relative_humidity_2m")
                    ? current.getInt("relative_humidity_2m") : null;
            int code = current.optInt("weather_code", -1);
            Float pressure = current.has("pressure_msl")
                    ? (float) current.getDouble("pressure_msl") : null;
            Integer windDir = current.has("wind_direction_10m")
                    ? current.getInt("wind_direction_10m") : null;
            Float windSpeed = current.has("wind_speed_10m")
                    ? (float) current.getDouble("wind_speed_10m") : null;
            Integer uv = current.has("uv_index")
                    ? Math.round((float) current.getDouble("uv_index")) : null;

            String sunset = null;
            JSONObject daily = root.optJSONObject("daily");
            if (daily != null) {
                JSONArray sunsets = daily.optJSONArray("sunset");
                if (sunsets != null && sunsets.length() > 0) {
                    sunset = formatSunset(sunsets.optString(0, ""));
                }
            }

            return new WeatherInfo(label, temp, humidity,
                    WeatherCodes.zhForWmo(code), WeatherCodes.enForWmo(code),
                    System.currentTimeMillis(), "Open-Meteo",
                    pressure, windDir, windSpeed, uv, sunset);
        } catch (Exception e) {
            out[0] = WeatherError.of(WeatherError.Kind.BAD_JSON, e.getClass().getSimpleName());
            return null;
        }
    }

    private static WeatherInfo fetchWttr(Float[] coords, String province, String city,
                                         String label, WeatherError[] out) {
        String location = coords != null
                ? String.format(Locale.US, "%.4f,%.4f", coords[0], coords[1])
                : encode(city);
        String url = "https://wttr.in/" + location + "?format=j1&lang=zh";
        HttpResult result = httpGet(url, 7000);
        if (!result.ok()) {
            out[0] = result.error;
            return null;
        }
        try {
            JSONObject root = new JSONObject(result.body);
            JSONArray conditions = root.optJSONArray("current_condition");
            if (conditions == null || conditions.length() == 0) {
                out[0] = WeatherError.of(WeatherError.Kind.MISSING_FIELD, "current_condition");
                return null;
            }
            JSONObject current = conditions.getJSONObject(0);

            Float temp = parseFloat(current.optString("temp_C", ""));
            Integer humidity = parseInt(current.optString("humidity", ""));
            int code = parseIntValue(current.optString("weatherCode", ""), -1);
            String desc = "";
            JSONArray descriptions = current.optJSONArray("weatherDesc");
            if (descriptions != null && descriptions.length() > 0) {
                desc = descriptions.getJSONObject(0).optString("value", "");
            }

            Integer pressure = parseInt(current.optString("pressure", ""));
            Integer windDir = parseInt(current.optString("winddirDegree", ""));
            Float windKmph = parseFloat(current.optString("windspeedKmph", ""));
            Float windSpeed = windKmph == null ? null : windKmph / 3.6f;
            Integer uv = parseInt(current.optString("uvIndex", ""));

            String sunset = null;
            JSONArray days = root.optJSONArray("weather");
            if (days != null && days.length() > 0) {
                JSONArray astronomy = days.getJSONObject(0).optJSONArray("astronomy");
                if (astronomy != null && astronomy.length() > 0) {
                    sunset = formatSunset(astronomy.getJSONObject(0).optString("sunset", ""));
                }
            }

            return new WeatherInfo(label, temp, humidity,
                    WeatherCodes.zhForWwo(code), WeatherCodes.enForWwo(code, desc),
                    System.currentTimeMillis(), "wttr.in",
                    pressure == null ? null : pressure.floatValue(),
                    windDir, windSpeed, uv, sunset);
        } catch (Exception e) {
            out[0] = WeatherError.of(WeatherError.Kind.BAD_JSON, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 和风天气实时天气（天气 API v1）。
     * 地址是每个账号独立的 API Host，公共地址 devapi.qweather.com 从 2026 年起逐步停用。
     * v1 的坐标放在路径里：/weather/v1/current/{lat}/{lon}，实测同样接受 ?key= 认证。
     */
    private static WeatherInfo fetchQWeather(String host, String key, float lat, float lon,
                                            String label, WeatherError[] out) {
        String url = String.format(Locale.US,
                "https://%s/weather/v1/current/%.2f/%.2f?key=%s&lang=zh",
                host, lat, lon, encode(key));
        HttpResult result = httpGet(url, 5000);
        if (!result.ok()) {
            out[0] = result.error;
            return null;
        }
        return parseQWeather(result.body, label, out);
    }

    /**
     * 拆出来单独一个方法，方便以后直接喂 JSON 做单测。
     * v1 的字段是嵌套的，单位写在 unit 里：温度 °C、风速 m/s、气压 hPa、湿度是 0-1 的小数。
     */
    static WeatherInfo parseQWeather(String json, String label, WeatherError[] out) {
        try {
            JSONObject root = new JSONObject(json);

            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                out[0] = WeatherError.qweather(
                        String.valueOf(error.optInt("status", 0)),
                        error.optString("detail", ""));
                return null;
            }

            JSONObject temperature = root.optJSONObject("temperature");
            if (temperature == null || !temperature.has("value")) {
                out[0] = WeatherError.of(WeatherError.Kind.MISSING_FIELD, "temperature.value");
                return null;
            }
            float temp = (float) temperature.optDouble("value");

            // v1 的湿度是 0-1，上报文案里是百分比
            Integer humidity = null;
            if (root.has("humidity")) {
                double value = root.optDouble("humidity", -1);
                if (value >= 0) humidity = (int) Math.round(value * 100);
            }

            JSONObject condition = root.optJSONObject("condition");
            String text = condition == null ? "" : condition.optString("text", "").trim();
            String icon = condition == null ? "" : condition.optString("code", "").trim();

            Float pressure = null;
            JSONObject pressureObject = root.optJSONObject("pressure");
            if (pressureObject != null && pressureObject.has("value")) {
                pressure = (float) pressureObject.optDouble("value");
            }

            Integer windDir = null;
            Float windMs = null;
            JSONObject wind = root.optJSONObject("wind");
            if (wind != null) {
                JSONObject direction = wind.optJSONObject("direction");
                if (direction != null && direction.has("degree")) {
                    windDir = direction.optInt("degree");
                }
                JSONObject speed = wind.optJSONObject("speed");
                // v1 的风速已经是 m/s，不需要换算
                if (speed != null && speed.has("value")) {
                    windMs = (float) speed.optDouble("value");
                }
            }

            Integer uv = null;
            Object uvRaw = root.opt("uvIndex");
            if (uvRaw instanceof Number) uv = ((Number) uvRaw).intValue();

            return new WeatherInfo(label, temp, humidity,
                    text.isEmpty() ? "未知" : text,
                    WeatherCodes.enForQWeather(icon),
                    System.currentTimeMillis(), "和风天气",
                    pressure, windDir, windMs, uv, null);
        } catch (Exception e) {
            out[0] = WeatherError.of(WeatherError.Kind.BAD_JSON, e.getClass().getSimpleName());
            return null;
        }
    }

    /** 没有坐标时，用城市名换经纬度（GeoAPI 返回里就带 lat/lon） */
    private static Float[] geoLookup(String host, String key, String city) {
        String url = "https://" + host + "/geo/v2/city/lookup?location=" + encode(city)
                + "&key=" + encode(key) + "&lang=zh";
        HttpResult result = httpGet(url, 5000);
        if (!result.ok()) return null;
        try {
            JSONObject root = new JSONObject(result.body);
            if (!"200".equals(root.optString("code", ""))) return null;
            JSONArray list = root.optJSONArray("location");
            if (list == null || list.length() == 0) return null;
            JSONObject first = list.getJSONObject(0);
            String lat = first.optString("lat", "");
            String lon = first.optString("lon", "");
            Float latValue = parseFloat(lat);
            Float lonValue = parseFloat(lon);
            if (latValue == null || lonValue == null) return null;
            return new Float[]{latValue, lonValue};
        } catch (Exception e) {
            return null;
        }
    }

    /** Open-Meteo 地理编码：把「江西省 上饶市」解析成经纬度并缓存 */
    private static Float[] geocode(SharedPreferences sp, String province, String city,
                                   String cityKey, Session session) {
        String[] candidates = new String[]{city, stripCitySuffix(city)};
        String want = normalizeRegion(province);
        WeatherError last = null;
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) continue;
            String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + encode(candidate) + "&count=10&language=zh&format=json&countryCode=CN";
            HttpResult result = httpGet(url, 6000);
            if (!result.ok()) {
                last = result.error;
                continue;
            }
            try {
                JSONObject root = new JSONObject(result.body);
                JSONArray results = root.optJSONArray("results");
                if (results == null || results.length() == 0) continue;

                JSONObject fallback = null;
                JSONObject matched = null;
                for (int i = 0; i < results.length(); i++) {
                    JSONObject item = results.getJSONObject(i);
                    if (fallback == null) fallback = item;
                    String admin1 = item.optString("admin1", "");
                    String normalized = normalizeRegion(admin1);
                    if (!normalized.isEmpty() && !want.isEmpty()
                            && (normalized.equals(want)
                            || normalized.contains(want)
                            || want.contains(normalized))) {
                        matched = item;
                        break;
                    }
                }
                JSONObject best = matched != null ? matched : fallback;
                if (best == null) continue;

                float lat = (float) best.getDouble("latitude");
                float lon = (float) best.getDouble("longitude");
                sp.edit()
                        .putFloat(KEY_LAT, lat)
                        .putFloat(KEY_LON, lon)
                        .putString(KEY_COORD_CITY_KEY, cityKey)
                        .apply();
                return new Float[]{lat, lon};
            } catch (Exception ignored) {
                // 试下一个候选名
            }
        }
        if (session != null && last != null) {
            session.step("· 坐标解析失败：" + last.logLine());
        }
        return null;
    }

    private static Float[] readCoords(SharedPreferences sp, String cityKey) {
        if (!cityKey.equals(sp.getString(KEY_COORD_CITY_KEY, ""))) return null;
        if (!sp.contains(KEY_LAT) || !sp.contains(KEY_LON)) return null;
        return new Float[]{sp.getFloat(KEY_LAT, 0f), sp.getFloat(KEY_LON, 0f)};
    }

    private static void save(SharedPreferences sp, WeatherInfo info, String cityKey) {
        SharedPreferences.Editor editor = sp.edit()
                .putInt(KEY_SCHEMA, SCHEMA_VERSION)
                .putLong(KEY_LAST_SUCCESS, info.fetchedAtMs)
                .putString(KEY_CITY_KEY, cityKey)
                .putString(KEY_COND_ZH, info.conditionZh)
                .putString(KEY_COND_EN, info.conditionEn)
                .putString(KEY_PROVIDER, info.provider);
        if (info.temperatureC != null) editor.putFloat(KEY_TEMP, info.temperatureC);
        else editor.remove(KEY_TEMP);
        if (info.humidityPct != null) editor.putInt(KEY_HUMIDITY, info.humidityPct);
        else editor.remove(KEY_HUMIDITY);
        if (info.pressureHpa != null) editor.putFloat(KEY_PRESSURE, info.pressureHpa);
        else editor.remove(KEY_PRESSURE);
        if (info.windDirectionDeg != null) editor.putInt(KEY_WIND_DIR, info.windDirectionDeg);
        else editor.remove(KEY_WIND_DIR);
        if (info.windSpeedMs != null) editor.putFloat(KEY_WIND_SPEED, info.windSpeedMs);
        else editor.remove(KEY_WIND_SPEED);
        if (info.uvIndex != null) editor.putInt(KEY_UV, info.uvIndex);
        else editor.remove(KEY_UV);
        if (info.sunset != null && !info.sunset.trim().isEmpty()) editor.putString(KEY_SUNSET, info.sunset);
        else editor.remove(KEY_SUNSET);
        editor.apply();
    }

    private static WeatherInfo readCache(SharedPreferences sp, String cityKey, String label) {
        if (!cityKey.equals(sp.getString(KEY_CITY_KEY, ""))) return null;
        long time = sp.getLong(KEY_LAST_SUCCESS, 0L);
        if (time <= 0) return null;
        Float temp = sp.contains(KEY_TEMP) ? sp.getFloat(KEY_TEMP, 0f) : null;
        Integer humidity = sp.contains(KEY_HUMIDITY) ? sp.getInt(KEY_HUMIDITY, 0) : null;
        Float pressure = sp.contains(KEY_PRESSURE) ? sp.getFloat(KEY_PRESSURE, 0f) : null;
        Integer windDir = sp.contains(KEY_WIND_DIR) ? sp.getInt(KEY_WIND_DIR, 0) : null;
        Float windSpeed = sp.contains(KEY_WIND_SPEED) ? sp.getFloat(KEY_WIND_SPEED, 0f) : null;
        Integer uv = sp.contains(KEY_UV) ? sp.getInt(KEY_UV, 0) : null;
        return new WeatherInfo(label, temp, humidity,
                sp.getString(KEY_COND_ZH, "未知"),
                sp.getString(KEY_COND_EN, "Unknown"),
                time, sp.getString(KEY_PROVIDER, ""),
                pressure, windDir, windSpeed, uv, sp.getString(KEY_SUNSET, null));
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @SuppressWarnings("deprecation")
    private static boolean hasNetwork(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            NetworkInfo info = manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    /** 一次 HTTP GET 的结果：要么拿到 body，要么拿到具体失败原因 */
    private static final class HttpResult {
        final int status;
        final String body;
        final WeatherError error;

        HttpResult(int status, String body, WeatherError error) {
            this.status = status;
            this.body = body;
            this.error = error;
        }

        boolean ok() {
            return error == null && body != null;
        }
    }

    private static HttpResult httpGet(String url, int readTimeoutMs) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "VRCDI/2.3.9 (Android) weather");
            connection.setRequestProperty("Accept", "application/json");
            // 显式要 gzip，自己解。不依赖 HttpURLConnection 的透明解压，
            // 因为和风天气不带 Accept-Encoding 也会压，各版本行为不一致。
            connection.setRequestProperty("Accept-Encoding", "gzip");

            int code = connection.getResponseCode();
            InputStream in = (code >= 200 && code < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String body = readAll(in);
            if (code < 200 || code >= 300) {
                return new HttpResult(code, body, WeatherError.http(code, errorDetail(body)));
            }
            if (body == null || body.trim().isEmpty()) {
                return new HttpResult(code, body, WeatherError.of(WeatherError.Kind.EMPTY_BODY));
            }
            return new HttpResult(code, body, null);
        } catch (Exception e) {
            return new HttpResult(0, null, WeatherError.fromException(e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** 错误响应里通常带一句话，取出来比裸截 JSON 有用 */
    private static String errorDetail(String body) {
        if (body == null) return "";
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String detail = error.optString("detail", "").trim();
                if (!detail.isEmpty()) return detail;
                String title = error.optString("title", "").trim();
                if (!title.isEmpty()) return title;
            }
            String message = root.optString("message", "").trim();
            if (!message.isEmpty()) return message;
        } catch (Exception ignored) {
            // 不是 JSON，退回截断
        }
        String snippet = body.trim();
        return snippet.length() > 80 ? snippet.substring(0, 80) + "…" : snippet;
    }

    /** 读完并按需解压（有的服务器不看 Accept-Encoding，直接发 gzip） */
    private static String readAll(InputStream in) {
        if (in == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            in.close();

            byte[] bytes = out.toByteArray();
            if (bytes.length > 2 && (bytes[0] & 0xff) == 0x1f && (bytes[1] & 0xff) == 0x8b) {
                bytes = gunzip(bytes);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = gzip.read(buffer)) > 0) out.write(buffer, 0, count);
        gzip.close();
        return out.toByteArray();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    private static String stripCitySuffix(String city) {
        if (city == null) return "";
        String value = city.trim();
        String[] suffixes = {"特别行政区", "自治州", "地区", "盟", "市"};
        for (String suffix : suffixes) {
            if (value.endsWith(suffix) && value.length() > suffix.length()) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private static String normalizeRegion(String value) {
        if (value == null) return "";
        return value.trim()
                .replace("特别行政区", "")
                .replace("自治区", "")
                .replace("维吾尔", "")
                .replace("壮族", "")
                .replace("回族", "")
                .replace("藏族", "")
                .replace("省", "")
                .replace("市", "");
    }

    private static Float parseFloat(String value) {
        try {
            return Float.parseFloat(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        try {
            return (int) Float.parseFloat(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseIntValue(String value, int fallback) {
        Integer parsed = parseInt(value);
        return parsed == null ? fallback : parsed;
    }

    /** Open-Meteo 返回 2026-11-17T17:14，wttr.in 返回 05:14 PM，统一成 17:14 */
    private static String formatSunset(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;

        int t = value.indexOf('T');
        if (t >= 0 && value.length() >= t + 6) {
            return value.substring(t + 1, t + 6);
        }

        try {
            java.text.SimpleDateFormat input =
                    new java.text.SimpleDateFormat("hh:mm a", Locale.US);
            java.text.SimpleDateFormat output =
                    new java.text.SimpleDateFormat("HH:mm", Locale.US);
            java.util.Date date = input.parse(value);
            if (date != null) return output.format(date);
        } catch (Exception ignored) {
            // 继续按 HH:mm 处理
        }

        if (value.length() >= 5 && value.charAt(2) == ':') return value.substring(0, 5);
        return value;
    }
}
