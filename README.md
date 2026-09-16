# ⛽ FULLTANK (פול טאנק)
> מערכת חכמה לניהול תדלוקים, ניטור הוצאות רכב (TCO), כיול מד דלק אוטומטי, ותמיכה ב-Android Auto.

---

## 🌟 מאפייני המערכת (Features)

1. **אפליקציית אנדרואיד מקומית (Android Client):**
   - **מאזין התראות ממוקד (SMS Notification Listener):** מאזין לחשבוניות דיגיטליות של חברות דלק (סונול, פז, דור אלון, טן, מיקה וכו') ומחלץ אוטומטית קישורי קבלות ללא התראות שווא.
   - **באנר צף (Heads-Up Banner):** התראה צפה מידית לאחר תדלוק המאפשרת הזנת קילומטראז' בלחיצה אחת.
   - **ווידג'ט מסך בית (Home Screen Widget):** מציג 9 קוביות דלק, כמות ליטרים, וטווח נסיעה משוער, עם כפתורי עדכון מהיר (`+10`, `+25`, פול טאנק).
   - **תמיכה ב-Android Auto:** ממשק רכב ייעודי לשליטה ועדכון ק"מ תוך כדי נהיגה.

2. **שרת סנכרון ודשבורד (Backend & Web Dashboard):**
   - **Webhook מקלט לחשבוניות:** מקבל את הקישור מהאפליקציה, מפענח את סכום התדלוק, הליטרים, התעריף ושם התחנה.
   - **סנכרון Google Sheets:** תיעוד אוטומטי בגיליון תדלוקים שנתי.
   - **דשבורד ווב אינטראקטיבי:** ניהול ציי רכב (Multi-Car), מעקב טיפולים וביטוחים, ומחשבון עלות כוללת לק"מ (TCO).

---

## 📁 מבנה הריפוזיטורי (Repository Structure)

```text
FULLTANK/
├── android/                   # קוד אפליקציית האנדרואיד
│   ├── app/
│   │   ├── src/main/java/com/natanel/sonolsync/
│   │   │   ├── AppConfig.java                    # ריכוז הגדרות שרת וטוקנים
│   │   │   ├── FuelDatabaseHelper.java           # מסד נתונים מקומי (SQLite)
│   │   │   ├── FuelNotificationListenerService.java # האזנה ממוקדת לסמסים
│   │   │   ├── FullTankCarAppService.java        # שירות Android Auto
│   │   │   ├── FullTankWidgetProvider.java       # ווידג'ט מסך הבית
│   │   │   ├── MainActivity.java                 # מסך ראשי ו-WebView
│   │   │   └── ...
│   │   ├── build.gradle
│   │   └── ...
│   └── gradle.properties
│
├── server/                    # שרת הפייתון והדשבורד
│   ├── sonol_webhook_service.py  # שרת Webhook ו-REST API
│   ├── sonol_parser.py           # מפענח קבלות דיגיטליות (Pairzon, Paz וכו')
│   ├── fuel_dashboard.py         # מודול חישוב דלק, כיול מיכל ו-TCO
│   ├── google_tools.py           # אינטגרציה מול Google Sheets API
│   ├── dashboard.html            # ממשק Web UI מודרני (Tailwind)
│   ├── car_state.example.json    # תבנית נתוני רכב לדוגמה
│   └── .env.example              # תבנית משתני סביבה
│
├── .gitignore                 # סינון קבצי Build, לוגים וסודות
└── README.md                  # תיעוד הפרויקט
```

---

## 🚀 התקנה והרצה (Quick Start)

### 1. שרת ה-Backend

1. עברו לתיקיית השרת:
   ```bash
   cd server
   ```
2. העתיקו את קובץ ההגדרות והזינו את הנתונים שלכם:
   ```bash
   cp .env.example .env
   cp car_state.example.json car_state.json
   ```
3. עירכו את `.env`:
   - `PORT`: פורט ההרצה (ברירת מחדל: 5088).
   - `SECRET_TOKEN`: טוקן אימות מאובטח שתבחרו.
   - `SPREADSHEET_ID`: מזהה גיליון ה-Google Sheets שלכם.
4. הריצו את השרת:
   ```bash
   python3 sonol_webhook_service.py
   ```

### 2. אפליקציית Android

1. פתחו את תיקיית `android/` ב-Android Studio.
2. הגדירו את כתובת השרת והטוקן ב-`gradle.properties` (או ב-`~/.gradle/gradle.properties`):
   ```properties
   FULLTANK_SERVER_URL=http://your-server-ip:5088
   FULLTANK_SECRET_TOKEN=your_token_here
   FULLTANK_SPREADSHEET_URL=https://docs.google.com/spreadsheets/d/your_sheet_id
   ```
3. בנו והתקינו את האפליקציה:
   ```bash
   gradle assembleDebug
   ```

---

## 🔒 אבטחה ופרטיות (Security & Privacy)

- הריפוזיטורי אינו מכיל מפתחות פרטיים, קבצי אימות (`token.json`, `credentials.json`), כתובות IP שרתיות או מסמכי ביטוח ופרטים אישיים.
- כל ההגדרות מוזנות באמצעות משתני סביבה (`.env` ו-`gradle.properties`).
- האפליקציה פועלת במודל Local-First ומסננת קפדנית התראות שאינן שייכות לתדלוק.
