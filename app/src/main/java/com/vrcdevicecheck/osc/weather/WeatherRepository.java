package com.vrcdevicecheck.osc.weather;

import android.content.Context;
import android.content.SharedPreferences;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.data.ReportField;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 天气查询 + 本地缓存。
 *
 * 缓存策略：
 * - 成功拿到数据后记录时间戳，1 小时内同一城市直接读缓存，绝不发请求；
 * - 时间戳存 SharedPreferences，杀进程/重启应用也不会丢；
 * - 失败后 10 分钟内不重试，避免每个上报周期都打一次网络；
 * - 换城市会清空缓存，立即按新城市请求。
 *
 * 数据源：
 * 1) Open-Meteo（免费、无需 key）——国内部分网络访问 api.open-meteo.com 会超时；
 * 2) wttr.in（免费、无需 key）——作为兜底，保证国内网络也能出天气。
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

    /** 成功缓存 1 小时 */
    private static final long CACHE_TTL_MS = 60L * 60L * 1000L;
    /** 失败后至少隔 10 分钟再试 */
    private static final long FAIL_BACKOFF_MS = 10L * 60L * 1000L;
    /** api.open-meteo.com 失败后 6 小时内直接走 wttr.in，避免每小时都白等一次超时 */
    private static final long OPEN_METEO_SKIP_MS = 6L * 60L * 60L * 1000L;

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final Object FETCH_LOCK = new Object();

    private WeatherRepository() {
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
        return get(context);
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
                .apply();
    }

    private static WeatherInfo get(Context context) {
        AppSettings settings = new AppSettings(context);
        String province = settings.getWeatherProvince();
        String city = settings.getWeatherCity();
        if (city.isEmpty()) return null;

        final String cityKey = province + "/" + city;
        final String label = settings.getWeatherCityLabel();
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
            WeatherInfo info = fetch(sp, province, city, label, cityKey);
            if (info != null) {
                save(sp, info, cityKey);
                return info;
            }
            // 失败时如果还有旧数据，宁可显示旧数据也不留空
            return cityKey.equals(sp.getString(KEY_CITY_KEY, ""))
                    ? readCache(sp, cityKey, label)
                    : null;
        }
    }

    private static WeatherInfo fetch(SharedPreferences sp, String province, String city,
                                     String label, String cityKey) {
        Float[] coords = readCoords(sp, cityKey);
        if (coords == null) {
            coords = geocode(sp, province, city, cityKey);
        }

        long now = System.currentTimeMillis();
        if (coords != null && now >= sp.getLong(KEY_OPEN_METEO_SKIP_UNTIL, 0L)) {
            WeatherInfo info = fetchOpenMeteo(coords[0], coords[1], label);
            if (info != null) return info;
            // Open-Meteo 不通：接下来 6 小时直接用 wttr.in，避免每次都等超时
            sp.edit().putLong(KEY_OPEN_METEO_SKIP_UNTIL, now + OPEN_METEO_SKIP_MS).apply();
        }

        return fetchWttr(coords, province, city, label);
    }

    private static WeatherInfo fetchOpenMeteo(float lat, float lon, String label) {
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&current=temperature_2m,relative_humidity_2m,weather_code,"
                        + "pressure_msl,wind_direction_10m,wind_speed_10m,uv_index"
                        + "&daily=sunset&timezone=auto&wind_speed_unit=ms",
                lat, lon);
        String json = httpGet(url, 5000);
        if (json == null) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject current = root.optJSONObject("current");
            if (current == null || !current.has("temperature_2m")) return null;

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
            return null;
        }
    }

    private static WeatherInfo fetchWttr(Float[] coords, String province, String city, String label) {
        String location = coords != null
                ? String.format(Locale.US, "%.4f,%.4f", coords[0], coords[1])
                : encode(city);
        String url = "https://wttr.in/" + location + "?format=j1&lang=zh";
        String json = httpGet(url, 7000);
        if (json == null) return null;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray conditions = root.optJSONArray("current_condition");
            if (conditions == null || conditions.length() == 0) return null;
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
            return null;
        }
    }

    /** Open-Meteo 地理编码：把「江西省 上饶市」解析成经纬度并缓存 */
    private static Float[] geocode(SharedPreferences sp, String province, String city, String cityKey) {
        String[] candidates = new String[]{city, stripCitySuffix(city)};
        String want = normalizeRegion(province);
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) continue;
            String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + encode(candidate) + "&count=10&language=zh&format=json&countryCode=CN";
            String json = httpGet(url, 6000);
            if (json == null) continue;
            try {
                JSONObject root = new JSONObject(json);
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

    private static String httpGet(String url, int readTimeoutMs) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "VRCDI/2.3.9 (Android) weather");
            connection.setRequestProperty("Accept", "application/json");

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;

            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            in.close();
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
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
