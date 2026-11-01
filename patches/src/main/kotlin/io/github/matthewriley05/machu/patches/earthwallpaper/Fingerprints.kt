package io.github.matthewriley05.machu.patches.earthwallpaper

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private const val SUPER_WALLPAPER =
    "Lcom/miui/miwallpaper/basesuperwallpaper/SuperWallpaper;"
private const val SUPER_WALLPAPER_RECEIVER =
    "Lcom/miui/miwallpaper/basesuperwallpaper/SuperWallpaper\$8;"
private const val SUPER_WALLPAPER_ENGINE =
    "Lcom/miui/miwallpaper/basesuperwallpaper/SuperWallpaper\$SuperWallpaperEngine;"
private const val DESK_RUNNABLE =
    "Lcom/miui/miwallpaper/basesuperwallpaper/SuperWallpaper\$8\$1;"

// The official releases since ALPHA-2.6.575 no longer render with Unity. The
// Earth wallpaper now extends this class and renders with Filament, while the
// old SuperWallpaper classes above remain in the APK as dead code.
internal const val BASE_EARTH_SUPER_WALLPAPER =
    "Lcom/miui/miwallpaper/earth/superwallpaper/BaseEarthSuperWallpaper;"

private const val DISPLAY_UTILS =
    "Lcom/miui/miwallpaper/baselib/utils/DisplayUtils;"
private const val HANDLER = "Landroid/os/Handler;"

// MIUI framework dependencies that do not exist on AOSP. The official
// Filament builds still reference these, while the legacy port removed them.
private const val MIUI_WALLPAPER_MANAGER =
    "Lcom/miui/miwallpaper/MiuiWallpaperManager;"
private const val WALLPAPER_AUTHORITY_UTILS =
    "Lcom/miui/miwallpaper/baselib/utils/WallpaperAuthorityUtils;"
private const val COMMON_UTILS =
    "Lcom/miui/miwallpaper/baselib/utils/CommonUtils;"
private const val AOD_UTILS =
    "Lcom/miui/miwallpaper/baselib/utils/AodUtils;"
private const val MIUI_USER_HANDLE = "Lmiui/os/UserHandle;"
internal const val MIUI_BUILD = "Lmiui/os/Build;"
private const val MIUI_SETTINGS_SYSTEM = "Landroid/provider/MiuiSettings\$System;"
private const val CONTEXT = "Landroid/content/Context;"

private val ON_RECEIVE_PARAMETERS = listOf(
    "Landroid/content/Context;",
    "Landroid/content/Intent;",
)

/**
 * `getDeskPauseDelay()` returns the 5 second delay used before pausing Unity.
 */
internal object DeskPauseDelayFingerprint : Fingerprint(
    definingClass = SUPER_WALLPAPER,
    name = "getDeskPauseDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(5000)),
)

/**
 * `getWallPaperAod2LockPauseDelay()` returns the 5 second delay used before pausing Unity.
 */
internal object Aod2LockPauseDelayFingerprint : Fingerprint(
    definingClass = SUPER_WALLPAPER,
    name = "getWallPaperAod2LockPauseDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(5000)),
)

/**
 * `getWallPaperOffsetDelay()` returns the 5 second delay used before pausing Unity.
 */
internal object WallPaperOffsetDelayFingerprint : Fingerprint(
    definingClass = SUPER_WALLPAPER,
    name = "getWallPaperOffsetDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(5000)),
)

/**
 * `SuperWallpaper.onCreate()` registers the broadcast receiver. It registers
 * `USER_PRESENT` only when `USE_COMMAND_NOTIFICATION` is false.
 */
internal object IntentFilterRegistrationFingerprint : Fingerprint(
    definingClass = SUPER_WALLPAPER,
    name = "onCreate",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        string("miui.miwallpaper.action.AOD_PREVIEW"),
    ),
)

/**
 * The `USER_PRESENT` branch of `SuperWallpaper$8.onReceive()` returns early when
 * `access$1600()` is false.
 */
internal object UserPresentEarlyReturnFingerprint : Fingerprint(
    definingClass = SUPER_WALLPAPER_RECEIVER,
    name = "onReceive",
    returnType = "V",
    parameters = ON_RECEIVE_PARAMETERS,
    filters = listOf(
        methodCall(
            definingClass = SUPER_WALLPAPER,
            name = "access\$1600",
            returnType = "Z",
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_NEZ, MatchAfterImmediately()),
    ),
)

/**
 * `SuperWallpaper$8.onReceive()` posts the `"Desk"` Unity message with a 100 ms delay.
 */
internal object DeskMessagePostFingerprint : Fingerprint(
    definingClass = SUPER_WALLPAPER_RECEIVER,
    name = "onReceive",
    returnType = "V",
    parameters = ON_RECEIVE_PARAMETERS,
    filters = listOf(
        string("Desk"),
        methodCall(
            definingClass = HANDLER,
            name = "postDelayed",
            returnType = "Z",
        ),
    ),
)

/**
 * `SuperWallpaper$8$1.run()` discards the `"Desk"` message unless the display
 * state is `DESK` (4).
 */
internal object DisplayStateCheckFingerprint : Fingerprint(
    definingClass = DESK_RUNNABLE,
    name = "run",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = DISPLAY_UTILS,
            name = "getDisplayState",
            returnType = "I",
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        literal(4, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQ, MatchAfterImmediately()),
    ),
)

/**
 * `SuperWallpaper$SuperWallpaperEngine.goToAodState()` posts the AOD zoom-out
 * runnable immediately.
 *
 * Only used by the legacy Unity builds.
 */
internal object AodPostFingerprint : Fingerprint(
    definingClass = SUPER_WALLPAPER_ENGINE,
    name = "goToAodState",
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        methodCall(
            definingClass = HANDLER,
            name = "post",
            returnType = "Z",
        ),
    ),
)

// ---------------------------------------------------------------------------
// Official Filament builds (ALPHA-2.6.575 and newer)
// ---------------------------------------------------------------------------

/**
 * `BaseEarthSuperWallpaper.getDeskPauseDelay()` returns the 5 second delay used
 * before pausing the Filament renderer.
 */
internal object FilamentDeskPauseDelayFingerprint : Fingerprint(
    definingClass = BASE_EARTH_SUPER_WALLPAPER,
    name = "getDeskPauseDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(5000)),
)

/**
 * `BaseEarthSuperWallpaper.getWallPaperAod2LockPauseDelay()` returns the 5
 * second delay used before pausing the Filament renderer.
 */
internal object FilamentAod2LockPauseDelayFingerprint : Fingerprint(
    definingClass = BASE_EARTH_SUPER_WALLPAPER,
    name = "getWallPaperAod2LockPauseDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(5000)),
)

/**
 * `BaseEarthSuperWallpaper.getWallPaperOffsetDelay()` returns the 4 second
 * delay used before pausing the Filament renderer.
 */
internal object FilamentWallPaperOffsetDelayFingerprint : Fingerprint(
    definingClass = BASE_EARTH_SUPER_WALLPAPER,
    name = "getWallPaperOffsetDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(4000)),
)

/**
 * `BaseEarthSuperWallpaper`'s broadcast receiver posts the `"Desk"` Filament
 * message with a 100 ms delay.
 *
 * The receiver is an anonymous class, so match it by its superclass and the
 * call to the Filament build's `getDeskPauseDelay()` instead of the generated
 * `$8` class name.
 */
internal object FilamentDeskMessagePostFingerprint : Fingerprint(
    superclass = "Landroid/content/BroadcastReceiver;",
    name = "onReceive",
    returnType = "V",
    parameters = ON_RECEIVE_PARAMETERS,
    filters = listOf(
        string("Desk"),
        methodCall(
            definingClass = BASE_EARTH_SUPER_WALLPAPER,
            name = "getDeskPauseDelay",
            returnType = "I",
        ),
        methodCall(
            definingClass = HANDLER,
            name = "postDelayed",
            returnType = "Z",
        ),
    ),
)

/**
 * The `Runnable` posted by the receiver discards the `"Desk"` message unless
 * the display state is `DESK` (4).
 *
 * The runnable is also an anonymous class. It is matched by `Runnable` and by
 * the Filament message it sends, so the unused legacy Unity runnable in the
 * same APK cannot be matched instead.
 */
internal object FilamentDisplayStateCheckFingerprint : Fingerprint(
    interfaces = listOf("Ljava/lang/Runnable;"),
    name = "run",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = DISPLAY_UTILS,
            name = "getDisplayState",
            returnType = "I",
        ),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        literal(4, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQ, MatchAfterImmediately()),
        methodCall(
            definingClass = BASE_EARTH_SUPER_WALLPAPER,
            name = "sendFilamentMessage",
            returnType = "V",
        ),
    ),
)

// ---------------------------------------------------------------------------
// AOSP compatibility for the official Filament builds
// ---------------------------------------------------------------------------

/**
 * `MiuiWallpaperManager.<clinit>` calls `miui.os.UserHandle.myUserId()`. The
 * class does not exist on AOSP, so the wallpaper service dies while loading
 * `MiuiWallpaperManager`. Use the AOSP `android.os.UserHandle` equivalent.
 */
internal object MiuiUserHandleFingerprint : Fingerprint(
    definingClass = MIUI_WALLPAPER_MANAGER,
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        methodCall(
            definingClass = MIUI_USER_HANDLE,
            name = "myUserId",
            returnType = "I",
        ),
    ),
)

/**
 * `WallpaperAuthorityUtils.<clinit>` reads `miui.os.Build.IS_INTERNATIONAL_BUILD`,
 * which crashes on AOSP. The port hard-codes the non-international default.
 */
internal object IsInternationalBuildFingerprint : Fingerprint(
    definingClass = WALLPAPER_AUTHORITY_UTILS,
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(
            definingClass = MIUI_BUILD,
            name = "IS_INTERNATIONAL_BUILD",
            type = "Z",
        ),
    ),
)

/**
 * `CommonUtils.getScreenSize()` reads `miui.os.Build.IS_TABLET`, which crashes
 * on AOSP. The port treats non-MIUI devices as phones.
 */
internal object IsTabletFingerprint : Fingerprint(
    definingClass = COMMON_UTILS,
    name = "getScreenSize",
    returnType = "Landroid/graphics/Point;",
    parameters = listOf(CONTEXT),
    filters = listOf(
        fieldAccess(
            definingClass = MIUI_BUILD,
            name = "IS_TABLET",
            type = "Z",
        ),
    ),
)

/**
 * `MiuiWallpaperManager.getFitScreenWallpaperPreview()` also reads
 * `miui.os.Build.IS_TABLET`, which crashes on AOSP.
 */
internal object MiuiWallpaperManagerIsTabletFingerprint : Fingerprint(
    definingClass = MIUI_WALLPAPER_MANAGER,
    name = "getFitScreenWallpaperPreview",
    returnType = "Landroid/graphics/Bitmap;",
    parameters = listOf(CONTEXT, "I"),
    filters = listOf(
        fieldAccess(
            definingClass = MIUI_BUILD,
            name = "IS_TABLET",
            type = "Z",
        ),
    ),
)

/**
 * The official method calls `isVideo24Wallpaper()`, which reaches
 * `miui.os.Build.DEVICE`. The port always reports a theme lock live wallpaper.
 */
internal object IsThemeLockLiveWallpaperFingerprint : Fingerprint(
    definingClass = WALLPAPER_AUTHORITY_UTILS,
    name = "isThemeLockLiveWallpaper",
    returnType = "Z",
    parameters = listOf(CONTEXT),
)

/**
 * The official method reads MIUI's AOD setting. On AOSP the key is absent and
 * the method returns false, so AOD is never treated as enabled.
 */
internal object IsAodOpenedFingerprint : Fingerprint(
    definingClass = AOD_UTILS,
    name = "isAodOpened",
    returnType = "Z",
    parameters = listOf(CONTEXT),
)

/**
 * The official method reads MIUI's `aod_using_super_wallpaper` setting. On AOSP
 * the key is absent and the method returns false.
 */
internal object IsAodUsingSuperWallpaperStyleFingerprint : Fingerprint(
    definingClass = AOD_UTILS,
    name = "isAodUsingSuperWallpaperStyle",
    returnType = "Z",
    parameters = listOf(CONTEXT),
)

/**
 * `UIDarkModeHelper$1.onChange()` reads `android.provider.MiuiSettings.System`,
 * which does not exist on AOSP and kills the wallpaper service during
 * `UIDarkModeHelper` construction.
 */
internal object DarkModeMiuiSettingsFingerprint : Fingerprint(
    superclass = "Landroid/database/ContentObserver;",
    name = "onChange",
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        methodCall(
            definingClass = MIUI_SETTINGS_SYSTEM,
            name = "getBoolean",
            returnType = "Z",
        ),
    ),
)

/**
 * Defensive stub for the `miui.os.Build.DEVICE` path reached from
 * `isVideo24Wallpaper()`. No MIUI device is a Video24 device.
 */
internal object SupportVideo24WallpaperFingerprint : Fingerprint(
    definingClass = WALLPAPER_AUTHORITY_UTILS,
    name = "supportVideo24Wallpaper",
    returnType = "Z",
    parameters = emptyList(),
)
