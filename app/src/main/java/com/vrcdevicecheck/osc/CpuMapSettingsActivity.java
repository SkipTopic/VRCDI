package com.vrcdevicecheck.osc;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.device.CpuNames;
import com.vrcdevicecheck.osc.theme.ThemeManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * CPU 映射表设置：把系统读到的平台代号换成型号名。
 *
 * JSON 格式：
 * {
 *   "cpus": {
 *     "kona": "Snapdragon 865",
 *     "taro": "Snapdragon 8 Gen 1"
 *   }
 * }
 * 也可以省略 "cpus" 这一层。导入后写入 files/cpu_map.json，覆盖内置同名条目。
 */
public class CpuMapSettingsActivity extends BaseActivity {

    private static final int REQUEST_CODE_CPU_MAP = 53;

    private TextView countView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.cpu_map_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(14);
        root.setPadding(padding, padding, padding, padding);

        TextView hint = new TextView(this);
        hint.setTextSize(13f);
        hint.setText(R.string.cpu_map_hint);
        root.addView(hint);

        countView = new TextView(this);
        countView.setTextSize(15f);
        countView.setPadding(0, dp(14), 0, dp(14));
        root.addView(countView);

        Button importButton = new Button(this);
        importButton.setText(R.string.cpu_map_import);
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
        format.setText(R.string.cpu_map_format);
        root.addView(format);

        setContentView(root);
        ThemeManager.apply(this);
        refresh();
    }

    private void refresh() {
        countView.setText(getString(R.string.cpu_map_count, CpuNames.count()));
    }

    private void pickFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/json", "text/plain", "text/*", "application/octet-stream"});
            startActivityForResult(Intent.createChooser(intent, getString(R.string.cpu_map_pick_title)),
                    REQUEST_CODE_CPU_MAP);
        } catch (Throwable t) {
            toast(getString(R.string.common_no_file_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_CPU_MAP) return;
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
                final int count = text == null ? -1 : CpuNames.importJson(CpuMapSettingsActivity.this, text);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (count < 0) {
                            toast(getString(R.string.cpu_map_import_failed));
                            return;
                        }
                        toast(getString(R.string.cpu_map_imported, count));
                        refresh();
                    }
                });
            }
        }, "vrcdi-cpu-map").start();
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
