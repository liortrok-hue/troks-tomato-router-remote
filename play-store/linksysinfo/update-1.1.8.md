# linksysinfo.org — 1.1.8 update post

Where to post: as a **reply** in the existing Troks Tomato Router Remote thread
(Tomato Firmware → FreshTomato). If you'd rather open a new thread, the title is below.

The forum runs XenForo, so the body uses **BBCode**. In the editor, switch to BBCode mode
(the `[ ]` button in the toolbar) before pasting, or the tags will show up as text.

Optional screenshots (take them in demo mode, so no real IPs show): the login screen with
the profile card, the "Choose a profile" list, and the top bar with "Name · address ▾".
Attach with **Attach files → Insert → Full image** where the `(image: …)` notes are, and
delete the notes you don't use.

---

## Title (only for a new thread)

```
Troks Tomato Router Remote 1.1.8 – profiles, remote address fallback, in-app updates
```

## Body (BBCode)

```
Hi everyone,

[B]Troks Tomato Router Remote 1.1.8[/B] is out – the free, open-source Android app for FreshTomato and Tomato64 routers. Nothing to install on the router: it talks to the router's own web interface.

[URL='https://play.google.com/store/apps/details?id=com.troks.tomatorouter.remote']Google Play[/URL] · [URL='https://github.com/liortrok-hue/troks-tomato-router-remote']Source on GitHub (GPLv3)[/URL]

Here's what changed since 1.1.7.1:

[SIZE=5][B]Profiles[/B][/SIZE]

If you look after more than one router (home, parents, office...) or use different logins, you can now save them as profiles and switch with one tap.

[LIST]
[*]Up to [B]5 profiles[/B]. Each one keeps the local address, external address, ports, HTTPS, user name and password
[*]Google-style account switcher: a card with a colored initial avatar, the profile name and its address. Tap it to pick, rename, delete or add a profile
[*][B]Switch from the main screen:[/B] the top bar shows [ICODE]Name · address ▾[/ICODE]. Tap it, pick another profile, and the app signs out and connects to the other router in one step
[*]Connection details fold away behind an "Edit connection details" button, so the login screen stays clean
[*]Your existing login becomes a "My router" profile automatically, saved password included
[*]Passwords stay on the phone only, encrypted with the Android Keystore
[/LIST]

(image: profile card)  (image: choose a profile)

[SIZE=5][B]External address with automatic fallback[/B][/SIZE]

The login screen now has a second address for remote access (public IP or DDNS name), with [B]its own port and HTTPS setting[/B].

[LIST]
[*]The app tries the [B]local address first[/B], and if it doesn't answer, it falls back to the external one. So at home it connects over the LAN, and away from home over the internet – same profile, no switching
[*]On mobile data only, the local address is skipped, so connecting is quicker
[*]A small globe icon in the top bar tells you when you're connected through the external address
[*]The HTTPS certificate is pinned separately for each address
[*]The background log-alert check uses the same local → external order
[/LIST]

Tip: for remote access, enable HTTPS remote admin on the router (Administration → Admin Access) and use a non-default port.

[SIZE=5][B]In-app update bar[/B][/SIZE]

When a new version is on Google Play, a bar shows at the top of the app. Tap [B]Update[/B] to download it in the background while you keep working, then [B]Restart[/B] to install. No system notification, and you can dismiss it.

[SIZE=5][B]Smaller things[/B][/SIZE]

[LIST]
[*]Log alert notifications now show [B]the time each event was logged[/B] on the router (plus the date if it isn't from today), e.g. [ICODE]18:58:52 [err] dnsmasq: ...[/ICODE]
[*]Fixed: the Settings sheet jittered when you dragged it down to close
[/LIST]

As always, bug reports, ideas and "it would be nice if…" requests are very welcome – here or as an issue on GitHub. Thanks to everyone who has tried the app and sent feedback so far!
```
