# 🤝 מדריך שיתוף פעולה ופיתוח (FullTank Collaboration & Architecture Guide)

ברוכים הבאים לצוות הפיתוח של **FullTank (פול טאנק)**! ⛽  
מסמך זה מגדיר את חלוקת האחריות בין הצוות, עקרונות הארכיטקטורה, חוזי ה-API וההנחיות למשתמשים חדשים.

---

## 👥 1. חלוקת תפקידים ואחריות (Division of Responsibilities)

כדי לאפשר עבודה מהירה, עצמאית וללא תלויות חוסמות:

### 🎨 חזית המשתמש ועיצוב (Frontend, Android UI & UX):
* **תחום אחריות:**
  1. **ממשק אפליקציית Android:** מסכי האפליקציה, חוויית משתמש, אנימציות, דיאלוגים והגדרות מקומיות.
  2. **ווידג'ט מסך הבית (Home Screen Widget):** עיצוב 9 קוביות הדלק, כפתורי הקיצור והטיפוגרפיה.
  3. **ממשק Android Auto:** התאמת המסכים לנהיגה בטוחה בתקן Car App API של גוגל.
  4. **דשבורד הווב (Web UI Styling):** שיפורי עיצוב, מודלים, רספונסיביות למובייל/דסקטופ ונגישות.
* **עקרון עבודה:** ה-UI נשען אך ורק על חוזי ה-API המתועדים במסמך זה. ניתן לעבוד מול נתוני Mock גם ללא שרת פעיל.

### ⚙️ מנוע ושרת (Backend, Data Pipelines & Integrations):
* **תחום אחריות:**
  1. **שרת ה-REST וה-Webhooks:** שרת הפייתון, קליטת נתונים ואימות מאובטח.
  2. **פענוח חשבוניות (Receipt Parsers):** חילוץ ליטרים, מחיר, תעריף ותחנה (Pairzon, Sonol, Paz וכו').
  3. **אינטגרציות ענן:** סנכרון דו-כיווני מול Google Sheets API, Gmail API ו-Morning (חשבונית ירוקה).
  4. **מודל חישובים וכיול מיכל:** חישוב צריכה עירונית/משולבת, טווח נסיעה, עלות לק"מ ומעטפת TCO.

---

## 🔄 2. ארכיטקטורת Multi-User וההפרדה מהסביבה האישית (The Switch)

חשוב מאוד להבדיל בין:
1. **הסביבה האישית של נתנאל (Private Production):**
   - שרת פרטי וסגור הרץ על תשתית עצמאית.
   - מחובר לגיליון התדלוקים האישי, רכב הטויוטה קורולה האישי (`86-369-79`) ומייל מורנינג אישי.
   - **הפרטים האלו אינם חלק מהריפוזיטורי ולעולם לא יועלו ל-Git!**
2. **הפרויקט המשותף בריפוזיטורי (Public Open-Source / Self-Hosted):**
   - קוד מודולרי, נקי מכל מידע אישי מזהה (Zero PII).
   - כל משתמש שמוריד את ה-APK או מתקין את השרת מקבל מערכת עצמאית הפועלת ישירות מול חשבון ה-Google והרכב שלו.
   - במצב התחלתי (Unconfigured), המערכת אינה קורסת — היא מציגה באנר הדרכה ומאפשרת הזנת נתונים קלה.

---

## 🚀 3. מסלול הגדרה למשתמש חדש (New User Onboarding)

כאשר משתמש חדש מקבל את ה-APK:
1. **התחברות לשרת:**  
   האפליקציה שומרת את כתובת השרת ב-`SharedPreferences`. אם השרת המוגדר לא זמין, קופץ חלון הגדרות פשוט המאפשר להזין את כתובת השרת והטוקן הסודי.
2. **חיבור Google Sheets (2 דקות):**  
   - המשתמש פותח קובץ Google Sheets חדש (עם הלשוניות: `תדלוקים 2026` ו-`יומן נסועה וכיול`).
   - לוחץ על כפתור ההגדרות (⚙️) בדשבורד, ומדביק את הקישור לקובץ.
3. **הגדרת מייל הוצאות (Morning / רואה חשבון):**  
   - מזין את כתובת המייל לקבלת הוצאות (למשל `xxx@expenses.morning.co`).
   - מרגע זה, לחיצה על "שלח למורנינג" מורידה את ה-PDF המקורי ושולחת אותו ישירות במייל!
4. **פרופיל רכב:**  
   - המשתמש מזין את לוחית הרישוי, נפח המיכל וצריכת הדלק. המערכת תומכת גם בשליפה אוטומטית ממאגר משרד התחבורה.

---

## 📡 4. חוזי ה-API המלאים (API Specification)

כל בקשות ה-POST דורשות כותרת `X-Auth-Token: <SECRET_TOKEN>` או פרמטר `?token=<SECRET_TOKEN>` ב-URL.

### 4.1 שליפת נתונים ודשבורד: `GET /api/stats`
מחזיר את תמונת המצב הכוללת של הרכב הפעיל.
* **תגובה לדוגמה (מצב מוגדר):**
```json
{
  "status": "success",
  "is_configured": true,
  "plate": "12-345-67",
  "car": "רכב ראשי",
  "latest_km": 152400,
  "tank_capacity": 50.0,
  "current_fuel_liters": 38.5,
  "fuel_percent": 77.0,
  "estimated_remaining_range": 539,
  "avg_km_per_liter": 14.0,
  "avg_cost_per_km": 0.54,
  "next_service_km": 162400,
  "service_remaining_km": 10000,
  "recent_refuels": [
    {
      "date": "02/10/2026",
      "km": 152350,
      "total": 245.50,
      "liters": 34.2,
      "rate": 7.18,
      "station": "סונול גלילות",
      "url": "https://public.pairzon.com/...",
      "pdf": "https://...",
      "morning_sent": true,
      "morning_status": "נשלח"
    }
  ],
  "morning_info": {
    "email": "expenses@example.com",
    "sent_count": 14,
    "sent_amount": 2495.20
  }
}
```
* **תגובה במצב שטרם הוגדר גיליון (`is_configured: false`):**
מחזיר מבנה תקין ללא שגיאת שרת, כאשר `is_configured` שווה ל-`false`. ממשק המשתמש מציג את באנר ההגדרה.

---

### 4.2 עדכון קילומטראז' וכיול מיכל: `POST /api/update-km`
* **גוף הבקשה (JSON):**
```json
{
  "km": 152450,
  "is_full_tank": true,
  "is_refuel_event": true,
  "source": "אפליקציית אנדרואיד",
  "notes": "תדלוק מלא - איפוס מד דלק ל-100%"
}
```
* **תוצאה:** מעדכן את מד האוץ בשרת, מאפס את המיכל ל-100% (אם `is_full_tank: true`), מעדכן את שורת התדלוק האחרונה ב-Google Sheets ורושם ביומן הנסועה.

---

### 4.3 הגדרות מערכת דינמיות: `GET /api/settings` ו-`POST /api/settings`
מאפשר למשתמש להגדיר את הקובץ האישי שלו ישירות מהממשק ללא עריכת קוד.
* **`POST /api/settings` - גוף הבקשה:**
```json
{
  "spreadsheet_id": "https://docs.google.com/spreadsheets/d/1abcXYZ.../edit",
  "morning_expense_email": "user@expenses.morning.co",
  "active_plate": "12-345-67",
  "car_model": "יונדאי איוניק 2021",
  "tank_capacity": 45.0,
  "avg_km_per_liter": 19.5,
  "test_expiry_date": "2027-05-15"
}
```
* השרת מחלץ אוטומטית את ה-ID מתוך קישור מלא של Google Sheets ושומר בקובץ `car_state.json`.

---

### 4.4 שליחת קבלה למורנינג / רואה חשבון: `POST /api/send-to-morning`
* **גוף הבקשה (JSON):**
```json
{
  "url": "https://public.pairzon.com/...",
  "pdf": "https://pairzon.../invoice.pdf",
  "total": 250.00,
  "date": "02/10/2026",
  "station": "סונול"
}
```
* **תוצאה:** מוריד את קובץ ה-PDF מהענן, מצרף אותו כקובץ אמיתי למייל, שולח למורנינג ומסמן בעמודה I ב-Google Sheet שהקבלה נשלחה.

---

### 4.5 אימות רכב מול משרד התחבורה: `GET /api/car-lookup?plate=1234567`
שולף בזמן אמת ממאגר הרכבים הפתוח של מדינת ישראל (Gov Data API):
* יצרן ודגם מסחרי
* שנת ייצור ורמת גימור
* תוקף טסט ותאריך מבחן אחרון
* מידת צמיגים ונפח מנוע

---

## 📱 5. גשר האנדרואיד לממשק הווב (`AndroidBridge`)

כאשר הדשבורד רץ בתוך ה-WebView של האנדרואיד, עומדות לרשותו הפונקציות הבאות:
* `window.AndroidBridge.vibrate()`: רטט הפטי (Haptic Feedback) בלחיצה.
* `window.AndroidBridge.openSheets()`: פתיחת קובץ ה-Google Sheets באפליקציית Sheets המקורית בטלפון.
* `window.AndroidBridge.setServerConfig(serverUrl, secretToken, spreadsheetUrl)`: סנכרון הגדרות מהווב ישירות ל-SharedPreferences של הטלפון.
* `window.AndroidBridge.getActiveVehiclePlate()`: קבלת הלוחית הפעילה ממסד הנתונים המקומי.
* `window.AndroidBridge.setActiveVehiclePlate(plate)`: החלפת לוחית פעילה באפליקציה ובווידג'ט.

---

בהצלחה! לכל שאלה על חוזי ה-API, מוזמנים לפנות לנתנאל / אנטי ג'רוויס. 🚀
