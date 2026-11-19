package com.vrcdevicecheck.osc.util;

/**
 * 对用户手写的 JSON 做容错，尽量把“肉眼几乎看不出”的常见错误修好再解析。
 *
 * 目前处理：
 *   1. UTF-8 BOM（Windows 记事本保存会带）；
 *   2. 对象/数组末尾多余的逗号：{"a":1,} / [1,2,]；
 *   3. 注释：// 行注释、/* 块注释 *\/（很多示例 JSON 会带）；
 *   4. 单引号字符串：{'k':'v'}（手写 JSON 很常见）；
 *   5. 没加引号的键：{k:"v"}。
 *
 * 处理不了的仍然交给 JSONObject 报错，不会把错误数据吞掉。
 */
public final class JsonRepair {

    private JsonRepair() {
    }

    public static String repair(String text) {
        if (text == null) return null;
        String value = text.trim();
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            value = value.substring(1).trim();
        }
        value = stripComments(value);
        value = convertSingleQuotes(value);
        value = quoteBareKeys(value);
        // 去掉紧挨着 } 或 ] 之前的多余逗号
        value = value.replaceAll(",\\s*([}\\]])", "$1");
        return value;
    }

    /** 去掉字符串外部的 // 与 /* *\/ 注释 */
    private static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        boolean inString = false;
        char quote = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(text.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == quote) inString = false;
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quote = c;
                out.append(c);
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') i++;
                out.append('\n');
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
                i += 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** 把简单的单引号字符串换成双引号，转义里面的双引号 */
    private static String convertSingleQuotes(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c != '\'') {
                out.append(c);
                i++;
                continue;
            }
            i++;
            out.append('"');
            while (i < n) {
                char inner = text.charAt(i);
                if (inner == '\\' && i + 1 < n) {
                    char next = text.charAt(i + 1);
                    if (next == '\'') {
                        out.append('\'');
                    } else {
                        out.append('\\').append(next);
                    }
                    i += 2;
                    continue;
                }
                if (inner == '\'') {
                    i++;
                    break;
                }
                if (inner == '"') out.append('\\');
                out.append(inner);
                i++;
            }
            out.append('"');
        }
        return out.toString();
    }

    /** 给没加引号的键补上双引号：{k:"v"} -> {"k":"v"} */
    private static String quoteBareKeys(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        int n = text.length();
        boolean inString = false;
        while (i < n) {
            char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < n) {
                    out.append(text.charAt(i + 1));
                    i += 2;
                    continue;
                }
                if (c == '"') inString = false;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                i++;
                continue;
            }
            if (c == '{' || c == ',') {
                out.append(c);
                i++;
                // 跳过空白，看看后面是不是一个裸键
                int start = i;
                while (i < n && Character.isWhitespace(text.charAt(i))) i++;
                int keyStart = i;
                while (i < n && isKeyChar(text.charAt(i))) i++;
                if (i > keyStart) {
                    int afterKey = i;
                    while (i < n && Character.isWhitespace(text.charAt(i))) i++;
                    if (i < n && text.charAt(i) == ':') {
                        out.append('"').append(text, keyStart, afterKey).append('"').append(':');
                        i++;
                        continue;
                    }
                    // 不是键，原样输出
                    out.append(text, start, i);
                    continue;
                }
                out.append(text, start, i);
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    private static boolean isKeyChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
    }
}
