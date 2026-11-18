package com.vrcdevicecheck.osc;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.device.AppNames;
import com.vrcdevicecheck.osc.theme.ThemeManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 应用表设置：把包名映射成应用名，用于「前台应用」的显示。
 *
 * JSON 格式与设备表不同，是"包名 -> 名字"的映射：
 * {
 *   "apps": {
 *     "com.tencent.mm": "微信",
 *     "com.tencent.mobileqq": "QQ"
 *   }
 * }
 * 也可以直接省略 "apps" 这一层。导入后写入 files/app_names.json，覆盖内置同名条目。
 */
public class AppTableSettingsActivity extends BaseActivity {

    private static final int REQUEST_CODE_APP_TABLE = 52;

    private TextView countView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_table_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(14);
        root.setPadding(padding, padding, padding, padding);

        TextView hint = new TextView(this);
        hint.setTextSize(13f);
        hint.setText(R.string.app_table_hint);
        root.addView(hint);

        countView = new TextView(this);
        countView.setTextSize(15f);
        countView.setPadding(0, dp(14), 0, dp(14));
        root.addView(countView);

        Button importButton = new Button(this);
        importButton.setText(R.string.app_table_import);
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFile();
            }
        });
        root.addView(importButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView format = new TextView(this);
        format.setTextSize(12f);
        format.setPadding(0, dp(14), 0, 0);
        format.setText(R.string.app_table_format);
        root.addView(format);

        setContentView(root);
        ThemeManager.apply(this);
        refresh();
    }

    private void refresh() {
        countView.setText(getString(R.string.app_table_count, AppNames.count(this)));
    }

    private void pickFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/json", "text/plain", "text/*", "application/octet-stream"});
            startActivityForResult(Intent.createChooser(intent, getString(R.string.app_table_pick_title)),
                    REQUEST_CODE_APP_TABLE);
        } catch (Throwable t) {
            toast(getString(R.string.common_no_file_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_APP_TABLE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        importTable(data.getData());
    }

    private void importTable(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text = null;
                try {
                    text = readText(uri);
                } catch (Throwable ignored) {
                    // 读不到按失败处理
                }
                final int count = text == null ? -1 : AppNames.importJson(AppTableSettingsActivity.this, text);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (count < 0) {
                            toast(getString(R.string.app_table_import_failed));
                            return;
                        }
                        toast(getString(R.string.app_table_imported, count));
                        refresh();
                    }
                });
            }
        }, "vrcdi-app-table").start();
    }

    private String readText(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
                // 忽略
            }
        }
    }

    private String displayName(Uri uri) {
        try {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0 && cursor.moveToFirst()) {
                        String name = cursor.getString(index);
                        if (name != null && !name.isEmpty()) return name;
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        return getString(R.string.common_selected_file);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
