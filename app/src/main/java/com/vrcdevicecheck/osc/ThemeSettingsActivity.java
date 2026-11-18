package com.vrcdevicecheck.osc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.theme.Theme;
import com.vrcdevicecheck.osc.theme.ThemeManager;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 主题页面，分两块：
 *
 * 1) 内置配色：默认 + 若干套现成配色，只换背景/卡片/文字四个颜色，点了立刻生效；
 * 2) 导入主题：zip / tar.gz 包，能带底图和按钮图，适合做复杂外观。
 *
 * 两块共用一个列表，中间用分组标题隔开。切换后自动重启应用。
 */
public class ThemeSettingsActivity extends Activity {

    private static final int REQUEST_CODE_THEME = 61;

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_DEFAULT = 1;
    private static final int TYPE_BUILTIN = 2;
    private static final int TYPE_IMPORTED = 3;
    private static final int TYPE_EMPTY = 4;

    /** 列表里的一行：标题行、默认项、内置配色、导入主题、空提示 */
    private static final class Row {
        final int type;
        final String title;
        final Theme theme;

        Row(int type, String title, Theme theme) {
            this.type = type;
            this.title = title;
            this.theme = theme;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private ThemeAdapter adapter;
    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("主题");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(12);
        root.setPadding(padding, padding, padding, padding);

        TextView hint = new TextView(this);
        hint.setText("内置配色点一下就能用，只换颜色。\n"
                + "想要底图、按钮素材这类复杂外观，用下面的「导入主题」，"
                + "主题包支持 .zip 与 .tar.gz，里面需要有 theme.json 和图片。\n"
                + "切换后会自动重启应用。");
        hint.setTextSize(12f);
        hint.setTextColor(getColor(R.color.text_secondary));
        hint.setLineSpacing(dp(2), 1f);
        root.addView(hint);

        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        Button importButton = new Button(this);
        importButton.setText("导入主题（zip / tar.gz）");
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickThemeFile();
            }
        });
        root.addView(importButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        adapter = new ThemeAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                applyRow(rows.get(position));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                return confirmDelete(rows.get(position));
            }
        });

        refresh();
    }

    private void refresh() {
        rows.clear();
        String currentId = ThemeManager.currentId(this);

        rows.add(new Row(TYPE_HEADER, "默认配色", null));
        rows.add(new Row(TYPE_DEFAULT, "默认（应用自带外观）", null));
        for (Theme theme : ThemeManager.builtIns()) {
            rows.add(new Row(TYPE_BUILTIN, theme.name, theme));
        }

        List<Theme> imported = ThemeManager.installed(this);
        rows.add(new Row(TYPE_HEADER, "已导入的主题", null));
        if (imported.isEmpty()) {
            rows.add(new Row(TYPE_EMPTY, "还没有导入过主题", null));
        } else {
            for (Theme theme : imported) {
                rows.add(new Row(TYPE_IMPORTED, theme.name, theme));
            }
        }

        adapter.notifyDataSetChanged();
        // 列表行是在布局时才创建的，紧跟着 apply 一次才能把新行也刷上主题色
        list.post(new Runnable() {
            @Override
            public void run() {
                ThemeManager.apply(ThemeSettingsActivity.this);
            }
        });
    }

    private void applyRow(Row row) {
        switch (row.type) {
            case TYPE_DEFAULT:
                ThemeManager.setCurrent(this, "");
                ThemeManager.restart(this);
                break;
            case TYPE_BUILTIN:
            case TYPE_IMPORTED:
                ThemeManager.setCurrent(this, row.theme.id);
                ThemeManager.restart(this);
                break;
            default:
                // 分组标题和空提示不响应
                break;
        }
    }

    private boolean confirmDelete(Row row) {
        if (row.type == TYPE_DEFAULT || row.type == TYPE_BUILTIN) {
            toast("内置配色不能删除");
            return true;
        }
        if (row.type != TYPE_IMPORTED) return false;

        final Theme theme = row.theme;
        new AlertDialog.Builder(this)
                .setTitle("删除主题")
                .setMessage("确定删除主题「" + theme.name + "」吗？")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ThemeManager.delete(ThemeSettingsActivity.this, theme.id);
                        refresh();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        return true;
    }

    private void pickThemeFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "application/zip", "application/x-zip-compressed",
                    "application/gzip", "application/x-gzip",
                    "application/x-tar", "application/octet-stream"});
            startActivityForResult(Intent.createChooser(intent, "选择主题包"), REQUEST_CODE_THEME);
        } catch (Throwable t) {
            toast("没有找到可用的文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_THEME) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        installTheme(data.getData());
    }

    private void installTheme(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Theme installed = null;
                String error = null;
                InputStream in = null;
                try {
                    in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new Exception("无法读取文件");
                    installed = ThemeManager.install(ThemeSettingsActivity.this, in);
                } catch (Throwable t) {
                    error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Throwable ignored) {
                            // 忽略
                        }
                    }
                }

                final Theme result = installed;
                final String failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null) {
                            toast("导入失败：" + (failure == null ? "未知错误" : failure));
                            return;
                        }
                        toast("已导入主题：" + result.name);
                        refresh();
                    }
                });
            }
        }, "vrcdi-theme-import").start();
    }

    // ---------------- 列表 ----------------

    private final class ThemeAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type == TYPE_HEADER ? 0 : 1;
        }

        @Override
        public boolean isEnabled(int position) {
            int type = rows.get(position).type;
            return type != TYPE_HEADER && type != TYPE_EMPTY;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Row row = rows.get(position);
            if (row.type == TYPE_HEADER) {
                return buildHeader(row.title);
            }
            return buildItem(row);
        }

        private View buildHeader(String title) {
            TextView header = new TextView(ThemeSettingsActivity.this);
            header.setText(title);
            header.setTextSize(12f);
            header.setTextColor(getColor(R.color.text_secondary));
            header.setPadding(dp(2), dp(14), dp(2), dp(6));
            return header;
        }

        private View buildItem(Row row) {
            LinearLayout view = new LinearLayout(ThemeSettingsActivity.this);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(android.view.Gravity.CENTER_VERTICAL);
            view.setPadding(dp(2), dp(12), dp(2), dp(12));

            View swatch = new View(ThemeSettingsActivity.this);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            swatchParams.rightMargin = dp(12);
            swatch.setBackground(swatchFor(row));
            view.addView(swatch, swatchParams);

            TextView name = new TextView(ThemeSettingsActivity.this);
            name.setTextSize(15f);
            if (row.type == TYPE_EMPTY) {
                name.setTextColor(getColor(R.color.text_secondary));
            }
            name.setText(row.title);
            view.addView(name, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            if (isCurrent(row)) {
                TextView badge = new TextView(ThemeSettingsActivity.this);
                badge.setText("当前");
                badge.setTextSize(12f);
                badge.setTextColor(getColor(R.color.accent));
                view.addView(badge);
            }
            return view;
        }

        private GradientDrawable swatchFor(Row row) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            int color;
            if (row.type == TYPE_DEFAULT) {
                color = getColor(R.color.bg);
            } else if (row.theme != null && row.theme.background != 0) {
                color = row.theme.background;
            } else {
                color = getColor(R.color.card_bg);
            }
            drawable.setColor(color);
            drawable.setStroke(dp(1), getColor(R.color.divider));
            return drawable;
        }

        private boolean isCurrent(Row row) {
            String currentId = ThemeManager.currentId(ThemeSettingsActivity.this);
            if (row.type == TYPE_DEFAULT) return currentId.isEmpty();
            return row.theme != null && row.theme.id.equals(currentId);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
