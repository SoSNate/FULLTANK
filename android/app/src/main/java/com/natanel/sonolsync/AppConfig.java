package com.natanel.sonolsync;

public final class AppConfig {
    // Base server URL (configured via build config or default)
    public static final String DEFAULT_SERVER_URL = BuildConfig.SERVER_URL;
    public static final String DEFAULT_SECRET_TOKEN = BuildConfig.SECRET_TOKEN;
    public static final String DEFAULT_SPREADSHEET_URL = BuildConfig.SPREADSHEET_URL;

    public static String getDashboardUrl() {
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
