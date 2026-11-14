# StreamBox Keeper

A free app for Google TV and Android TV boxes that makes the box's remote run your TV too, and quietly looks after
the box while it sleeps.

## What it does

- **Your box's remote controls the TV.** When the TV is on another input (a game console, a PC, antenna TV, the
  TV's own home screen), the box's remote arrows, OK and Back go to the TV. Any other button brings you back to
  the box. Works with **Roku TVs** and **Fire TV smart TVs**.
- **A full-screen input menu.** Press the remote's Input button: every input your TV has, by the names you gave
  them, with the one you're on highlighted. Pick one with OK; Back takes you back where you were.
- **Back on Wi-Fi after a drop.** Some boxes don't rejoin Wi-Fi by themselves while they sleep, so after a router
  restart they stay offline until someone wakes them. StreamBox Keeper rejoins within 5 minutes, screen off.
- **Nightly cleanup.** Closes background apps at night while the box sleeps, so it stays quick.
- **Weekly restart when needed.** At night, only if memory stays full after the cleanup, and never while the TV
  is on.
- **Google Assistant off (optional).** The remote's mic button stops starting Google Assistant, while voice search in
  apps keeps the mic. Switched back on any time; if the mic then sends no sound at all, pair the remote again
  (Settings > Remotes & Accessories).
- **Buttons that do nothing.** Press a remote button on its page to switch it off, such as an app button for an app
  you don't have (which would open the Play Store).
- **App updates (optional).** Keeps any app you choose up to date: give it the app's Downloader code (the numbers
  you'd type into the Downloader app), its GitHub page, or a link to its .apk. Checked once a day, installed at night
  while the box sleeps. It only installs the same app, newer, and Android refuses an update signed by anyone else.

Every feature has its own switch on the app's screen. Nothing happens on screen while you're watching.

## What it needs

- A Google TV or Android TV box with Android 9 or newer. Tested on the Walmart Onn 4K Pro.
- For the TV remote: a Roku TV or Fire TV smart TV on the same network.
  - Roku: Settings > System > Advanced system settings > Control by mobile apps > Network access: **Enabled**.
  - Fire TV: Settings > My Fire TV > Developer options > ADB debugging: **On**. The TV asks once to allow the box.
- **Network debugging on the box.** StreamBox Keeper uses the box's own debugging connection to do what normal apps
  can't (rejoin Wi-Fi while asleep, install updates, read which HDMI port it's on). It only ever connects to the box
  itself. The app's screen walks you through it.

## Install

1. On the box, install **Downloader** (by AFTVnews) from the Play Store.
2. In Downloader, open the newest `streambox-keeper-<version>.apk` from this page's
   [Releases](https://github.com/ExpertLizard56/streambox-keeper/releases), and install it.
3. Open **StreamBox Keeper** from your apps and follow its three set-up steps.

## Privacy

No accounts, ads, analytics or tracking. StreamBox Keeper talks only to your own box, your TV on your home network, and
GitHub (to check for updates, and only if those switches are on). See [PRIVACY.md](PRIVACY.md).

## Building it yourself

JDK 17 and the Android SDK (platform 34): `./gradlew :app:assembleRelease`. Without a `keystore.properties` the
build is signed with your debug key, which installs fine but can't update a copy installed from Releases (Android
only updates an app signed with the same key).

## License

MIT, see [LICENSE](LICENSE).
