# Manual test list (run before a release)

The unit checks (`test/`) cover the pure logic. Everything below needs a phone: try it on Android 12 and on 14 or newer.

## Setup
- [ ] Fresh install: Accessibility and Notification access can be enabled (Android 13+: after *Allow restricted settings*).
- [ ] Island shows the idle clock; "Try it" demos (music, call, payment, charge, notification, download, timer, system) all appear and go away.
- [ ] Rotate the phone with a banner showing: the island recovers.

## Banners and quick reply
- [ ] A chat message shows a banner with **Reply**; tapping it grows the box out of the banner's bottom edge, keyboard opens.
- [ ] Send: arrow turns into a tick, banner and box leave, the message really arrives in the chat app.
- [ ] Cancel button, back, tap outside, swipe the banner up: box and banner leave.
- [ ] Tap the banner / another action button while the box is open: box closes, app or action runs.
- [ ] While the box is open a **newer message in the same chat** arrives: the card updates and the box stays attached to its bottom edge (also when the new text is longer or shorter).
- [ ] A message from a different chat arrives while the box is open: it waits, and shows afterwards.
- [ ] A ringing call arrives while the box is open: the call takes over, the box closes.
- [ ] Screen off / home during the box: it closes by itself. A forgotten box lets go after 3 minutes.
- [ ] Lock screen with "Show on lock screen" on: banners show **no** Reply button.

## Only on the island (opt-in)
- [ ] Switch on, confirm the dialog: a shown banner's original leaves the shade *after* it appears on screen.
- [ ] While a video is fullscreen the notification stays in the shade.
- [ ] A burst of 5 messages: queued ones are not removed until they are shown. All of them appear in **Recent banners**.
- [ ] Apps on *Keep in shade* keep their notification.
- [ ] With the island idle (nothing else showing), the **first** message banner's original leaves the shade right after the banner appears (it used to stay, because the removal was armed too late).
- [ ] *Rules > Stock pop-up*: tapping an app opens that app's Android notification settings; after switching off *Pop on screen* there, the island shows the message and no system pop-up appears.
- [ ] A chat app that marks messages read on dismiss: note whether the message shows as read after the island absorbed it.

## Lock screen privacy
- [ ] PIN / pattern set, phone locked, *Show on lock screen* on: a message from a normal chat app shows as usual (Android's *Show all notification content*).
- [ ] Settings > Notifications > Lock screen > *Hide sensitive content*, then a message: the banner shows the app name and \"Content hidden\", no text, no Reply, no action buttons.
- [ ] A notification the app marks secret (e.g. a banking / OTP app, or a channel set to *Don't show* on the lock screen): no banner at all, and its original is still in the shade after unlocking.
- [ ] *Only on the island* on, phone locked, a redacted banner shows: after unlocking the original notification is still in the shade.
- [ ] Swipe-only lock (no PIN): banners show in full.
- [ ] Lock the phone while a second banner is queued: the queued one comes out redacted, not in full.

## Calls, timers, music
- [ ] Incoming call popup, accept / decline, ongoing call timer. Ending the call on a paired watch removes the pill.
- [ ] Clock-app stopwatch / timer show and tick; pausing it stops the island's timer within a couple of seconds.
- [ ] Music pill and card, seek, lyrics; skip through five songs quickly: only the song you stay on is looked up.
- [ ] Demo song for 30 s, then your real music comes back by itself.

## Backup
- [ ] Back up, change settings, restore: a confirmation asks first (Cancel changes nothing); after *Restore* the settings come back; \"Only on the island\" stays off until re-enabled.
- [ ] Restore a text file that is not a backup, and a backup with only unknown keys: the island reports it and no setting changes.
- [ ] Restore a file with a wrong value type (edit `b|dur|true` into the backup): the island still starts, that setting uses its default.

## First run and lyrics
- [ ] Fresh install, first launch: the \"Lyrics on the music pill\" notice shows once and cannot be dismissed with back; *Keep on* keeps lyrics, *Turn off* switches *Behavior > Music lyrics* off. It does not come back after either answer.
- [ ] After a restore on a phone that already answered the notice, it does not show again.

## Android 14 (target 34)
- [ ] Banner buttons and the banner itself open their apps from the background; Reply box opens (or falls back to the app).
- [ ] System broadcasts still reach the island: plug / unplug the charger, toggle the ringer, connect headphones, change Do Not Disturb.

## Languages and accessibility
- [ ] Phone set to Bahasa Indonesia: island texts are Indonesian.
- [ ] TalkBack on: a new banner is read out; the reply box buttons have names.

## Help
- [ ] Behavior > Help > Diagnostic log opens, *Copy* works, *Clear* empties it.
