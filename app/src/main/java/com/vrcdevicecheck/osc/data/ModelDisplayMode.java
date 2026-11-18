package com.vrcdevicecheck.osc.data;

import android.content.Context;

import com.vrcdevicecheck.osc.R;

/**
 * 上报的「设备型号」用哪一种写法。
 *
 * REAL_MODEL   —— 设备真实型号，例如 HUAWEI HED-AL00（取自 ro.product.model / Build.MODEL）
 * PRODUCT_NAME —— 设备产品名称，例如 HUAWEI Pura 80（取自设备表 series，读不到时退回真实型号）
 */
public enum ModelDisplayMode {

    REAL_MODEL("real", R.string.model_mode_real),
    PRODUCT_NAME("product", R.string.model_mode_product);

    public final String key;
    /** 界面显示用的文案资源 */
    public final int labelRes;

    ModelDisplayMode(String key, int labelRes) {
        this.key = key;
        this.labelRes = labelRes;
    }

    public String label(Context context) {
        return context == null ? "" : context.getString(labelRes);
    }

    public static ModelDisplayMode fromKey(String key) {
        for (ModelDisplayMode mode : values()) {
            if (mode.key.equals(key)) return mode;
        }
        // 默认沿用老版本行为：有产品名就用产品名
        return PRODUCT_NAME;
    }
}
