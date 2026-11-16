# Forum post for linksysinfo.org

Where to post: **Tomato Firmware → FreshTomato** sub-forum (Post thread).
The forum runs XenForo, so the body below uses **BBCode**. In the editor, switch to BBCode mode
(the `[ ]` button in the toolbar) before pasting, or the tags will show up as text.

Before posting:
1. Replace `[Google Group link]` with your testers group address.
2. Upload the images with **Attach files** (in this order: 00, 02, 04, 05, 07, 08, 06, 03),
   then use "Insert → Full image" on each one where the `(image: …)` notes are, and delete the notes.
   All images are in this folder: `play-store/linksysinfo/images/`.

---

## Title

```
[Beta] Troks Tomato Router Remote – free Android app for FreshTomato, looking for 12 testers
```

## Body (BBCode)

```
Hi everyone,

For years DD-WRT users had a phone app to manage their routers, but for Tomato / FreshTomato there was never really anything like it. I kept waiting for someone to build one, nobody did, so I decided to build it myself – and it's [B]completely free[/B].

Meet [B]Troks Tomato Router Remote[/B] – a free and open-source Android app for routers running FreshTomato.

(image: 00-collage.png)

[SIZE=5][B]What it does[/B][/SIZE]

[LIST]
[*][B]Overview[/B] – model, firmware, uptime, CPU load, memory, WAN status
[*][B]Devices[/B] – every connected client with name, IP, MAC, band and signal. Block / unblock internet access, set a static IP (DHCP reservation), Wake-on-LAN, ping
[*][B]WiFi[/B] – turn 2.4 / 5 GHz on or off, change SSID and password
[*][B]Live monitor[/B] – real-time bandwidth chart per interface, and real-time traffic per device (tap a device to pin it with its own live chart)
[*][B]Manage[/B] – WAN (DHCP / PPPoE / static, DNS, MTU), port forwarding, DMZ, static IPs, admin access (ports, remote access, SSH, password)
[*][B]Tools[/B] – ping, traceroute, DNS lookup and shell commands run from the router itself, plus reboot (with confirmation)
[/LIST]

(image: 02-devices.png)  (image: 04-bandwidth.png)  (image: 05-ip-traffic.png)

[SIZE=5][B]How it works[/B][/SIZE]

[LIST]
[*]It talks to the router's own web interface (httpd) – [B]nothing to install on the router[/B], no scripts, no Entware, no JFFS
[*]Works on your LAN (HTTP) or remotely (HTTPS – the router's certificate is pinned on first connect)
[*]Material 3 design, light / dark / system theme
[*]4 languages: English, Hebrew, Russian and Arabic (full right-to-left support)
[/LIST]

(image: 07-port-forwarding.png)  (image: 08-tools.png)

[SIZE=5][B]Privacy – the app collects nothing[/B][/SIZE]

I want to be very clear about this, because it's an app that holds your router's admin password:

[LIST]
[*]The app does [B]not collect any data, in any way[/B] – no analytics, no crash reporting, no tracking, no ads
[*]No account and no sign-up
[*]There is no server of mine anywhere – the app connects [B]only to your router[/B], nothing else
[*]Your router password is stored only on your phone, encrypted with the Android Keystore
[*]Don't take my word for it – the app is [B]open source (GPLv3)[/B], so you can read every line: [URL]https://github.com/liortrok-hue/troks-tomato-router-remote[/URL]
[/LIST]

[SIZE=5][B]Try it without touching your router[/B][/SIZE]

There's a built-in [B]demo mode[/B] with a simulated router: on the login screen tap [B]"Try demo mode"[/B] (or log in with [ICODE]demo[/ICODE] / [ICODE]demo[/ICODE]). Every screen works with fake data, so you can look around before connecting it to your real router. All the screenshots here are from demo mode, so the IPs and MACs are fake.

[SIZE=5][B]I need 12 beta testers[/B][/SIZE]

Google Play requires a new developer to run a [B]closed beta with at least 12 testers for 14 days[/B] before the app can be published publicly. If you'd like to help:

[LIST=1]
[*]Join the testers group: [Google Group link]
[*]Then opt in and install from Google Play: [URL]https://play.google.com/apps/testing/com.troks.tomatorouter.remote[/URL]
[*]Please keep the app installed for the 14 days – that's what Google counts
[/LIST]

Any phone with Android 8.0 or newer works. You don't even need a FreshTomato router to help – demo mode is enough.

[SIZE=5][B]Feedback and ideas are very welcome[/B][/SIZE]

This is a first version and I'd really love to make it better with your help. Bug reports, feature requests, "it would be nice if…" ideas – please post them here or open an issue on GitHub. Pull requests are welcome too. I'm especially interested in hearing from people with different router models (ARM / MIPS, Broadcom, different WiFi chipsets) and firmware versions, since every build exposes things a little differently.

Thanks for reading, and thanks to the FreshTomato developers for keeping these routers alive!

[I]Not affiliated with the FreshTomato project – just a user who wanted a proper phone app.[/I]
```
