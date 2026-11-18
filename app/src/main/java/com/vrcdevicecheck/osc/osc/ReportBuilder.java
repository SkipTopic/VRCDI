package com.vrcdevicecheck.osc.osc;

import android.content.Context;

import com.vrcdevicecheck.osc.R;
import com.vrcdevicecheck.osc.data.ReportField;
import com.vrcdevicecheck.osc.device.DeviceSnapshot;
import com.vrcdevicecheck.osc.weather.WeatherInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 把设备快照拼成 Chatbox 文本。纯逻辑，可单元测试。
 *
 * 输出是分段的（每段一行），形如：
 *
 *   OPPO PCAT00
 *   CPU: Qualcomm SM8550 2.21GHz 46.1℃ 10%
 *   GPU: Adreno 740 不可用 41.3℃ 不可用
 *   RAM: 3.9/5.5GB 71%
 *   电量: 45% 充电中
 *   天气: 多云 17.3℃ 湿度 86%
 *   气压 1018hPa 西南风 3.2m/s 紫外线 5 日落 17:14
 *   App: VRCDI（本应用）
 *
 * 硬约束：VRChat Chatbox 单条上限 144 字符（含换行）。超长时从最后一段开始整段丢弃，
 * 并把被丢掉的字段回传给界面如实提示，绝不静默截断。
 *
 * 「自动屏蔽不可用」开启时，值为「不可用」的字段不进入文本，也不占 144 字符；
 * 这些字段记在 {@link Report#hidden}，与「超长被省略」的 {@link Report#dropped} 区分开。
 */
public final class ReportBuilder {

    public static final int CHATBOX_LIMIT = 144;
    private static final String NA = "不可用";

    /** 一次渲染结果 */
    public static final class Report {
        public final String text;
        public final List<ReportField> included;
        /** 因超过 144 字符而被整行丢弃的字段 */
        public final List<ReportField> dropped;
        /** 因值为「不可用」而被自动隐藏的字段（不占字符） */
        public final List<ReportField> hidden;
        public final boolean hardTruncated;

        Report(String text, List<ReportField> included, List<ReportField> dropped,
               List<ReportField> hidden, boolean hardTruncated) {
            this.text = text;
            this.included = included;
            this.dropped = dropped;
            this.hidden = hidden;
            this.hardTruncated = hardTruncated;
        }

        public int charCount() {
            return text.length();
        }

        public boolean overLimit() {
            return hardTruncated || !dropped.isEmpty();
        }

        /**
         * 界面提示文案：只提示「超长被省略」，不把自动隐藏算进去。
         * 注意上报正文本身不本地化，只有这条提示跟着界面语言走。
         */
        public String limitHint(Context context) {
            if (hardTruncated) return context.getString(R.string.hint_hard_truncated);
            if (dropped.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < dropped.size(); i++) {
                if (i > 0) sb.append(context.getString(R.string.list_separator));
                sb.append(dropped.get(i).label(context));
            }
            return context.getString(R.string.hint_dropped, sb.toString());
        }
    }

    /** 一行文本 + 这一行包含的字段（用来算"丢了什么"） */
    private static final class Line {
        final String text;
        final List<ReportField> fields;

        Line(String text, List<ReportField> fields) {
            this.text = text;
            this.fields = fields;
        }
    }

    /**
     * 一行文本的构造器：值为不可用时，按开关决定是写「不可用」还是直接隐藏。
     */
    private static final class LineBuilder {
        final StringBuilder text = new StringBuilder();
        final List<ReportField> fields = new ArrayList<>(4);
        final Set<ReportField> hidden;
        final boolean hideUnavailable;

        LineBuilder(Set<ReportField> hidden, boolean hideUnavailable) {
            this.hidden = hidden;
            this.hideUnavailable = hideUnavailable;
        }

        void add(ReportField field, String value) {
            if (isAvailable(value)) {
                append(text, value);
                fields.add(field);
            } else if (!hideUnavailable) {
                append(text, NA);
                fields.add(field);
            } else {
                hidden.add(field);
            }
        }

        /** 带标签的不可用项，例如「气压 不可用」 */
        void addUnavailable(ReportField field, String label) {
            if (hideUnavailable) {
                hidden.add(field);
            } else {
                append(text, label + NA);
                fields.add(field);
            }
        }

        /** 追加不属于独立字段的后缀，例如「充电中」 */
        void suffix(String value) {
            append(text, value);
        }

        boolean isEmpty() {
            return fields.isEmpty();
        }
    }

    private ReportBuilder() {
    }

    public static Report build(DeviceSnapshot snapshot, Set<ReportField> fields, boolean asciiOnly) {
        return build(snapshot, fields, asciiOnly, true, null, false);
    }

    public static Report build(DeviceSnapshot snapshot, Set<ReportField> fields, boolean asciiOnly,
                               boolean useProductName) {
        return build(snapshot, fields, asciiOnly, useProductName, null, false);
    }

    public static Report build(DeviceSnapshot snapshot, Set<ReportField> fields, boolean asciiOnly,
                               boolean useProductName, WeatherInfo weather) {
        return build(snapshot, fields, asciiOnly, useProductName, weather, false);
    }

    /**
     * @param weather         可为 null；为 null 时天气行显示「不可用」或被隐藏
     * @param hideUnavailable true 时，任何读到「不可用」的字段都不进文本，节省 144 字符
     */
    public static Report build(DeviceSnapshot snapshot, Set<ReportField> fields, boolean asciiOnly,
                               boolean useProductName, WeatherInfo weather, boolean hideUnavailable) {
        List<ReportField> ordered = new ArrayList<>();
        for (ReportField field : ReportField.values()) {
            if (fields != null && fields.contains(field)) ordered.add(field);
        }
        if (ordered.isEmpty()) {
            return new Report("", ordered, ordered, Collections.<ReportField>emptyList(), false);
        }

        Set<ReportField> hidden = EnumSet.noneOf(ReportField.class);
        List<Line> lines = buildLines(snapshot, ordered, asciiOnly, useProductName,
                weather, hideUnavailable, hidden);
        while (lineText(lines).length() > CHATBOX_LIMIT && lines.size() > 1) {
            lines.remove(lines.size() - 1);
        }

        String text = lineText(lines);
        boolean hard = false;
        if (text.length() > CHATBOX_LIMIT) {
            text = text.substring(0, CHATBOX_LIMIT - 1) + "…";
            hard = true;
        }

        Set<ReportField> includedSet = new LinkedHashSet<>();
        for (Line line : lines) includedSet.addAll(line.fields);

        List<ReportField> kept = new ArrayList<>();
        List<ReportField> dropped = new ArrayList<>();
        for (ReportField field : ordered) {
            if (includedSet.contains(field)) kept.add(field);
            else if (!hidden.contains(field)) dropped.add(field);
        }
        List<ReportField> hiddenList = new ArrayList<>(hidden);
        return new Report(text, kept, dropped, hiddenList, hard);
    }

    /** 分段渲染，返回用换行连接的多行文本 */
    public static String render(DeviceSnapshot snapshot, List<ReportField> fields, boolean asciiOnly) {
        return render(snapshot, fields, asciiOnly, true);
    }

    public static String render(DeviceSnapshot snapshot, List<ReportField> fields, boolean asciiOnly,
                                boolean useProductName) {
        Set<ReportField> hidden = EnumSet.noneOf(ReportField.class);
        return lineText(buildLines(snapshot, fields, asciiOnly, useProductName,
                null, false, hidden));
    }

    private static List<Line> buildLines(DeviceSnapshot snapshot, List<ReportField> fields,
                                         boolean asciiOnly, boolean useProductName,
                                         WeatherInfo weather, boolean hideUnavailable,
                                         Set<ReportField> hidden) {
        List<Line> lines = new ArrayList<>(7);

        // 第一行：品牌 + 型号（真实型号或产品名称）
        LineBuilder device = new LineBuilder(hidden, hideUnavailable);
        if (fields.contains(ReportField.BRAND)) {
            device.add(ReportField.BRAND, orNa(snapshot.brand));
        }
        if (fields.contains(ReportField.MODEL)) {
            String modelText = useProductName && snapshot.seriesName != null
                    ? snapshot.seriesName
                    : snapshot.model;
            device.add(ReportField.MODEL, orNa(modelText));
        }
        if (!device.isEmpty()) {
            lines.add(new Line(device.text.toString(), device.fields));
        }

        // CPU 行
        LineBuilder cpu = new LineBuilder(hidden, hideUnavailable);
        if (fields.contains(ReportField.CPU_NAME)) {
            cpu.add(ReportField.CPU_NAME, orNa(snapshot.cpuName));
        }
        if (fields.contains(ReportField.CPU_FREQ)) {
            cpu.add(ReportField.CPU_FREQ, freq(snapshot.cpuFreqMHz));
        }
        if (fields.contains(ReportField.CPU_TEMP)) {
            cpu.add(ReportField.CPU_TEMP, temp(snapshot.cpuTempC, asciiOnly));
        }
        if (fields.contains(ReportField.CPU_USAGE)) {
            cpu.add(ReportField.CPU_USAGE, pct(snapshot.cpuUsagePct));
        }
        if (!cpu.isEmpty()) {
            lines.add(new Line("CPU: " + cpu.text, cpu.fields));
        }

        // GPU 行
        LineBuilder gpu = new LineBuilder(hidden, hideUnavailable);
        if (fields.contains(ReportField.GPU_NAME)) {
            gpu.add(ReportField.GPU_NAME, orNa(snapshot.gpuName));
        }
        if (fields.contains(ReportField.GPU_FREQ)) {
            gpu.add(ReportField.GPU_FREQ, freq(snapshot.gpuFreqMHz));
        }
        if (fields.contains(ReportField.GPU_TEMP)) {
            gpu.add(ReportField.GPU_TEMP, temp(snapshot.gpuTempC, asciiOnly));
        }
        if (fields.contains(ReportField.GPU_USAGE)) {
            gpu.add(ReportField.GPU_USAGE, pct(snapshot.gpuUsagePct));
        }
        if (!gpu.isEmpty()) {
            lines.add(new Line("GPU: " + gpu.text, gpu.fields));
        }

        // RAM 行
        if (fields.contains(ReportField.RAM)) {
            if (snapshot.ramUsedMb == null || snapshot.ramTotalMb == null || snapshot.ramTotalMb <= 0) {
                if (hideUnavailable) hidden.add(ReportField.RAM);
                else lines.add(new Line("RAM: " + NA, Collections.singletonList(ReportField.RAM)));
            } else {
                lines.add(new Line(ramLine(snapshot.ramUsedMb, snapshot.ramTotalMb),
                        Collections.singletonList(ReportField.RAM)));
            }
        }

        // 电量行
        if (fields.contains(ReportField.BATTERY)) {
            LineBuilder battery = new LineBuilder(hidden, hideUnavailable);
            if (snapshot.batteryPct != null) {
                battery.add(ReportField.BATTERY, snapshot.batteryPct + "%");
                if (snapshot.charging) battery.suffix(asciiOnly ? "Charging" : "充电中");
            } else if (snapshot.charging) {
                battery.add(ReportField.BATTERY, asciiOnly ? "Charging" : "充电中");
            } else {
                battery.add(ReportField.BATTERY, NA);
            }
            if (!battery.isEmpty()) {
                lines.add(new Line((asciiOnly ? "Battery: " : "电量: ") + battery.text,
                        battery.fields));
            }
        }

        // 天气第一行：天气 / 气温 / 湿度
        boolean weatherFirst = fields.contains(ReportField.WEATHER)
                || fields.contains(ReportField.TEMPERATURE)
                || fields.contains(ReportField.HUMIDITY);
        if (weatherFirst) {
            List<ReportField> selected = new ArrayList<>(3);
            if (fields.contains(ReportField.WEATHER)) selected.add(ReportField.WEATHER);
            if (fields.contains(ReportField.TEMPERATURE)) selected.add(ReportField.TEMPERATURE);
            if (fields.contains(ReportField.HUMIDITY)) selected.add(ReportField.HUMIDITY);

            if (weather == null) {
                if (hideUnavailable) {
                    hidden.addAll(selected);
                } else {
                    lines.add(new Line((asciiOnly ? "Weather: " : "天气: ") + NA, selected));
                }
            } else {
                LineBuilder w = new LineBuilder(hidden, hideUnavailable);
                if (fields.contains(ReportField.WEATHER)) {
                    w.add(ReportField.WEATHER,
                            asciiOnly ? weather.conditionEn : weather.conditionZh);
                }
                if (fields.contains(ReportField.TEMPERATURE)) {
                    w.add(ReportField.TEMPERATURE, temp(weather.temperatureC, asciiOnly));
                }
                if (fields.contains(ReportField.HUMIDITY)) {
                    w.add(ReportField.HUMIDITY, humidity(weather.humidityPct, asciiOnly));
                }
                if (!w.isEmpty()) {
                    lines.add(new Line((asciiOnly ? "Weather: " : "天气: ") + w.text, w.fields));
                }
            }
        }

        // 天气第二行：气压 / 风向风速 / 紫外线 / 日落
        boolean weatherSecond = fields.contains(ReportField.PRESSURE)
                || fields.contains(ReportField.WIND)
                || fields.contains(ReportField.UV)
                || fields.contains(ReportField.SUNSET);
        if (weatherSecond) {
            List<ReportField> selected = new ArrayList<>(4);
            if (fields.contains(ReportField.PRESSURE)) selected.add(ReportField.PRESSURE);
            if (fields.contains(ReportField.WIND)) selected.add(ReportField.WIND);
            if (fields.contains(ReportField.UV)) selected.add(ReportField.UV);
            if (fields.contains(ReportField.SUNSET)) selected.add(ReportField.SUNSET);

            if (weather == null) {
                if (hideUnavailable) {
                    hidden.addAll(selected);
                } else {
                    LineBuilder w = new LineBuilder(hidden, false);
                    if (fields.contains(ReportField.PRESSURE)) {
                        w.addUnavailable(ReportField.PRESSURE, asciiOnly ? "Pressure " : "气压 ");
                    }
                    if (fields.contains(ReportField.WIND)) {
                        w.addUnavailable(ReportField.WIND, asciiOnly ? "Wind " : "风向风速 ");
                    }
                    if (fields.contains(ReportField.UV)) {
                        w.addUnavailable(ReportField.UV, asciiOnly ? "UV " : "紫外线 ");
                    }
                    if (fields.contains(ReportField.SUNSET)) {
                        w.addUnavailable(ReportField.SUNSET, asciiOnly ? "Sunset " : "日落 ");
                    }
                    if (!w.isEmpty()) lines.add(new Line(w.text.toString(), w.fields));
                }
            } else {
                LineBuilder w = new LineBuilder(hidden, hideUnavailable);
                if (fields.contains(ReportField.PRESSURE)) {
                    if (weather.pressureHpa != null) {
                        w.add(ReportField.PRESSURE, pressureText(weather, asciiOnly));
                    } else {
                        w.addUnavailable(ReportField.PRESSURE, asciiOnly ? "Pressure " : "气压 ");
                    }
                }
                if (fields.contains(ReportField.WIND)) {
                    String wind = windText(weather, asciiOnly);
                    if (wind != null) {
                        w.add(ReportField.WIND, wind);
                    } else {
                        w.addUnavailable(ReportField.WIND, asciiOnly ? "Wind " : "风向风速 ");
                    }
                }
                if (fields.contains(ReportField.UV)) {
                    if (weather.uvIndex != null) {
                        w.add(ReportField.UV, uvText(weather, asciiOnly));
                    } else {
                        w.addUnavailable(ReportField.UV, asciiOnly ? "UV " : "紫外线 ");
                    }
                }
                if (fields.contains(ReportField.SUNSET)) {
                    if (weather.sunset != null && !weather.sunset.trim().isEmpty()) {
                        w.add(ReportField.SUNSET, sunsetText(weather, asciiOnly));
                    } else {
                        w.addUnavailable(ReportField.SUNSET, asciiOnly ? "Sunset " : "日落 ");
                    }
                }
                if (!w.isEmpty()) {
                    lines.add(new Line(w.text.toString(), w.fields));
                }
            }
        }

        // 前台应用行
        if (fields.contains(ReportField.FOREGROUND_APP)) {
            LineBuilder app = new LineBuilder(hidden, hideUnavailable);
            app.add(ReportField.FOREGROUND_APP, orNa(snapshot.foregroundApp));
            if (!app.isEmpty()) {
                lines.add(new Line("App: " + app.text, app.fields));
            }
        }

        // 帧率行
        if (fields.contains(ReportField.VRCHAT_FPS)) {
            String fps = snapshot.vrchatFps == null
                    ? NA
                    : String.format(Locale.US, "%.0f", snapshot.vrchatFps);
            LineBuilder fpsLine = new LineBuilder(hidden, hideUnavailable);
            fpsLine.add(ReportField.VRCHAT_FPS, fps);
            if (!fpsLine.isEmpty()) {
                lines.add(new Line("FPS: " + fpsLine.text, fpsLine.fields));
            }
        }

        return lines;
    }

    private static String lineText(List<Line> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines.get(i).text);
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null) return;
        if (sb.length() > 0) sb.append(' ');
        sb.append(part);
    }

    private static boolean isAvailable(String value) {
        return value != null && !value.trim().isEmpty() && !NA.equals(value.trim());
    }

    private static String orNa(String value) {
        return value == null || value.trim().isEmpty() ? NA : value.trim();
    }

    private static String freq(Integer mhz) {
        if (mhz == null) return NA;
        if (mhz >= 1000) return String.format(Locale.US, "%.2fGHz", mhz / 1000f);
        return mhz + "MHz";
    }

    private static String temp(Float celsius, boolean asciiOnly) {
        if (celsius == null) return NA;
        return String.format(Locale.US, asciiOnly ? "%.1fC" : "%.1f℃", celsius);
    }

    private static String pct(Float value) {
        if (value == null) return NA;
        return String.format(Locale.US, "%.0f%%", value);
    }

    private static String humidity(Integer value, boolean asciiOnly) {
        if (value == null) return NA;
        return String.format(Locale.US, asciiOnly ? "Humidity %d%%" : "湿度 %d%%", value);
    }

    private static String pressureText(WeatherInfo weather, boolean asciiOnly) {
        if (weather.pressureHpa == null) return (asciiOnly ? "Pressure " : "气压 ") + NA;
        return String.format(Locale.US, asciiOnly ? "Pressure %.0fhPa" : "气压 %.0fhPa",
                weather.pressureHpa);
    }

    private static String uvText(WeatherInfo weather, boolean asciiOnly) {
        String label = asciiOnly ? "UV " : "紫外线 ";
        if (weather.uvIndex == null) return label + NA;
        return label + weather.uvIndex;
    }

    private static String sunsetText(WeatherInfo weather, boolean asciiOnly) {
        String label = asciiOnly ? "Sunset " : "日落 ";
        if (weather.sunset == null || weather.sunset.trim().isEmpty()) return label + NA;
        return label + weather.sunset.trim();
    }

    /** 风向转 8 方位；风速用 m/s，保留 1 位小数 */
    private static String windText(WeatherInfo weather, boolean asciiOnly) {
        boolean hasDirection = weather.windDirectionDeg != null;
        boolean hasSpeed = weather.windSpeedMs != null;
        if (!hasDirection && !hasSpeed) return null;

        StringBuilder sb = new StringBuilder();
        if (hasDirection) {
            sb.append(directionText(weather.windDirectionDeg, asciiOnly));
        }
        if (hasSpeed) {
            if (sb.length() > 0) sb.append(' ');
            else sb.append(asciiOnly ? "Wind " : "风速 ");
            sb.append(String.format(Locale.US, "%.1fm/s", weather.windSpeedMs));
        }
        return sb.toString();
    }

    private static String directionText(int degrees, boolean asciiOnly) {
        String[] zh = {"北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"};
        String[] en = {"N wind", "NE wind", "E wind", "SE wind",
                "S wind", "SW wind", "W wind", "NW wind"};
        int normalized = ((degrees % 360) + 360) % 360;
        int index = (int) Math.floor((normalized + 22.5) / 45.0) % 8;
        return asciiOnly ? en[index] : zh[index];
    }

    private static String ramLine(Long usedMb, Long totalMb) {
        if (usedMb == null || totalMb == null || totalMb <= 0) return "RAM: " + NA;
        int pct = Math.round(usedMb * 100f / totalMb);
        return String.format(Locale.US, "RAM: %.1f/%.1fGB %d%%",
                usedMb / 1024f, totalMb / 1024f, pct);
    }
}
