# VoidIsland

Dynamic Island for Android (by vxyzview) — notifications, media and calls on a floating island. Pure Java, no Gradle.

## Features

- Notifications, calls, music, timers, navigation and downloads on a floating island.
- **Lyrics** on the music pill: one line at a time; long lines slide sideways in step with the song (pause, seek and all).
- **Only on the island** (opt-in, off by default): messages and payments shown on the island are removed from the shade. Apps on the *Keep in shade* list keep theirs. An original is only ever removed while the island is running and really showing that banner; if the island is off, hidden (landscape, lock screen, shade open) or ignoring the app, the notification stays in the shade. Android cannot bring back a notification that was already removed, so a banner you miss is gone.
- **Quick reply**: expand a message banner and tap *Reply* to answer without opening the app.
- **Island rules**: quiet hours (optionally only for chosen apps), mute group chats, one banner per chat.
- **Live activities**: rides, food delivery and live scores stay on the island while they run.
- **Material You accent on a black island**: on Android 12+ the accent follows your wallpaper colours by default (play button, seek / progress bars, rings, action buttons, glyphs, in both the compact pill and the expanded card) while the island itself stays pure black. Change it at **Style > Colors > Accent** (*Auto* = album-art / app colour, *Material You*, or *Custom*).

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
