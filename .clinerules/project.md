# For Cline

The root `AGENTS.md` (the map of this app; Claude Code reads it through `CLAUDE.md`) is already loaded in your rules
above: don't read it again. Before anything else, read the `AGENTS.md` and doc for the part you're working on. Everything you need is in those files and git; anything you learn
or decide goes back into them.

This repository is the public app: nothing from the owner's home goes in it (no addresses, names, device lists, or
home apps). It's the only version (the owner's boxes run it too, since Nov 14): a home-only need goes in a separate
app, never in this one.

Never without asking the owner first:
- publishing a release (`AGENTS.md`, Releasing): the installed copies update themselves from it;
- installing on, or testing on, a real box: people watch those TVs, and putting a box to sleep turns its TV off. Start
  an emulator instead (`scripts/tv-emulator`).

Before committing: lint and test what you changed (`./gradlew --console=plain -q :app:lintDebug
:app:testDebugUnitTest`), and commit only your own files with explicit paths. End every commit message with a line
naming you and your model, e.g. `Co-Authored-By: Cline (DeepSeek V4.1 Flash) <noreply@cline.bot>`.

Save everything: when a piece of work is finished, commit it, with a commit message that says what changed and why.
Leave nothing uncommitted, and no scratch files behind: delete helper scripts you made for yourself.
Checks run by themselves when you commit and push (lefthook; `AGENTS.md`, Checks). If one fails, fix what it says and
commit again. Never skip them (no --no-verify, no LEFTHOOK=0).

Add-ons (MCP) you have, shared by every Cline task:
- context7: current docs for a library. Before writing code against an API you aren't sure of (Android
  build/Gradle, the accessibility service, Media3 and the like change fast), look it up there instead of guessing from
  memory.
- mobile-mcp: drives an Android device: open apps, tap, type, read the screen, screenshots, logs. Use it to check your
  own change works before committing. Only ever on emulators (device names `emulator-NNNN`; start one with
  `scripts/tv-emulator/start.sh <profile>`, see its `AGENTS.md`). Never on the real boxes, and
  never its cloud or remote devices or login.

Keeping the context small (everything in this task is resent with every step, so a bloated task costs more and gets sloppier):
- Don't call mobile_take_screenshot: in this Cline the picture arrives as about 40,000 tokens of base64 text you can't
  see. Read the screen with mobile_list_elements_on_screen (text, with positions). If the owner should see the screen,
  save it with mobile_save_screenshot to a file and give the owner the path.
- Build, test and lint output: keep only what matters (`2>&1 | Select-Object -Last 40`, or `Select-String` for the error),
  as you already mostly do. Never print whole logs (logcat: filter by tag or `-t 200`).
- Find before you read: search for the place, then read just those lines (start_line/end_line). Read a whole file only
  when it's small or you need all of it, and don't re-read a file you already have unless it changed.
