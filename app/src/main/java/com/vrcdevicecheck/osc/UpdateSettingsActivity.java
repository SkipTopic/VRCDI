package com.vrcdevicecheck.osc;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.vrcdevicecheck.osc.data.AppSettings;
import com.vrcdevicecheck.osc.theme.ThemeManager;
import com.vrcdevicecheck.osc.update.UpdateChecker;
import com.vrcdevicecheck.osc.update.UpdateManager;

import java.io.File;

/**
 * 软件更新设置。
 *
 * 检测原理：
 *   1. 通过 GitHub / Gitee 的「最新发行版」API 拿到 tag_name 与附件列表；
 *   2. 把 tag 里的版本号（如 2.0.0-Omega -> 2.0.0）与当前 versionName 比较，
 *      远端更大才认为有更新；
 *   3. 选附件（优先 .apk，其次 .apk.zip / .zip），用自建 UA 直接下载到
 *      cache/update/VRCDI-update.apk；
 *   4. 通过自带的 ApkProvider 以 content:// 交给系统安装器。
 */
public class UpdateSettingsActivity extends BaseActivity {

    private AppSettings settings;
    private TextView statusView;
    private TextView detailView;
    private Button checkButton;
    private Button installButton;
    private RadioGroup sourceGroup;

    private UpdateChecker.Release latest;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.update_title);
        settings = new AppSettings(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(14);
        root.setPadding(padding, padding, padding, padding);
        scroll.addView(root);

        TextView current = new TextView(this);
        current.setTextSize(15f);
        current.setText(getString(R.string.update_current, versionName(), versionCode()));
        root.addView(current);

        CheckBox autoCheck = new CheckBox(this);
        autoCheck.setText(R.string.update_auto_check);
        autoCheck.setTextSize(14f);
        autoCheck.setPadding(0, dp(10), 0, 0);
        autoCheck.setChecked(settings.isAutoUpdateCheck());
        autoCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                settings.setAutoUpdateCheck(isChecked);
            }
        });
        root.addView(autoCheck);

        TextView sourceLabel = new TextView(this);
        sourceLabel.setText(R.string.update_source_label);
        sourceLabel.setTextSize(14f);
        sourceLabel.setPadding(0, dp(18), 0, dp(6));
        root.addView(sourceLabel);

        sourceGroup = new RadioGroup(this);
        sourceGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton gitee = new RadioButton(this);
        gitee.setText(R.string.update_source_gitee);
        gitee.setId(1001);
        RadioButton github = new RadioButton(this);
        github.setText(R.string.update_source_github);
        github.setId(1002);
        sourceGroup.addView(gitee);
        sourceGroup.addView(github);
        if (UpdateChecker.SOURCE_GITHUB.equals(settings.getUpdateSource())) {
            sourceGroup.check(1002);
        } else {
            sourceGroup.check(1001);
        }
        sourceGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                settings.setUpdateSource(checkedId == 1002
                        ? UpdateChecker.SOURCE_GITHUB : UpdateChecker.SOURCE_GITEE);
            }
        });
        root.addView(sourceGroup);

        statusView = new TextView(this);
        statusView.setTextSize(14f);
        statusView.setPadding(0, dp(14), 0, dp(6));
        statusView.setText(R.string.update_not_checked);
        root.addView(statusView);

        detailView = new TextView(this);
        detailView.setTextSize(12f);
        detailView.setPadding(0, 0, 0, dp(10));
        root.addView(detailView);

        checkButton = new Button(this);
        checkButton.setText(R.string.update_check_button);
        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkForUpdate();
            }
        });
        root.addView(checkButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        installButton = new Button(this);
        installButton.setText(R.string.update_install_button);
        installButton.setEnabled(false);
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadAndInstall();
            }
        });
        root.addView(installButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = new TextView(this);
        note.setTextSize(12f);
        note.setPadding(0, dp(16), 0, 0);
        note.setText(R.string.update_note);
        root.addView(note);

        setContentView(scroll);
        ThemeManager.apply(this);
    }

    private void checkForUpdate() {
        if (busy) return;
        busy = true;
        checkButton.setEnabled(false);
        installButton.setEnabled(false);
        statusView.setText(R.string.update_checking);
        detailView.setText("");

        final String source = settings.getUpdateSource();
        new Thread(new Runnable() {
            @Override
            public void run() {
                UpdateChecker.Release release = null;
                String error = null;
                try {
                    release = UpdateChecker.fetchLatest(
                            getApplicationContext(), source);
                } catch (Throwable t) {
                    error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                }
                final UpdateChecker.Release result = release;
                final String failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        checkButton.setEnabled(true);
                        if (result == null) {
                            statusView.setText(getString(R.string.update_check_failed, failure));
                            return;
                        }
                        latest = result;
                        detailView.setText(result.name.isEmpty()
                                ? getString(R.string.update_latest_release, result.tag)
                                : getString(R.string.update_latest_release_named,
                                        result.tag, result.name));
                        if (UpdateChecker.isNewer(result.tag, versionName())) {
                            statusView.setText(getString(R.string.update_found, result.tag));
                            installButton.setEnabled(result.apkAsset() != null);
                            if (result.apkAsset() == null) {
                                statusView.setText(R.string.update_found_no_apk);
                            }
                            showReleaseNotes(result);
                        } else {
                            statusView.setText(R.string.update_is_latest);
                            installButton.setEnabled(false);
                        }
                    }
                });
            }
        }, "vrcdi-update-check").start();
    }

    private void showReleaseNotes(UpdateChecker.Release release) {
        if (release.body.trim().isEmpty()) return;
        String message = release.body;
        if (needsSignatureMigration(release)) {
            message = message + "\n\n———\n"
                    + "注意：当前版本与目标版本的签名不一致（旧版为 debug 签名，或 Beta 与正式版签名不同）。"
                    + "安装前需要先卸载 VRCDI，否则系统会提示安装失败。";
        }
        final String text = message;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_notes_title, release.tag))
                .setMessage(text)
                .setPositiveButton(R.string.update_got_it, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    /**
     * 需要卸载重装的情况：
     *   - 目标正式版用发布私钥签名，而当前是 4.x 之前的 debug 签名包；
     *   - 当前是 4.x Beta（debug 签名），目标是正式版（私钥签名）。
     * 注意：2.x/3.x 装 4.x Beta 都是 debug 签名，可以直接覆盖，不需要卸载。
     */
    private boolean needsSignatureMigration(UpdateChecker.Release release) {
        if (release == null || release.prerelease) return false;
        String current = versionName();
        int major = UpdateChecker.majorVersion(current);
        boolean legacyDebug = major >= 0 && major < 4;
        boolean beta = current.toLowerCase(java.util.Locale.US).contains("beta");
        return legacyDebug || beta;
    }

    private void downloadAndInstall() {
        if (busy || latest == null) return;
        final UpdateChecker.Asset asset = latest.apkAsset();
        if (asset == null) {
            toast(getString(R.string.update_no_apk));
            return;
        }
        if (needsSignatureMigration(latest)) {
            new AlertDialog.Builder(this)
                    .setTitle("安装正式版前需要卸载")
                    .setMessage("当前版本与目标版本的签名不一致，直接覆盖会安装失败。\n\n"
                            + "请先卸载 VRCDI，再安装新的 APK。\n"
                            + "卸载会清除应用数据：已导入的设备表 / 应用表 / CPU 映射表 / 主题需要重新导入。\n\n"
                            + "是否现在下载新的安装包？")
                    .setPositiveButton("下载", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            startDownload(asset);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        startDownload(asset);
    }

    private void startDownload(final UpdateChecker.Asset asset) {
        busy = true;
        checkButton.setEnabled(false);
        installButton.setEnabled(false);
        statusView.setText(getString(R.string.update_downloading, 0));

        new Thread(new Runnable() {
            @Override
            public void run() {
                File apk = null;
                String error = null;
                try {
                    apk = UpdateManager.download(UpdateSettingsActivity.this,
                            asset, new UpdateManager.Progress() {
                                @Override
                                public void onProgress(final int percent) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            statusView.setText(getString(
                                                    R.string.update_downloading, percent));
                                        }
                                    });
                                }
                            });
                } catch (Throwable t) {
                    error = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                }

                final File file = apk;
                final String failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        checkButton.setEnabled(true);
                        if (file == null) {
                            statusView.setText(getString(R.string.update_download_failed, failure));
                            installButton.setEnabled(true);
                            return;
                        }
                        statusView.setText(R.string.update_downloaded);
                        try {
                            UpdateManager.install(UpdateSettingsActivity.this, file);
                        } catch (Throwable t) {
                            statusView.setText(getString(R.string.update_install_failed,
                                    t.getMessage() == null
                                            ? t.getClass().getSimpleName() : t.getMessage()));
                            installButton.setEnabled(true);
                        }
                    }
                });
            }
        }, "vrcdi-update-download").start();
    }

    private String versionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName;
        } catch (Throwable t) {
            return "4.4.4 Beta";
        }
    }

    private int versionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionCode;
        } catch (Throwable t) {
            return 0;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
