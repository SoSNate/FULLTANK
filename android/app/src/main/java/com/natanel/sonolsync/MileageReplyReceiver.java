package com.natanel.sonolsync;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MileageReplyReceiver extends BroadcastReceiver {
    private static final String TAG = "MileageReplyReceiver";
    public static final String KEY_TEXT_REPLY = "key_text_reply";
    public static final String CHANNEL_ID = "sonol_fuel_alerts_channel";
    private static final String UPDATE_KM_URL = AppConfig.getUpdateKmUrl();

    @Override
    public void onReceive(final Context context, Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            CharSequence replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY);
            if (replyText != null) {
                final String km = replyText.toString().trim();
                Log.d(TAG, "Received inline odometer reply: " + km);

                final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            URL url = new URL(UPDATE_KM_URL);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                            conn.setDoOutput(true);
                            conn.setConnectTimeout(10000);
                            conn.setReadTimeout(10000);

                            JSONObject payload = new JSONObject();
                            payload.put("km", km);

                            OutputStream os = conn.getOutputStream();
                            os.write(payload.toString().getBytes("UTF-8"));
                            os.close();

                            int code = conn.getResponseCode();
                            if (code == 200 && nm != null) {
                                NotificationCompat.Builder repliedNotification = new NotificationCompat.Builder(context, CHANNEL_ID)
                                        .setSmallIcon(R.mipmap.ic_launcher)
                                        .setContentTitle("✅ קילומטראז' נשמר בהצלחה!")
                                        .setContentText("עודכן: " + km + " ק\"מ ב-Google Sheets")
                                        .setAutoCancel(true);
                                nm.notify(2026, repliedNotification.build());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error saving km from notification reply", e);
                        }
                    }
                }).start();
            }
        }
    }
}
