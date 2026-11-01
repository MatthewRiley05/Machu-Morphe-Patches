# 🧩 Machu Morphe Patches

Unofficial [Morphe](https://morphe.software) patches for apps I use.

## ❓ About

This repository contains patches that restore functionality I need on my own
devices. The patches are built and released automatically from the `dev`
branch as Morphe pre-releases.

### How to use these patches

Add this repository as a patch source in Morphe Manager:

<https://github.com/MatthewRiley05/Machu-Morphe-Patches>

<a href="https://morphe.software/add-source?github=MatthewRiley05/Machu-Morphe-Patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.1.0-dev.3](https://github.com/MatthewRiley05/Machu-Morphe-Patches/releases/tag/v1.1.0-dev.3)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 Earth Super Wallpaper&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**Package name:** `com.miui.miwallpaper.earth`

**🎯 Supported versions:**

| ALPHA-2.6.575-09041449-ogl-64 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Allow Earth Super Wallpaper on AOSP](#allow-earth-super-wallpaper-on-aosp) | Disables the ambient-mode declaration that requires the signature-only AMBIENT_WALLPAPER permission on non-MIUI ROMs. |  |
| [Fix weather settings on AOSP](#fix-weather-settings-on-aosp) | Prevents the unguarded Settings.Global writes from crashing the wallpaper process during weather or day/night updates on non-MIUI ROMs. |  |
| [Make Earth Super Wallpaper work on AOSP](#make-earth-super-wallpaper-work-on-aosp) | Replaces MIUI-only framework calls so the official Earth Super Wallpaper can be previewed, selected, and rendered on non-MIUI Android ROMs. |  |
| [Unlock Earth Super Wallpaper](#unlock-earth-super-wallpaper) | Restores the lock-to-home unlock animation and prevents the wallpaper from pausing while it is visible on AOSP-based ROMs. |  |

</details>

<!-- PATCHES_END -->

## 🌎 Earth Super Wallpaper – AOSP unlock + idle animation

Unofficial patches for `com.miui.miwallpaper.earth` that restore the
lock → home transition and keep the wallpaper animating on AOSP-based ROMs
(Pixel, Android 14–17). The official Filament build also needs an AOSP
compatibility patch to remove its MIUI-only framework calls.

**Tested on:** Pixel 10 Pro XL, Android 17, with the base APK below.

> **Not affiliated with Xiaomi or Google.** This repository contains only patch
> code and documentation. It does **not** contain the APK, decompiled sources,
> assets, native libraries or signing keys. You must supply your own copy of
> the APK.

### Base APK

The base is the **official Xiaomi release**
`ALPHA-2.6.575-09041449-ogl-64` (`versionCode 206000575`, package
`com.miui.miwallpaper.earth`).

Future official releases may work, but the smali context must match. If Morphe
reports a failed fingerprint, the smali has changed and the patch needs
updating.


### Symptom

1. The wallpaper renders (including the AOD/lock frame), but unlocking never
   animates from the lock-screen framing to the home-screen framing.
2. After a few seconds without touching the screen, the wallpaper stutters and
   freezes; touching or swiping starts it again for a few seconds.

### Root cause

Two separate problems:

1. The app schedules a `MSG_Filament_PAUSE` 4–5 seconds after unlock and after
   every wallpaper offset change. `EarthMrePlayer.pause()` then freezes the
   scene until the launcher sends another offset change.
2. After unlock the receiver only forwards the `"Desk"` event when
   `DisplayUtils.getDisplayState() == DESK` (4). During the AOSP unlock
   transition the display state can still be something else, so the event is
   discarded and the lock → home animation never runs.

### What the patch changes

There are four patches:

- **Make Earth Super Wallpaper work on AOSP** — replaces MIUI-only framework calls.
- **Allow Earth Super Wallpaper on AOSP** — disables the ambient-mode metadata that requires a signature permission.
- **Fix weather settings on AOSP** — removes unguarded `Settings.Global` writes.
- **Unlock Earth Super Wallpaper** — restores the lock → home animation and stops the pause.

#### AOSP compatibility (`Make Earth Super Wallpaper work on AOSP`)

The official 2.6.575 build still calls MIUI framework classes that do not exist
on AOSP. Without this patch the wallpaper service crashes while loading
`MiuiWallpaperManager` or `UIDarkModeHelper`.

| #   | Target                                                | Change                                                                                                              |
| --- | ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| 1   | `MiuiWallpaperManager.<clinit>`                       | `miui.os.UserHandle.myUserId()` → `android.os.UserHandle.myUserId()`.                                               |
| 2   | `WallpaperAuthorityUtils.<clinit>`                    | Replace the `miui.os.Build.IS_INTERNATIONAL_BUILD` read with `false`.                                               |
| 3   | `CommonUtils.getScreenSize()`                         | Replace the `miui.os.Build.IS_TABLET` read with `false`.                                                            |
| 4   | `MiuiWallpaperManager.getFitScreenWallpaperPreview()` | Replace its `miui.os.Build.IS_TABLET` read with `false`.                                                            |
| 5   | `WallpaperAuthorityUtils.isThemeLockLiveWallpaper()`  | Return `true` instead of reaching `miui.os.Build.DEVICE` through `isVideo24Wallpaper()`.                            |
| 6   | `AodUtils.isAodOpened()`                              | Return `true` instead of reading MIUI's AOD setting.                                                                |
| 7   | `AodUtils.isAodUsingSuperWallpaperStyle()`            | Return `true` instead of reading MIUI's `aod_using_super_wallpaper` setting.                                        |
| 8   | `WallpaperAuthorityUtils.supportVideo24Wallpaper()`   | Replace its `miui.os.Build.DEVICE` reads with empty strings so no MIUI type remains.                                |
| 9   | `UIDarkModeHelper$1.onChange()`                       | Return `false` instead of calling `android.provider.MiuiSettings$System.getBoolean()`.                              |

#### AOSP ambient-mode metadata (`Allow Earth Super Wallpaper on AOSP`)

The official `res/xml/super_wallpaper.xml` hard-codes
`android:supportsAmbientMode="true"`. AOSP's `WallpaperManagerService` then
requires the selected wallpaper service to hold
`android.permission.AMBIENT_WALLPAPER`, a signature/privileged permission a
sideloaded APK can never get, so the system picker throws `SecurityException`.

This resource patch sets `android:supportsAmbientMode="false"`, which avoids
that permission check.

#### Weather settings (`Fix weather settings on AOSP`)

`WeatherUtils$1.onPostExecute()` writes sunrise, sunset and `sunrise_update` to
`Settings.Global` without a permission check. On AOSP that throws
`SecurityException` because `WRITE_SECURE_SETTINGS` is signature/privileged.
This patch removes those writes; the fetched values are still passed directly to
the renderer for the current session.

#### Unlock + pause (`Unlock Earth Super Wallpaper`)

| #   | Target                                                    | Change                                                                                                  |
| --- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 1   | `BaseEarthSuperWallpaper.smali`                           | Raise `getDeskPauseDelay()` / `getWallPaperAod2LockPauseDelay()` from 5000 ms and `getWallPaperOffsetDelay()` from 4000 ms to `0x7fffffff`, so the renderer is not paused while visible. |
| 2   | `BaseEarthSuperWallpaper$8$1.smali`                       | Drop the `DisplayUtils.getDisplayState() == DESK` check that could discard the unlock during animation. |
| 3   | `BaseEarthSuperWallpaper$8.smali`                         | Post `"Desk"` with 0 ms instead of 100 ms.                                                              |

The hard-coded 2 s pause on screen-off and the visibility path are untouched, so
the scene still stops when the display is off or the wallpaper is hidden.

### Notes / limitations

- The unlock transition starts when SystemUI sends `ACTION_USER_PRESENT`, which
  is after the unlock animation finishes. The earlier `keyguardgoingaway`
  signal is permission-gated on Android 16+, so a normal app cannot start the
  wallpaper transition sooner.
- Raising the pause delays means the scene keeps rendering while the wallpaper
  is visible. Screen-off and hidden-wallpaper paths still pause.
- The Filament build sends the AOD message synchronously, so the old AOD
  zoom-out delay tweak has no equivalent hook. If the AOD zoom-out overlapped
  the display fade, it would need a dedicated patch.
- No version bump is included in the patches.
- These patches were generated using AI as I don't specialize in this type of
  programming. They are tested on a Pixel 10 Pro XL running Android 17.

## 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches `.mpp` file is found in `patches/build/libs/patches-*.mpp`
- Patch the `.mpp` file using [Morphe Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation)
for more information.

## 🧑‍💻 Development

- Make changes on the `dev` branch.
- Use [semantic commit](https://www.conventionalcommits.org/) messages:
  `feat:` for new features, `fix:` for fixes, and `chore:` for changes that
  should not appear in the changelog.
- `fix:` and `feat:` commits automatically create a new pre-release from `dev`.
  `chore:` commits do not create a release.
- When `dev` is ready for a stable release, merge it into `main` without
  squashing.
- Do not manually edit generated files such as `patches-list.json`,
  `patches-bundle.json` or `CHANGELOG.md`; `release.yml` updates them.

## 📜 License

Machu Morphe Patches are licensed under the
[GNU General Public License v3.0](LICENSE).
