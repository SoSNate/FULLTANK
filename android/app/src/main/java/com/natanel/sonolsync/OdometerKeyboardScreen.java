package com.natanel.sonolsync;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class OdometerKeyboardScreen extends Screen {
    private static final String PRIMARY_URL = AppConfig.getUpdateKmUrl();

    private final int currentKm;
    private final MainCarScreen mainScreen;
    private String currentTypedText = "";
    private boolean isSubmitting = false;

    public OdometerKeyboardScreen(@NonNull CarContext carContext, int currentKm, MainCarScreen mainScreen) {
        super(carContext);
        this.currentKm = currentKm;
        this.mainScreen = mainScreen;
        this.currentTypedText = String.valueOf(currentKm);
    }

    private void submitKm(String text) {
        if (isSubmitting) return;

        String cleaned = text.replaceAll("[^0-9]", "");
        if (cleaned.isEmpty()) {
            CarToast.makeText(getCarContext(), "נא להקליד מספר קילומטראז' מלא", CarToast.LENGTH_SHORT).show();
            return;
        }

        final int newKm;
        try {
            newKm = Integer.parseInt(cleaned);
        } catch (Exception e) {
            CarToast.makeText(getCarContext(), "מספר לא תקין", CarToast.LENGTH_SHORT).show();
            return;
        }

        if (newKm < 100000 || newKm > 999999) {
            CarToast.makeText(getCarContext(), "קילומטראז' לא הגיוני לקורולה", CarToast.LENGTH_SHORT).show();
            return;
        }

        isSubmitting = true;

        CarToast.makeText(getCarContext(), "שומר " + newKm + " ק\"מ...", CarToast.LENGTH_SHORT).show();

        new Thread(() -> {
            boolean success = false;
            try {
                URL url = new URL(PRIMARY_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);

                    JSONObject payload = new JSONObject();
                    payload.put("km", String.valueOf(newKm));
                    payload.put("is_full_tank", false);

                    OutputStream os = conn.getOutputStream();
                    os.write(payload.toString().getBytes("utf-8"));
                    os.flush();
                    os.close();

                    if (conn.getResponseCode() == 200) {
                        success = true;
                    }
            } catch (Exception ignored) {
            }

            final boolean ok = success;
            getCarContext().getMainExecutor().execute(() -> {
                isSubmitting = false;
                if (ok) {
                    CarToast.makeText(getCarContext(), "✅ קילומטראז' " + newKm + " נשמר בהצלחה!", CarToast.LENGTH_LONG).show();
                    if (mainScreen != null) {
                        mainScreen.fetchStats();
                    }
                    // Pop back twice to main screen
                    getScreenManager().popToRoot();
                } else {
                    CarToast.makeText(getCarContext(), "❌ שגיאה בשמירה לשרת", CarToast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        SearchTemplate.SearchCallback callback = new SearchTemplate.SearchCallback() {
            @Override
            public void onSearchTextChanged(@NonNull String searchText) {
                currentTypedText = searchText;
            }

            @Override
            public void onSearchSubmitted(@NonNull String searchText) {
                submitKm(searchText);
            }
        };

        ItemList.Builder suggestions = new ItemList.Builder();
        String displayVal = currentTypedText.isEmpty() ? String.valueOf(currentKm) : currentTypedText;

        Row confirmRow = new Row.Builder()
                .setTitle("💾 אישור ושמירת מד אוץ: " + displayVal + " ק\"מ")
                .addText("לחץ לאישור מיידי או הקלד מספר במקלדת ולחץ Enter")
                .setOnClickListener(() -> submitKm(displayVal))
                .build();
        suggestions.addItem(confirmRow);

        ActionStrip actionStrip = new ActionStrip.Builder()
                .addAction(new Action.Builder()
                        .setTitle("שמור")
                        .setBackgroundColor(CarColor.GREEN)
                        .setOnClickListener(() -> submitKm(displayVal))
                        .build())
                .build();

        return new SearchTemplate.Builder(callback)
                .setHeaderAction(Action.BACK)
                .setSearchHint("הזן קילומטראז' מלא (למשל 162050)")
                .setInitialSearchText(currentTypedText)
                .setItemList(suggestions.build())
                .setActionStrip(actionStrip)
                .setShowKeyboardByDefault(true)
                .build();
    }
}
