package com.natanel.sonolsync;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FuelNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "FuelNotifListener";
    
    // Server Endpoints
    private static final String PRIMARY_WEBHOOK_URL = AppConfig.getWebhookUrl();

    // Known Fuel Apps Package Names & Identifiers
    private static final List<String> FUEL_APP_PACKAGES = Arrays.asList(
            "com.sonol",
            "com.paz",
            "com.doralon",
            "com.ten.petroleum",
            "com.sadesh",
            "il.co.mika",
            "com.delek.israel",
            "com.pairzon"
    );

    // Known Credit Card & Banking Apps Package Names
    private static final List<String> CREDIT_CARD_PACKAGES = Arrays.asList(
            "com.leumi.leumiwallet",
            "com.bankhapoalim.hapoalimpass",
            "com.isracard",
            "com.cal.calapp",
            "il.co.max.mobile",
            "com.discountbank",
            "com.pepper.bank",
            "com.onezero.bank"
    );

    // SMS & Messaging Apps Package Names
    private static final List<String> SMS_PACKAGES = Arrays.asList(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.xiaomi.channel"
    );

    // Verified Fuel SMS Sender IDs in Israel
    private static final List<String> FUEL_SENDER_IDS = Arrays.asList(
            "sonol", "סונול",
            "paz", "yellow", "פז", "ילו",
            "doralon", "dor alon", "dor-alon", "דור אלון", "דור-אלון",
            "ten", "טן", "10",
            "mika", "מיקה",
            "sadesh", "סדש",
            "delek", "מנטה", "menta", "דלק", "דלק ישראל",
            "pairzon", "ezcount", "דלקן"
    );

    // Regex Patterns for Fuel Station Detection & Digital Receipts
    private static final Pattern FUEL_RECEIPT_URL_PATTERN = Pattern.compile(
            "https?://(?:public\\.|pdf\\.)?(?:pairzon\\.com|d-paz\\.co\\.il|paz\\.co\\.il|yellow\\.co\\.il|sonol\\.co\\.il|doralon\\.co\\.il|dor-alon\\.co\\.il|ten\\.co\\.il|10\\.co\\.il|ezcount\\.co\\.il|meshulam\\.co\\.il|morning\\.co\\.il)/[a-zA-Z0-9/\\?=&_\\.-]+"
    );
    private static final Pattern CONVENIENCE_STORE_IGNORE_PATTERN = Pattern.compile("^(?=.*(סוגוד|מנטה|אלונית|yellow|קפה|סנדוויץ|מאפה|קיוסק))(?!.*(ליטר|משאבה|בנזין|סולר|תדלוק)).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:סך|בסך|סכום|לתשלום|ע\\\"ס|על סך|ש\\\"ח|₪)\\s*([0-9]+(?:\\.[0-9]{1,2})?)|([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:ש\\\"ח|₪|שח)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        String packageName = sbn.getPackageName();
        if (packageName == null) return;

        // 1. Strict Gatekeeper: Listen ONLY to SMS apps. Drop everything else immediately!
        if (!isSmsApp(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) return;

        CharSequence titleCharSeq = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCharSeq = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextCharSeq = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title = titleCharSeq != null ? titleCharSeq.toString() : "";
        String text = textCharSeq != null ? textCharSeq.toString() : "";
        String bigText = bigTextCharSeq != null ? bigTextCharSeq.toString() : "";

        String fullMessage = (title + " " + text + " " + bigText).trim();
        if (fullMessage.isEmpty()) return;

        Log.d(TAG, "SMS Notification received from: " + title + " | Content: " + fullMessage);

        classifyAndProcessSmsNotification(title, fullMessage);
    }

    private void classifyAndProcessSmsNotification(String senderTitle, String message) {
        // Step 1: Check for digital receipt URL from verified fuel receipt domains
        Matcher urlMatcher = FUEL_RECEIPT_URL_PATTERN.matcher(message);
        if (urlMatcher.find()) {
            String receiptUrl = urlMatcher.group(0);
            Log.i(TAG, "Found Verified Fuel Receipt URL: " + receiptUrl);
            dispatchFuelWebhook(receiptUrl, "NOTIFICATION_VERIFIED_URL");
            return;
        }

        // Step 2: If sender is a known fuel company, check if there's any generic URL (excluding privacy/opt-out)
        if (isFuelSender(senderTitle) || isFuelText(message)) {
            Pattern genericUrl = Pattern.compile("https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}/[a-zA-Z0-9/\\?=&_\\.-]+");
            Matcher m = genericUrl.matcher(message);
            while (m.find()) {
                String candidateUrl = m.group(0);
                if (!candidateUrl.contains("tinyurl.com") && !candidateUrl.contains("bit.ly") && !candidateUrl.contains("priv") && !candidateUrl.contains("unsubscribe")) {
                    Log.i(TAG, "Found candidate URL from fuel sender: " + candidateUrl);
                    dispatchFuelWebhook(candidateUrl, "NOTIFICATION_FUEL_SENDER");
                    return;
                }
            }
        }

        // Step 3: Ignore convenience store purchases
        if (CONVENIENCE_STORE_IGNORE_PATTERN.matcher(message).find()) {
            Log.d(TAG, "Ignored convenience store SMS: " + message);
            return;
        }

        // Step 4: Check if this is a credit card charge SMS for fuel
        boolean isFinancialSms = (message.contains("חוייבת") || message.contains("עסקה") || message.contains("כרטיס") || message.contains("חיוב") || message.contains("תשלום") || message.contains("בסך") || message.contains("ע\"ס") || message.contains("גיהוץ"));
        if (isFinancialSms && (isFuelSender(senderTitle) || isFuelText(message))) {
            String amount = extractAmount(message);
            if (!amount.isEmpty()) {
                String station = senderTitle.isEmpty() ? "תחנת דלק" : senderTitle;
                Log.i(TAG, "Detected credit card fuel charge: " + station + " | Amount: " + amount);
                showLocalCreditCardNotification(station, amount);
            }
        }
    }

    private boolean isFuelSender(String sender) {
        if (sender == null || sender.isEmpty()) return false;
        String lower = sender.toLowerCase(java.util.Locale.ROOT);
        for (String id : FUEL_SENDER_IDS) {
            if (lower.contains(id.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean isFuelText(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String id : FUEL_SENDER_IDS) {
            if (lower.contains(id.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return lower.contains("תדלוק") || lower.contains("חשבונית חדשה") || lower.contains("בנזין 95");
    }

    private boolean isFuelApp(String pkg) {
        for (String fuelPkg : FUEL_APP_PACKAGES) {
            if (pkg.contains(fuelPkg)) return true;
        }
        return false;
    }

    private boolean isCreditCardApp(String pkg) {
        for (String ccPkg : CREDIT_CARD_PACKAGES) {
            if (pkg.contains(ccPkg)) return true;
        }
        return false;
    }

    private boolean isSmsApp(String pkg) {
        for (String smsPkg : SMS_PACKAGES) {
            if (pkg.contains(smsPkg)) return true;
        }
        return false;
    }

    private String extractAmount(String text) {
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        if (matcher.find()) {
            String val = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (val != null) {
                try {
                    double num = Double.parseDouble(val.trim());
                    // Disallow model years (2012, 2024, 2025, 2026) and out-of-range values
                    if (num >= 10.0 && num <= 1500.0 && num != 2012 && num != 2024 && num != 2025 && num != 2026) {
                        return String.format(java.util.Locale.US, "%.2f", num);
                    }
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    private void showLocalCreditCardNotification(String stationName, String amount) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = "fulltank_fuel_alerts";

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        channelId,
                        "התראות תדלוק FullTank",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("התראות להזנת קילומטראז' לאחר זיהוי תדלוק");
                channel.enableVibration(true);
                if (nm != null) nm.createNotificationChannel(channel);
            }

            Intent promptIntent = new Intent(this, MainActivity.class);
            promptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            promptIntent.putExtra("prompt_mode", "credit_card_prompt");
            promptIntent.putExtra("station_name", stationName);
            promptIntent.putExtra("amount", amount);

            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                    this,
                    102,
                    promptIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("💳 זוהה חיוב דלק ב-" + stationName)
                    .setContentText("סך: " + amount + " ₪. לחץ כאן להזנת קילומטראז' ושמירה")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .addAction(android.R.drawable.ic_input_add, "🚗 אשר תדלוק", pi);

            if (nm != null) {
                nm.notify(2027, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying credit card notification: " + e.getMessage());
        }
    }

    private void dispatchFuelWebhook(String receiptUrl, String source) {
        new Thread(() -> {
            try {
                URL url = new URL(PRIMARY_WEBHOOK_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    JSONObject payload = new JSONObject();
                    payload.put("url", receiptUrl);
                    payload.put("source", source);
                    payload.put("plate", FuelDatabaseHelper.getInstance(FuelNotificationListenerService.this).getActiveVehiclePlate());

                    OutputStream os = conn.getOutputStream();
                    os.write(payload.toString().getBytes("utf-8"));
                    os.flush();
                    os.close();

                    if (conn.getResponseCode() == 200) {
                        Log.i(TAG, "Successfully dispatched fuel webhook to: " + PRIMARY_WEBHOOK_URL);
                        showLocalFuelNotification("זוהה תדלוק חדש!", "לחץ כאן להזנת קילומטראז' וכיול מד דלק");
                    }
            } catch (Exception e) {
                Log.w(TAG, "Failed dispatch to " + PRIMARY_WEBHOOK_URL + ": " + e.getMessage());
            }
        }).start();
    }

    private void showLocalFuelNotification(String title, String message) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            // Bump channel ID to force Android to re-create it with HEADS-UP banner importance
            String channelId = "fulltank_fuel_heads_up_v3";

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        channelId,
                        "התראות תדלוק FullTank (באנר צף)",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("באנר קופץ להזנת קילומטראז' מיד לאחר זיהוי תדלוק");
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[]{0, 250, 150, 250});
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                if (nm != null) nm.createNotificationChannel(channel);
            }

            Intent appIntent = new Intent(this, MainActivity.class);
            appIntent.putExtra("TRIGGER_KM_INPUT", true);
            appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                    this,
                    101,
                    appIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );

            androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("⛽ " + title)
                    .setContentText(message)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_EVENT)
                    .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, false) // Requests Heads-Up floating banner on modern Android
                    .addAction(android.R.drawable.ic_input_add, "🚗 לחץ להזנת קילומטראז'", pi);

            if (nm != null) {
                nm.notify(2026, builder.build());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error displaying local fuel notification: " + e.getMessage());
        }
    }
}
