# Manual test list (run before a release)

The unit checks (`test/`) cover the pure logic. Everything below needs a phone: try it on Android 12 and on 14 or newer.

## Setup
- [ ] Fresh install: Accessibility and Notification access can be enabled (Android 13+: after *Allow restricted settings*).
- [ ] Island shows the idle clock; "Try it" demos (music, call, payment, charge, notification, download, timer, headset, Bluetooth, navigation, ride, ringer) all appear and go away.
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

## Hiding, unhiding and Choose apps
- [ ] *Hide idle clock in apps* off, *Hide when an app is fullscreen* **off**: open apps of every kind (also video, games): the idle clock never disappears.
- [ ] *Hide when an app is fullscreen* on, *Hide idle clock in apps* off: the clock hides only while an app is really fullscreen, and returns when it ends.
- [ ] Music playing, then switch that music app off in *Choose apps*: its pill leaves at once (not after the song ends) and does not come back frozen.
- [ ] Switch the same app on again: its pill is back and updates normally.
- [ ] Toggle any app in *Choose apps* (or drag *Banner time*) while a quick-reply box is open: the box stays, the island does not blink.
- [ ] Tap the idle clock to open it, then open an app with *Hide idle clock in apps* on: the card goes away and does not come back by itself when you return home within 8 s.
- [ ] Three message banners queued, then open a fullscreen video before they show: when the video ends the old banners do **not** pop up late, and their originals are still in the shade.
- [ ] A chat whose queued banner was dropped this way: its next message shows a banner (it is not silenced for 5 minutes).
- [ ] (With *Show on lock screen* on) lock the phone while a second banner is queued: it still comes out redacted.

## Calls, timers, music
- [ ] Incoming call popup, accept / decline, ongoing call timer. Ending the call on a paired watch removes the pill.
- [ ] Clock-app stopwatch / timer show and tick; pausing it stops the island's timer within a couple of seconds.
- [ ] Music pill and card, seek, lyrics; skip through five songs quickly: only the song you stay on is looked up.
- [ ] Demo song for 30 s, then your real music comes back by itself.

## Activity cards (flat look, compact and expanded)
- [ ] Try it > Call: the incoming card has rings swelling out of the photo; *Accept* makes an ongoing call with a timer capsule and three buttons; Speaker / Mute flip their labels, Hang up ends it. The pill shows photo, name and timer.
- [ ] Try it > Payment: check ring, amount, time and a copy button; tapping it puts the amount on the clipboard and the icon turns into a tick. With the lock screen hiding content there is no amount and no copy button.
- [ ] Try it > Charging: the bar fills in, plug type and temperature chips, \"min to full\" when Android has an estimate. Unplugged at 15% or less: a red card with *Charge soon*.
- [ ] Try it > Download: icon with a download badge, percent and bar move, a \"time left\" chip appears after a few seconds, *Cancel* stops it. The pill shows icon, title and a ring with the percent.
- [ ] Try it > Timer: the ring drains and \"Ends\" shows the time, *+1 min* makes the ring jump and the end time move, *Stop* removes it. At 0:00 the ring and time turn red. A stopwatch sweeps one lap a minute.
- [ ] Try it > Headset / Bluetooth / Ringer, then for real: plug a wired headset, connect Bluetooth devices of different kinds (the icon follows the kind), flip the ringer switch. Each card shows its state; the ringer's pips light the right mode.
- [ ] Flashlight: switch the torch on; the pill shows a bolt and *On*; tapping it shows *Turn off*, which really switches the torch off.
- [ ] Try it > Navigation / Ride: they open as the navigation card and the ride card (car badge, bar, *Cancel ride*).
- [ ] Every card in light and dark theme, with Material You and a custom accent, at the smallest and largest island width and with large text: nothing clipped, no gradients, text readable.
- [ ] Display off while a call rings: the rings stop; they resume when the display comes on.

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

## Known behaviour and log noise (not bugs in this app)

- **Icon-only pill after a notification.** A live pill (call, timer, navigation, ride / food / score, download) stays on the
  island for as long as its source notification is in the shade, by design. When that notification leaves the shade (swiped,
  cancelled, or snoozed) the pill is removed and the island goes back to the idle clock. Plain message banners are different:
  they always end on their own timer. If a pill lingers, check `adb shell dumpsys notification --noredact` for the source.
- **Idle memory.** After a minute on the idle clock the icon, artwork and lyric caches are released (`IslandController.idleTrim`),
  and closing the Settings screen releases the icon cache. Re-measure with `adb shell dumpsys meminfo com.vxyzview.voidisland`
  after a few minutes idle; the native heap that remains is mostly the overlay window's own render buffers.
- **Animation smoothness.** The end of an expand / collapse no longer rebuilds the window in the same frame the springs finish
  (it runs 160 ms later). Check with `adb shell dumpsys gfxinfo com.vxyzview.voidisland reset`, expand and collapse a banner ten
  times, then `dumpsys gfxinfo com.vxyzview.voidisland` and compare the 99th percentile with the previous build.
- **`SURFACE DESTROY` RuntimeException lines and `NotificationHistoryDatabase ... Icon.getType()` NPE in logcat.** These come
  from the system server relaying out the "displaying over other apps" channel when an overlay window is resized. They are
  logged by Android, not thrown in this process, and do not affect the island. Likewise the single "splash input channel
  disposed" warning on relaunch.
- **Bluetooth permission.** Never requested at launch: only when the person taps "Allow" on the Bluetooth row in Setup.
  If a dialog shows at first start, something other than this app asked for it.
