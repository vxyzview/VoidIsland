# Screenshots

The README gallery (commented out until the images exist) expects these files in this folder. Everything below can be
produced with the **Home > Try it** buttons, so no real notifications or accounts are needed.

| File | What to capture | How |
| :-- | :-- | :-- |
| `banner.png` | A message banner | Try it > **Message** |
| `reply.png` | The quick-reply box grown out of a banner | Try it > **Message**, tap the banner, tap *Reply* |
| `music.png` | Compact music pill, then the expanded card | Try it > **Music** (switch styles under *Style > Music*) |
| `clock.png` | Idle clock pill and expanded card | Leave the island idle (styles under *Style > Idle clock*) |
| `call.png` | Incoming call card | Try it > **Call** |
| `live.png` | A live activity (download or timer) | Try it > **Download** or **Timer** |
| `settings.png` | The settings home | Open the app |

## Capturing

Use a phone with a pure-black wallpaper area behind the island so the shape reads clearly, and capture over USB:

```sh
adb exec-out screencap -p > assets/screenshots/banner.png
```

Tips: keep the status bar clean (Developer options > *Demo mode*, or just a full battery and no stray icons), use the
same phone for every shot, and crop to the top third for banner-style images. Keep each PNG under about 300 KB
(`pngquant --quality 70-90` or `oxipng -o4` are fine).

## Then turn the gallery on

In `README.md`, remove the `<!--` and `-->` around the gallery block under the banner.
