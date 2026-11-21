# גרסה 1.1.8 — כל השינויים מאז 1.1.7.1 (עברית)

## חדש
**פרופילים**
- שמירה של עד 5 פרופילים: ראוטרים שונים, או משתמשים שונים על אותו ראוטר (למשל "בית", "משרד", "Lior").
- כל פרופיל שומר הכל: כתובת מקומית, כתובת חיצונית, פורטים, HTTPS, שם משתמש וסיסמה.
- בסגנון מחליף החשבונות של Google: כרטיס עם אווטאר צבעוני (האות הראשונה של השם), שם הפרופיל והכתובת שלו.
- לחיצה על הכרטיס פותחת רשימה מלמטה: בחירה בפרופיל, עיפרון לשינוי שם או מחיקה, ו"הוספת פרופיל".
- **מעבר מהמסך הראשי:** בסרגל העליון מופיע "שם · כתובת ▾". לחיצה פותחת את הרשימה, ובחירה בפרופיל אחר מנתקת ומתחברת אליו בצעד אחד.
- פרופיל בלי סיסמה שמורה (או שמחכה לטביעת אצבע) עוצר במסך הכניסה עם הפרטים שלו מוכנים.
- פרטי החיבור מקופלים לכפתור "עריכת פרטי החיבור", כך שמסך הכניסה נקי. בפרופיל חדש, או כשההתחברות נכשלת, הם נפתחים לבד.
- החיבור הקיים הופך אוטומטית לפרופיל "הראוטר שלי", כולל הסיסמה השמורה.
- הסיסמה של כל פרופיל מוצפנת בטלפון (Android Keystore), ורק כשמסמנים "זכור סיסמה".

**כתובת חיצונית (גישה מרחוק)**
- שדה שני לכתובת IP / דומיין חיצוני (למשל DDNS), עם פורט ו-HTTPS משלו.
- האפליקציה מנסה קודם את הכתובת המקומית, ואם היא לא עונה עוברת לחיצונית. בבית החיבור מקומי, ומחוץ לבית דרך האינטרנט.
- כשהטלפון מחובר רק לרשת סלולרית, הכתובת המקומית מדולגת וההתחברות מהירה יותר.
- סמל 🌐 ליד הכתובת בסרגל העליון כשהחיבור הוא דרך הכתובת החיצונית.
- תעודת ה-HTTPS נשמרת ונבדקת בנפרד לכל כתובת.
- גם הבדיקה ברקע של התראות הלוג משתמשת באותו סדר: מקומית ואז חיצונית.

**בר עדכון בתוך האפליקציה**
- כשיש גרסה חדשה ב-Google Play, מופיע בר בראש המסך (לא התראת מערכת).
- "עדכן" מוריד את העדכון ברקע בזמן שממשיכים לעבוד, ואחרי זה "הפעל מחדש" מתקין אותו.
- אפשר לסגור את הבר.

## שיפורים
- התראות הלוג מציגות את השעה שבה כל אירוע נרשם בראוטר (ואם האירוע לא מהיום, גם את התאריך).

## תיקונים
- חלון ההגדרות (וחלון התרומה) ריצד כשגוררים אותו למטה כדי לסגור.

---

# Version 1.1.8 — forum post (English)

**Troks Tomato Router Remote 1.1.8 is out**

An Android app for managing FreshTomato and Tomato64 routers. It talks to the router's own web UI, so nothing needs to be installed on the router. This post covers everything that changed since 1.1.7.1.

Google Play: https://play.google.com/store/apps/details?id=com.troks.tomatorouter.remote
Source: https://github.com/liortrok-hue/troks-tomato-router-remote

**New**

**Profiles**
- Save up to 5 profiles: different routers, or different users on the same router.
- Each profile keeps everything: local address, external address, ports, HTTPS, user name and password.
- Google-style account switcher: a card with a colored initial avatar, the profile name and its address.
- Tap the card for the profile list: pick one, rename or delete it with the pencil, or add a new one.
- **Switch from the main screen:** the top bar shows "Name · address ▾". Tap it, pick another profile, and the app signs out and connects to it in one step.
- A profile without a saved password (or one waiting for fingerprint / face unlock) stops at the login screen with its details filled in.
- Connection details fold into an "Edit connection details" button, so the login screen stays clean. They open by themselves for a new profile or when signing in fails.
- Your existing login becomes a "My router" profile automatically, saved password included.
- Each profile's password is encrypted on the phone (Android Keystore), and only if "Remember password" is on.

**External address (remote access)**
- A second IP / domain field (e.g. DDNS) with its own port and HTTPS setting.
- The app tries the local address first and falls back to the external one if it doesn't answer. At home it connects locally, away from home over the internet.
- On mobile data only, the local address is skipped, so connecting is faster.
- A 🌐 icon next to the address in the top bar when connected through the external address.
- The HTTPS certificate is pinned separately for each address.
- The background log-alert check uses the same order: local, then external.

**In-app update bar**
- When a new version is on Google Play, a bar shows at the top of the app (not a system notification).
- "Update" downloads it in the background while you keep working, then "Restart" installs it.
- The bar can be dismissed.

**Improvements**
- Log alert notifications show the time each event was logged on the router (and the date, if it isn't from today).

**Fixes**
- The Settings sheet (and the Donate sheet) jittered while being dragged down to close.

Feedback and bug reports are welcome, here or on GitHub.
