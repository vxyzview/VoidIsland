# Security policy

## Supported versions

Only the [latest release](https://github.com/vxyzview/VoidIsland/releases/latest) gets fixes. Please update before reporting.

## Reporting a vulnerability

Please report problems **privately**, not in a public issue:

1. Open [**Report a vulnerability**](https://github.com/vxyzview/VoidIsland/security/advisories/new) on the repository's Security tab.
2. Describe what happens, how to reproduce it, the Android version and the phone.
3. Do not include real message text, numbers or other personal data.

I will read it as soon as I can, tell you whether I can reproduce it, and credit you in the release notes if you like.

## What counts

VoidIsland reads notifications and music state and draws over other apps, so these are the areas that matter most:

- A notification marked *secret*, or a redacted one, showing its content on the island (see *Lock-screen privacy* in the README).
- Notification text, replies, the Recent banners list or the diagnostic log being stored, logged, backed up or sent anywhere.
- Anything other than the song title, artist and length (and the app name in the `User-Agent`) leaving the phone.
- A banner, button or intent that lets another app trigger actions it should not be able to.
- A backup file that can change something it should not, or that carries message content.
- Problems with the release signing process.

Android's own behaviour, such as an app marking a message read when its notification is dismissed, is documented in the README and is not a vulnerability in VoidIsland.
