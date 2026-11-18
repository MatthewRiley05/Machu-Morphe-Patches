package io.github.matthewriley05.machu.patches.snowmountain

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val SNOW_BASE_SUPER_WALLPAPER =
    "Lcom/miui/miwallpaper/snowmountain/superwallpaper/SnowmountainBaseSuperWallpaper;"
internal const val SNOW_SUPER_WALLPAPER =
    "Lcom/miui/miwallpaper/snowmountain/superwallpaper/SnowmountainSuperWallpaper;"

private const val DISPLAY_UTILS =
    "Lcom/miui/miwallpaper/baselib/utils/DisplayUtils;"

// ---------------------------------------------------------------------------
// Pause delays. Snowmountain overrides the shared SuperWallpaper getters and
// returns 3000 ms (0xbb8) instead of the 5000 ms of the base class.
// ---------------------------------------------------------------------------

internal object SnowDeskPauseDelayFingerprint : Fingerprint(
    definingClass = SNOW_BASE_SUPER_WALLPAPER,
    name = "getDeskPauseDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(3000)),
)

internal object SnowBaseAod2LockPauseDelayFingerprint : Fingerprint(
    definingClass = SNOW_BASE_SUPER_WALLPAPER,
    name = "getWallPaperAod2LockPauseDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(3000)),
)

internal object SnowAod2LockPauseDelayFingerprint : Fingerprint(
    definingClass = SNOW_SUPER_WALLPAPER,
    name = "getWallPaperAod2LockPauseDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(3000)),
)

internal object SnowOffsetDelayFingerprint : Fingerprint(
    definingClass = SNOW_BASE_SUPER_WALLPAPER,
    name = "getWallPaperOffsetDelay",
    returnType = "I",
    parameters = emptyList(),
    filters = listOf(literal(3000)),
)

// ---------------------------------------------------------------------------
// Unlock. The USER_PRESENT receiver posts a Runnable (SuperWallpaper$8$1) that
// drops the "Desk" Unity message unless DisplayUtils.getDisplayState() == 4.
// On non-MIUI ROMs the state is still wrong during the unlock animation, so
// lock -> home never animates.
// ---------------------------------------------------------------------------

internal object SnowDisplayStateCheckFingerprint : Fingerprint(
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
        // Distinguishes this Runnable from the other getDisplayState users.
        string("renderThreadID--unlock"),
    ),
)
