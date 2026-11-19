package com.vrcdevicecheck.osc.weather;

import android.content.Context;

import com.vrcdevicecheck.osc.util.LocaleHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 内置的「国家 → 城市」数据（assets/world_cities.json）。
 *
 * 数据来源：GeoNames cities15000（人口 &gt; 15000 的城市），每个国家按人口取前 15 个；
 * 城市的中文名取自 Open-Meteo 地理编码的本地化结果，所以和 App 内搜出来的写法一致。
 *
 * 只用于界面选择；经纬度直接写在数据里，选中后按坐标查天气，不再解析名字。
 */
public final class WorldCities {

    public static final class City {
        public final String nameZh;
        public final String nameEn;
        public final float latitude;
        public final float longitude;

        City(String nameZh, String nameEn, float latitude, float longitude) {
            this.nameZh = nameZh == null ? "" : nameZh;
            this.nameEn = nameEn == null ? "" : nameEn;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        /** 中文界面优先中文名，英文界面优先英文名；缺哪个用另一个顶上 */
        public String displayName(Context context) {
            if (!LocaleHelper.isChinese(context)) {
                return nameEn.isEmpty() ? nameZh : nameEn;
            }
            return nameZh.isEmpty() ? nameEn : nameZh;
        }
    }

    public static final class Country {
        public final String code;
        public final String nameZh;
        public final String nameEn;
        public final List<City> cities;

        Country(String code, String nameZh, String nameEn, List<City> cities) {
            this.code = code == null ? "" : code;
            this.nameZh = nameZh == null ? "" : nameZh;
            this.nameEn = nameEn == null ? "" : nameEn;
            this.cities = Collections.unmodifiableList(cities);
        }

        public String displayName(Context context) {
            if (!LocaleHelper.isChinese(context)) {
                return nameEn.isEmpty() ? nameZh : nameEn;
            }
            return nameZh.isEmpty() ? nameEn : nameZh;
        }
    }

    private static volatile List<Country> cached;

    private WorldCities() {
    }

    public static List<Country> load(Context context) {
        List<Country> result = cached;
        if (result != null) return result;

        result = new ArrayList<>();
        try {
            InputStream in = context.getAssets().open("world_cities.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            in.close();

            JSONArray array = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String code = item.optString("c", "");
                String nameZh = item.optString("z", "");
                String nameEn = item.optString("e", "");
                JSONArray citiesJson = item.optJSONArray("t");
                List<City> cities = new ArrayList<>();
                if (citiesJson != null) {
                    for (int j = 0; j < citiesJson.length(); j++) {
                        JSONArray row = citiesJson.optJSONArray(j);
                        if (row == null || row.length() < 4) continue;
                        cities.add(new City(
                                row.optString(0, ""),
                                row.optString(1, ""),
                                (float) row.optDouble(2, 0d),
                                (float) row.optDouble(3, 0d)));
                    }
                }
                if (!code.isEmpty() && !cities.isEmpty()) {
                    result.add(new Country(code, nameZh, nameEn, cities));
                }
            }
        } catch (Exception ignored) {
            // 资源损坏时返回空表，界面会提示加载失败
        }

        // 统一按英文名排序：中文界面下顺序也稳定（拼音首字母大致对得上）
        Collections.sort(result, new Comparator<Country>() {
            @Override
            public int compare(Country a, Country b) {
                String left = a.nameEn.isEmpty() ? a.nameZh : a.nameEn;
                String right = b.nameEn.isEmpty() ? b.nameZh : b.nameEn;
                return left.compareToIgnoreCase(right);
            }
        });

        if (!result.isEmpty()) cached = result;
        return result;
    }
}
