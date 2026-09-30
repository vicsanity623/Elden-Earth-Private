package com.eldenearth.game;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "EldenEarth";
    private WebView webView;
    private ProgressBar loadingBar;
    private GoogleSignInClient googleSignInClient;
    private FirebaseAuth firebaseAuth;

    private String buildVersion = "";
    private int buildVersionCode = 0;
    private boolean updateAvailable = false;
    private String updateVersion = "";
    private String updateUrl = "";

    private static final String GAME_URL = "https://vicsanity623.github.io/Elden-Earth-v0-1-10-90b/";
    private static final int PERMISSION_REQUEST_CODE = 100;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null && account.getIdToken() != null) {
                            String googleIdToken = account.getIdToken();
                            Toast.makeText(this, "Google account selected, signing in...", Toast.LENGTH_SHORT).show();
                            firebaseAuthWithGoogle(googleIdToken);
                        } else {
                            Toast.makeText(this, "No Google account token returned", Toast.LENGTH_LONG).show();
                        }
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign-in failed: " + e.getStatusCode() + " - " + e.getMessage());
                        Toast.makeText(this, "Google sign-in failed: " + e.getStatusCode(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show();
                }
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.game_webview);
        loadingBar = findViewById(R.id.loading_bar);

        firebaseAuth = FirebaseAuth.getInstance();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        extractIntentData();
        setupWebView();
        setupLocationPermissions();
        loadGame();
    }

    private void firebaseAuthWithGoogle(String googleIdToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(googleIdToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = firebaseAuth.getCurrentUser();
                        if (user != null) {
                            Toast.makeText(this, "Signed in as " + user.getEmail(), Toast.LENGTH_SHORT).show();
                            injectFirebaseAuth(googleIdToken);
                        }
                    } else {
                        Exception e = task.getException();
                        Log.w(TAG, "Firebase auth failed", e);
                        Toast.makeText(this, "Firebase auth failed: " + (e != null ? e.getMessage() : "unknown"), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void injectFirebaseAuth(String googleIdToken) {
        String js = "javascript:(function(){"
                + "try {"
                + "  var auth = firebase.auth();"
                + "  var credential = firebase.auth.GoogleAuthProvider.credential('" + googleIdToken + "');"
                + "  auth.signInWithCredential(credential).then(function(result) {"
                + "    console.log('[EldenEarth] Native Google Sign-In successful: ' + result.user.email);"
                + "    Toast.makeText && console.log('[EldenEarth] Signed in!');"
                + "    if(typeof window.onNativeSignIn === 'function') window.onNativeSignIn(result.user);"
                + "  }).catch(function(err) {"
                + "    console.error('[EldenEarth] Credential sign-in failed:', err);"
                + "  });"
                + "} catch(e) {"
                + "  console.error('[EldenEarth] Auth inject error:', e);"
                + "}"
                + "})()";
        webView.evaluateJavascript(js, null);
        Toast.makeText(this, "Auth token injected. Checking game state...", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private void extractIntentData() {
        Intent intent = getIntent();
        if (intent != null) {
            buildVersion = intent.getStringExtra("buildVersion");
            buildVersionCode = intent.getIntExtra("buildVersionCode", 0);
            updateAvailable = intent.getBooleanExtra("updateAvailable", false);
            updateVersion = intent.getStringExtra("updateVersion");
            updateUrl = intent.getStringExtra("updateUrl");
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        // Enable GPU-accelerated canvas and WebGL
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        String ua = "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + "; " + Build.MODEL
                + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36";
        settings.setUserAgentString(ua);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                loadingBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                loadingBar.setVisibility(View.GONE);
                injectNativeBridge();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    showError("Connection lost. Check your internet and try again.");
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isGoogleAuthUrl(url)) {
                    launchNativeGoogleSignIn();
                    return true;
                }
                if (url.startsWith("intent://") || url.startsWith("market://")) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    loadingBar.setVisibility(View.VISIBLE);
                    loadingBar.setProgress(newProgress);
                } else {
                    loadingBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView newWebView = new WebView(MainActivity.this);
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (isGoogleAuthUrl(url)) {
                            launchNativeGoogleSignIn();
                            return true;
                        }
                        webView.loadUrl(url);
                        return true;
                    }
                });
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
        });

        webView.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return false;
        });
    }

    private boolean isGoogleAuthUrl(String url) {
        return url.contains("accounts.google.com")
                || url.contains("accounts.google.co.")
                || (url.contains("google") && url.contains("o/oauth"));
    }

    private void launchNativeGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void injectNativeBridge() {
        String js = "javascript:(function(){"
                + "window.EldenEarthNative = {"
                + "  isNativeApp: true,"
                + "  platform: 'android',"
                + "  buildVersion: '" + buildVersion + "',"
                + "  buildVersionCode: " + buildVersionCode + ","
                + "  updateAvailable: " + updateAvailable + ","
                + "  updateVersion: '" + (updateVersion != null ? updateVersion : "") + "',"
                + "  requestUpdate: function() { AndroidBridge.requestUpdate(); },"
                + "  getAppVersion: function() { return '" + buildVersion + "'; },"
                + "  vibrate: function(ms) { AndroidBridge.doVibrate(ms || 50); },"
                + "  getDeviceId: function() { return AndroidBridge.getAndroidId(); },"
                + "  isOnline: function() { return navigator.onLine; },"
                + "  openExternal: function(url) { AndroidBridge.openExternal(url); },"
                + "  signInWithGoogle: function() { AndroidBridge.nativeGoogleSignIn(); }"
                + "};"

                // Intercept Google Sign-In button clicks
                + "var observer = new MutationObserver(function() {"
                + "  document.querySelectorAll('[class*=google], [id*=google], [data-provider=google], button').forEach(function(el) {"
                + "    if (el.dataset.nativeBound) return;"
                + "    var text = (el.textContent || '').toLowerCase();"
                + "    if (text.includes('sign in with google') || text.includes('google')) {"
                + "      el.dataset.nativeBound = 'true';"
                + "      el.addEventListener('click', function(e) {"
                + "        e.preventDefault(); e.stopPropagation();"
                + "        AndroidBridge.nativeGoogleSignIn();"
                + "      }, true);"
                + "    }"
                + "  });"
                + "});"
                + "observer.observe(document.body, {childList: true, subtree: true});"

                // Also override Firebase signInWithPopup/Redirect for Google
                + "try {"
                + "  if (typeof firebase !== 'undefined' && firebase.auth) {"
                + "    var origPopup = firebase.auth.prototype.signInWithPopup;"
                + "    firebase.auth.prototype.signInWithPopup = function(provider) {"
                + "      if (provider && provider.providerId === 'google.com') {"
                + "        AndroidBridge.nativeGoogleSignIn();"
                + "        return new Promise(function(resolve, reject) { reject({code:'native-intercept'}); });"
                + "      }"
                + "      return origPopup.call(this, provider);"
                + "    };"
                + "    var origRedirect = firebase.auth.prototype.signInWithRedirect;"
                + "    firebase.auth.prototype.signInWithRedirect = function(provider) {"
                + "      if (provider && provider.providerId === 'google.com') {"
                + "        AndroidBridge.nativeGoogleSignIn();"
                + "        return Promise.resolve();"
                + "      }"
                + "      return origRedirect.call(this, provider);"
                + "    };"
                + "  }"
                + "} catch(e) {}"

                + "if(typeof window.onNativeReady === 'function') window.onNativeReady();"
                + "console.log('[EldenEarth] Native bridge loaded — build " + buildVersion + "');"
                + "})()";
        webView.evaluateJavascript(js, null);
    }

    private void loadGame() {
        webView.loadUrl(GAME_URL);
    }

    private void setupLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            android.Manifest.permission.CAMERA
                    }, PERMISSION_REQUEST_CODE);
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE + 2);
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE + 1);
            }
        }
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private void showError(String msg) {
        new AlertDialog.Builder(this, R.style.EldenDialog)
                .setTitle("Connection Error")
                .setMessage(msg)
                .setPositiveButton("Retry", (d, w) -> loadGame())
                .setNegativeButton("Exit", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            new AlertDialog.Builder(this, R.style.EldenDialog)
                    .setTitle("Exit Game")
                    .setMessage("Return to the realm later, Traveler.")
                    .setPositiveButton("Exit", (d, w) -> finishAffinity())
                    .setNegativeButton("Stay", null)
                    .show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        hideSystemUI();
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUI();
    }

    @JavascriptInterface
    public void nativeGoogleSignIn() {
        runOnUiThread(this::launchNativeGoogleSignIn);
    }

    @JavascriptInterface
    public void requestUpdate() {
        if (updateAvailable && updateUrl != null) {
            runOnUiThread(() -> {
                new AlertDialog.Builder(this, R.style.EldenDialog)
                        .setTitle("Update Available")
                        .setMessage("Version " + updateVersion + " is ready to install.")
                        .setPositiveButton("Update", (d, w) -> UpdateManager.downloadAndInstall(this, updateUrl))
                        .setNegativeButton("Later", null)
                        .show();
            });
        }
    }

    @SuppressWarnings("deprecation")
    @JavascriptInterface
    public void doVibrate(int ms) {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        }
    }

    @JavascriptInterface
    public String getAndroidId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    @JavascriptInterface
    public void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show();
        }
    }
}
