package com.natanel.sonolsync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FuelDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "fulltank_local.db";
    private static final int DATABASE_VERSION = 3;

    // Table 1: Refuels
    public static final String TABLE_REFUELS = "refuels";
    public static final String COL_REFUEL_ID = "_id";
    public static final String COL_REFUEL_DATE = "date";
    public static final String COL_REFUEL_KM = "km";
    public static final String COL_REFUEL_TOTAL = "total";
    public static final String COL_REFUEL_LITERS = "liters";
    public static final String COL_REFUEL_RATE = "rate";
    public static final String COL_REFUEL_STATION = "station";
    public static final String COL_REFUEL_URL = "receipt_url";
    public static final String COL_REFUEL_PLATE = "plate";
    public static final String COL_REFUEL_SYNCED = "is_synced";

    // Table 2: Odometer & Trip Logs
    public static final String TABLE_ODO_LOGS = "odometer_logs";
    public static final String COL_ODO_ID = "_id";
    public static final String COL_ODO_TIMESTAMP = "timestamp";
    public static final String COL_ODO_KM = "km";
    public static final String COL_ODO_SINCE_FULL = "km_since_full";
    public static final String COL_ODO_FUEL_LITERS = "fuel_liters";
    public static final String COL_ODO_SOURCE = "source";
    public static final String COL_ODO_NOTES = "notes";
    public static final String COL_ODO_PLATE = "plate";

    // Table 3: Maintenance & Service Records
    public static final String TABLE_MAINTENANCE = "maintenance_records";
    public static final String COL_MAINT_ID = "_id";
    public static final String COL_MAINT_DATE = "date";
    public static final String COL_MAINT_KM = "km";
    public static final String COL_MAINT_TYPE = "service_type";
    public static final String COL_MAINT_DESC = "description";
    public static final String COL_MAINT_COST = "cost";
    public static final String COL_MAINT_GARAGE = "garage";
    public static final String COL_MAINT_NEXT_KM = "next_service_km";
    public static final String COL_MAINT_RECEIPT_URL = "receipt_url";
    public static final String COL_MAINT_PARTS = "parts_detail";
    public static final String COL_MAINT_CATEGORY = "category";
    public static final String COL_MAINT_PLATE = "plate";

    // Table 4: Vehicles (Multi-car support)
    public static final String TABLE_VEHICLES = "vehicles";
    public static final String COL_VEH_PLATE = "plate";
    public static final String COL_VEH_MODEL = "car_model";
    public static final String COL_VEH_TANK = "tank_capacity";
    public static final String COL_VEH_AVG_KML = "avg_km_per_liter";
    public static final String COL_VEH_CITY_KML = "city_km_per_liter";
    public static final String COL_VEH_TEST_DATE = "test_expiry_date";
    public static final String COL_VEH_TIRES = "tire_size";
    public static final String COL_VEH_IS_ACTIVE = "is_active";

    // Table 5: Insurance Records (Manual Privacy-First Entry)
    public static final String TABLE_INSURANCE = "insurance_records";
    public static final String COL_INS_ID = "_id";
    public static final String COL_INS_PLATE = "plate";
    public static final String COL_INS_TYPE = "insurance_type"; // חובה + מקיף / חובה בלבד / צד ג'
    public static final String COL_INS_COMPANY = "company";
    public static final String COL_INS_START_DATE = "start_date";
    public static final String COL_INS_END_DATE = "end_date";
    public static final String COL_INS_ANNUAL_COST = "annual_cost";
    public static final String COL_INS_MONTHLY_COST = "monthly_cost";
    public static final String COL_INS_POLICY_NO = "policy_number";
    public static final String COL_INS_NOTES = "notes";
    public static final String COL_INS_CREATED_AT = "created_at";

    // Table 6: Pro License & Offline Auth
    public static final String TABLE_LICENSE = "license_info";
    public static final String COL_LIC_KEY = "license_key";
    public static final String COL_LIC_EMAIL = "user_email";
    public static final String COL_LIC_PLAN = "plan_type";
    public static final String COL_LIC_EXPIRES = "valid_until_timestamp";
    public static final String COL_LIC_SIGNATURE = "crypto_signature";
    public static final String COL_LIC_IS_ACTIVE = "is_active";

    private static FuelDatabaseHelper instance;

    public static synchronized FuelDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new FuelDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private FuelDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_REFUELS + " (" +
                COL_REFUEL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_REFUEL_DATE + " TEXT, " +
                COL_REFUEL_KM + " INTEGER, " +
                COL_REFUEL_TOTAL + " REAL, " +
                COL_REFUEL_LITERS + " REAL, " +
                COL_REFUEL_RATE + " REAL, " +
                COL_REFUEL_STATION + " TEXT, " +
                COL_REFUEL_URL + " TEXT, " +
                COL_REFUEL_PLATE + " TEXT, " +
                COL_REFUEL_SYNCED + " INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_ODO_LOGS + " (" +
                COL_ODO_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_ODO_TIMESTAMP + " TEXT, " +
                COL_ODO_KM + " INTEGER, " +
                COL_ODO_SINCE_FULL + " INTEGER, " +
                COL_ODO_FUEL_LITERS + " REAL, " +
                COL_ODO_SOURCE + " TEXT, " +
                COL_ODO_NOTES + " TEXT, " +
                COL_ODO_PLATE + " TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_MAINTENANCE + " (" +
                COL_MAINT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_MAINT_DATE + " TEXT, " +
                COL_MAINT_KM + " INTEGER, " +
                COL_MAINT_TYPE + " TEXT, " +
                COL_MAINT_DESC + " TEXT, " +
                COL_MAINT_COST + " REAL, " +
                COL_MAINT_GARAGE + " TEXT, " +
                COL_MAINT_NEXT_KM + " INTEGER, " +
                COL_MAINT_RECEIPT_URL + " TEXT, " +
                COL_MAINT_PARTS + " TEXT, " +
                COL_MAINT_CATEGORY + " TEXT, " +
                COL_MAINT_PLATE + " TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_VEHICLES + " (" +
                COL_VEH_PLATE + " TEXT PRIMARY KEY, " +
                COL_VEH_MODEL + " TEXT, " +
                COL_VEH_TANK + " REAL DEFAULT 50.0, " +
                COL_VEH_AVG_KML + " REAL DEFAULT 14.24, " +
                COL_VEH_CITY_KML + " REAL DEFAULT 7.9, " +
                COL_VEH_TEST_DATE + " TEXT DEFAULT '2026-12-24', " +
                COL_VEH_TIRES + " TEXT DEFAULT '195/65R15', " +
                COL_VEH_IS_ACTIVE + " INTEGER DEFAULT 1)");

        db.execSQL("CREATE TABLE " + TABLE_INSURANCE + " (" +
                COL_INS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_INS_PLATE + " TEXT, " +
                COL_INS_TYPE + " TEXT, " +
                COL_INS_COMPANY + " TEXT, " +
                COL_INS_START_DATE + " TEXT, " +
                COL_INS_END_DATE + " TEXT, " +
                COL_INS_ANNUAL_COST + " REAL, " +
                COL_INS_MONTHLY_COST + " REAL, " +
                COL_INS_POLICY_NO + " TEXT, " +
                COL_INS_NOTES + " TEXT, " +
                COL_INS_CREATED_AT + " TEXT)");

        db.execSQL("CREATE TABLE " + TABLE_LICENSE + " (" +
                COL_LIC_KEY + " TEXT PRIMARY KEY, " +
                COL_LIC_EMAIL + " TEXT, " +
                COL_LIC_PLAN + " TEXT, " +
                COL_LIC_EXPIRES + " INTEGER, " +
                COL_LIC_SIGNATURE + " TEXT, " +
                COL_LIC_IS_ACTIVE + " INTEGER DEFAULT 0)");

        // Insert initial default vehicle if none exists
        db.execSQL("INSERT OR IGNORE INTO " + TABLE_VEHICLES + " (" +
                COL_VEH_PLATE + ", " + COL_VEH_MODEL + ", " + COL_VEH_TANK + ", " +
                COL_VEH_AVG_KML + ", " + COL_VEH_CITY_KML + ", " + COL_VEH_TEST_DATE + ", " +
                COL_VEH_TIRES + ", " + COL_VEH_IS_ACTIVE + ") VALUES " +
                "('12-345-67', 'רכב ראשי', 50.0, 14.0, 8.0, '2027-01-01', '195/65R15', 1)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_VEHICLES + " (" +
                    COL_VEH_PLATE + " TEXT PRIMARY KEY, " +
                    COL_VEH_MODEL + " TEXT, " +
                    COL_VEH_TANK + " REAL DEFAULT 50.0, " +
                    COL_VEH_AVG_KML + " REAL DEFAULT 14.24, " +
                    COL_VEH_CITY_KML + " REAL DEFAULT 7.9, " +
                    COL_VEH_TEST_DATE + " TEXT DEFAULT '2026-12-24', " +
                    COL_VEH_TIRES + " TEXT DEFAULT '195/65R15', " +
                    COL_VEH_IS_ACTIVE + " INTEGER DEFAULT 1)");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_LICENSE + " (" +
                    COL_LIC_KEY + " TEXT PRIMARY KEY, " +
                    COL_LIC_EMAIL + " TEXT, " +
                    COL_LIC_PLAN + " TEXT, " +
                    COL_LIC_EXPIRES + " INTEGER, " +
                    COL_LIC_SIGNATURE + " TEXT, " +
                    COL_LIC_IS_ACTIVE + " INTEGER DEFAULT 0)");
        }

        if (oldVersion < 3) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_INSURANCE + " (" +
                    COL_INS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_INS_PLATE + " TEXT, " +
                    COL_INS_TYPE + " TEXT, " +
                    COL_INS_COMPANY + " TEXT, " +
                    COL_INS_START_DATE + " TEXT, " +
                    COL_INS_END_DATE + " TEXT, " +
                    COL_INS_ANNUAL_COST + " REAL, " +
                    COL_INS_MONTHLY_COST + " REAL, " +
                    COL_INS_POLICY_NO + " TEXT, " +
                    COL_INS_NOTES + " TEXT, " +
                    COL_INS_CREATED_AT + " TEXT)");

            try {
                db.execSQL("ALTER TABLE " + TABLE_MAINTENANCE + " ADD COLUMN " + COL_MAINT_RECEIPT_URL + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_MAINTENANCE + " ADD COLUMN " + COL_MAINT_PARTS + " TEXT");
                db.execSQL("ALTER TABLE " + TABLE_MAINTENANCE + " ADD COLUMN " + COL_MAINT_CATEGORY + " TEXT");
            } catch (Exception ignored) {}
        }
    }

    // ==========================================
    // VEHICLE MANAGEMENT (DYNAMIC & MULTI-CAR)
    // ==========================================

    public String getActiveVehiclePlate() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_VEHICLES, new String[]{COL_VEH_PLATE}, COL_VEH_IS_ACTIVE + " = 1", null, null, null, null, "1");
        String plate = "12-345-67";
        if (c != null) {
            if (c.moveToFirst()) {
                plate = c.getString(c.getColumnIndexOrThrow(COL_VEH_PLATE));
            }
            c.close();
        }
        return plate;
    }

    public boolean setActiveVehiclePlate(String plate) {
        if (plate == null || plate.trim().isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues resetCv = new ContentValues();
            resetCv.put(COL_VEH_IS_ACTIVE, 0);
            db.update(TABLE_VEHICLES, resetCv, null, null);

            ContentValues setCv = new ContentValues();
            setCv.put(COL_VEH_IS_ACTIVE, 1);
            int updated = db.update(TABLE_VEHICLES, setCv, COL_VEH_PLATE + " = ?", new String[]{plate.trim()});

            if (updated == 0) {
                // If car does not exist yet, insert it as active
                ContentValues newCv = new ContentValues();
                newCv.put(COL_VEH_PLATE, plate.trim());
                newCv.put(COL_VEH_MODEL, "רכב " + plate.trim());
                newCv.put(COL_VEH_TANK, 50.0);
                newCv.put(COL_VEH_AVG_KML, 14.24);
                newCv.put(COL_VEH_CITY_KML, 7.9);
                newCv.put(COL_VEH_TEST_DATE, "2026-12-24");
                newCv.put(COL_VEH_TIRES, "195/65R15");
                newCv.put(COL_VEH_IS_ACTIVE, 1);
                db.insert(TABLE_VEHICLES, null, newCv);
            }

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public boolean saveOrUpdateVehicle(String plate, String model, double tankCapacity, double avgKml, double cityKml, String testDate, String tires, boolean setActive) {
        if (plate == null || plate.trim().isEmpty()) return false;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (setActive) {
                ContentValues resetCv = new ContentValues();
                resetCv.put(COL_VEH_IS_ACTIVE, 0);
                db.update(TABLE_VEHICLES, resetCv, null, null);
            }

            ContentValues cv = new ContentValues();
            cv.put(COL_VEH_PLATE, plate.trim());
            cv.put(COL_VEH_MODEL, model != null ? model : "רכב " + plate.trim());
            cv.put(COL_VEH_TANK, tankCapacity > 0 ? tankCapacity : 50.0);
            cv.put(COL_VEH_AVG_KML, avgKml > 0 ? avgKml : 14.24);
            cv.put(COL_VEH_CITY_KML, cityKml > 0 ? cityKml : 7.9);
            cv.put(COL_VEH_TEST_DATE, testDate != null ? testDate : "2026-12-24");
            cv.put(COL_VEH_TIRES, tires != null ? tires : "195/65R15");
            cv.put(COL_VEH_IS_ACTIVE, setActive ? 1 : 0);

            db.insertWithOnConflict(TABLE_VEHICLES, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public JSONObject getActiveVehicle() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_VEHICLES, null, COL_VEH_IS_ACTIVE + " = 1", null, null, null, null, "1");
        JSONObject obj = new JSONObject();
        try {
            if (c != null && c.moveToFirst()) {
                obj.put("plate", c.getString(c.getColumnIndexOrThrow(COL_VEH_PLATE)));
                obj.put("model", c.getString(c.getColumnIndexOrThrow(COL_VEH_MODEL)));
                obj.put("tank_capacity", c.getDouble(c.getColumnIndexOrThrow(COL_VEH_TANK)));
                obj.put("avg_km_per_liter", c.getDouble(c.getColumnIndexOrThrow(COL_VEH_AVG_KML)));
                obj.put("city_km_per_liter", c.getDouble(c.getColumnIndexOrThrow(COL_VEH_CITY_KML)));
                obj.put("test_expiry_date", c.getString(c.getColumnIndexOrThrow(COL_VEH_TEST_DATE)));
                obj.put("tire_size", c.getString(c.getColumnIndexOrThrow(COL_VEH_TIRES)));
                obj.put("is_active", 1);
            } else {
                // Default fallback
                obj.put("plate", "12-345-67");
                obj.put("model", "רכב ראשי");
                obj.put("tank_capacity", 50.0);
                obj.put("avg_km_per_liter", 14.0);
                obj.put("city_km_per_liter", 8.0);
                obj.put("test_expiry_date", "2027-01-01");
                obj.put("tire_size", "195/65R15");
                obj.put("is_active", 1);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return obj;
    }

    public JSONArray getAllVehicles() {
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_VEHICLES, null, null, null, null, null, COL_VEH_IS_ACTIVE + " DESC, " + COL_VEH_PLATE + " ASC");
        try {
            while (c != null && c.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("plate", c.getString(c.getColumnIndexOrThrow(COL_VEH_PLATE)));
                obj.put("model", c.getString(c.getColumnIndexOrThrow(COL_VEH_MODEL)));
                obj.put("tank_capacity", c.getDouble(c.getColumnIndexOrThrow(COL_VEH_TANK)));
                obj.put("avg_km_per_liter", c.getDouble(c.getColumnIndexOrThrow(COL_VEH_AVG_KML)));
                obj.put("city_km_per_liter", c.getDouble(c.getColumnIndexOrThrow(COL_VEH_CITY_KML)));
                obj.put("test_expiry_date", c.getString(c.getColumnIndexOrThrow(COL_VEH_TEST_DATE)));
                obj.put("tire_size", c.getString(c.getColumnIndexOrThrow(COL_VEH_TIRES)));
                obj.put("is_active", c.getInt(c.getColumnIndexOrThrow(COL_VEH_IS_ACTIVE)) == 1);
                arr.put(obj);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return arr;
    }

    // ==========================================
    // REFUELING & ODOMETER
    // ==========================================

    public long insertRefuel(String date, int km, double total, double liters, double rate, String station, String url, String plate) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_REFUEL_DATE, date);
        cv.put(COL_REFUEL_KM, km);
        cv.put(COL_REFUEL_TOTAL, total);
        cv.put(COL_REFUEL_LITERS, liters);
        cv.put(COL_REFUEL_RATE, rate);
        cv.put(COL_REFUEL_STATION, station);
        cv.put(COL_REFUEL_URL, url);
        cv.put(COL_REFUEL_PLATE, plate != null ? plate : getActiveVehiclePlate());
        return db.insert(TABLE_REFUELS, null, cv);
    }

    public long insertOdometerLog(String timestamp, int km, int sinceFull, double fuelLiters, String source, String notes, String plate) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_ODO_TIMESTAMP, timestamp != null ? timestamp : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
        cv.put(COL_ODO_KM, km);
        cv.put(COL_ODO_SINCE_FULL, sinceFull);
        cv.put(COL_ODO_FUEL_LITERS, fuelLiters);
        cv.put(COL_ODO_SOURCE, source);
        cv.put(COL_ODO_NOTES, notes);
        cv.put(COL_ODO_PLATE, plate != null ? plate : getActiveVehiclePlate());
        return db.insert(TABLE_ODO_LOGS, null, cv);
    }

    // ==========================================
    // INSURANCE (MANUAL PRIVACY-FIRST ENTRY)
    // ==========================================

    public long insertOrUpdateInsurance(String plate, String type, String company, String startDate, String endDate, double annualCost, String policyNo, String notes) {
        SQLiteDatabase db = getWritableDatabase();
        String targetPlate = (plate != null && !plate.isEmpty()) ? plate : getActiveVehiclePlate();
        double monthly = annualCost > 0 ? (annualCost / 12.0) : 0.0;

        ContentValues cv = new ContentValues();
        cv.put(COL_INS_PLATE, targetPlate);
        cv.put(COL_INS_TYPE, type != null ? type : "מקיף + חובה");
        cv.put(COL_INS_COMPANY, company != null ? company : "");
        cv.put(COL_INS_START_DATE, startDate != null ? startDate : "");
        cv.put(COL_INS_END_DATE, endDate != null ? endDate : "");
        cv.put(COL_INS_ANNUAL_COST, annualCost);
        cv.put(COL_INS_MONTHLY_COST, monthly);
        cv.put(COL_INS_POLICY_NO, policyNo != null ? policyNo : "");
        cv.put(COL_INS_NOTES, notes != null ? notes : "");
        cv.put(COL_INS_CREATED_AT, new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));

        return db.insert(TABLE_INSURANCE, null, cv);
    }

    public JSONObject getActiveInsurance(String plate) {
        String targetPlate = (plate != null && !plate.isEmpty()) ? plate : getActiveVehiclePlate();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_INSURANCE, null, COL_INS_PLATE + " = ?", new String[]{targetPlate}, null, null, COL_INS_ID + " DESC", "1");
        JSONObject obj = new JSONObject();
        try {
            if (c != null && c.moveToFirst()) {
                obj.put("id", c.getInt(c.getColumnIndexOrThrow(COL_INS_ID)));
                obj.put("plate", c.getString(c.getColumnIndexOrThrow(COL_INS_PLATE)));
                obj.put("insurance_type", c.getString(c.getColumnIndexOrThrow(COL_INS_TYPE)));
                obj.put("company", c.getString(c.getColumnIndexOrThrow(COL_INS_COMPANY)));
                obj.put("start_date", c.getString(c.getColumnIndexOrThrow(COL_INS_START_DATE)));
                obj.put("end_date", c.getString(c.getColumnIndexOrThrow(COL_INS_END_DATE)));
                obj.put("annual_cost", c.getDouble(c.getColumnIndexOrThrow(COL_INS_ANNUAL_COST)));
                obj.put("monthly_cost", c.getDouble(c.getColumnIndexOrThrow(COL_INS_MONTHLY_COST)));
                obj.put("daily_cost", c.getDouble(c.getColumnIndexOrThrow(COL_INS_ANNUAL_COST)) / 365.0);
                obj.put("policy_number", c.getString(c.getColumnIndexOrThrow(COL_INS_POLICY_NO)));
                obj.put("notes", c.getString(c.getColumnIndexOrThrow(COL_INS_NOTES)));
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return obj;
    }

    public JSONArray getAllInsuranceRecords(String plate) {
        String targetPlate = (plate != null && !plate.isEmpty()) ? plate : getActiveVehiclePlate();
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_INSURANCE, null, COL_INS_PLATE + " = ?", new String[]{targetPlate}, null, null, COL_INS_ID + " DESC");
        try {
            while (c != null && c.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("id", c.getInt(c.getColumnIndexOrThrow(COL_INS_ID)));
                obj.put("plate", c.getString(c.getColumnIndexOrThrow(COL_INS_PLATE)));
                obj.put("insurance_type", c.getString(c.getColumnIndexOrThrow(COL_INS_TYPE)));
                obj.put("company", c.getString(c.getColumnIndexOrThrow(COL_INS_COMPANY)));
                obj.put("start_date", c.getString(c.getColumnIndexOrThrow(COL_INS_START_DATE)));
                obj.put("end_date", c.getString(c.getColumnIndexOrThrow(COL_INS_END_DATE)));
                obj.put("annual_cost", c.getDouble(c.getColumnIndexOrThrow(COL_INS_ANNUAL_COST)));
                obj.put("monthly_cost", c.getDouble(c.getColumnIndexOrThrow(COL_INS_MONTHLY_COST)));
                obj.put("policy_number", c.getString(c.getColumnIndexOrThrow(COL_INS_POLICY_NO)));
                obj.put("notes", c.getString(c.getColumnIndexOrThrow(COL_INS_NOTES)));
                arr.put(obj);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return arr;
    }

    // ==========================================
    // MAINTENANCE & REPAIR RECORDS (WITH RECEIPTS)
    // ==========================================

    public long insertMaintenanceRecord(String date, int km, String serviceType, String description, double cost, String garage, int nextKm, String receiptUrl, String partsDetail, String category, String plate) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_MAINT_DATE, date != null ? date : new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
        cv.put(COL_MAINT_KM, km);
        cv.put(COL_MAINT_TYPE, serviceType != null ? serviceType : "טיפול שוטף");
        cv.put(COL_MAINT_DESC, description != null ? description : "");
        cv.put(COL_MAINT_COST, cost);
        cv.put(COL_MAINT_GARAGE, garage != null ? garage : "");
        cv.put(COL_MAINT_NEXT_KM, nextKm > 0 ? nextKm : km + 15000);
        cv.put(COL_MAINT_RECEIPT_URL, receiptUrl != null ? receiptUrl : "");
        cv.put(COL_MAINT_PARTS, partsDetail != null ? partsDetail : "");
        cv.put(COL_MAINT_CATEGORY, category != null ? category : "תחזוקה שוטפת");
        cv.put(COL_MAINT_PLATE, plate != null ? plate : getActiveVehiclePlate());
        return db.insert(TABLE_MAINTENANCE, null, cv);
    }

    public JSONArray getAllMaintenance(String plate) {
        String targetPlate = (plate != null && !plate.isEmpty()) ? plate : getActiveVehiclePlate();
        JSONArray arr = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE_MAINTENANCE, null, COL_MAINT_PLATE + " = ?", new String[]{targetPlate}, null, null, COL_MAINT_DATE + " DESC, " + COL_MAINT_ID + " DESC");
        try {
            while (c != null && c.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("id", c.getInt(c.getColumnIndexOrThrow(COL_MAINT_ID)));
                obj.put("date", c.getString(c.getColumnIndexOrThrow(COL_MAINT_DATE)));
                obj.put("km", c.getInt(c.getColumnIndexOrThrow(COL_MAINT_KM)));
                obj.put("service_type", c.getString(c.getColumnIndexOrThrow(COL_MAINT_TYPE)));
                obj.put("description", c.getString(c.getColumnIndexOrThrow(COL_MAINT_DESC)));
                obj.put("cost", c.getDouble(c.getColumnIndexOrThrow(COL_MAINT_COST)));
                obj.put("garage", c.getString(c.getColumnIndexOrThrow(COL_MAINT_GARAGE)));
                obj.put("next_service_km", c.getInt(c.getColumnIndexOrThrow(COL_MAINT_NEXT_KM)));
                obj.put("receipt_url", c.getString(c.getColumnIndexOrThrow(COL_MAINT_RECEIPT_URL)));
                obj.put("parts_detail", c.getString(c.getColumnIndexOrThrow(COL_MAINT_PARTS)));
                obj.put("category", c.getString(c.getColumnIndexOrThrow(COL_MAINT_CATEGORY)));
                obj.put("plate", c.getString(c.getColumnIndexOrThrow(COL_MAINT_PLATE)));
                arr.put(obj);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return arr;
    }

    // ==========================================
    // TOTAL COST OF OWNERSHIP (TCO) & EXPENSE SHELL
    // ==========================================

    public JSONObject getVehicleTCOSummary(String plate) {
        String targetPlate = (plate != null && !plate.isEmpty()) ? plate : getActiveVehiclePlate();
        SQLiteDatabase db = getReadableDatabase();
        JSONObject tco = new JSONObject();

        double totalFuelCost = 0.0;
        double totalFuelLiters = 0.0;
        int minKm = 0;
        int maxKm = 0;

        // Calculate fuel total
        Cursor cFuel = db.query(TABLE_REFUELS, new String[]{"SUM(" + COL_REFUEL_TOTAL + ")", "SUM(" + COL_REFUEL_LITERS + ")", "MIN(" + COL_REFUEL_KM + ")", "MAX(" + COL_REFUEL_KM + ")"},
                COL_REFUEL_PLATE + " = ?", new String[]{targetPlate}, null, null, null);
        if (cFuel != null && cFuel.moveToFirst()) {
            totalFuelCost = cFuel.getDouble(0);
            totalFuelLiters = cFuel.getDouble(1);
            minKm = cFuel.getInt(2);
            maxKm = cFuel.getInt(3);
            cFuel.close();
        }

        // Calculate maintenance total
        double totalMaintCost = 0.0;
        Cursor cMaint = db.query(TABLE_MAINTENANCE, new String[]{"SUM(" + COL_MAINT_COST + ")"},
                COL_MAINT_PLATE + " = ?", new String[]{targetPlate}, null, null, null);
        if (cMaint != null && cMaint.moveToFirst()) {
            totalMaintCost = cMaint.getDouble(0);
            cMaint.close();
        }

        // Insurance annual/monthly
        JSONObject ins = getActiveInsurance(targetPlate);
        double annualInsCost = ins.optDouble("annual_cost", 0.0);
        double monthlyInsCost = ins.optDouble("monthly_cost", 0.0);

        int totalDrivenKm = (maxKm > minKm && minKm > 0) ? (maxKm - minKm) : 0;
        double overallSpent = totalFuelCost + totalMaintCost + annualInsCost;
        double costPerKm = totalDrivenKm > 0 ? (overallSpent / totalDrivenKm) : (totalFuelLiters > 0 ? (totalFuelCost / (totalFuelLiters * 14.24)) : 0.54);

        try {
            tco.put("plate", targetPlate);
            tco.put("total_fuel_cost", totalFuelCost);
            tco.put("total_fuel_liters", totalFuelLiters);
            tco.put("total_maintenance_cost", totalMaintCost);
            tco.put("annual_insurance_cost", annualInsCost);
            tco.put("monthly_insurance_cost", monthlyInsCost);
            tco.put("total_driven_km", totalDrivenKm);
            tco.put("total_overall_spent", overallSpent);
            tco.put("cost_per_km", Math.round(costPerKm * 100.0) / 100.0);
        } catch (Exception ignored) {}

        return tco;
    }

    // ==========================================
    // LICENSING & EXPORT
    // ==========================================

    public boolean saveLicense(String key, String email, String plan, long validUntil, String signature) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_LIC_KEY, key);
        cv.put(COL_LIC_EMAIL, email);
        cv.put(COL_LIC_PLAN, plan);
        cv.put(COL_LIC_EXPIRES, validUntil);
        cv.put(COL_LIC_SIGNATURE, signature);
        cv.put(COL_LIC_IS_ACTIVE, 1);
        long res = db.insertWithOnConflict(TABLE_LICENSE, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return res != -1;
    }

    public boolean isLicenseActiveLocally() {
        SQLiteDatabase db = getReadableDatabase();
        long now = System.currentTimeMillis();
        Cursor c = db.query(TABLE_LICENSE, null, COL_LIC_IS_ACTIVE + " = 1 AND " + COL_LIC_EXPIRES + " > ?",
                new String[]{String.valueOf(now)}, null, null, null);
        boolean active = c != null && c.getCount() > 0;
        if (c != null) c.close();
        return active;
    }

    public JSONArray exportRefuelsAsJson() {
        JSONArray array = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_REFUELS, null, null, null, null, null, COL_REFUEL_ID + " DESC");
        try {
            while (cursor.moveToNext()) {
                JSONObject obj = new JSONObject();
                obj.put("date", cursor.getString(cursor.getColumnIndexOrThrow(COL_REFUEL_DATE)));
                obj.put("km", cursor.getInt(cursor.getColumnIndexOrThrow(COL_REFUEL_KM)));
                obj.put("total", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_REFUEL_TOTAL)));
                obj.put("liters", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_REFUEL_LITERS)));
                obj.put("rate", cursor.getDouble(cursor.getColumnIndexOrThrow(COL_REFUEL_RATE)));
                obj.put("station", cursor.getString(cursor.getColumnIndexOrThrow(COL_REFUEL_STATION)));
                obj.put("url", cursor.getString(cursor.getColumnIndexOrThrow(COL_REFUEL_URL)));
                obj.put("plate", cursor.getString(cursor.getColumnIndexOrThrow(COL_REFUEL_PLATE)));
                array.put(obj);
            }
        } catch (Exception ignored) {
        } finally {
            cursor.close();
        }
        return array;
    }
}
