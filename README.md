# VoidIsland

Dynamic Island for Android (by vxyzview) — notifications, media and calls on a floating island. Pure Java, no Gradle.

## Install

Grab an APK from [Releases](https://github.com/vxyzview/VoidIsland/releases) and sideload it (Android 8+ / API 26+).
Then enable both accessibility services VoidIsland asks for, so it can see notifications and the shade.

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
