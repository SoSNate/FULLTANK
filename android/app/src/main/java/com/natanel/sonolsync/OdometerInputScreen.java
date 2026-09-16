package com.natanel.sonolsync;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;

public class OdometerInputScreen extends Screen {
    private static final String PRIMARY_URL = AppConfig.getUpdateKmUrl();

    private final int currentKm;
    private final MainCarScreen mainScreen;
    private boolean isUpdating = false;

    public OdometerInputScreen(@NonNull CarContext carContext, int currentKm, MainCarScreen mainScreen) {
        super(carContext);
        this.currentKm = currentKm;
        this.mainScreen = mainScreen;
    }

    private void updateMileage(int newKm, boolean isFullTank) {
        if (isUpdating) return;
        isUpdating = true;

        CarToast.makeText(getCarContext(), "מעדכן קילומטראז' " + newKm + "...", CarToast.LENGTH_SHORT).show();

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
                payload.put("is_full_tank", isFullTank);

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
                isUpdating = false;
                if (ok) {
                    CarToast.makeText(getCarContext(), "✅ קילומטראז' עודכן בהצלחה!", CarToast.LENGTH_LONG).show();
                    if (mainScreen != null) {
                        mainScreen.fetchStats();
                    }
                    getScreenManager().pop();
                } else {
                    CarToast.makeText(getCarContext(), "❌ שגיאה בשמירה לשרת", CarToast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);

        ItemList.Builder listBuilder = new ItemList.Builder();

        // Option 1: Native car screen keyboard input
        Row keyboardRow = new Row.Builder()
                .setTitle("⌨️ הקלד מספר ידני במקלדת המסך")
                .addText("פתיחת מקלדת מלאה על מסך הרכב")
                .setOnClickListener(() -> getScreenManager().push(new OdometerKeyboardScreen(getCarContext(), currentKm, mainScreen)))
                .build();
        listBuilder.addItem(keyboardRow);

        // Option 2: Quick preset +20 km
        int km20 = currentKm + 20;
        Row p20 = new Row.Builder()
                .setTitle("⚡ +20 ק\"מ (" + nf.format(km20) + " ק\"מ)")
                .addText("עדכון מהיר בנגיעה אחת")
                .setOnClickListener(() -> updateMileage(km20, false))
                .build();
        listBuilder.addItem(p20);

        // Option 3: Quick preset +50 km
        int km50 = currentKm + 50;
        Row p50 = new Row.Builder()
                .setTitle("⚡ +50 ק\"מ (" + nf.format(km50) + " ק\"מ)")
                .addText("עדכון מהיר בנגיעה אחת")
                .setOnClickListener(() -> updateMileage(km50, false))
                .build();
        listBuilder.addItem(p50);

        // Option 4: Quick preset +100 km
        int km100 = currentKm + 100;
        Row p100 = new Row.Builder()
                .setTitle("⚡ +100 ק\"מ (" + nf.format(km100) + " ק\"מ)")
                .addText("עדכון מהיר בנגיעה אחת")
                .setOnClickListener(() -> updateMileage(km100, false))
                .build();
        listBuilder.addItem(p100);

        // Option 5: Refueled Full Tank reset
        Row fullRow = new Row.Builder()
                .setTitle("⛽ מילאתי פול כעת (איפוס ל-100%)")
                .addText("מכייל את מד הדלק ל-100% מלא בק\"מ נוכחי")
                .setOnClickListener(() -> updateMileage(currentKm, true))
                .build();
        listBuilder.addItem(fullRow);

        return new ListTemplate.Builder()
                .setHeaderAction(Action.BACK)
                .setTitle("עדכון קילומטראז' ברכב")
                .setSingleList(listBuilder.build())
                .build();
    }
}
