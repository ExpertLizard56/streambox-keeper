# StreamBox Keeper (public)

StreamBox Keeper, for any Google TV / Android TV box. Any AI tool starts here (Claude Code reads it through
`CLAUDE.md`). Made Nov 8 from the owner's private app. Since Nov 14 there is only this one version: the owner's boxes
run it too, set up on its own screen like anyone's. A home-only need goes in a separate app (the box health report is
Home Watch's box app, which reads `StatusProvider`), never into this one.

## Rules

- **Nothing from the owner's home goes in here:** no addresses, names, device lists, or home apps.
  This repository is meant to be public.
- Same package for good: `io.github.expertlizard56.streamingboxkeeper`. Releases are signed with the release key
  (`keystore.properties`, never committed). Losing it means installed copies can't be updated.
- Every feature has a switch (`Features`); app updates are off until the person turns them on.
- Onn-only behaviour (its remote's TV and star buttons) only on an Onn box (`LinkService.ONN`).
- Test on the Onn box emulators first (`scripts/tv-emulator`); real boxes need no ask,
  and only after checking nobody's using them.
- Commit with explicit paths, ending with a line naming the AI and model.

## Checks (free, on commit and push)

`lefthook.yml` runs these by themselves, so they cost no AI tokens; set them up once per checkout with `lefthook
install`. A failed check stops the commit or push: fix what it says, and never skip it (`--no-verify`, `LEFTHOOK=0`).
- **On every commit**: Gitleaks refuses new lines holding a password, token or key (this repo will be public).
  A false alarm: a `gitleaks:allow` comment on that line.
- **On commit**, when Java, XML or Gradle files are staged: Android Lint on the app (`./gradlew --console=plain -q
  :app:lintDebug`). Lint already fails on an error (`lint { abortOnError true }` in `app/build.gradle`), so a new
  finding is fixed in the commit that brings it. On Nov 9 it reports 0 errors and 5 warnings: targetSdk 34's old age,
  `Check`'s exact-alarm permission, two `commit()` calls in `Jobs`, and one `SetTextI18n` in `MainActivity`. Warnings
  don't stop a commit, so they stay unless the change is about them.
- **On push**, when Java is pushed: the app's unit tests (`./gradlew --console=plain -q :app:testDebugUnitTest`,
  `app/src/test`). They are plain Java, no Android started for them (`testOptions.unitTests.returnDefaultValues`), and
  cover the parts that need no box: version comparison, which update sources are accepted, the SHA-256 check, streams.
- **LeakCanary** is in debug builds only (`debugImplementation` in `app/build.gradle`): a debug build on an emulator
  reports memory leaks. It is never in a release build, which is what people install.
- The private build gets all of this by building this repository's code, so what has to pass there is its own
  `lintDebug` and `assembleRelease` (with its own folder's hooks).

## Map (app/src/main/java/io/github/expertlizard56/streamingboxkeeper)

| File | What |
| --- | --- |
| `MainActivity` | The app's screen: set-up steps, feature switches, last done |
| `Features` | The switches |
| `Check` | 5-minute wake-up alarm: Keep my settings first (`KeepSettings`), then Wi-Fi rejoin, then the Google Assistant re-check (`Assistant`), then Jobs, then the daily self-update (when app updates are on and it's on their list) |
| `Jobs` | Nightly cleanup, weekly restart (one-touch play off just for the restart); runs the app updates |
| `AppSources`, `AppsActivity` | App updates for any app: a Downloader code (its aftv.news page names the link), a GitHub page (newest release, the file for this box's processor) or a direct .apk link; only the same package, newer, installs. It installs when that app isn't in use (`inUse`: on screen while awake, or playing sound, from one `IN_USE` shell call), asleep or not. `KNOWN` apps (SmartTube, Moonlight, Home Watch) go on the list once, when first seen on the box, even after setup (`knownOffered`); one taken off stays off |
| `Updater` | Self-update from this repository's GitHub releases (`streambox-keeper-<version>.apk`), for StreamBox Keeper's own listing in the app updates (AppSources skips it); writes that listing's status |
| `SelfAdb` | The box's own ADB (127.0.0.1:5555), allowed once on the box |
| `Assistant` | Google Assistant switched off (switch `ASSISTANT`, off until turned on), so the remote's mic button can't start it (on Google TV it does so from the remote's own voice signal, which no key filter sees): `pm disable-user --user 0 com.google.android.katniss`, then, only where `com.google.android.tts` is installed, Google TTS's recognition service put in `voice_recognition_service`, so apps' voice search keeps the mic (tested by hand on an Onn 4K Plus Nov 14). Back on: `pm enable`. Check re-reads both in one shell call at every wake-up and redoes whichever a system update undid. If the mic then sends no sound at all, the remote needs pairing again (the screen says so) |
| `KeepSettings`, `KeptSettings` | Keep my settings (switch `KEEP_SETTINGS`, off until turned on): after a firmware update (`ro.build.fingerprint` changed) puts back match_content_frame_rate, hdr_conversion_mode (takes effect at the next restart; the box isn't restarted for it), screensaver_components, the Home app (`cmd package set-home-activity`) and its own LinkService entry in the accessibility list (other services never touched). Same fingerprint: the box's current values become the remembered ones, so the person's own changes stand. Nothing built in; an unreadable value is skipped, never written blank. Runs first in each Check and when switched on; `KeptSettings` is the plain-Java decision (unit tested), `KeepSettings` reads in one SelfAdb call and writes. Last put-back in `lastSettingsFix` |
| `InstallFor` | Another app hands over its own newer version (broadcast `…INSTALL_UPDATE`, APK as a content: link, a PendingIntent for the answer) and it goes in through SelfAdb: an ADB install, which Google's 2027 developer verification leaves alone. Only the asker's own package, installed, same key, newer (switch `INSTALL_FOR_APPS`, on by default). Tested on the emulator Nov 9 with a TV app update (300 to 308) |
| `LinkService` | The TV remote (accessibility service): forwards keys while the TV is elsewhere, the Input button |
| `InputKey`, `InputKeyActivity` | Which button opens the input menu: the Input button (screen-with-arrow symbol, `res/drawable/input_button.xml`, drawn into text by `InputKey.label`) and ★ always (the same symbol on different remotes: MACRO_1 on an Onn remote, FEATURED_APP_1 on others, so on an Onn remote FEATURED_APP_1 — its "Free TV" button — is not Input), plus an Onn remote's TV button; someone with another remote adds its button by pressing it (arrows, OK, Back, Home, power, volume can't be). While that screen is open LinkService takes no keys |
| `RemoteSetup` | The remote's own power, volume and Input buttons as Google TV set them up (`dumpsys activity service com.google.android.tv.axel/.remote.IrService` through SelfAdb: NONE, IR, CEC, BROADCAST or a device name); the main screen's step opens Google TV's own wizard (`com.google.android.tv.IR_WIZARD`); warns when Input or ★ ("Magic", anything but "Software [...]") is set to switch the TV by infrared while it also opens the menu. Not on the emulator (no axel) |
| `RemoteMic` | A remote whose buttons work but whose mic Google TV never set up (paired during first setup, Onn 4K Plus Nov 14: voice search heard nothing): a bonded LE device named like a remote ("Remote" in its name) whose address is missing from, or not READY in, the remote service's "BLE remotes:" list (`dumpsys bluetooth_manager` and `dumpsys activity service com.google.android.tv.remote.service/.RemoteService`, one SelfAdb call). The main screen then says to forget the remote and pair it again; nothing when there's no remote service (the emulator) |
| `RemapApps` | A button-remapping app (Button Mapper...) gets the remote's buttons, except the Input button: the CleanYourTV home screen's own button helpers are switched off while one is on. Android hands each key to every key-filtering app at once, so a remapper mapped to the Input button acts too; if that pushes the input menu away within 0.9 s, LinkService opens it again |
| `BlockedKeys`, `BlockedKeysActivity` | Disable remote buttons (an app button for an app that isn't installed): added on their own page by pressing them, each one a card that removes it; LinkService takes them first, after the Input button. The buttons InputKey refuses, and Input itself, can't be added |
| `StatusProvider` | Read only, for another app on the box (a health monitor): `content://io.github.expertlizard56.streamingboxkeeper.status`, rows of name and value (version, lastTidy, lastRestart, lastAppUpdate, lastSettingsFix, tvRemote, tvRemoteProblem). Nothing private goes in it: any app can read it |
| `Tv`, `Roku`, `FireTv`, `TvConfig`, `TvFinder` | The TV: which one (found by itself), and talking to it |
| `InputPanelActivity`, `SetupActivity` | The input menu; finding and picking the TV |
| `Screen` | The one look of the app's own pages (title, line, cards with a white edge when chosen), like Apps To Keep Updated: every new page uses it |
| `app/firekeys` | FireKeys, kept running on a Fire TV for fast input switching (build.sh puts it in assets) |
| `app/src/test` | The unit tests the push hook runs: `Jobs.compare`, `AppSources.validSource`, `Updater.sha256`, `Assistant.parse`, `Streams`, `BlockedKeys`, `RemoteMic.parse`, `KeptSettings` |

## Releasing

Raise `versionCode` and `versionName` in `app/build.gradle`, build with the release key, and attach
`streambox-keeper-<versionName>.apk` to a GitHub release tagged `v<versionName>`. Installed copies with "Update
StreamBox Keeper" on pick it up within a day.

## Emulator notes

`adb shell input keyevent` (any source) never reaches LinkService's key filter, and the emulator console's key events
don't arrive either, so what a remote button does there is only tested with a real remote. The `onn_s905x5m` emulator
(Android 14) does show the "Allow USB debugging?" question (Nov 14: Ask now, tick Always allow, allow it), so system
access (and with it installing and switching the remote on in one press, and the Assistant switch) can be tested there.
The main screen only makes the box ask when Ask now is pressed (`SelfAdb.knownAllowed`).

## Real box notes (Onn 4K Pro, Android 14)

- Testing a remote button without a remote: the box's own `uinput` tool makes a pretend remote whose presses go through
  the key filter like a real one (`input keyevent` doesn't). A file of JSON lines, run as `uinput <file>`; the
  configuration types are numbers (100 = UI_SET_EVBIT with data [1], 101 = UI_SET_KEYBIT with the Linux key codes):
  `{"id":1,"command":"register","name":"Test remote","vid":4660,"pid":22136,"bus":"usb","configuration":[{"type":100,"data":[1]},{"type":101,"data":[264]}]}`,
  then `{"id":1,"command":"delay","duration":1500}`, `{"id":1,"command":"inject","events":[1,264,1,0,0,0]}`, a 100 ms
  delay and the release (`[1,264,0,0,0,0]`). Linux 264/265 are BUTTON_9/BUTTON_10 (Generic.kl), the Onn remote's app buttons.
- An Onn remote's app buttons (keycodes 196, 197...) are taken by `com.droidlogic.GlobalKeyReceiver`, which opens the
  app or its Play Store page before any window sees them; only the key filter (LinkService) gets them first. So
  InputKeyActivity and BlockedKeysActivity get their presses from LinkService while open (0.1.1).
- "Allow USB debugging?" jam: an ask the person didn't start (right after install, the box asleep) can leave adbd with
  "prompt currently pending, skipping" and no question on screen; every later ask then hangs. A reboot clears it, and
  the question shows at boot. Turn one-touch play off for that reboot or the TV wakes, and back on after.
- Wi-Fi asleep: the box's chip (rtl8852bs) never looks for the network again after an access point restarts, and
  Android ignores an app's own scan request, so Check asks for `cmd wifi start-scan` through SelfAdb (back within 3
  minutes in a test). Offline is read from the interfaces, not ConnectivityManager, which hides the network from a
  background app on a sleeping box. Can't be tested on an emulator.
- Deep sleep (Doze) gives a wake-up alarm about 10 s of network, the box's own ADB included, so network jobs first ask
  for 10 minutes (`SelfAdb.allowNetworkWhileAsleep`). A test broadcast to a box in deep sleep (`dumpsys deviceidle`,
  mState=IDLE) times out unless it's first given `cmd deviceidle tempwhitelist -d 10000 <package>`.
- From the PC, `input keyevent` on the box never reaches the TV, so what goes to the TV is only proven by a real press.
