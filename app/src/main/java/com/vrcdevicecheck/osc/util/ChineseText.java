package com.vrcdevicecheck.osc.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 简体 → 繁体 的字符映射，只用于「地名搜索」的兜底查询。
 *
 * 为什么需要：Open-Meteo 的地理编码基于 GeoNames，很多国外地名的中文写法存的是
 * 繁体/汉字原形（東京、紐約、首爾、廣島）。用户输入简体（东京、纽约、首尔）时，
 * 数据源里根本不存在这个字符串，于是要么查不到，要么只匹配到国内重名的村子。
 *
 * 这里不做完整的繁简转换（那需要一张几万字的表），只覆盖地名里常见的那批字：
 * 转换后仍查不到就查不到，不会比现在更差。
 */
public final class ChineseText {

    /** 两两一组：前简体、后繁体。只收地名里高频的字 */
    private static final String PAIRS =
            "东東广廣台臺湾灣县縣岛島泽澤滨濱冈岡宫宮门門关關阳陽阴陰静靜长長马馬"
            + "尔爾维維纳納萨薩罗羅兰蘭圣聖约約汉漢华華顿頓爱愛丽麗亚亞欧歐伦倫纽紐"
            + "图圖卢盧贝貝壳殼户戶叶葉冲沖绳繩矶磯旧舊迈邁灵靈达達内內买買开開毕畢"
            + "热熱诺諾温溫鲁魯苏蘇辅輔韩韓庆慶国國岭嶺峡峽莱萊兹茲宁寧宾賓";

    private static final Map<Character, Character> SIMP_TO_TRAD = new HashMap<>();

    static {
        for (int i = 0; i + 1 < PAIRS.length(); i += 2) {
            SIMP_TO_TRAD.put(PAIRS.charAt(i), PAIRS.charAt(i + 1));
        }
    }

    private ChineseText() {
    }

    /** 没有可转换的字时，原样返回（调用方可以直接用 equals 判断要不要再查一次） */
    public static String toTraditional(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character mapped = SIMP_TO_TRAD.get(c);
            if (mapped == null) {
                if (sb != null) sb.append(c);
            } else {
                if (sb == null) sb = new StringBuilder(text.length()).append(text, 0, i);
                sb.append(mapped.charValue());
            }
        }
        return sb == null ? text : sb.toString();
    }

    /** 是否含有汉字（用来判断要不要做繁简兜底） */
    public static boolean hasCjk(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }
}
