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

## Calls, timers, music
- [ ] Incoming call popup, accept / decline, ongoing call timer. Ending the call on a paired watch removes the pill.
- [ ] Clock-app stopwatch / timer show and tick; pausing it stops the island's timer within a couple of seconds.
- [ ] Music pill and card, seek, lyrics; skip through five songs quickly: only the song you stay on is looked up.
- [ ] Demo song for 30 s, then your real music comes back by itself.

## Backup
- [ ] Back up, change settings, restore: settings come back; "Only on the island" stays off until re-enabled.
- [ ] Restore a file with a wrong value type (edit `b|dur|true` into the backup): the island still starts, that setting uses its default.

## Languages and accessibility
- [ ] Phone set to Bahasa Indonesia: island texts are Indonesian.
- [ ] TalkBack on: a new banner is read out; the reply box buttons have names.

## Help
- [ ] Behavior > Help > Diagnostic log opens, *Copy* works, *Clear* empties it.
