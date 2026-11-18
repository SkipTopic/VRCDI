package com.vrcdevicecheck.osc;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.device.DeviceModelTables;
import com.vrcdevicecheck.osc.theme.ThemeManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备表设置（二级菜单）。
 *
 * 页面内容：
 *   设备表设置
 *   品牌设备表（默认空列表）
 *   [按品牌创建设备表]
 *
 * 创建设备表时填品牌名称 + 选一个 JSON 文件，确认后文件会被原样复制到
 * files/device_tables/&lt;品牌&gt;.json，之后应用只从自己的目录读取。
 */
public class DeviceTableSettingsActivity extends BaseActivity {

    private static final int REQUEST_CODE_TABLE_FILE = 51;

    private ListView listView;
    private TextView emptyView;
    private ArrayAdapter<String> adapter;
    private final List<DeviceModelTables.BrandTableInfo> tables = new ArrayList<>();

    private Uri pendingUri;
    private TextView pendingFileLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.device_table_title);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        root.setPadding(padding, padding, padding, padding);

        TextView section = new TextView(this);
        section.setText(R.string.device_table_section);
        section.setTextSize(15f);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(section);

        emptyView = new TextView(this);
        emptyView.setText(R.string.device_table_empty);
        emptyView.setTextSize(13f);
        emptyView.setPadding(0, dp(12), 0, 0);
        root.addView(emptyView);

        listView = new ListView(this);
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        listView.setEmptyView(emptyView);

        Button create = new Button(this);
        create.setText(R.string.device_table_create);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateDialog();
            }
        });
        root.addView(create, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        ThemeManager.apply(this);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        listView.setAdapter(adapter);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                confirmDelete(position);
                return true;
            }
        });

        refresh();
    }

    private void refresh() {
        tables.clear();
        tables.addAll(DeviceModelTables.listBrandTables(this));

        List<String> lines = new ArrayList<>();
        for (DeviceModelTables.BrandTableInfo info : tables) {
            lines.add(info.count >= 0
                    ? getString(R.string.device_table_count, info.brand, info.count)
                    : getString(R.string.device_table_unparsable, info.brand));
        }
        adapter.clear();
        adapter.addAll(lines);
        adapter.notifyDataSetChanged();
    }

    private void confirmDelete(final int position) {
        if (position < 0 || position >= tables.size()) return;
        final DeviceModelTables.BrandTableInfo info = tables.get(position);
        new AlertDialog.Builder(this)
                .setTitle(R.string.device_table_delete_title)
                .setMessage(getString(R.string.device_table_delete_message, info.brand))
                .setPositiveButton(R.string.common_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        DeviceModelTables.deleteBrand(DeviceTableSettingsActivity.this, info.brand);
                        refresh();
                    }
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void showCreateDialog() {
        pendingUri = null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        box.setPadding(padding, dp(8), padding, 0);

        final EditText brandInput = new EditText(this);
        brandInput.setHint(R.string.device_table_brand_hint);
        box.addView(brandInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        pendingFileLabel = new TextView(this);
        pendingFileLabel.setText(R.string.device_table_file_none);
        pendingFileLabel.setTextSize(12f);
        pendingFileLabel.setPadding(0, dp(8), 0, 0);
        box.addView(pendingFileLabel);

        Button pick = new Button(this);
        pick.setText(R.string.device_table_pick);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTableFile();
            }
        });
        box.addView(pick, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.device_table_create)
                .setView(box)
                .setPositiveButton(R.string.common_confirm, null)
                .setNegativeButton(R.string.common_cancel, null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String brand = brandInput.getText().toString().trim();
                        if (brand.isEmpty()) {
                            toast(getString(R.string.device_table_need_brand));
                            return;
                        }
                        if (pendingUri == null) {
                            toast(getString(R.string.device_table_need_file));
                            return;
                        }
                        dialog.dismiss();
                        importTable(brand, pendingUri);
                    }
                });
            }
        });
        dialog.show();
    }

    private void pickTableFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"application/json", "text/plain", "text/*", "application/octet-stream"});
            startActivityForResult(Intent.createChooser(intent, getString(R.string.device_table_pick_title)),
                    REQUEST_CODE_TABLE_FILE);
        } catch (Throwable t) {
            toast(getString(R.string.common_no_file_picker));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_TABLE_FILE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        pendingUri = data.getData();
        if (pendingFileLabel != null) {
            pendingFileLabel.setText(getString(R.string.device_table_file_selected, displayName(pendingUri)));
        }
    }

    private void importTable(final String brand, final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text = null;
                try {
                    text = readText(uri);
                } catch (Throwable ignored) {
                    // 读不到按失败处理
                }
                final DeviceModelTables.ImportedBrand imported = text == null
                        ? null
                        : DeviceModelTables.importBrand(DeviceTableSettingsActivity.this, brand, text);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (imported == null) {
                            toast(getString(R.string.device_table_import_failed));
                            return;
                        }
                        toast(getString(R.string.device_table_created, imported.brand, imported.count));
                        refresh();
                    }
                });
            }
        }, "vrcdi-table-import").start();
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
            // 拿不到就用路径末段
        }
        String path = uri.getLastPathSegment();
        return path == null ? getString(R.string.common_selected_file) : path;
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
