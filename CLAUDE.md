# CLAUDE.md

Guidance for Claude Code working in this repo.

## What this is

**My TV Shows**: an Android TV / Google TV app that tracks the shows a household watches.
Kotlin + Compose for TV, single module (`app/`), package `com.dalethomas.mytvshows`.

- The show list lives **only on the TV**, in the app's storage (`files/user.json`). No account, no server.
- Show info, episodes and air dates come from the free TVMaze API (no key needed).
- Look: "cinematic" Home. The focused show's TVMaze backdrop fills the screen behind a big title, a season progress bar and a main button; poster rows below (focused row snaps to the top). Font: Manrope (bundled, SIL OFL, see `MANROPE-OFL.txt`).
- Tabs: **Home**, **By service**, **Coming up** (next 7 days as artwork cards, day names only, no clock times), **+ Add** (search TVMaze, pick the service).
- Streaming apps: the app scans the TV's installed apps (`data/InstalledApps.kt`). An app stays hidden (no "Your apps" tile, no "By service" column) until a show on the list is watched there. Adding a show: if TVMaze's network matches exactly one installed app, it's used automatically; otherwise the user picks from the apps on the TV (any app, not only the 12 recognized ones). Details has "Watch on · Change app".
- On each show: mark the last episode watched (gives "N new episodes" and a "Ready to watch" row) and set a status: Watching, To watch, Waiting for new season, Loved, Liked, Meh (`domain/Statuses.kt`). Marking an episode of a To watch show sets Watching.
- Rows: Ready to watch, Start something new (To watch), New this week, Coming soon, All caught up, Finished.

## Rules

- **Ask the user before installing on their TV** or changing any TV setting.
- **Uninstalling the app erases the list.** Updating with `adb install -r` keeps it.
- Debug builds are signed with this computer's debug key. Installing a build from a *different* computer over the top fails with a signature error; the only way past that is uninstalling, which erases the list. Build and install from the same computer each time.
- Never commit `local.properties` (machine-specific, gitignored).
- UI text: plain words, no em dashes.

## First-time setup (do these in order, check each before moving on)

1. **JDK 17.** Gradle 8.11 + AGP 8.7 need exactly JDK 17 (newer JDKs, including Android Studio's bundled one, fail).
   - Windows: `winget install EclipseAdoptium.Temurin.17.JDK`
   - macOS: `brew install --cask temurin@17`
   - Set `JAVA_HOME` to it for the build commands.
2. **Android SDK.** Easiest is Android Studio (installs the SDK on first run). Or the command-line tools, then `sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"`.
3. **`local.properties`** in the repo root, pointing at the SDK (forward slashes work on Windows):
   ```
   sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
   ```
   macOS default: `sdk.dir=/Users/<you>/Library/Android/sdk`
4. **Build and test:**
   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
   ```
5. **Developer mode on the TV (required).** Nothing installs without it. The user does this with the remote, so walk them through it one step at a time and wait for each "done" (the README has the same steps for them):
   - **Developer mode:** Settings > System > About > scroll to **Android TV OS build**, press it **7 times** until "You are now a developer!" (older Android TVs: Settings > Device Preferences > About > Build). It may ask for the TV's PIN.
   - **Debugging:** Settings > System > Developer options (older: Settings > Device Preferences > Developer options) > turn on **USB debugging**, plus **Network debugging** / **Wireless debugging** if listed.
   - **IP address:** Settings > Network & Internet > the connected network.
   - Developer options gone after a TV update or reset? Repeat the 7 presses.
6. **Connect adb** (`adb` is in the SDK's `platform-tools`):
   ```bash
   adb connect <TV-IP>:5555
   ```
   The TV shows "Allow USB debugging?". The user picks **Always allow from this computer**.
   If the TV only offers **Wireless debugging** with a pairing code: on the TV choose "Pair device with pairing code", then `adb pair <IP>:<pair-port> <code>` and `adb connect <IP>:<port shown on that screen>`.
   Check with `adb devices` (should say `device`, not `unauthorized`).
7. **Install and open** (ask first):
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell monkey -p com.dalethomas.mytvshows -c android.intent.category.LEANBACK_LAUNCHER 1
   ```
   It also appears under **Your apps** on the Google TV home screen.

## Updating later

`git pull`, rebuild (step 4), install with `adb install -r` (step 7). The list carries over.

## Code map

- `data/UserRepository.kt`: the saved list, watched markers, show status, TVMaze episode cache.
- `data/TvMazeApi.kt`: search and episode lists.
- `data/BackdropRepository.kt`: backdrop image URL per show (TVMaze `/shows/{id}/images`), looked up once and cached in `files/backdrops.json`.
- `domain/Rows.kt`, `Progress.kt`, `Seasons.kt`, `Week.kt`, `Services.kt`: row grouping, unwatched counts, season progress, the Coming up list, the the 12 recognized streaming apps (brand colors, TVMaze network matching; others still work as plain name tiles). Add an app to `Services.all` only to give it brand colors or automatic matching.
- `ui/`: Compose screens.

## Troubleshooting

- "Unable to delete directory" during a build: `./gradlew --stop`, delete `app/build`, rebuild.
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: signature mismatch (see Rules). Uninstalling fixes it but erases the list.
- No app under "Your apps": apps appear only once a show on the list uses them.
- An app is missing from the picker: it must have a TV home-screen entry (LEANBACK_LAUNCHER). Utilities are hidden by `NOT_STREAMING` in `data/InstalledApps.kt`.
