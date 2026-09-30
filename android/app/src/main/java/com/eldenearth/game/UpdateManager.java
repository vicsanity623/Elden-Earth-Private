package com.eldenearth.game;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

import java.io.File;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String VERSION_DOC_PATH = "app_config/latest_version";

    public interface UpdateCallback {
        void onUpdateAvailable(String latestVersion, String downloadUrl, boolean required);
        void onNoUpdate();
        void onError(String error);
    }

    public static void checkForUpdate(Context context, int currentVersionCode, UpdateCallback callback) {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("app_config").document("latest_version")
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Long latestCode = doc.getLong("versionCode");
                            String latestVersion = doc.getString("versionName");
                            String downloadUrl = doc.getString("downloadUrl");
                            Boolean required = doc.getBoolean("required");

                            if (latestCode != null && latestVersion != null && downloadUrl != null) {
                                if (latestCode > currentVersionCode) {
                                    callback.onUpdateAvailable(latestVersion, downloadUrl,
                                            required != null && required);
                                } else {
                                    callback.onNoUpdate();
                                }
                            } else {
                                callback.onNoUpdate();
                            }
                        } else {
                            callback.onNoUpdate();
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Update check failed", e);
                        callback.onError(e.getMessage());
                    });
        } catch (Exception e) {
            Log.w(TAG, "Update check error", e);
            callback.onError(e.getMessage());
        }
    }

    public static void downloadAndInstall(Context context, String downloadUrl) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("Elden Earth Update");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "elden-earth-update.apk");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                request.setRequiresCharging(false);
                request.setAllowedOverMetered(true);
                request.setAllowedOverRoaming(true);
            }

            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) {
                long downloadId = dm.enqueue(request);

                BroadcastReceiver onComplete = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context ctx, Intent intent) {
                        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                        if (id == downloadId) {
                            installApk(ctx, downloadId);
                            ctx.unregisterReceiver(this);
                        }
                    }
                };

                IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(onComplete, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    context.registerReceiver(onComplete, filter);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Download error", e);
        }
    }

    private static void installApk(Context context, long downloadId) {
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) return;

            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor cursor = dm.query(query);

            if (cursor != null && cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);

                if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                    String uriString = cursor.getString(uriIndex);
                    Uri fileUri = Uri.parse(uriString);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        File apkFile = new File(fileUri.getPath());
                        Uri contentUri = FileProvider.getUriForFile(context,
                                context.getPackageName() + ".fileprovider", apkFile);

                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                        installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive");
                        installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(installIntent);
                    } else {
                        Intent installIntent = new Intent(Intent.ACTION_VIEW);
                        installIntent.setDataAndType(fileUri, "application/vnd.android.package-archive");
                        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(installIntent);
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Install error", e);
        }
    }
}
