package com.eldenearth.game;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

public class BootActivity extends AppCompatActivity {

    private TextView logoText;
    private TextView versionText;
    private TextView statusText;
    private TextView compileLog;
    private ProgressBar progressBar;
    private View bootContainer;

    private String buildVersion = "";
    private int buildVersionCode = 0;
    private boolean updateAvailable = false;
    private String updateVersion = "";
    private String updateUrl = "";

    private final String[] BOOT_STAGES = {
        "Initializing game engine...",
        "Loading core modules...",
        "Compiling shader pipeline...",
        "Generating world seed...",
        "Loading territory data...",
        "Syncing player profile...",
        "Verifying asset integrity...",
        "Preparing realm map...",
        "Calibrating passive income...",
        "Loading rarity tables...",
        "Syncing leaderboard data...",
        "Finalizing boot sequence..."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        setContentView(R.layout.activity_boot);

        logoText = findViewById(R.id.boot_logo);
        versionText = findViewById(R.id.boot_version);
        statusText = findViewById(R.id.boot_status);
        compileLog = findViewById(R.id.boot_compile_log);
        progressBar = findViewById(R.id.boot_progress);
        bootContainer = findViewById(R.id.boot_container);

        buildVersion = getVersionName();
        buildVersionCode = getVersionCode();
        versionText.setText("BUILD " + buildVersion);

        startBootSequence();
    }

    private void startBootSequence() {
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(logoText, "alpha", 0f, 1f);
        fadeIn.setDuration(800);
        fadeIn.start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            runBootStages(0);
        }, 1000);
    }

    private void runBootStages(int stageIndex) {
        if (stageIndex >= BOOT_STAGES.length) {
            onBootComplete();
            return;
        }

        statusText.setText(BOOT_STAGES[stageIndex]);

        compileLog.append("> " + BOOT_STAGES[stageIndex] + "\n");
        compileLog.post(() -> {
            int scrollAmount = compileLog.getLayout() != null
                    ? compileLog.getLayout().getLineTop(compileLog.getLineCount()) - compileLog.getHeight()
                    : 0;
            if (scrollAmount > 0) compileLog.scrollTo(0, scrollAmount);
        });

        int targetProgress = (int) (((float) (stageIndex + 1) / BOOT_STAGES.length) * 100);
        int stageDuration = 150 + (int)(Math.random() * 200);

        ObjectAnimator progressAnim = ObjectAnimator.ofInt(progressBar, "progress",
                progressBar.getProgress(), targetProgress);
        progressAnim.setDuration(stageDuration);
        progressAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        progressAnim.start();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            runBootStages(stageIndex + 1);
        }, stageDuration);
    }

    private void onBootComplete() {
        statusText.setText("Boot complete. Launching game...");

        compileLog.append("\n> ✓ ELDEN EARTH v" + buildVersion + " READY\n");
        compileLog.append("> © 2026 Elden Earth Studios\n");

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            checkForUpdatesAndLaunch();
        }, 600);
    }

    private void checkForUpdatesAndLaunch() {
        UpdateManager.checkForUpdate(this, buildVersionCode, new UpdateManager.UpdateCallback() {
            @Override
            public void onUpdateAvailable(String latestVersion, String downloadUrl, boolean required) {
                updateAvailable = true;
                updateVersion = latestVersion;
                updateUrl = downloadUrl;

                runOnUiThread(() -> {
                    statusText.setText("Update v" + latestVersion + " available");
                    compileLog.append("\n> UPDATE: v" + latestVersion + " ready\n");
                });

                launchGame();
            }

            @Override
            public void onNoUpdate() {
                launchGame();
            }

            @Override
            public void onError(String error) {
                launchGame();
            }
        });
    }

    private void launchGame() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(BootActivity.this, MainActivity.class);
            intent.putExtra("buildVersion", buildVersion);
            intent.putExtra("buildVersionCode", buildVersionCode);
            intent.putExtra("updateAvailable", updateAvailable);
            intent.putExtra("updateVersion", updateVersion);
            intent.putExtra("updateUrl", updateUrl);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 400);
    }

    private String getVersionName() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0.0";
        }
    }

    private int getVersionCode() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }
}
