package com.example.posprint;

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
import android.os.Message;
import java.net.URLEncoder;
import android.view.View;
import java.util.HashMap;
import java.util.Map;

import com.example.posprint.notification.NotificationHelper;

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
        webView = findViewById(R.id.webView);
        urlInput = findViewById(R.id.urlInput);
        goButton = findViewById(R.id.goButton);
        urlBar = findViewById(R.id.urlBar);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportZoom(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        String baseUA = settings.getUserAgentString();
        settings.setUserAgentString(baseUA + " Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return handleDeepLinkUri(uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleDeepLinkUri(Uri.parse(url));
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
                        return handleDeepLinkUri(req.getUrl());
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView vw, String u) {
                        return handleDeepLinkUri(Uri.parse(u));
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }
        });

        String saved = getSharedPreferences("web_prefs", MODE_PRIVATE).getString("web_url", "");
        if (saved == null || saved.trim().isEmpty()) {
            urlBar.setVisibility(View.VISIBLE);
            urlBar.bringToFront();
            urlInput.setText("https://allinonepos.joopos.com/login");
        } else {
            urlBar.setVisibility(View.GONE);
            Map<String,String> headers = new HashMap<>();
            headers.put("User-Agent", webView.getSettings().getUserAgentString());
            webView.loadUrl(saved, headers);
        }

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

        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            Intent svc = new Intent(this, BackgroundPrintService.class);
            svc.setData(uri);
            startService(svc);
            return true;
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
