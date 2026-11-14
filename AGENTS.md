# Streaming Box Keeper (public)

The public version of Streaming Box Keeper, for any Google TV / Android TV box. Any AI tool starts here (Claude Code
reads it through `CLAUDE.md`). Made Nov 8 from the private app, which stays private and keeps running on the
owner's boxes.

## One app, two builds (since Nov 8)

This repository is the app. A private build may compile this repository's `app/src/main` as-is and swap
`app/src/edition` (the public build's empty `Edition`) for its own extras, so keep `app/src/main` self-contained.
Every feature and fix is made here, once, and both get it; a change that only
makes sense for the private build goes in its `Edition` instead. Keep `Edition`'s methods as they are (or
change both), and don't move shared code into `app/src/edition`. Building the private version also needs this
repository committed (its version adds both commit counts).

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
| `Check` | 5-minute wake-up alarm: Wi-Fi rejoin, then the Google Assistant re-check (`Assistant`), then Jobs, then the daily self-update |
| `Jobs` | Nightly cleanup, weekly restart (one-touch play off just for the restart); runs the app updates |
| `AppSources`, `AppsActivity` | App updates for any app: a Downloader code (its aftv.news page names the link), a GitHub page (newest release, the file for this box's processor) or a direct .apk link; only the same package, newer, installs |
| `Updater` | Self-update from this repository's GitHub releases (`streaming-box-keeper-<version>.apk`) |
| `SelfAdb` | The box's own ADB (127.0.0.1:5555), allowed once on the box |
| `Assistant` | Google Assistant switched off (switch `ASSISTANT`, off until turned on), so the remote's mic button can't start it (on Google TV it does so from the remote's own voice signal, which no key filter sees): `pm disable-user --user 0 com.google.android.katniss`, then, only where `com.google.android.tts` is installed, Google TTS's recognition service put in `voice_recognition_service`, so apps' voice search keeps the mic (tested by hand on an Onn 4K Plus Nov 14). Back on: `pm enable`. Check re-reads both in one shell call at every wake-up and redoes whichever a system update undid. If the mic then sends no sound at all, the remote needs pairing again (the screen says so) |
| `InstallFor` | Another app hands over its own newer version (broadcast `…INSTALL_UPDATE`, APK as a content: link, a PendingIntent for the answer) and it goes in through SelfAdb: an ADB install, which Google's 2027 developer verification leaves alone. Only the asker's own package, installed, same key, newer (switch `INSTALL_FOR_APPS`, on by default). Tested on the emulator Nov 9 with a TV app update (300 to 308) |
| `LinkService` | The TV remote (accessibility service): forwards keys while the TV is elsewhere, the Input button |
| `InputKey`, `InputKeyActivity` | Which button opens the input menu: the Input button (screen-with-arrow symbol, `res/drawable/input_button.xml`, drawn into text by `InputKey.label`) and ★ always (the same button on different remotes), plus an Onn remote's TV button; someone with another remote adds its button by pressing it (arrows, OK, Back, Home, power, volume can't be). While that screen is open LinkService takes no keys |
| `RemoteSetup` | The remote's own power, volume and Input buttons as Google TV set them up (`dumpsys activity service com.google.android.tv.axel/.remote.IrService` through SelfAdb: NONE, IR, CEC, BROADCAST or a device name); the main screen's step opens Google TV's own wizard (`com.google.android.tv.IR_WIZARD`); warns when Input or ★ ("Magic", anything but "Software [...]") is set to switch the TV by infrared while it also opens the menu. Not on the emulator (no axel) |
| `RemapApps` | A button-remapping app (Button Mapper...) gets the remote's buttons, except the Input button: the CleanYourTV home screen's own button helpers are switched off while one is on. Android hands each key to every key-filtering app at once, so a remapper mapped to the Input button acts too; if that pushes the input menu away within 0.9 s, LinkService opens it again |
| `Edition` (app/src/edition) | This build's extras: none. A private build brings its own (start, feature defaults, jobs, test jobs, self-update, a key taken first) |
| `Tv`, `Roku`, `FireTv`, `TvConfig`, `TvFinder` | The TV: which one (found by itself), and talking to it |
| `InputPanelActivity`, `SetupActivity` | The input menu; finding and picking the TV |
| `Screen` | The one look of the app's own pages (title, line, cards with a white edge when chosen), like Apps To Keep Updated: every new page uses it |
| `app/firekeys` | FireKeys, kept running on a Fire TV for fast input switching (build.sh puts it in assets) |
| `app/src/test` | The unit tests the push hook runs: `Jobs.compare`, `AppSources.validSource`, `Updater.sha256`, `Assistant.parse`, `Streams` |

## Releasing

Raise `versionCode` and `versionName` in `app/build.gradle`, build with the release key, and attach
`streaming-box-keeper-<versionName>.apk` to a GitHub release tagged `v<versionName>`. Installed copies with "Update
Streaming Box Keeper" on pick it up within a day.

## Emulator notes

`adb shell input keyevent` (any source) never reaches LinkService's key filter, and the emulator console's key events
don't arrive either, so what a remote button does there is only tested with a real remote. The `onn_s905x5m` emulator
(Android 14) does show the "Allow USB debugging?" question (Nov 14: Ask now, tick Always allow, allow it), so system
access (and with it installing and switching the remote on in one press, and the Assistant switch) can be tested there.
The main screen only makes the box ask when Ask now is pressed
pressed (`SelfAdb.knownAllowed`).
