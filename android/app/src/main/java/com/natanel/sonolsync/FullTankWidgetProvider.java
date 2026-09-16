package com.natanel.sonolsync;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;

public class FullTankWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "FullTankWidget";

    public static final String ACTION_PLUS_10 = "com.natanel.sonolsync.ACTION_PLUS_10";
    public static final String ACTION_PLUS_25 = "com.natanel.sonolsync.ACTION_PLUS_25";
    public static final String ACTION_REFUEL_FULL = "com.natanel.sonolsync.ACTION_REFUEL_FULL";
    public static final String ACTION_REFRESH_WIDGET = "com.natanel.sonolsync.ACTION_REFRESH_WIDGET";

    private static final String PRIMARY_STATS_URL = AppConfig.getStatsUrl();
    private static final String PRIMARY_UPDATE_URL = AppConfig.getUpdateKmUrl();

    // Cached values
    private static double sFuelLiters = 46.5;
    private static double sFuelPercent = 93.0;
    private static int sLatestKm = 162089;
    private static int sEstimatedRange = 662;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
        fetchAndUpdateAsync(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case ACTION_PLUS_10:
                sLatestKm += 10;
                sendKmUpdateAsync(context, sLatestKm, false);
                break;
            case ACTION_PLUS_25:
                sLatestKm += 25;
                sendKmUpdateAsync(context, sLatestKm, false);
                break;
            case ACTION_REFUEL_FULL:
                sFuelLiters = 50.0;
                sFuelPercent = 100.0;
                sendKmUpdateAsync(context, sLatestKm, true);
                break;
            case ACTION_REFRESH_WIDGET:
                fetchAndUpdateAsync(context);
                break;
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_fulltank);
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);

        // Update Text Views
        String gauge = get9BarsGauge(sFuelPercent);
        views.setTextViewText(R.id.widget_fuel_gauge, gauge);
        views.setTextViewText(R.id.widget_fuel_text, String.format(Locale.US, "%.1fL (%.0f%%)", sFuelLiters, sFuelPercent));
        views.setTextViewText(R.id.widget_range_text, "🛣️ טווח: ~" + sEstimatedRange + " ק\"מ");
        views.setTextViewText(R.id.widget_odo_text, "🚗 " + nf.format(sLatestKm) + " ק\"מ");

        // Click to open main app
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPi = PendingIntent.getActivity(context, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_title, openAppPi);

        // Button +10 KM
        Intent plus10Intent = new Intent(context, FullTankWidgetProvider.class);
        plus10Intent.setAction(ACTION_PLUS_10);
        PendingIntent p10Pi = PendingIntent.getBroadcast(context, 10, plus10Intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_plus10, p10Pi);

        // Button +25 KM
        Intent plus25Intent = new Intent(context, FullTankWidgetProvider.class);
        plus25Intent.setAction(ACTION_PLUS_25);
        PendingIntent p25Pi = PendingIntent.getBroadcast(context, 25, plus25Intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_plus25, p25Pi);

        // Button Refuel Full
        Intent refuelIntent = new Intent(context, FullTankWidgetProvider.class);
        refuelIntent.setAction(ACTION_REFUEL_FULL);
        PendingIntent refuelPi = PendingIntent.getBroadcast(context, 100, refuelIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_btn_refuel, refuelPi);

        // Button Refresh
        Intent refreshIntent = new Intent(context, FullTankWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH_WIDGET);
        PendingIntent refreshPi = PendingIntent.getBroadcast(context, 1, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh_btn, refreshPi);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static String get9BarsGauge(double percent) {
        int filled = (int) Math.round((percent / 100.0) * 9.0);
        filled = Math.max(0, Math.min(9, filled));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filled; i++) sb.append("🟩");
        for (int i = filled; i < 9; i++) sb.append("⬜");
        return sb.toString();
    }

    private static void refreshAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, FullTankWidgetProvider.class));
        for (int id : ids) {
            updateAppWidget(context, manager, id);
        }
    }

    private void fetchAndUpdateAsync(Context context) {
        new Thread(() -> {
            try {
                URL url = new URL(PRIMARY_STATS_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    if ("success".equals(json.optString("status"))) {
                        sFuelLiters = json.optDouble("current_fuel_liters", sFuelLiters);
                        sFuelPercent = json.optDouble("fuel_percent", sFuelPercent);
                        sLatestKm = json.optInt("latest_km", sLatestKm);
                        sEstimatedRange = json.optInt("estimated_remaining_range", sEstimatedRange);
                        refreshAllWidgets(context);
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void sendKmUpdateAsync(Context context, int newKm, boolean isFullTank) {
        refreshAllWidgets(context);
        new Thread(() -> {
            try {
                URL url = new URL(PRIMARY_UPDATE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);

                JSONObject payload = new JSONObject();
                payload.put("km", String.valueOf(newKm));
                payload.put("is_full_tank", isFullTank);
                payload.put("source", "ווידג'ט מסך הבית");

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("utf-8"));
                os.flush();
                os.close();

                if (conn.getResponseCode() == 200) {
                    fetchAndUpdateAsync(context);
                }
            } catch (Exception ignored) {}
        }).start();
    }
}
