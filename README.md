# Troks Tomato Router Remote

Android app for managing routers running [FreshTomato](https://freshtomato.org/) firmware.

- **Overview** – model, firmware, uptime, CPU, memory, WAN status
- **Devices** – connected clients, block/unblock, static IP (DHCP reservation), Wake-on-LAN, ping
- **WiFi** – radios on/off, SSID and password
- **Monitor** – live bandwidth per interface and live traffic per device
- **Manage** – WAN, static IPs, port forwarding, DMZ, admin access
- **Tools** – ping, traceroute, DNS lookup, shell commands, reboot

Kotlin + Jetpack Compose (Material 3). English, Hebrew, Russian and Arabic. Built-in demo mode
(tap "Try demo mode", or log in with `demo` / `demo`).

Package: `com.troks.tomatorouter.remote`

## How it talks to the router

Through the router's own web UI (httpd): HTTP Basic auth, the `http_id` token, and `shell.cgi`
(Tools → System Commands) — see `app/src/main/java/com/troks/tomatorouters/data/`.
Nothing has to be installed on the router.

## Build

Requires Android Studio's JDK (`C:\Program Files\Android\Android Studio\jbr`).

```
.\gradlew.bat :app:assembleDebug        # debug APK
.\gradlew.bat :app:bundleRelease        # signed AAB for Google Play
```

Release signing reads `keystore.properties` (not in git). Create the upload key once with
`create-upload-key.ps1` (add `-Gui` for a password dialog).

## Google Play

Store assets, listing texts (4 languages), privacy policy and the Play Console guide are in `play-store/`.
