# Contributing to VoidIsland

Thanks for wanting to help. Bug reports, translations, new island styles and small fixes are all welcome.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md), and that your contribution is released under the [MIT License](LICENSE).

## Ways to help

| I want to... | Do this |
| --- | --- |
| Report a bug | Open a [bug report](https://github.com/vxyzview/VoidIsland/issues/new/choose). Add the diagnostic log (**Behavior > Help > Diagnostic log > Copy**). |
| Suggest something | Open a [feature request](https://github.com/vxyzview/VoidIsland/issues/new/choose). |
| Report a security problem | Follow [SECURITY.md](SECURITY.md). Please do not open a public issue. |
| Translate the island | Copy `res/values/strings.xml` to `res/values-xx/strings.xml` (`xx` is the language code) and translate the text. `res/values-in/` is a ready example. Run `tools/check-strings.sh` afterwards: it lists missing keys and `%` placeholders that do not match. |
| Fix or build something | Fork, branch, change, open a pull request (see below). |

## Set up

VoidIsland is plain Java and has no Gradle. You need a JDK, an `android.jar` for API 34 at `libs/android-34.jar` (or the `android-34` platform in `$ANDROID_HOME`), and the Android build tools `aapt`, `d8` (or `dx`), `zipalign` and `apksigner`.

```sh
./build.sh
```

The first run creates a local signing key `void.jks` and its password file `void.jks.pass`. Both are git-ignored. Never commit them.

## Run the unit checks

```sh
javac -d out/test -cp out/classes $(find test -name '*.java')
java -cp out/test:out/classes com.vxyzview.voidisland.Check
```

To add an area, write an `XCheck.run()` class in `test/` (use `import static com.vxyzview.voidisland.Check.ok;`) and list it in `Check.main`.

## Code guidelines

- **Keep logic testable.** Rules that can be pure Java (`Privacy`, `Gate`, `Dedupe`, `Rules`, `Backup`, `Lyrics`...) must not touch Android types. Put the check next to it in `test/`.
- **Privacy first.** Notification text, reply text and message content are never written to storage, logged, put in a backup or sent anywhere. The diagnostic log records where and what kind of error, never text.
- **One network file.** All network code lives in `LyricsNet.java`. Do not add new network use without discussing it in an issue first.
- **Stay light in the background.** Respect the display-off and memory-pressure rules described in the README. Do not add idle threads or timers that run while the screen is off.
- **Flat design.** New styles use solid fills and thin lines, no gradients, and follow the accent colour (Material You or custom).
- **Follow Android's animation settings** so scale 0 still makes the island jump straight to its next shape.
- Match the surrounding code style, keep changes focused, and avoid unrelated reformatting.

## Test on a phone

The unit checks cover pure logic only. For anything the island draws or reads from the system, walk through the relevant sections of [TESTING.md](TESTING.md), ideally on Android 12 and on Android 14 or newer. Say in your pull request what you tested on.

## Pull requests

1. Fork the repo and create a branch from `main` (`fix-reply-box`, `style-clock-orbit`...).
2. Make the change, with a unit check if it is logic.
3. Run `./build.sh` and the unit checks.
4. Open a pull request and fill in the template.

Commit messages: a short imperative first line (`Fix reply box overlapping the keyboard`), then details if needed.

## Releases

Maintainers release by pushing a tag. See *Releases via GitHub Actions* in the README.
