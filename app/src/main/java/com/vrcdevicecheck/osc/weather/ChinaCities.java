package com.vrcdevicecheck.osc.weather;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 读取内置的省市两级数据（assets/china_cities.json）。
 * 数据只用于界面选择；具体经纬度由 Open-Meteo 地理编码在首次请求时解析并缓存。
 */
public final class ChinaCities {

    public static final class Province {
        public final String name;
        public final List<String> cities;

        Province(String name, List<String> cities) {
            this.name = name;
            this.cities = Collections.unmodifiableList(cities);
        }
    }

    private static volatile List<Province> cached;

    private ChinaCities() {
    }

    public static List<Province> load(Context context) {
        List<Province> result = cached;
        if (result != null) return result;
        result = new ArrayList<>();
        try {
            InputStream in = context.getAssets().open("china_cities.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) > 0) out.write(buffer, 0, count);
            in.close();

            JSONArray array = new JSONArray(new String(out.toByteArray(), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String province = item.optString("province", "");
                JSONArray citiesJson = item.optJSONArray("cities");
                List<String> cities = new ArrayList<>();
                if (citiesJson != null) {
                    for (int j = 0; j < citiesJson.length(); j++) {
                        String city = citiesJson.optString(j, "");
                        if (!city.isEmpty()) cities.add(city);
                    }
                }
                if (!province.isEmpty() && !cities.isEmpty()) {
                    result.add(new Province(province, cities));
                }
            }
        } catch (Exception ignored) {
            // 资源损坏时返回空表，界面会提示加载失败
        }
        if (!result.isEmpty()) cached = result;
        return result;
    }
}
