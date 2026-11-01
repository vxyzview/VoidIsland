# VoidIsland

Dynamic Island for Android (by vxyzview) — notifications, media and calls on a floating island. Pure Java, no Gradle.

## Features

- Notifications, calls, music, timers, navigation and downloads on a floating island.
- **Lyrics** on the music pill: one line at a time; long lines slide sideways in step with the song (pause, seek and all).
- **Only on the island** (opt-in, off by default): messages and payments shown on the island are removed from the shade. Apps on the *Keep in shade* list keep theirs. An original is only ever removed while the island is running and really showing that banner; if the island is off, hidden (fullscreen app, lock screen, shade open) or ignoring the app, the notification stays in the shade. Android cannot bring back a notification that was already removed, so a banner you miss is gone.
- **Quick reply**: expand a message banner and tap *Reply* to answer without opening the app.
- **Island rules**: quiet hours (optionally only for chosen apps), mute group chats, one banner per chat.
- **Live activities**: rides, food delivery and live scores stay on the island while they run. Missing an app? Add it under **Apps > Ride / Food / Score apps**.
- **Hides only when it should**: the island stays visible everywhere, landscape included, and steps aside only while an app is fullscreen (video, game, reader: anything that hides the status bar). Turn that off under **Behavior > Rules**. The idle clock can also stay visible inside apps (turn off *Hide idle clock in apps*) and still disappears in fullscreen.
- **Idle clock styles**: pick how the clock looks under **Style > Idle clock**.
  - *Compact pill* (9): *Dial* (mini dial whose ring fills with the minutes, time and date), *Center* (just the time with a small AM / PM), *Split* (hour left, minutes right, a camera hole in between), *Date* (day on the left, time on the right), *Line* (centred time over a slim line whose round head travels the hour; always drawn, the text shrinks to fit short pills), *Capsule* (the time in a filled accent capsule), *Sky* (a sun, sinking sun or moon for the part of the day, plus the time), *Aura* (digits that fade from your accent into white) and *Dots* (the week as seven dots with today lit, plus the time).
  - *Expanded card* (8): *Classic*, *Digital*, *Analog*, *Bold*, *Week*, *Rings* (three activity rings for the day, the hour and the minute), *Flip* (a flip clock with a seconds line) and *Horizon* (the sun, or the moon at night, travelling an arc over the time).
- **Backup**: **Apps > Backup** saves every setting to a text file and restores it after a reinstall or on a new phone.
- **Battery**: timers stop while the screen is off, the music pill refreshes slowly when no lyric is moving, and songs LRCLIB has no lyrics for (and video / podcast apps) are not asked about again for an hour. A song the exact lookup misses is searched again by artist + title and by its cleaned-up title (no "feat." / "Remastered"), accepted only when its length fits.
- **Material You accent on a black island**: on Android 12+ the accent follows your wallpaper colours by default (play button, seek / progress bars, rings, action buttons, glyphs, in both the compact pill and the expanded card) while the island itself stays pure black. It follows the Material 3 dark-scheme roles: primary for the accent and filled buttons, secondary container for tonal buttons, tertiary for "good" things (accept, success, charging), error for "bad" ones (decline, low battery), and on-surface tones for text and tiles. Change it at **Style > Colors > Accent** (*Auto* = album-art / app colour, *Material You*, or *Custom*).

## Memory and background use

The island is a long-lived process, so idle cost matters more than peak speed:

- **Display off = quiet.** The once-a-minute clock broadcast is only registered while the screen is on, the equalizer bars stop redrawing, and the music card skips updates (it catches up the moment the screen lights up).
- **Equalizer bars** redraw at about 20 fps (was 30) and only while visible, playing and the display is on.
- **Compact music pill** with lyrics ticks once a second instead of 2.5 times a second; the lyric view schedules its own line changes and slide, and a seek or pause re-anchors it immediately.
- **Bounded caches.** App icons (about 2 MB) and album artwork (about 1 MB) are limited by size, not by count. Lyrics keep the last 40 songs.
- **Memory pressure.** Both services release icons, artwork and most cached lyrics when Android reports a real shortage (not merely because the settings screen was closed). Everything refills on demand.
- **No idle threads.** The two lyric-lookup workers exit after 20 s without work instead of living for the whole process.
- **No redundant image work.** A repeated artwork refresh no longer re-scales the picture, re-reads its colour or re-uploads its texture.

## Install

Grab an APK from [Releases](https://github.com/vxyzview/VoidIsland/releases) and sideload it (Android 8+ / API 26+).
Then enable both accessibility services VoidIsland asks for, so it can see notifications and the shade.

## Keeping it running

Some phone makers (Xiaomi/Redmi/Poco, Oppo/Realme/OnePlus, Vivo, Huawei/Honor, Samsung, Asus) close background apps to save battery, and then the island disappears.
On those phones the app shows a one-time hint after setup, and **Home > Setup** gets an *Autostart & background* item that opens the maker's own screen when it can
(otherwise the app info page). Also set **Battery: unrestricted** (it turns into a tick once the system agrees). Those screens are private to each ROM and change between versions, so
if the button does not land on the right page, open *Settings > Apps > VoidIsland* and allow autostart / background activity there.

## Animations

The island follows Android's animation settings (*Developer options > Animator duration scale*, and *Remove animations* in accessibility): a faster or slower scale speeds up or slows
down the springs, and scale 0 makes the island jump straight to its next shape with no motion.

## Lyrics & privacy

Lyrics on the music pill are looked up at runtime on [LRCLIB](https://lrclib.net) (open, community-run, no account or key). For each new song the app sends LRCLIB the **song title, artist
and length** (as shown by your music player), along with the app name in the `User-Agent`. Nothing else leaves the phone. Lyrics are not bundled with the app and are never written to storage:
they are kept in memory for the current session only (up to 40 songs) and shown on the island, nothing more. The lyrics belong to their rights holders, and LRCLIB's own terms apply to the
service; check them before reusing or redistributing anything you see. Turn it off at **Behavior > Music lyrics** and no lookups are made.

## Building yourself

Local build needs an `android.jar` (API 34) at `libs/android-34.jar`, a JDK, and `aapt` + `d8` (or `dx`) + `apksigner` + `zipalign`, then:

```sh
./build.sh
```

The signing key is not included in the repo: `build.sh` creates a new one (`void.jks`) on first run. CI signs with the
keystore kept in the `SIGNING_KEYSTORE` repository secret (base64 of `void.jks`), so CI builds have one consistent
signature; note your locally generated key differs from the CI one unless you upload the same file.

## Releases via GitHub Actions

The `Build APK` workflow builds and signs on every trigger:

- push a tag **`v1.2`** → stable → attached to a regular **Release**
- push a tag **`v1.2-beta.1`** / **`v1.2-alpha.1`** → pre-release
- `Run workflow` (Actions tab) → pick `beta` / `alpha` / `stable`; beta/alpha become pre-releases, stable bumps the next patch from the latest tag

`versionCode` is a monotonic build number (10 × commit count; pre-releases 5 points below), `versionName` comes from the tag.

## Tests

Unit checks for the pure-Java logic live in `test/` (Spring, Parse, Col, Geo, ShadeMatch). CI runs them before every
release, and locally you can run them after building:

```sh
javac -d out/test -cp out/classes $(find test -name '*.java')
java -cp out/test:out/classes com.vxyzview.voidisland.Check
```
