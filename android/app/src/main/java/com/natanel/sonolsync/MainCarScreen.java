package com.natanel.sonolsync;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;

public class MainCarScreen extends Screen {
    // Endpoints
    private static final String PRIMARY_STATS_URL = AppConfig.getStatsUrl();
    private static final String PRIMARY_UPDATE_URL = AppConfig.getUpdateKmUrl();

    // View Modes
    public enum ViewMode {
        DASHBOARD,
        STEPPER_INPUT,
        MAINTENANCE
    }

    private ViewMode currentMode = ViewMode.DASHBOARD;

    // Vehicle State
    private double currentFuelLiters = 50.0;
    private double fuelPercent = 100.0;
    private int latestKm = 162089;
    private int kmSinceFull = 65;
    private int estimatedRange = 712;
    private int cityRange = 395;
    private double avgKmPerL = 14.24;
    private double avgCostPerKm = 0.54;
    private int nextServiceKm = 163000;
    private int serviceRemainingKm = 911;
    private String testExpiryDate = "24/12/2026";
    private int testDaysRemaining = 101;
    private String tireSize = "195/65R15";
    private String carName = "קורולה GLI 2012";

    public MainCarScreen(@NonNull CarContext carContext) {
        super(carContext);
        fetchStats();
    }

    public void fetchStats() {
        new Thread(() -> {
            boolean updated = false;
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
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        JSONObject json = new JSONObject(sb.toString());
                        if ("success".equals(json.optString("status"))) {
                            currentFuelLiters = json.optDouble("current_fuel_liters", currentFuelLiters);
                            fuelPercent = json.optDouble("fuel_percent", fuelPercent);
                            latestKm = json.optInt("latest_km", latestKm);
                            kmSinceFull = json.optInt("km_driven_since_full", kmSinceFull);
                            estimatedRange = json.optInt("estimated_remaining_range", estimatedRange);
                            cityRange = json.optInt("city_range", cityRange);
                            avgKmPerL = json.optDouble("avg_km_per_liter", avgKmPerL);
                            avgCostPerKm = json.optDouble("avg_cost_per_km", avgCostPerKm);
                            nextServiceKm = json.optInt("next_service_km", nextServiceKm);
                            serviceRemainingKm = json.optInt("service_remaining_km", serviceRemainingKm);
                            
                            if (json.has("test_expiry_date")) {
                                testExpiryDate = json.optString("test_expiry_date", testExpiryDate);
                            }
                            if (json.has("test_days_remaining")) {
                                testDaysRemaining = json.optInt("test_days_remaining", testDaysRemaining);
                            }
                            
                            String car = json.optString("car", "");
                            if (car.contains("קורולה")) carName = "קורולה GLI";
                            updated = true;
                        }
                    }
                } catch (Exception ignored) {}

            if (updated) {
                try {
                    getCarContext().getMainExecutor().execute(this::invalidate);
                } catch (Exception ignored) {}
            }
        }).start();
    }

    private void adjustKm(int deltaKm, boolean isFullTank) {
        final int targetKm = latestKm + deltaKm;
        latestKm = targetKm;
        invalidate();

        CarToast.makeText(getCarContext(), isFullTank ? "⛽ מאפס מיכל ל-100%..." : "🚗 מעדכן מד אוץ: " + targetKm + "...", CarToast.LENGTH_SHORT).show();

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
                payload.put("km", String.valueOf(targetKm));
                payload.put("is_full_tank", isFullTank);
                payload.put("source", "אנדרואיד אוטו (בקר מהיר)");

                OutputStream os = conn.getOutputStream();
                os.write(payload.toString().getBytes("utf-8"));
                os.flush();
                os.close();

                if (conn.getResponseCode() == 200) {
                    fetchStats();
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private String generate9BarsGauge(double percent) {
        int filledBars = (int) Math.round((percent / 100.0) * 9.0);
        filledBars = Math.max(0, Math.min(9, filledBars));
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filledBars; i++) sb.append("🟩");
        for (int i = filledBars; i < 9; i++) sb.append("⬜");
        return sb.toString() + " " + filledBars + "/9 פסים";
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        try {
            switch (currentMode) {
                case STEPPER_INPUT:
                    return buildStepperTemplate();
                case MAINTENANCE:
                    return buildMaintenanceTemplate();
                case DASHBOARD:
                default:
                    return buildDashboardTemplate();
            }
        } catch (Exception e) {
            // Failsafe template
            Pane fallbackPane = new Pane.Builder()
                    .addRow(new Row.Builder().setTitle("פוול טנק • " + carName).addText("מד אוץ: " + latestKm + " ק\"מ").build())
                    .build();
            return new PaneTemplate.Builder(fallbackPane)
                    .setHeaderAction(Action.APP_ICON)
                    .setTitle("פוול טנק")
                    .build();
        }
    }

    private Template buildDashboardTemplate() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        Pane.Builder paneBuilder = new Pane.Builder();

        // Row 1: 9-Bars Visual Gauge & Fuel
        String gaugeText = generate9BarsGauge(fuelPercent);
        Row fuelRow = new Row.Builder()
                .setTitle("⛽ " + gaugeText)
                .addText("נשאר: " + String.format(Locale.US, "%.1fL (%.0f%%)", currentFuelLiters, fuelPercent) + " • טווח: ~" + estimatedRange + " ק\"מ")
                .build();
        paneBuilder.addRow(fuelRow);

        // Row 2: Odometer & Trip since full
        Row odoRow = new Row.Builder()
                .setTitle("🚗 מד אוץ: " + nf.format(latestKm) + " ק\"מ")
                .addText("נסועה מאז פול: " + kmSinceFull + " ק\"מ • עירוני: ~" + cityRange + " ק\"מ")
                .build();
        paneBuilder.addRow(odoRow);

        // Row 3: Fuel Efficiency & Mileage Cost
        Row consRow = new Row.Builder()
                .setTitle("📊 צריכת דלק: " + String.format(Locale.US, "%.2f", avgKmPerL) + " ק\"מ/ל'")
                .addText("עלות נסועה: " + String.format(Locale.US, "%.2f", avgCostPerKm) + " ₪ לק\"מ • 7.72 ₪/ל'")
                .build();
        paneBuilder.addRow(consRow);

        // Row 4: Service & Test quick alert
        Row maintRow = new Row.Builder()
                .setTitle("🔧 טיפול קרוב: בעוד " + nf.format(serviceRemainingKm) + " ק\"מ")
                .addText("📋 טסט שנתי: בתוקף עד " + testExpiryDate + " (" + testDaysRemaining + " יום)")
                .build();
        paneBuilder.addRow(maintRow);

        // Actions: Switch to Stepper, Switch to Maintenance
        Action stepperAction = new Action.Builder()
                .setTitle("עדכון מהיר ⚡")
                .setBackgroundColor(CarColor.RED)
                .setOnClickListener(() -> {
                    currentMode = ViewMode.STEPPER_INPUT;
                    invalidate();
                })
                .build();
        paneBuilder.addAction(stepperAction);

        Action maintAction = new Action.Builder()
                .setTitle("תחזוקה 🔧")
                .setOnClickListener(() -> {
                    currentMode = ViewMode.MAINTENANCE;
                    invalidate();
                })
                .build();
        paneBuilder.addAction(maintAction);

        return new PaneTemplate.Builder(paneBuilder.build())
                .setHeaderAction(Action.APP_ICON)
                .setTitle("פוול טנק • " + carName)
                .build();
    }

    private Template buildStepperTemplate() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        ItemList.Builder listBuilder = new ItemList.Builder();

        // Current Odometer Status Row
        Row headerRow = new Row.Builder()
                .setTitle("🚗 מד אוץ נוכחי: " + nf.format(latestKm) + " ק\"מ")
                .addText("לחץ על אחד מכפתורי העדכון המהירים בנגיעה אחת:")
                .build();
        listBuilder.addItem(headerRow);

        // Quick Steppers
        Row p10 = new Row.Builder()
                .setTitle("⚡ +10 ק\"מ (" + nf.format(latestKm + 10) + " ק\"מ)")
                .addText("נסיעה עירונית קצרה")
                .setOnClickListener(() -> adjustKm(10, false))
                .build();
        listBuilder.addItem(p10);

        Row p25 = new Row.Builder()
                .setTitle("⚡ +25 ק\"מ (" + nf.format(latestKm + 25) + " ק\"מ)")
                .addText("נסיעה בינעירונית בינונית")
                .setOnClickListener(() -> adjustKm(25, false))
                .build();
        listBuilder.addItem(p25);

        Row p50 = new Row.Builder()
                .setTitle("⚡ +50 ק\"מ (" + nf.format(latestKm + 50) + " ק\"מ)")
                .addText("נסיעה ארוכה / יום עבודה")
                .setOnClickListener(() -> adjustKm(50, false))
                .build();
        listBuilder.addItem(p50);

        Row fullTankRow = new Row.Builder()
                .setTitle("⛽ מילאתי פול כעת (איפוס 100%)")
                .addText("מאפס את מד הדלק ל-50L מלא ומכייל נסועה")
                .setOnClickListener(() -> adjustKm(0, true))
                .build();
        listBuilder.addItem(fullTankRow);

        Row fineTuneRow = new Row.Builder()
                .setTitle("🔍 כוונון עדין: [+1 ק\"מ] או [-10 ק\"מ]")
                .addText("לחץ לתיקון: +1 ק\"מ (" + nf.format(latestKm + 1) + ")")
                .setOnClickListener(() -> adjustKm(1, false))
                .build();
        listBuilder.addItem(fineTuneRow);

        return new ListTemplate.Builder()
                .setHeaderAction(new Action.Builder(Action.BACK).setOnClickListener(() -> {
                    currentMode = ViewMode.DASHBOARD;
                    invalidate();
                }).build())
                .setTitle("עדכון נסועה מהיר • פוול טנק")
                .setSingleList(listBuilder.build())
                .build();
    }

    private Template buildMaintenanceTemplate() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        ItemList.Builder listBuilder = new ItemList.Builder();

        // 1. Annual Inspection / License
        Row testRow = new Row.Builder()
                .setTitle("📋 רישיון רכב וטסט: בתוקף עד " + testExpiryDate)
                .addText("נותרו עוד " + testDaysRemaining + " ימים • מבחן אחרון: 05/12/2025")
                .build();
        listBuilder.addItem(testRow);

        // 2. Next 15,000 KM Service
        Row serviceRow = new Row.Builder()
                .setTitle("🔧 טיפול תקופתי: ב-" + nf.format(nextServiceKm) + " ק\"מ")
                .addText("נותרו עוד " + nf.format(serviceRemainingKm) + " ק\"מ (שמן 5W-30 ומסננים)")
                .build();
        listBuilder.addItem(serviceRow);

        // 3. Tires
        Row tireRow = new Row.Builder()
                .setTitle("🛞 צמיגים: " + tireSize)
                .addText("לחץ אוויר מומלץ: 32 PSI • בדיקה תקופתית תקינה")
                .build();
        listBuilder.addItem(tireRow);

        // 4. Vehicle Specs
        Row specRow = new Row.Builder()
                .setTitle("🚘 מפרט: טויוטה קורולה GLI 2012 ידנית")
                .addText("מנוע 1ZR (1.6L Dual VVT-i) • נפח מיכל: 50.0 ליטר")
                .build();
        listBuilder.addItem(specRow);

        return new ListTemplate.Builder()
                .setHeaderAction(new Action.Builder(Action.BACK).setOnClickListener(() -> {
                    currentMode = ViewMode.DASHBOARD;
                    invalidate();
                }).build())
                .setTitle("מידע ותחזוקת הרכב")
                .setSingleList(listBuilder.build())
                .build();
    }
}
