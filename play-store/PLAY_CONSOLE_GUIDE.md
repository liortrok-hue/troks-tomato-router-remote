# מדריך העלאה ל-Google Play — Troks Tomato Router Remote

## פרטי האפליקציה

| | |
|---|---|
| **שם בחנות** | Troks Tomato Router Remote |
| **שם מתחת לאייקון בטלפון** | Troks Router |
| **Package name** | `com.troks.tomatorouter.remote` |
| **גרסה** | versionName `1.0.0`, versionCode `1` |
| **minSdk / targetSdk** | 26 (Android 8.0) / 36 |
| **קטגוריה** | Tools |

> ⚠️ ה-Package name נקבע בהעלאה הראשונה ו**אי אפשר לשנות אותו אחר כך**. אם רוצים אחר — משנים
> `applicationId` ב-`app/build.gradle.kts` **לפני** ההעלאה הראשונה.

## מה יש בתיקייה `play-store/`

| קובץ | שימוש ב-Play Console |
|---|---|
| `graphics/icon-512.png` | App icon (512×512) |
| `graphics/feature-graphic.png` | Feature graphic (1024×500) |
| `graphics/phone-screenshots/*.png` | Phone screenshots (8 תמונות, 1080×1920) |
| `listing.md` | שם, תיאור קצר ותיאור מלא — אנגלית, עברית, רוסית, ערבית |
| `privacy-policy.html` | מדיניות פרטיות — צריך לפרסם בכתובת ציבורית (ראה שלב 4) |
| `tools/make-graphics.ps1` | מייצר מחדש את האייקון, ה-feature graphic וההמרה של צילומי המסך |

כל צילומי המסך צולמו ב**מצב הדגמה** — כתובות IP ו-MAC מדומות (10.0.0.x, 203.0.113.x), אין בהם שום נתון אמיתי מהרשת שלך.

---

## שלב 1 — יצירת מפתח העלאה (פעם אחת)

בטרמינל מתיקיית הפרויקט:

```
powershell -ExecutionPolicy Bypass -File .\create-upload-key.ps1
```

- הסקריפט מבקש סיסמה, יוצר את `%USERPROFILE%\.android-keys\troks-tomato-router-upload.jks` וכותב `keystore.properties`.
- **גבה** את קובץ ה-`.jks` ואת הסיסמה (למשל במנהל סיסמאות). `keystore.properties` לא נכנס ל-git.

## שלב 2 — בניית קובץ ה-AAB

```
.\gradlew.bat :app:bundleRelease
```

הקובץ להעלאה: `app\build\outputs\bundle\release\app-release.aab`

> בלי `keystore.properties` ה-build נחתם במפתח debug — Google Play דוחה קובץ כזה. תמיד צור את המפתח קודם.

## שלב 3 — יצירת האפליקציה ב-Play Console

1. [play.google.com/console](https://play.google.com/console) ← **Create app**
2. App name: `Troks Tomato Router Remote` · Default language: **English (United States)**
3. App or game: **App** · Free or paid: **Free** (ראה "חינם עכשיו, בתשלום בעתיד" למטה)
4. מאשרים את ה-Declarations ← **Create app**

## שלב 4 — מדיניות פרטיות (חובה)

צריך כתובת ציבורית ל-`privacy-policy.html`. אפשרויות חינמיות:
- **GitHub Pages** — repo ציבורי, מעלים את הקובץ בשם `index.html`, מפעילים Pages.
- **Google Sites** — מעתיקים את הטקסט לדף חדש ומפרסמים.

לפני הפרסום: מחליפים `[your contact email]` / `[כתובת המייל שלך]` בכתובת שתרצה שתופיע.

## שלב 5 — App content (Policy → App content)

| סעיף | מה לענות |
|---|---|
| **Privacy policy** | הכתובת משלב 4 |
| **App access** | *All or some functionality is restricted* ← Add instructions (טקסט למטה) |
| **Ads** | No, my app does not contain ads |
| **Content rating** | שאלון IARC ← קטגוריה *All Other App Types* ← "No" לכל השאלות ← יוצא *Everyone / 3+* |
| **Target audience** | **18 and over** (מונע את דרישות מדיניות המשפחות) |
| **Data safety** | ראה למטה |
| **Government apps** | No |
| **Financial features** | My app doesn't provide any financial features |
| **Health** | My app does not have any health features |
| **News app** | No |

**App access** (לבודקים של גוגל — אין להם ראוטר). גוגל דורשת שם משתמש וסיסמה, לכן באפליקציה יש כניסה קבועה
`demo` / `demo` שפותחת את מצב ההדגמה (ראוטר מדומה, בלי חיבור לרשת, עם כל כתובת).

ב-Policy → App content → App access ← *All or some functionality is restricted* ← **Add instructions**:

| שדה | ערך |
|---|---|
| Name | `Demo router` |
| Username | `demo` |
| Password | `demo` |
| Any other information | הטקסט למטה |

```
The app manages a router running FreshTomato firmware. Reviewers don't need a router:
1. Open the app. Leave the address and port as they are.
2. Username: demo   Password: demo   -> tap "Connect".
   (Or scroll down and tap "Try demo mode".)
3. A simulated router opens. All screens work: Overview, Devices, WiFi, Monitor, Manage, Tools.
   All addresses are fake and nothing is sent to any network.
```

לשמור ← **Save**, ואז לחזור ל-Publishing overview ← **Send for review**.

**Data safety** — האפליקציה לא אוספת ולא משתפת מידע (המידע עובר רק בין הטלפון לראוטר של המשתמש, לא למפתח ולא לצד שלישי):
- Does your app collect or share any of the required user data types? → **No**
- (אם נשאל) Is all of the user data collected by your app encrypted in transit? — לא רלוונטי כשאין איסוף.
- Account deletion — לא רלוונטי (אין חשבונות).

## שלב 6 — Store listing (Grow users → Store presence → Main store listing)

- **App name / Short description / Full description** — מ-`listing.md` (אנגלית).
- **App icon** — `graphics/icon-512.png`
- **Feature graphic** — `graphics/feature-graphic.png`
- **Phone screenshots** — כל 8 הקבצים מ-`graphics/phone-screenshots/` בסדר המספרים.
- **Translations** — *Manage translations → Add your own translations* ← עברית (he-IL), רוסית (ru-RU), ערבית (ar) — הטקסטים ב-`listing.md`. צילומי המסך באנגלית ישמשו לכל השפות.
- **Store settings** — Category: *Tools* · Contact email: חובה (מוצג בחנות).

## שלב 7 — בדיקה ושחרור

**חשבון מפתח אישי שנפתח אחרי 13 בנובמבר 2023** חייב לעבור **Closed testing** לפני פרסום לכולם:
- לפחות **12 בודקים** שהצטרפו לבדיקה ונשארו בה **14 ימים רצופים**.
- Testing → Closed testing ← Create track ← מעלים את ה-AAB ← מוסיפים רשימת מיילים של בודקים ← שולחים להם את קישור ההצטרפות.
- אחרי 14 יום: Dashboard ← **Apply for production** (ממלאים שאלון קצר על הבדיקה).

אם החשבון ארגוני או ישן יותר — אפשר ישר: Release → **Production** ← Create new release ← מעלים AAB ← Review ← **Start rollout**.

בהעלאה הראשונה גוגל תציע **Play App Signing** — לאשר (ברירת המחדל). גוגל מחזיקה את מפתח החתימה הסופי, ואתה חותם רק במפתח ההעלאה.

---

## חינם עכשיו, בתשלום בעתיד — איך לתכנן נכון

⚠️ **ב-Google Play אי אפשר להפוך אפליקציה שפורסמה בחינם לאפליקציה בתשלום.** (ההפך — מתשלום לחינם — אפשר, אבל רק פעם אחת ולא חוזרים.)

לכן אם יש סיכוי שתרצה לגבות כסף בעתיד, האפשרויות הן:

1. **מומלץ — חינם + רכישה בתוך האפליקציה (In-app purchase)**
   האפליקציה נשארת חינם, ואפשר להוסיף בעתיד "Pro" שפותח פיצ'רים (למשל: כמה ראוטרים, ניטור מתקדם, ווידג'טים) — כתשלום חד-פעמי או כמנוי.
   - דורש **Payments profile** (חשבון סוחר) ב-Play Console: Settings → Payments profile. אפשר לפתוח כבר עכשיו.
   - דורש שילוב **Google Play Billing Library** בקוד — אעשה את זה כשתחליט.
   - כשמוסיפים רכישות, מעדכנים ב-Store listing: "Contains in-app purchases".
   - עמלת גוגל: 15% על ההכנסות (עד מיליון דולר בשנה).
2. **אפליקציה נפרדת בתשלום** — למשל `Troks Tomato Router Remote Pro` עם Package name חדש. פשוט, אבל המשתמשים הקיימים לא עוברים אליה אוטומטית.

הגדרות מחיר כשיגיע הזמן: Monetize → Products → In-app products / Subscriptions.

---

## עדכונים בעתיד

1. מעלים את `versionCode` (חובה, כל העלאה +1) ואת `versionName` ב-`app/build.gradle.kts`.
2. `.\gradlew.bat :app:bundleRelease`
3. Play Console ← אותו track ← Create new release ← מעלים את ה-AAB החדש.

אם שינית מסכים — מצלמים מחדש במצב הדגמה ומריצים `play-store\tools\make-graphics.ps1`.

## רשימת בדיקה לפני שליחה

- [ ] נוצר מפתח העלאה ויש לו גיבוי
- [ ] AAB נבנה חתום (`app-release.aab`)
- [ ] מדיניות פרטיות מפורסמת בכתובת ציבורית, עם כתובת מייל
- [ ] App access עם הוראות מצב הדגמה
- [ ] Content rating, Target audience (18+), Data safety, Ads — מולאו
- [ ] אייקון, Feature graphic, 8 צילומי מסך, תיאורים (+ תרגומים)
- [ ] Contact email בהגדרות החנות
- [ ] Closed testing עם 12 בודקים × 14 ימים (אם החשבון אישי וחדש)
