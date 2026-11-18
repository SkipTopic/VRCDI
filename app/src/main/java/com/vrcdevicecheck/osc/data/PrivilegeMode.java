package com.vrcdevicecheck.osc.data;

import android.content.Context;

import com.vrcdevicecheck.osc.R;

/** 权限申请方式 */
public enum PrivilegeMode {

    SHIZUKU("shizuku", R.string.privilege_shizuku),
    ROOT("root", R.string.privilege_root),
    NONE("none", R.string.privilege_none);

    public final String key;
    /** 界面显示用的文案资源 */
    public final int labelRes;

    PrivilegeMode(String key, int labelRes) {
        this.key = key;
        this.labelRes = labelRes;
    }

    public String label(Context context) {
        return context == null ? "" : context.getString(labelRes);
    }

    public static PrivilegeMode fromKey(String key) {
        for (PrivilegeMode mode : values()) {
            if (mode.key.equals(key)) return mode;
        }
        return SHIZUKU;
    }
}
