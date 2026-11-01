# VoidIsland

Dynamic Island for Android (by vxyzview) — notifications, media and calls on a floating island. Pure Java, no Gradle.

## Features

- Notifications, calls, music, timers, navigation and downloads on a floating island.
- **Lyrics** on the music pill: one line at a time; long lines slide sideways in step with the song (pause, seek and all).
- **Only on the island** (opt-in, off by default): messages and payments shown on the island are removed from the shade. Apps on the *Keep in shade* list keep theirs. An original is only ever removed once the island has really put that banner on screen (not when it is merely queued); if the island is off, hidden (fullscreen app, lock screen, shade open) or ignoring the app, the notification stays in the shade. Android cannot bring back a notification that was already removed, so a banner you look away from would be gone: **Behavior > Help > Recent banners** keeps the last 20 banners the island showed (in memory only) and opens the app when you tap one.
- **Quick reply**: expand a message banner and tap *Reply* to answer without opening the app. The reply box grows out of the banner's bottom edge as part of the same black shape (same width, same corner radius, same border, no seam), with a rounded text field, a cancel button and a round send button in the island's accent. While it is open the banner is held: it does not time out and no newer banner replaces it. Banner and box leave together when the reply is sent (the arrow turns into a tick first), when you cancel (cancel button, back, tap outside, swipe the banner up), or when you press another button on the banner or tap the banner to open the app. A banner you give up on carries on as a normal one for a few more seconds; a forgotten box lets go after 3 minutes without typing.
- **Diagnostic log**: **Behavior > Help > Diagnostic log** lists the last errors the island caught (where and what kind, never any message text) with a *Copy* button for bug reports. Memory only, gone when the process ends.
- **Languages**: the text the island itself shows (banners' buttons, call and payment titles, battery / ringer / Bluetooth pop-ups, the reply box) is translated (English, Bahasa Indonesia); the settings screens are still English. Add a language by copying `res/values/strings.xml` to `res/values-xx/`. Banners are announced by TalkBack.
- **Island rules**: quiet hours (optionally only for chosen apps), mute group chats, one banner per chat.
- **Live activities**: rides, food delivery and live scores stay on the island while they run. Missing an app? Add it under **Apps > Ride / Food / Score apps**.
- **Hides only when it should**: the island stays visible everywhere, landscape included, and steps aside only while an app is fullscreen (video, game, reader: anything that hides the status bar). Turn that off under **Behavior > Rules**. The idle clock can also stay visible inside apps (turn off *Hide idle clock in apps*) and still disappears in fullscreen.
- **Idle clock styles**: pick how the clock looks under **Style > Idle clock**.
  - *Compact pill* (10): *Dial* (mini dial whose ring fills with the minutes, time and date), *Center* (just the time with a small AM / PM), *Split* (hour left, minutes right, a camera hole in between), *Date* (day on the left, time on the right), *Line* (centred time over a slim line whose round head travels the hour; always drawn, the text shrinks to fit short pills), *Capsule* (the time in a filled accent capsule), *Sky* (a sun, sinking sun or moon for the part of the day, plus the time), *Aura* (digits that fade from your accent into white), *Dots* (the week as seven dots with today lit, plus the time) and *Beacon* (a glowing lamp inside a ring that fills with the day, plus a softly glowing time and how much of the day has gone).
  - *Expanded card* (8): *Classic*, *Digital*, *Analog*, *Bold*, *Week*, *Rings* (three activity rings for the day, the hour and the minute), *Flip* (a flip clock with a seconds line) and *Horizon* (the sun, or the moon at night, travelling an arc over the time).
- **Music styles**: the music is not one design but several, picked under **Style > Music** (the island shows the new look right away, with the demo song if nothing is playing).
  - *Compact pill* (5): *Classic* (round-cornered art, the lyric line or four bars), *Vinyl* (a little record that spins while the song plays), *Ring* (round art inside a ring that fills as the song goes on), *Wave* (round art and a wide rolling waveform, the lyric line takes its place when the song has one) and *Song* (the title and artist on the pill instead of lyrics, and no lyric lookups at all).
  - *Expanded card* (5): *Classic*, *Vinyl* (a record under a tonearm that lowers when the song plays and lifts when it pauses), *Poster* (big centred art in a soft accent frame), *Mini* (one slim row with a thin seek bar, the shortest card) and *Lyrics* (the previous, current and next line of the song in the middle of the card).
  - Every design follows the accent (Material You or custom), the seek bar can be dragged, the lyric nudge under **Behavior** applies, and nothing animates while the song is paused or the display is off.
- **Backup**: **Apps > Backup** saves every setting to a text file and restores it after a reinstall or on a new phone.
- **Battery**: timers stop while the screen is off, the music pill refreshes slowly when no lyric is moving, and songs LRCLIB has no lyrics for (and video / podcast apps) are not asked about again for an hour. A song the exact lookup misses is searched again by artist + title and by its cleaned-up title (no "feat." / "Remastered"), accepted only when its length fits.
- **Material You accent on a black island**: on Android 12+ the accent follows your wallpaper colours by default (play button, seek / progress bars, rings, action buttons, glyphs, in both the compact pill and the expanded card) while the island itself stays pure black. It follows the Material 3 dark-scheme roles: primary for the accent and filled buttons, secondary container for tonal buttons, tertiary for "good" things (accept, success, charging), error for "bad" ones (decline, low battery), and on-surface tones for text and tiles. Change it at **Style > Colors > Accent** (*Auto* = album-art / app colour, *Material You*, or *Custom*).

## Memory and background use

The island is a long-lived process, so idle cost matters more than peak speed:

- **Display off = quiet.** The once-a-minute clock broadcast is only registered while the screen is on, the equalizer bars stop redrawing, and the music card skips updates (it catches up the moment the screen lights up).
- **Equalizer bars** redraw at about 20 fps (was 30) and only while visible, playing and the display is on. The spinning record and the waveform of the music styles follow the same rule (about 25 and 20 fps), and a paused record draws once and sleeps.
- **Compact music pill** with lyrics ticks once a second instead of 2.5 times a second; the lyric view schedules its own line changes and slide, and a seek or pause re-anchors it immediately.
- **Bounded caches.** App icons (about 2 MB) and album artwork (about 1 MB) are limited by size, not by count. Lyrics keep the last 40 songs.
- **Memory pressure.** Both services release icons, artwork and most cached lyrics when Android reports a real shortage (not merely because the settings screen was closed). Everything refills on demand.
- **No idle threads.** The two lyric-lookup workers exit after 20 s without work instead of living for the whole process.
- **No redundant image work.** A repeated artwork refresh no longer re-scales the picture, re-reads its colour or re-uploads its texture.

## Install

Grab an APK from [Releases](https://github.com/vxyzview/VoidIsland/releases) and sideload it (Android 8+ / API 26+).
Then turn on the two things VoidIsland asks for: the **Accessibility** service (draws the island, sees the shade) and **Notification access** (reads notifications and music). On Android 13+ a sideloaded app needs *App info > ⋮ > Allow restricted settings* first, or those switches stay greyed out.

## Keeping it running

Some phone makers (Xiaomi/Redmi/Poco, Oppo/Realme/OnePlus, Vivo, Huawei/Honor, Samsung, Asus) close background apps to save battery, and then the island disappears.
On those phones the app shows a one-time hint after setup, and **Home > Setup** gets an *Autostart & background* item that opens the maker's own screen when it can
(otherwise the app info page). Also set **Battery: unrestricted** (it turns into a tick once the system agrees). Those screens are private to each ROM and change between versions, so
if the button does not land on the right page, open *Settings > Apps > VoidIsland* and allow autostart / background activity there.

## Animations

The island follows Android's animation settings (*Developer options > Animator duration scale*, and *Remove animations* in accessibility): a faster or slower scale speeds up or slows
down the springs, and scale 0 makes the island jump straight to its next shape with no motion.

## Privacy

Notification text, replies, the Recent banners list and the diagnostic log stay on the phone: nothing in them is ever sent anywhere, written to storage or included in a backup (a backup holds settings only). The app's only network use is the lyric lookup below, which can be switched off.

## Lyrics & privacy

Lyrics on the music pill are looked up at runtime on [LRCLIB](https://lrclib.net) (open, community-run, no account or key). For each new song the app sends LRCLIB the **song title, artist
and length** (as shown by your music player), along with the app name in the `User-Agent`. Nothing else leaves the phone. Lyrics are not bundled with the app and are never written to storage:
they are kept in memory for the current session only (up to 40 songs) and shown on the island, nothing more. The lyrics belong to their rights holders, and LRCLIB's own terms apply to the
service; check them before reusing or redistributing anything you see. Turn it off at **Behavior > Music lyrics** and no lookups are made.

## Compatibility and testing

Built for Android 8+ (API 26) and targeting API 33. Android 14+ restricts background activity starts (the island opts in when it sends a notification's own intents) and Android 13+ greys out accessibility / notification access for sideloaded apps until *Allow restricted settings* is ticked. Before a release, walk through `TESTING.md` on at least one Android 12 and one Android 14+ phone. Raising the target to 35 turns on forced edge-to-edge for the settings screens and the reply box, so it needs its own pass.

## Building yourself

Local build needs an `android.jar` (API 34) at `libs/android-34.jar`, a JDK, and `aapt` + `d8` (or `dx`) + `apksigner` + `zipalign`, then:

```sh
./build.sh
```

The signing key is not included in the repo: `build.sh` creates a new one (`void.jks`) on first run (password `voidisland` unless you set `KS_PASS`; CI must use the same password as the keystore in its secret). CI signs with the
keystore kept in the `SIGNING_KEYSTORE` repository secret (base64 of `void.jks`), so CI builds have one consistent
signature; note your locally generated key differs from the CI one unless you upload the same file.

## Releases via GitHub Actions

The `Build APK` workflow builds and signs on every trigger:

- push a tag **`v1.2`** → stable → attached to a regular **Release**
- push a tag **`v1.2-beta.1`** / **`v1.2-alpha.1`** → pre-release
- `Run workflow` (Actions tab) → pick `beta` / `alpha` / `stable`; beta/alpha become pre-releases, stable bumps the next patch from the latest tag

`versionCode` is a monotonic build number (10 × commit count; pre-releases 5 points below), `versionName` comes from the tag.

## Tests

Unit checks for the pure-Java logic live in `test/` (Spring, Parse, Col, Geo, ShadeMatch, Lyrics, Backup, the music style names). CI runs them before every
release, and locally you can run them after building:

```sh
javac -d out/test -cp out/classes $(find test -name '*.java')
java -cp out/test:out/classes com.vxyzview.voidisland.Check
```
