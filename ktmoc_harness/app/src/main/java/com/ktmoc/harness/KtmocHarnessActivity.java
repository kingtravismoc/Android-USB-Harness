// Copyright 2024 KTMOC Project
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ktmoc.harness;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * KTMOC CRT v4.0+ Native Android Harness
 * F-Droid compatible WebUSB debugging tool with AI integration
 */
public class KtmocHarnessActivity extends AppCompatActivity {
    private static final String TAG = "KtmocHarness";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int USB_PERMISSION_CODE = 1002;

    private WebView webView;
    private ProgressBar progressBar;
    private UsbManager usbManager;
    private UsbDeviceConnection usbConnection;
    private SharedPreferences prefs;
    private ExecutorService executor;
    private Handler mainHandler;
    private OkHttpClient httpClient;

    // AI Service configuration
    private String aiServiceName = "";
    private String aiModel = "";
    private String aiApiKey = "";
    private boolean useHuggingFaceNoKey = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Keep screen on for debugging sessions
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Initialize components
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newFixedThreadPool(4);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        
        prefs = getSharedPreferences("ktmoc_prefs", Context.MODE_PRIVATE);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        // Load saved AI configuration
        aiServiceName = prefs.getString("ai_service", "huggingface");
        aiModel = prefs.getString("ai_model", "microsoft/Phi-3-mini-4k-instruct");
        aiApiKey = prefs.getString("ai_api_key", "");
        useHuggingFaceNoKey = prefs.getBoolean("hf_nokey", true);
        
        setupUI();
        setupWebView();
        requestPermissions();
    }

    private void setupUI() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        
        progressBar = new ProgressBar(this);
        progressBar.setVisibility(View.GONE);
        
        webView = new WebView(this);
        
        layout.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT, 1.0f));
        layout.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        
        setContentView(layout);
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDatabaseEnabled(true);
        settings.setAppCacheEnabled(true);
        
        // Enable WebUSB through JavaScript interface
        webView.addJavascriptInterface(new WebUsbInterface(), "AndroidUsb");
        webView.addJavascriptInterface(new AiInterface(), "AiEngine");
        webView.addJavascriptInterface(new StorageInterface(), "KBankStorage");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Allow i2pd eepsite URLs
                if (url.startsWith("http://") || url.startsWith("https://") || 
                    url.startsWith("file://") || url.contains(".i2p")) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
            
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                injectKtmocScript();
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            }
            
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                Log.d(TAG, "Permission request: " + request.getResources());
                // Auto-grant permissions for WebUSB and media
                if (request.getResources() != null) {
                    request.grant(request.getResources());
                }
            }
            
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, 
                    GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
            
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "Console: " + consoleMessage.message() + " at line " + 
                        consoleMessage.lineNumber());
                return true;
            }
            
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                // Handle file selection for uploads
                return super.onShowFileChooser(webView, filePathCallback, fileChooserParams);
            }
        });
        
        // Load the KTMOC interface - can be from assets or URL
        String ktmocUrl = prefs.getString("ktmoc_url", "file:///android_asset/ktmoc.html");
        webView.loadUrl(ktmocUrl);
    }

    private void injectKtmocScript() {
        // Inject native bridge scripts
        String script = "if (typeof window.KtmocNativeBridge === 'undefined') { " +
                "window.KtmocNativeBridge = { " +
                "isNative: true, " +
                "platform: 'android', " +
                "version: '4.0+' " +
                "}; " +
                "console.log('KTMOC Native Bridge injected'); " +
                "}";
        webView.evaluateJavascript(script, null);
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
            
            boolean needRequest = false;
            for (String perm : permissions) {
                if (ContextCompat.checkSelfPermission(this, perm) 
                        != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }
            
            if (needRequest) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            Toast.makeText(this, "Permissions granted for debugging harness", 
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // WebUSB JavaScript Interface
    @JavascriptInterface
    public class WebUsbInterface {
        @JavascriptInterface
        public String getDevices() {
            try {
                HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                JSONArray devices = new JSONArray();
                
                for (UsbDevice device : deviceList.values()) {
                    JSONObject devObj = new JSONObject();
                    devObj.put("vendorId", device.getVendorId());
                    devObj.put("productId", device.getProductId());
                    devObj.put("deviceName", device.getDeviceName());
                    devObj.put("manufacturerName", device.getManufacturerName());
                    devObj.put("productName", device.getProductName());
                    devObj.put("serialNumber", device.getSerialNumber());
                    devices.put(devObj);
                }
                
                return devices.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error getting USB devices", e);
                return "[]";
            }
        }
        
        @JavascriptInterface
        public boolean requestDevice() {
            mainHandler.post(() -> {
                HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
                if (deviceList.isEmpty()) {
                    Toast.makeText(KtmocHarnessActivity.this, 
                            "No USB devices found", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Show device selection dialog
                String[] deviceNames = new String[deviceList.size()];
                final UsbDevice[] devices = deviceList.values().toArray(new UsbDevice[0]);
                
                for (int i = 0; i < devices.length; i++) {
                    deviceNames[i] = devices[i].getProductName() + 
                            " (VID: " + devices[i].getVendorId() + 
                            ", PID: " + devices[i].getProductId() + ")";
                }
                
                new AlertDialog.Builder(KtmocHarnessActivity.this)
                        .setTitle("Select USB Device")
                        .setItems(deviceNames, (dialog, which) -> {
                            UsbDevice selectedDevice = devices[which];
                            Intent intent = new Intent("android.hardware.usb.action.USB_DEVICE_ATTACHED");
                            intent.setPackage(getPackageName());
                            PendingIntent permissionIntent = PendingIntent.getActivity(
                                    KtmocHarnessActivity.this, 0, intent,
                                    PendingIntent.FLAG_IMMUTABLE);
                            usbManager.requestPermission(selectedDevice, permissionIntent);
                            
                            // Store connection reference
                            try {
                                usbConnection = usbManager.openDevice(selectedDevice);
                                Toast.makeText(KtmocHarnessActivity.this, 
                                        "Connected to: " + selectedDevice.getProductName(), 
                                        Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(KtmocHarnessActivity.this, 
                                        "Connection failed: " + e.getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            return true;
        }
        
        @JavascriptInterface
        public String sendControlTransfer(int requestType, int request, int value, 
                int index, byte[] data) {
            if (usbConnection == null) {
                return "{\"error\": \"No USB connection\"}";
            }
            
            try {
                int result = usbConnection.controlTransfer(
                        requestType, request, value, index, 
                        data, data != null ? data.length : 0, 1000);
                
                JSONObject response = new JSONObject();
                response.put("result", result);
                response.put("success", result >= 0);
                return response.toString();
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }
        
        @JavascriptInterface
        public String bulkTransfer(int endpoint, byte[] data, int length) {
            if (usbConnection == null) {
                return "{\"error\": \"No USB connection\"}";
            }
            
            try {
                byte[] buffer = new byte[length];
                int result;
                
                if ((endpoint & 0x80) == 0x80) {
                    // IN transfer
                    result = usbConnection.bulkTransfer(endpoint, buffer, length, 1000);
                    String encodedData = Base64.encodeToString(buffer, 0, result, Base64.NO_WRAP);
                    
                    JSONObject response = new JSONObject();
                    response.put("result", result);
                    response.put("data", encodedData);
                    return response.toString();
                } else {
                    // OUT transfer
                    result = usbConnection.bulkTransfer(endpoint, data, length, 1000);
                    
                    JSONObject response = new JSONObject();
                    response.put("result", result);
                    response.put("success", result >= 0);
                    return response.toString();
                }
            } catch (Exception e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }
    }

    // AI Engine JavaScript Interface
    @JavascriptInterface
    public class AiInterface {
        @JavascriptInterface
        public String configureService(String serviceName, String model, String apiKey, 
                boolean noKeyMode) {
            prefs.edit()
                    .putString("ai_service", serviceName)
                    .putString("ai_model", model)
                    .putString("ai_api_key", apiKey)
                    .putBoolean("hf_nokey", noKeyMode)
                    .apply();
            
            aiServiceName = serviceName;
            aiModel = model;
            aiApiKey = apiKey;
            useHuggingFaceNoKey = noKeyMode;
            
            return "{\"success\": true, \"service\": \"" + serviceName + "\"}";
        }
        
        @JavascriptInterface
        public String getServiceConfig() {
            try {
                JSONObject config = new JSONObject();
                config.put("service", aiServiceName);
                config.put("model", aiModel);
                config.put("hasApiKey", !aiApiKey.isEmpty());
                config.put("noKeyMode", useHuggingFaceNoKey);
                return config.toString();
            } catch (JSONException e) {
                return "{\"error\": \"" + e.getMessage() + "\"}";
            }
        }
        
        @JavascriptInterface
        public String fetchDocumentation(String serviceUrl) {
            executor.execute(() -> {
                try {
                    Request request = new Request.Builder()
                            .url(serviceUrl)
                            .build();
                    
                    Response response = httpClient.newCall(request).execute();
                    String docContent = response.body() != null ? response.body().string() : "";
                    
                    // Parse and extract API documentation
                    String extractedDoc = extractApiDocs(docContent);
                    
                    mainHandler.post(() -> {
                        String js = "window.onDocumentationLoaded && window.onDocumentationLoaded(" +
                                "'" + extractedDoc.replace("'", "\\'") + "')";
                        webView.evaluateJavascript(js, null);
                    });
                } catch (IOException e) {
                    Log.e(TAG, "Error fetching documentation", e);
                }
            });
            return "{\"status\": \"fetching\"}";
        }
        
        @JavascriptInterface
        public String generateShim(String apiSpec, String targetFramework) {
            // Generate integration shim code based on API specification
            try {
                JSONObject spec = new JSONObject(apiSpec);
                StringBuilder shim = new StringBuilder();
                
                shim.append("// Auto-generated shim for ").append(spec.optString("name")).append("\n");
                shim.append("// Target: ").append(targetFramework).append("\n\n");
                shim.append("class GeneratedShim {\n");
                shim.append("  constructor(config) {\n");
                shim.append("    this.config = config;\n");
                shim.append("    this.endpoint = config.endpoint || '';\n");
                shim.append("  }\n\n");
                
                // Generate methods from API spec
                JSONArray endpoints = spec.optJSONArray("endpoints");
                if (endpoints != null) {
                    for (int i = 0; i < endpoints.length(); i++) {
                        JSONObject endpoint = endpoints.getJSONObject(i);
                        String method = endpoint.optString("method", "GET").toLowerCase();
                        String path = endpoint.optString("path", "/");
                        String name = endpoint.optString("name", "request");
                        
                        shim.append("  async ").append(name).append("(params) {\n");
                        shim.append("    const response = await fetch(this.endpoint + '").append(path).append("', {\n");
                        shim.append("      method: '").append(endpoint.optString("method", "GET")).append("',\n");
                        shim.append("      headers: { 'Content-Type': 'application/json' },\n");
                        shim.append("      body: JSON.stringify(params)\n");
                        shim.append("    });\n");
                        shim.append("    return response.json();\n");
                        shim.append("  }\n\n");
                    }
                }
                
                shim.append("}\n");
                
                return shim.toString();
            } catch (JSONException e) {
                return "// Error generating shim: " + e.getMessage();
            }
        }
        
        @JavascriptInterface
        public String inferWithAi(String prompt, String imageDataBase64) {
            executor.execute(() -> {
                try {
                    String responseText;
                    
                    if (useHuggingFaceNoKey || aiServiceName.equals("huggingface")) {
                        // Use Hugging Face Inference API (no key required for some models)
                        responseText = callHuggingFaceInference(prompt, imageDataBase64);
                    } else if (!aiApiKey.isEmpty()) {
                        // Use configured API key
                        responseText = callAiServiceWithKey(prompt, imageDataBase64);
                    } else {
                        // Fallback to local processing
                        responseText = "AI service not configured. Please set up Hugging Face or provide API key.";
                    }
                    
                    String finalResponse = responseText;
                    mainHandler.post(() -> {
                        String js = "window.onAiResponse && window.onAiResponse('" + 
                                finalResponse.replace("'", "\\'").replace("\n", "\\n") + "')";
                        webView.evaluateJavascript(js, null);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "AI inference error", e);
                    mainHandler.post(() -> {
                        String js = "window.onAiError && window.onAiError('" + 
                                e.getMessage().replace("'", "\\'") + "')";
                        webView.evaluateJavascript(js, null);
                    });
                }
            });
            return "{\"status\": \"processing\"}";
        }
        
        private String callHuggingFaceInference(String prompt, String imageDataBase64) throws IOException {
            String apiUrl = "https://api-inference.huggingface.co/models/" + aiModel;
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("inputs", prompt);
            
            if (imageDataBase64 != null && !imageDataBase64.isEmpty()) {
                // For vision models
                JSONObject imageInput = new JSONObject();
                imageInput.put("image", imageDataBase64);
                requestBody.put("image", imageInput);
            }
            
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    requestBody.toString());
            
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            Response response = httpClient.newCall(request).execute();
            
            if (response.body() != null) {
                return response.body().string();
            }
            return "No response from AI service";
        }
        
        private String callAiServiceWithKey(String prompt, String imageDataBase64) throws IOException {
            // Generic AI service caller with API key
            String apiUrl = "https://api." + aiServiceName + ".com/v1/chat/completions";
            
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", aiModel);
            
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);
            
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 2048);
            requestBody.put("temperature", 0.7);
            
            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json"),
                    requestBody.toString());
            
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + aiApiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            Response response = httpClient.newCall(request).execute();
            
            if (response.body() != null) {
                return response.body().string();
            }
            return "No response from AI service";
        }
        
        private String extractApiDocs(String htmlContent) {
            // Simple extraction of API documentation from HTML
            // In production, use a proper HTML parser like Jsoup
            StringBuilder docs = new StringBuilder();
            
            // Extract code blocks, endpoints, etc.
            int start = htmlContent.indexOf("<code");
            while (start != -1) {
                int end = htmlContent.indexOf("</code>", start);
                if (end != -1) {
                    String codeBlock = htmlContent.substring(start + 10, end);
                    docs.append(codeBlock).append("\n");
                    start = htmlContent.indexOf("<code", end);
                } else {
                    break;
                }
            }
            
            return docs.length() > 0 ? docs.toString() : "No API documentation found";
        }
    }

    // K-Bank Storage JavaScript Interface
    @JavascriptInterface
    public class StorageInterface {
        @JavascriptInterface
        public String getData(String key) {
            return prefs.getString(key, "");
        }
        
        @JavascriptInterface
        public boolean setData(String key, String value) {
            prefs.edit().putString(key, value).apply();
            return true;
        }
        
        @JavascriptInterface
        public String getAllData() {
            try {
                JSONObject allData = new JSONObject();
                Map<String, ?> allEntries = prefs.getAll();
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    allData.put(entry.getKey(), entry.getValue().toString());
                }
                return allData.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }
        
        @JavascriptInterface
        public boolean clearData() {
            prefs.edit().clear().apply();
            return true;
        }
        
        @JavascriptInterface
        public String exportData() {
            try {
                JSONObject export = new JSONObject();
                export.put("timestamp", System.currentTimeMillis());
                export.put("data", new JSONObject(prefs.getAll()));
                return export.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
        if (usbConnection != null) {
            usbConnection.close();
        }
        webView.destroy();
    }
}
