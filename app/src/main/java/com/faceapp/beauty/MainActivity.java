package com.faceapp.beauty;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.CookieManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/**
 * 纯前端 WebView 壳:
 * 用 shouldInterceptRequest 把本地 assets 以 https://appsapp.local 起源正确 MIME 提供,
 * 使 ES Module(import)、wasm、fetch 都能以本地资源运行, MediaPipe 可离线工作。
 */
public class MainActivity extends Activity {

    private static final String HOST = "appsapp.local";
    private static final String BASE = "https://" + HOST + "/";
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER = 1986;
    private static final int REQ_CAMERA = 101;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // 允许 wasm/worker 需要的特性
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptCookie(true);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override
            @SuppressLint("ObsoleteSdkInt")
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serve(request.getUrl());
            }

            @Override
            @SuppressLint("ObsoleteSdkInt")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return serve(Uri.parse(url));
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // 只放行摄像头/麦克风(WebView getUserMedia 需要), 授予资源受限更安全
                java.util.List<String> ok = new java.util.ArrayList<>();
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)
                            || PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) {
                        ok.add(r);
                    }
                }
                if (!ok.isEmpty()) request.grant(ok.toArray(new String[0]));
                else request.deny();
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                // 权限被取消: 静默处理, 页面会自行提示
            }

            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> filePath,
                                             FileChooserParams params) {
                filePathCallback = filePath;
                Intent intent = params.createIntent();
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择照片"), FILE_CHOOSER);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new JS(this), "NativeBridge");

        // 加载本地网页
        web.loadUrl(BASE + "index.html");
        WebView.setWebContentsDebuggingEnabled(true);
    }

    // ---------------- 摄像头运行时权限 ----------------
    @Override
    protected void onStart() {
        super.onStart();
        ensureCameraPermission();
    }

    private void ensureCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.CAMERA }, REQ_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] perms, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, perms, grantResults);
        if (requestCode == REQ_CAMERA && web != null) {
            // 无论结果如何都刷新页面, 让页面重新执行 getUserMedia
            web.postDelayed(() -> {
                if (web != null) web.reload();
            }, 500);
        }
    }

    // ---------------- 资源服务 ----------------
    private WebResourceResponse serve(Uri uri) {
        if (!HOST.equals(uri.getHost())) return null;
        String path = uri.getPath(); // 如 /mp/vision_bundle.mjs
        if (path == null || path.length() <= 1) return null;
        // 去根
        String rel = path.substring(1);

        InputStream is = null;
        try {
            is = getAssets().open(rel);
        } catch (IOException e) {
            // 某些资源(如 img 目录)也可能在源码未打包, 补一批常见
            if (!rel.startsWith("img/") && !rel.startsWith("models/")) {
                // 忽略, 交给回退
            }
            if (is == null) return null;
        }
        if (is == null) return null;

        String mime = mimeFor(rel);
        return new WebResourceResponse(mime, "utf-8", is);
    }

    private static String mimeFor(String rel) {
        int i = rel.lastIndexOf('.');
        if (i < 0) return "application/octet-stream";
        String ext = rel.substring(i + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "html": case "htm": return "text/html";
            case "js": return "text/javascript";
            case "mjs": return "text/javascript";
            case "css": return "text/css";
            case "wasm": return "application/wasm";
            case "json": return "application/json";
            case "task": case "onnx": case "bin": return "application/octet-stream";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "svg": return "image/svg+xml";
            case "ttf": return "font/ttf";
            case "woff": return "font/woff";
            case "woff2": return "font/woff2";
            case "txt": return "text/plain";
            case "mp4": case "webm": return "video/mp4";
            default: return "application/octet-stream";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER) {
            if (filePathCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        if (n > 0) {
                            result = new Uri[1];
                            result[0] = data.getClipData().getItemAt(0).getUri();
                        }
                    } else if (data.getData() != null) {
                        result = new Uri[]{ data.getData() };
                    }
                    if (result == null) result = new Uri[]{ null };
                }
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    /** JS 桥(可留空或供页面探测) */
    public static class JS {
        private final MainActivity act;
        JS(MainActivity a) { this.act = a; }
        @JavascriptInterface
        public String version() { return Build.VERSION.RELEASE; }
    }
}