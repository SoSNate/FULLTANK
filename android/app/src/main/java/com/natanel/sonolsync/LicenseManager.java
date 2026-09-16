package com.natanel.sonolsync;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class LicenseManager {
    private static final String TAG = "LicenseManager";
    // Salt/Secret used for local HMAC-SHA256 signature verification (Zero-Backend)
    private static final String APP_SIGNING_SALT = "FullTank_Pro_Secured_2026_Bit_Natanel";

    public static class LicenseResult {
        public boolean isValid;
        public String email;
        public String plan;
        public long validUntil;
        public String message;

        public LicenseResult(boolean isValid, String email, String plan, long validUntil, String message) {
            this.isValid = isValid;
            this.email = email;
            this.plan = plan;
            this.validUntil = validUntil;
            this.message = message;
        }
    }

    public static boolean isPro(Context context) {
        FuelDatabaseHelper db = FuelDatabaseHelper.getInstance(context);
        return db.isLicenseActiveLocally();
    }

    public static LicenseResult activateLicenseCode(Context context, String encodedKey) {
        if (encodedKey == null || encodedKey.trim().isEmpty()) {
            return new LicenseResult(false, "", "", 0, "קוד רישיון ריק");
        }

        try {
            // Expected format: Base64(Payload_JSON).Base64(Signature)
            String cleanKey = encodedKey.trim().replaceAll("\\s+", "");
            String[] parts = cleanKey.split("\\.");
            if (parts.length != 2) {
                return new LicenseResult(false, "", "", 0, "פורמט רישיון לא תקין");
            }

            String payloadJson = new String(Base64.decode(parts[0], Base64.URL_SAFE), StandardCharsets.UTF_8);
            String signature = parts[1];

            // Verify Signature
            String expectedSignature = generateHmacSignature(payloadJson, APP_SIGNING_SALT);
            if (!expectedSignature.equals(signature)) {
                return new LicenseResult(false, "", "", 0, "חתימת רישיון לא תקפה או מזויפת");
            }

            // Parse Payload
            JSONObject obj = new JSONObject(payloadJson);
            String email = obj.optString("email", "driver@fulltank.app");
            String plan = obj.optString("plan", "PRO_YEARLY");
            long validUntil = obj.optLong("exp", 0);

            long now = System.currentTimeMillis();
            if (validUntil > 0 && validUntil < now) {
                return new LicenseResult(false, email, plan, validUntil, "תוקף הרישיון פג");
            }

            // Save to local database
            FuelDatabaseHelper db = FuelDatabaseHelper.getInstance(context);
            boolean saved = db.saveLicense(cleanKey, email, plan, validUntil, signature);

            if (saved) {
                return new LicenseResult(true, email, plan, validUntil, "רישיון Pro הופעל בהצלחה!");
            } else {
                return new LicenseResult(false, email, plan, validUntil, "שגיאה בשמירת הרישיון למכשיר");
            }
        } catch (Exception e) {
            Log.e(TAG, "License activation error: " + e.getMessage());
            return new LicenseResult(false, "", "", 0, "שגיאה בפענוח הרישיון: " + e.getMessage());
        }
    }

    public static String generateLicenseForTesting(String email, String plan, long validUntilTimestamp) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("email", email);
            obj.put("plan", plan);
            obj.put("exp", validUntilTimestamp);

            String payload = obj.toString();
            String payloadB64 = Base64.encodeToString(payload.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
            String sig = generateHmacSignature(payload, APP_SIGNING_SALT);

            return payloadB64 + "." + sig;
        } catch (Exception e) {
            return "";
        }
    }

    private static String generateHmacSignature(String data, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((data + "::" + salt).getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }
}
