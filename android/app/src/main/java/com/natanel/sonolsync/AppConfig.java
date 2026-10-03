package com.natanel.sonolsync;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppConfig {
    private static final String PREFS_NAME = "fulltank_app_prefs";
    private static final String KEY_SERVER_URL = "custom_server_url";
    private static final String KEY_SECRET_TOKEN = "custom_secret_token";
    private static final String KEY_SPREADSHEET_URL = "custom_spreadsheet_url";

    public static final String DEFAULT_SERVER_URL = BuildConfig.SERVER_URL;
    public static final String DEFAULT_SECRET_TOKEN = BuildConfig.SECRET_TOKEN;
    public static final String DEFAULT_SPREADSHEET_URL = BuildConfig.SPREADSHEET_URL;

    public static String getServerUrl(Context context) {
        if (context == null) return DEFAULT_SERVER_URL;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public static void setServerUrl(Context context, String url) {
        if (context == null || url == null) return;
        String cleanUrl = url.trim();
        if (cleanUrl.endsWith("/")) cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SERVER_URL, cleanUrl)
                .apply();
    }

    public static String getSecretToken(Context context) {
        if (context == null) return DEFAULT_SECRET_TOKEN;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SECRET_TOKEN, DEFAULT_SECRET_TOKEN);
    }

    public static void setSecretToken(Context context, String token) {
        if (context == null || token == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SECRET_TOKEN, token.trim())
                .apply();
    }

    public static String getSpreadsheetUrl(Context context) {
        if (context == null) return DEFAULT_SPREADSHEET_URL;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_SPREADSHEET_URL, DEFAULT_SPREADSHEET_URL);
    }

    public static void setSpreadsheetUrl(Context context, String url) {
        if (context == null || url == null) return;
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SPREADSHEET_URL, url.trim())
                .apply();
    }

    public static boolean hasConfiguredServer(Context context) {
        String server = getServerUrl(context);
        return server != null && !server.trim().isEmpty();
    }

    // Dynamic URL builders taking context
    public static String getDashboardUrl(Context context) {
        String server = getServerUrl(context);
        if (server == null || server.trim().isEmpty()) {
            return "file:///android_asset/dashboard.html";
        }
        return server + "/dashboard";
    }

    public static String getStatsUrl(Context context) {
        return getServerUrl(context) + "/api/stats?token=" + getSecretToken(context);
    }

    public static String getUpdateKmUrl(Context context) {
        return getServerUrl(context) + "/api/update-km?token=" + getSecretToken(context);
    }

    public static String getWebhookUrl(Context context) {
        return getServerUrl(context) + "/api/webhook?token=" + getSecretToken(context);
    }

    // Static fallbacks for places without Context
    public static String getDashboardUrl() {
        if (DEFAULT_SERVER_URL == null || DEFAULT_SERVER_URL.trim().isEmpty()) {
            return "file:///android_asset/dashboard.html";
        }
        return DEFAULT_SERVER_URL + "/dashboard";
    }

    public static String getStatsUrl() {
        return DEFAULT_SERVER_URL + "/api/stats?token=" + DEFAULT_SECRET_TOKEN;
    }

    public static String getUpdateKmUrl() {
        return DEFAULT_SERVER_URL + "/api/update-km?token=" + DEFAULT_SECRET_TOKEN;
    }

    public static String getWebhookUrl() {
        return DEFAULT_SERVER_URL + "/api/webhook?token=" + DEFAULT_SECRET_TOKEN;
    }
}
