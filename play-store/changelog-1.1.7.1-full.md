# גרסה 1.1.7.1 — כל השינויים מאז 1.1.4 (עברית)

## חדש
**התראות מהלוג (Beta)**
- האפליקציה בודקת את לוג המערכת של הראוטר ומקפיצה התראה על אירועים חדשים.
- בחירה של עד 3 רמות לוג להתראה (למשל Critical, Error, Warning).
- שתי הגדרות נפרדות:
  - **כשהאפליקציה פתוחה:** בדיקה כל דקה, כל 3 דקות או כל 5 דקות.
  - **כשהאפליקציה סגורה:** בדיקה ברקע כל 15 דקות, חצי שעה, שעה, או ערך מותאם אישית (60 דקות עד 24 שעות).
- לחיצה על ההתראה פותחת ישר את לוג המערכת.
- שורת מצב סוללה: מראה אם Android מגביל את האפליקציה ברקע, עם קיצור להגדרות הסוללה.
- הבדיקה ברקע צריכה סיסמה שמורה ("זכור סיסמה").
- בהתקנה ראשונה ההתראות כבויות, והמשתמש בוחר אם להפעיל אותן.
- לא מותקן שום דבר על הראוטר, ושום מידע לא יוצא מהרשת: האפליקציה רק קוראת את הלוג.

**ערכות צבעים**
- 11 ערכות: עגבנייה (ברירת המחדל), אוקיינוס, טורקיז, יער, סגול, ורוד, ו-5 בהירות: כתום, חמנייה, מנטה, שמיים, לבנדר.
- כל ערכה עובדת במצב בהיר ובמצב כהה.
- Dynamic color (צבעים מהטפט) ובחירת הערכה נמצאים יחד במסגרת אחת בהגדרות.

**מראה חדש**
- הסרגל העליון וסרגל הלשוניות התחתון מעוגלים, עם צל, והתוכן נגלל מתחתיהם.

**לוג המערכת**
- סינון לפי כל 8 הרמות של הראוטר (Emergency עד Debug), עם מספר השורות בכל רמה.
- סרגל גלילה עם ידית שאפשר לגרור.

**מכשירים**
- הרשימה ממוינת לפי א–ת (A–Z).
- סרגל גלילה עם ידית שאפשר לגרור.

**אודות**
- קישור לקוד המקור ב-GitHub.

## שיפורים
- מכשירים עם IP קבוע (רשומת ARP סטטית) נבדקים עכשיו באמת, ומוצגים כמנותקים כשהם כבויים.
- כפתורים מחולקים (Ping / Traceroute / DNS / Command ועוד) נשארים בגודל הטקסט שנבחר; מילה שלא נכנסת נחתכת עם "…" במקום שהגופן יקטן.
- חלון הפלט של הכלים רחב יותר.
- הבדיקה ברקע שומרת את ההתחברות לראוטר, כך שהיא לא מוסיפה שורת "login successful" ללוג בכל בדיקה.
- כשהראוטר לא זמין, הבדיקה ברקע מוותרת אחרי 30 שניות ומחכה לבדיקה הבאה.
- חיסכון בסוללה: בדיקת המצב בכותרת (כל 15 שניות) והמעקב החי במסך Monitor (כל 2 שניות) נעצרים מיד כשיוצאים מהאפליקציה, וממשיכים כשחוזרים.

## תיקונים
- הסינון "אזהרות" בלוג הציג גם שגיאות.
- טקסט בכפתורים הוקטן מאוד בגופן גדול.
- גרסה 1.1.7 קרסה מיד בעלייה (רכיב של הבדיקה ברקע נמחק בבנייה). תוקן ב-1.1.7.1.

---

# Version 1.1.7.1 — forum post (English)

**Troks Tomato Router Remote 1.1.7.1 is out**

An Android app for managing FreshTomato and Tomato64 routers. It talks to the router's own web UI, so nothing needs to be installed on the router. This post covers everything that changed since 1.1.4.

Google Play: https://play.google.com/store/apps/details?id=com.troks.tomatorouter.remote
Source: https://github.com/liortrok-hue/troks-tomato-router-remote

**New**

**Log alerts (Beta)**
- Get a notification when new lines of the levels you choose (up to 3) show up in the router's system log.
- Two separate settings:
  - **While the app is open:** checks every 1, 3 or 5 minutes.
  - **While the app is closed:** background check every 15 min, 30 min, 1 hour, or a custom interval (60 min to 24 h), using Android's WorkManager.
- Tapping a notification opens the system log.
- A battery status row shows whether Android restricts background work, with a shortcut to the app's battery settings.
- Background checks need the password to be saved in the app.
- Off by default on a fresh install.
- Nothing is installed or changed on the router, and nothing leaves your network: the app just reads the log, the same way the web UI does.
- The background check keeps its router session, so it doesn't add a "login successful" line to the log every time.
- If the router can't be reached, the check gives up after 30 seconds and waits for the next one.
- Works away from home too, as long as the app can reach the router (DDNS / public IP with remote access, ideally over HTTPS).

**Color palettes**
- 11 palettes: Tomato (default), Ocean, Teal, Forest, Violet, Rose, plus 5 brighter ones: Orange, Sunflower, Mint, Sky, Lavender.
- Each one has a light and a dark version.
- They sit next to Dynamic color (wallpaper colors, Android 12+) in Settings.

**New look**
- Rounded top bar and bottom tab bar with a soft shadow; pages scroll under them.

**System log**
- Filters for all 8 syslog levels (Emergency to Debug), each with a line count.
- Draggable scrollbar.

**Devices**
- Sorted A–Z.
- Draggable scrollbar.

**About**
- Link to the source on GitHub.

**Improvements**
- Devices with a static ARP entry are now probed for their real state, so they show as Offline when they're off.
- Segmented buttons (Ping / Traceroute / DNS / Command, etc.) keep the chosen text size; a label that doesn't fit ends with "…" instead of shrinking.
- Wider output window for the diagnostic tools.
- Battery: the header status check (every 15 s) and the live Monitor graphs (every 2 s) now stop as soon as you leave the app, and resume when you come back.

**Fixes**
- The Warnings filter in the log also showed errors.
- Button text became very small with a large font size.
- 1.1.7 crashed on startup (a background-check component was stripped from the release build). Fixed in 1.1.7.1.

Feedback and bug reports are welcome, here or on GitHub.
