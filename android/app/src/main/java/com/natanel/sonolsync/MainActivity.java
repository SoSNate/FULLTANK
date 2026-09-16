package com.natanel.sonolsync;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String DASHBOARD_URL = AppConfig.getDashboardUrl();
    private static final String SPREADSHEET_URL = AppConfig.DEFAULT_SPREADSHEET_URL;
    private static final int PERM_REQUEST_CODE = 2026;

    private SwipeRefreshLayout swipeRefresh;
    private WebView webView;
    private boolean triggerKmOnLoad = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        webView = findViewById(R.id.webViewDashboard);

        handleIncomingIntent(getIntent());
        setupWebView();
        checkAndRequestPermissions();

        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                webView.reload();
            }
        });
    }

    private void setupWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setUseWideViewPort(false);
        ws.setLoadWithOverviewMode(false);
        ws.setTextZoom(100);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);

        // Native bridge interface
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.contains("docs.google.com/spreadsheets")) {
                    openNativeSheets(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                swipeRefresh.setRefreshing(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                swipeRefresh.setRefreshing(false);
                if (triggerKmOnLoad) {
                    triggerKmOnLoad = false;
                    view.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            view.evaluateJavascript("if (typeof openOdometerSheet === 'function') { openOdometerSheet(true); }", null);
                        }
                    }, 400);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                swipeRefresh.setRefreshing(false);
            }
        });

        String initialUrl = DASHBOARD_URL;
        if (triggerKmOnLoad) {
            initialUrl += "?triggerKm=true";
        }
        webView.loadUrl(initialUrl);
    }

    private void openNativeSheets(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage("com.google.android.apps.docs.editors.sheets");
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent generic = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            generic.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(generic);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;

        // 1. Deep link license activation (e.g. fulltank://activate?code=...)
        Uri data = intent.getData();
        if (data != null && "fulltank".equalsIgnoreCase(data.getScheme())) {
            String code = data.getQueryParameter("code");
            if (code != null && !code.isEmpty()) {
                LicenseManager.LicenseResult res = LicenseManager.activateLicenseCode(this, code);
                if (res.isValid) {
                    Toast.makeText(this, "🎉 " + res.message + " (" + res.plan + ")", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "❌ " + res.message, Toast.LENGTH_LONG).show();
                }
                return;
            }
        }

        // 2. Trigger KM input from notification
        if (intent.getBooleanExtra("TRIGGER_KM_INPUT", false)) {
            triggerKmOnLoad = true;
            if (webView != null) {
                webView.loadUrl(DASHBOARD_URL + "?triggerKm=true&t=" + System.currentTimeMillis());
                webView.postDelayed(() -> {
                    webView.evaluateJavascript("if (typeof openOdometerSheet === 'function') openOdometerSheet(true);", null);
                }, 600);
            }
        }

        // 3. Credit Card Prompt
        if ("credit_card_prompt".equals(intent.getStringExtra("prompt_mode"))) {
            String station = intent.getStringExtra("station_name");
            String amount = intent.getStringExtra("amount");
            showCreditCardPromptDialog(station != null ? station : "תחנת דלק", amount != null ? amount : "0");
        }
    }

    private void showCreditCardPromptDialog(String station, String amount) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("⛽ זוהה תשלום של " + amount + " ₪ ב-" + station);
        builder.setMessage("האם מילאת דלק כעת?\n\n💡 טיפ לחסכון בזמן: בפעם הבאה בחר במשאבה 'קבלה ב-SMS / אפליקציה' – והמערכת תשאב את הליטרים והתעריף אוטומטית!");

        builder.setPositiveButton("✅ כן, תדלקתי", (dialog, which) -> {
            if (webView != null) {
                webView.loadUrl(DASHBOARD_URL + "?triggerKm=true&creditStation=" + Uri.encode(station) + "&creditAmount=" + amount);
            }
        });

        builder.setNegativeButton("❌ לא, רק חנות", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void checkAndRequestPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!perms.isEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERM_REQUEST_CODE);
        }

        // Check if Notification Listener permission is granted for reading Push receipts
        if (!isNotificationServiceEnabled()) {
            try {
                Intent notifIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                startActivity(notifIntent);
                Toast.makeText(this, "🔔 נא לאשר גישת התראות עבור 'פוול טנק' לקליטת קבלות דלק", Toast.LENGTH_LONG).show();
            } catch (Exception ignored) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                try {
                    Intent overlayIntent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivity(overlayIntent);
                } catch (Exception ignored) {}
            }
        }
    }

    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final android.content.ComponentName cn = android.content.ComponentName.unflattenFromString(name);
                if (cn != null && pkgName.equals(cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // JavaScript Bridge for Haptic and Native Feedback
    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void vibrate() {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(40);
                }
            }
        }

        @JavascriptInterface
        public void openSheets() {
            openNativeSheets(SPREADSHEET_URL);
        }

        @JavascriptInterface
        public String getActiveVehiclePlate() {
            return FuelDatabaseHelper.getInstance(mContext).getActiveVehiclePlate();
        }

        @JavascriptInterface
        public boolean setActiveVehiclePlate(String plate) {
            return FuelDatabaseHelper.getInstance(mContext).setActiveVehiclePlate(plate);
        }

        @JavascriptInterface
        public String getActiveVehicleJson() {
            return FuelDatabaseHelper.getInstance(mContext).getActiveVehicle().toString();
        }

        @JavascriptInterface
        public String getAllVehiclesJson() {
            return FuelDatabaseHelper.getInstance(mContext).getAllVehicles().toString();
        }

        @JavascriptInterface
        public boolean saveVehicle(String plate, String model, double tankCapacity, double avgKml, double cityKml, String testDate, String tires, boolean setActive) {
            return FuelDatabaseHelper.getInstance(mContext).saveOrUpdateVehicle(plate, model, tankCapacity, avgKml, cityKml, testDate, tires, setActive);
        }

        @JavascriptInterface
        public boolean saveInsurance(String plate, String type, String company, String startDate, String endDate, double annualCost, String policyNo, String notes) {
            long id = FuelDatabaseHelper.getInstance(mContext).insertOrUpdateInsurance(plate, type, company, startDate, endDate, annualCost, policyNo, notes);
            return id != -1;
        }

        @JavascriptInterface
        public String getActiveInsuranceJson(String plate) {
            return FuelDatabaseHelper.getInstance(mContext).getActiveInsurance(plate).toString();
        }

        @JavascriptInterface
        public String getAllInsuranceJson(String plate) {
            return FuelDatabaseHelper.getInstance(mContext).getAllInsuranceRecords(plate).toString();
        }

        @JavascriptInterface
        public boolean saveMaintenance(String date, int km, String serviceType, String description, double cost, String garage, int nextKm, String receiptUrl, String partsDetail, String category, String plate) {
            long id = FuelDatabaseHelper.getInstance(mContext).insertMaintenanceRecord(date, km, serviceType, description, cost, garage, nextKm, receiptUrl, partsDetail, category, plate);
            return id != -1;
        }

        @JavascriptInterface
        public String getAllMaintenanceJson(String plate) {
            return FuelDatabaseHelper.getInstance(mContext).getAllMaintenance(plate).toString();
        }

        @JavascriptInterface
        public String getTCOSummaryJson(String plate) {
            return FuelDatabaseHelper.getInstance(mContext).getVehicleTCOSummary(plate).toString();
        }
    }
}
