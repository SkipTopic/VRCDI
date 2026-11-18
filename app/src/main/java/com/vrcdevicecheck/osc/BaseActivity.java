package com.vrcdevicecheck.osc;

import android.app.Activity;
import android.content.Context;

import com.vrcdevicecheck.osc.util.LocaleHelper;

/**
 * 所有界面的基类，只做一件事：在挂载前把 locale 换成用户选的语言，
 * 这样本页里的 getString()、getResources().getStringArray() 都会用对应语言。
 *
 * 保持继承 android.app.Activity（不引入 AndroidX）。
 */
public abstract class BaseActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }
}
