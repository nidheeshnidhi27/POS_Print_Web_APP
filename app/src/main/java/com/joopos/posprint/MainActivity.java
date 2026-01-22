package com.joopos.posprint;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.JsResult;
import android.webkit.JsPromptResult;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.SslErrorHandler;
import android.os.Message;
import android.webkit.ConsoleMessage;
import android.net.http.SslError;
import java.net.URLEncoder;
import android.view.View;
import android.view.WindowManager;
import java.util.HashMap;
import java.util.Map;
import androidx.appcompat.app.AlertDialog;
import android.webkit.CookieManager;

import com.joopos.posprint.R;
import com.google.android.material.snackbar.Snackbar;

import com.joopos.posprint.notification.NotificationHelper;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private WebView webView;
    private android.widget.EditText urlInput;
    private android.widget.Button goButton;
    private View urlBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestNotificationPermission();
        NotificationHelper.createChannel(this);

        setupWebView();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101
                );
            }
        }
    }

    private void setupWebView() {
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        goButton = findViewById(R.id.goButton);
        urlBar = findViewById(R.id.urlBar);
        WebView.setWebContentsDebuggingEnabled(true);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setSupportZoom(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cm.setAcceptThirdPartyCookies(webView, true);
        }
        String baseUA = settings.getUserAgentString();
        settings.setUserAgentString(baseUA + " Android");
        webView.setKeepScreenOn(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    urlBar.setVisibility(View.VISIBLE);
                    urlBar.bringToFront();
                    String failing = request.getUrl() != null ? request.getUrl().toString() : "";
                    urlInput.setText(failing);
                    urlInput.requestFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(urlInput, 0);
                    Snackbar.make(urlBar, "Unable to load. Please re-enter URL.", Snackbar.LENGTH_LONG).show();
                }
            }
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                urlBar.setVisibility(View.VISIBLE);
                urlBar.bringToFront();
                urlInput.setText(failingUrl != null ? failingUrl : "");
                urlInput.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(urlInput, 0);
                Snackbar.make(urlBar, "Unable to load. Please re-enter URL.", Snackbar.LENGTH_LONG).show();
            }
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request != null && request.isForMainFrame()) {
                    urlBar.setVisibility(View.VISIBLE);
                    urlBar.bringToFront();
                    String failing = request.getUrl() != null ? request.getUrl().toString() : "";
                    urlInput.setText(failing);
                    urlInput.requestFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(urlInput, 0);
                    Snackbar.make(urlBar, "Page error. Please re-enter URL.", Snackbar.LENGTH_LONG).show();
                }
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                urlBar.setVisibility(View.VISIBLE);
                urlBar.bringToFront();
                urlInput.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(urlInput, 0);
                Snackbar.make(urlBar, "SSL error. Please check and re-enter URL.", Snackbar.LENGTH_LONG).show();
            }
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                String notifPolyfillEarly =
                        "(function(){try{var R=function(){try{if(window.AndroidBell&&AndroidBell.ring){AndroidBell.ring();}}catch(e){}};if(typeof window.Notification==='undefined'){var N=function(t,o){R();};N.permission='granted';N.requestPermission=function(cb){var r='granted';if(cb)cb(r);return Promise.resolve(r);};window.Notification=N;}else{var _N=window.Notification;window.Notification=function(t,o){R();return new _N(t,o)};window.Notification.requestPermission=function(cb){return _N.requestPermission(cb)}}}catch(e){}})();";
                view.evaluateJavascript(notifPolyfillEarly, null);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return handleDeepLinkUri(uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleDeepLinkUri(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String notifPolyfill =
                        "(function(){try{var R=function(){try{if(window.AndroidBell&&AndroidBell.ring){AndroidBell.ring();}}catch(e){}};if(typeof window.Notification==='undefined'){var N=function(t,o){R();};N.permission='granted';N.requestPermission=function(cb){var r='granted';if(cb)cb(r);return Promise.resolve(r);};window.Notification=N;}else{var _N=window.Notification;window.Notification=function(t,o){R();return new _N(t,o)};window.Notification.requestPermission=function(cb){return _N.requestPermission(cb)}}}catch(e){}})();";
                view.evaluateJavascript(notifPolyfill, null);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView newWebView = new WebView(view.getContext());
                WebSettings s = newWebView.getSettings();
                s.setJavaScriptEnabled(true);
                s.setDomStorageEnabled(true);
                s.setUserAgentString(webView.getSettings().getUserAgentString());
                newWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView vw, WebResourceRequest req) {
                        Uri u = req.getUrl();
                        if (handleDeepLinkUri(u)) return true;
                        webView.loadUrl(u.toString());
                        return true;
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView vw, String u) {
                        Uri uri = Uri.parse(u);
                        if (handleDeepLinkUri(uri)) return true;
                        webView.loadUrl(u);
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                b.setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                b.setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setNegativeButton("Cancel", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                input.setText(defaultValue);
                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                b.setMessage(message)
                        .setView(input)
                        .setPositiveButton("OK", (d, w) -> result.confirm(String.valueOf(input.getText())))
                        .setNegativeButton("Cancel", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                b.setMessage(message)
                        .setPositiveButton("Leave", (d, w) -> result.confirm())
                        .setNegativeButton("Stay", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("JSConsole", consoleMessage.message() + " @" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber());
                return true;
            }
        });

        String saved = getSharedPreferences("web_prefs", MODE_PRIVATE).getString("web_url", "");
        if (saved == null || saved.trim().isEmpty()) {
            urlBar.setVisibility(View.VISIBLE);
            urlBar.bringToFront();
            urlInput.setText("https://");
        } else {
            urlBar.setVisibility(View.GONE);
            Map<String,String> headers = new HashMap<>();
            headers.put("User-Agent", webView.getSettings().getUserAgentString());
            webView.loadUrl(saved, headers);
        }
        webView.addJavascriptInterface(new AndroidBell(this), "AndroidBell");

        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                String u = String.valueOf(urlInput.getText()).trim();
                if (u.isEmpty()) return true;
                if (!u.startsWith("http://") && !u.startsWith("https://")) {
                    u = "https://" + u;
                }
                getSharedPreferences("web_prefs", MODE_PRIVATE).edit().putString("web_url", u).apply();
                Map<String,String> headers = new HashMap<>();
                headers.put("User-Agent", webView.getSettings().getUserAgentString());
                webView.loadUrl(u, headers);
                urlBar.setVisibility(View.GONE);
                urlInput.clearFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        goButton.setOnClickListener(v -> {
            String u = String.valueOf(urlInput.getText()).trim();
            if (u.isEmpty()) return;
            if (!u.startsWith("http://") && !u.startsWith("https://")) {
                u = "https://" + u;
            }
            getSharedPreferences("web_prefs", MODE_PRIVATE).edit().putString("web_url", u).apply();
            Map<String,String> headers = new HashMap<>();
            headers.put("User-Agent", webView.getSettings().getUserAgentString());
            webView.loadUrl(u, headers);
            urlBar.setVisibility(View.GONE);
            urlInput.clearFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        });
    }

    private void handleDeepLink(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            handleDeepLinkUri(data);
        }
    }

    private boolean handleDeepLinkUri(Uri uri) {
        if (uri == null) return false;
        Log.d(TAG, "Deep link: " + uri);

        String scheme = uri.getScheme();
        if (scheme == null) return false;

        if (scheme.equalsIgnoreCase("intent")) {
            try {
                Intent parsed = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME);
                Uri dataUri = parsed.getData();
                if (dataUri != null) {
                    Intent svc = new Intent(this, BackgroundPrintService.class);
                    svc.setData(dataUri);
                    startService(svc);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }

        if (scheme.equalsIgnoreCase("app")) {
            Intent svc = new Intent(this, BackgroundPrintService.class);
            svc.setData(uri);
            startService(svc);
            return true;
        }
        // Allow javascript: and other non-http schemes to proceed normally
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            return false;
        }

        String url = uri.toString();
        if (isPrintUrl(url)) {
            try {
                String encoded = URLEncoder.encode(url, "UTF-8");
                Uri deep = Uri.parse("app://open.my.app?base_url=" + encoded);
                Intent svc = new Intent(this, BackgroundPrintService.class);
                svc.setData(deep);
                startService(svc);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    private boolean isPrintUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return u.contains("invoice_print")
                || u.contains("reprint_kot")
                || u.contains("online_kot")
                || u.contains("online_invoice")
                || u.contains("equal_split_payable_invoice")
                || u.contains("print_today_petty_cash")
                || u.contains("daily_summary_report")
                || u.contains("online_report")
                || u.contains("offline_report")
                || u.contains("booking_report")
                || u.contains("mainsaway")
                || u.contains("print_");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }
}
