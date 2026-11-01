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
> **[v1.1.0-dev.1](https://github.com/MatthewRiley05/Machu-Morphe-Patches/releases/tag/v1.1.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 Earth Super Wallpaper&nbsp;&nbsp;•&nbsp;&nbsp;2 patches</summary>
<br>

**Package name:** `com.miui.miwallpaper.earth`

**🎯 Supported versions:**

| ALPHA-2.6.575-09041449-ogl-64 | ALPHA-2.6.561-11211547-ogl-64-fix1 |
| :---: | :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
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

> **Not affiliated with Xiaomi, Google, Ouxyl or AppPorts.** This repository
> contains only patch code and documentation. It does **not** contain the APK,
> decompiled sources, assets, native libraries or signing keys. You must
> supply your own copy of the APK.

### Base APK

The preferred base is the **official Xiaomi release**
`ALPHA-2.6.575-09041449-ogl-64` (`versionCode 206000575`, package
`com.miui.miwallpaper.earth`). The patch also still supports the older
**SuperWallpaperEarth (Ouxyl's port)** `ALPHA-2.6.561-11211547-ogl-64-fix1`
(`versionCode 206000561`) that the first release was built against.

Other ports/versions may work, but the smali context must match. If Morphe
reports a failed fingerprint, the smali has changed and the patch needs
updating.

> [!TIP]
> Prefer the official release. The Ouxyl port is about 110 MB larger because it
> ships the whole **Unity** runtime (`libunity.so`, `libil2cpp.so`, Mono and
> ~190 MB of `assets/bin/Data`). Official builds since `ALPHA-2.6.575` render
> with Google's **Filament** engine instead (`libfilament-*.so`,
> `libgltfio-jni.so` and `assets/textures/*.ktx`), which is smaller and is the
> code Xiaomi actually maintains.


### Symptom

1. The wallpaper renders (including the AOD/lock frame), but unlocking never
   animates from the lock-screen framing to the home-screen framing.
2. After a few seconds without touching the screen, the wallpaper stutters and
   freezes; touching or swiping starts it again for a few seconds.

### Root cause

Two separate problems affect both engines:

1. The app schedules a pause message 4–5 seconds after unlock and after every
   wallpaper offset change. On the Unity build this is `MSG_UNITY_PAUSE` →
   `UnityPlayer.pause()`; on the Filament build it is `MSG_Filament_PAUSE` →
   `EarthMrePlayer.pause()`. The scene freezes until the launcher sends another
   offset change.
2. After unlock the receiver only forwards the `"Desk"` event when
   `DisplayUtils.getDisplayState() == DESK` (4). During the AOSP unlock
   transition the display state can still be something else, so the event is
   discarded and the lock → home animation never runs.

#### Legacy Unity port only

On Android 14+ the app disables its old broadcast-based screen/unlock handling:

```smali
# SuperWallpaper.smali
invoke-static {}, Lcom/miui/miwallpaper/utils/SystemBuildUtils;->isAndroidUAtLeast()Z
iput-boolean v0, ...,->USE_COMMAND_NOTIFICATION:Z
```

With `USE_COMMAND_NOTIFICATION = true` it does **not** register
`ACTION_USER_PRESENT` / `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF`, and its
`USER_PRESENT` branch returns early. Instead it waits for
`android.wallpaper.keyguardgoingaway`.

On Android 16+ (including Android 17) that command is only delivered to apps
holding:

```
android.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE
```

which is `signature|module|role` — a sideloaded APK can never get it, and
`adb shell pm grant` refuses it. The unlock event is silently dropped, so the
`"Desk"` message is never sent.

The official Filament build (`ALPHA-2.6.575+`) already registers
`ACTION_USER_PRESENT` unconditionally and no longer has that early return, so
only the two problems above need patching there.

### What the patch changes

There are three patches for the official build: **Make Earth Super Wallpaper
work on AOSP** (bytecode), **Allow Earth Super Wallpaper on AOSP** (wallpaper
metadata), and **Unlock Earth Super Wallpaper**. The legacy Unity port only
needs the unlock patch because its AOSP fixes are already baked in.

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

This resource patch sets `android:supportsAmbientMode="false"`, matching the
legacy port's `values-v29` behaviour.

#### Unlock + pause (`Unlock Earth Super Wallpaper`)

##### Official Filament build (`BaseEarthSuperWallpaper`)

| #   | Target                                                    | Change                                                                                                  |
| --- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 1   | `BaseEarthSuperWallpaper.smali`                           | Raise `getDeskPauseDelay()` / `getWallPaperAod2LockPauseDelay()` from 5000 ms and `getWallPaperOffsetDelay()` from 4000 ms to `0x7fffffff`, so the renderer is not paused while visible. |
| 2   | `BaseEarthSuperWallpaper$8$1.smali`                       | Drop the `DisplayUtils.getDisplayState() == DESK` check that could discard the unlock during animation. |
| 3   | `BaseEarthSuperWallpaper$8.smali`                         | Post `"Desk"` with 0 ms instead of 100 ms.                                                              |

##### Legacy Unity port (`SuperWallpaper`)

| #   | Target                                                  | Change                                                                                                  |
| --- | ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| 1   | `SuperWallpaper.smali`                                  | Register `android.intent.action.USER_PRESENT` unconditionally (also in command mode).                   |
| 2   | `SuperWallpaper$8.smali`                                | Replace the `USE_COMMAND_NOTIFICATION` early-return with `nop` so the `USER_PRESENT` branch runs.       |
| 3   | `SuperWallpaper$8$1.smali`                              | Drop the `DisplayUtils.getDisplayState() == DESK` check that could discard the unlock during animation. |
| 4   | `SuperWallpaper$8.smali`                                | Post `"Desk"` with 0 ms instead of 100 ms.                                                              |
| 5   | `SuperWallpaper$SuperWallpaperEngine.smali`             | Delay the AOD zoom-out by 500 ms so it does not run during the display fade.                            |
| 6   | `SuperWallpaper.smali`                                  | Raise the three pause delays from 5000 ms to `0x7fffffff` so the renderer is not paused while visible.  |

The hard-coded 2 s pause on screen-off and the visibility path are untouched, so
the scene still stops when the display is off or the wallpaper is hidden.

### Port-only AOSP changes not part of these patches

Patch A covers the MIUI framework stubs needed to make the wallpaper service
start and render on AOSP. The Ouxyl port also contained a few extras that are
not required for the core fix but may matter in specific situations:

- `PreferenceUtils` replaces the official app's unguarded
  `Settings.Global.putLong/putInt` writes in `WeatherUtils$1` (sunrise, sunset
  and `sunrise_update`) and the matching reads in `UIDarkModeHelper` with plain
  `SharedPreferences`. On a sideloaded AOSP build the official writes can throw
  `SecurityException` because `WRITE_SECURE_SETTINGS` is a signature/privileged
  permission. If the wallpaper process dies during a weather or day/night
  update, this is the next thing to patch.
- `PreviewManager` blocks more than one simultaneous wallpaper preview. The
  official 2.6.575 build has a redesigned preview flow, so it may not be needed.
- The port exposed five services (`EarthSuperWallpaper1..4` plus
  `super_wallpaper1..4.xml`) because the AOSP picker cannot interpret MIUI's
  `desk_position_count` metadata. The official build works with its single
  service, but positions 1–4 are not selectable from the AOSP picker without a
  separate resource patch.

None of these are required for the unlock/pause fixes above.

### Notes / limitations

- The unlock transition starts when SystemUI sends `ACTION_USER_PRESENT`, which
  is after the unlock animation finishes. The earlier `keyguardgoingaway`
  signal is permission-gated on Android 16+, so a normal app cannot start the
  wallpaper transition sooner.
- Raising the pause delays means the scene keeps rendering while the wallpaper
  is visible. Screen-off and hidden-wallpaper paths still pause.
- The legacy Unity build also delays the AOD zoom-out by 500 ms. The Filament
  build sends the AOD message synchronously and has no equivalent `Handler.post`
  to retime, so that tweak is intentionally not applied there. If the AOD
  zoom-out still overlaps the display fade on the official build, it needs a
  dedicated hook.
- No version bump is included in the patch.
- This patch was generated using AI as I don't specialize in this type of
  programming. The legacy Unity path is tested on my personal device; the new
  official Filament path still needs on-device testing.

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
