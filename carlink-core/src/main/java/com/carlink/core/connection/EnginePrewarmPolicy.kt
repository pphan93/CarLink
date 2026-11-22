package com.carlink.core.connection

import com.carlink.core.core.PhoneKind

/**
 * Whether to start Android Auto's ":aa" process ahead of time ([Connection.prewarm]). The warm
 * process saves a cold process start when an Android phone connects, and costs its memory for as
 * long as CarLink runs (18 MB PSS on the low-end emulator, 2026-10-15). On a memory-tight unit
 * (a low-RAM build or 3 GB or less) it is mostly worth that only when an Android phone is known; with
 * only iPhones the process starts when an Android phone first connects, as it always
 * could. Units with more memory prewarm too, except when only iPhones are known: there the warm
 * process held back every CarPlay start after a restart of the main process, while CarLink asked
 * it whether a session ran (up to 5 s, connection log 2026-11-10). With no phone known yet a 3 GB
 * unit ([THREE_GB_CLASS_MB] or more reported, not a low-RAM build) prewarms as well: the first
 * phone is as likely an Android phone, and 18 MB is under 1% of its memory.
 */
object EnginePrewarmPolicy {
    /** A nominal 3 GB unit reports 2.6-2.9 GB (firmware carve-outs); a 2 GB one at most ~1.95 GB. */
    const val THREE_GB_CLASS_MB = 2_500L

    fun memoryTight(signals: DeviceSignals): Boolean =
        signals.lowRamDevice || signals.totalRamMb in 1..DeviceSignals.LOW_END_RAM_MB

    /** A constrained unit ([DeviceSignals.constrained]) never holds the second process warm: 18-27 MB for its whole run. */
    fun shouldPrewarm(signals: DeviceSignals, knownPhones: Collection<PhoneKind>): Boolean = when {
        signals.constrained -> false
        PhoneKind.ANDROID in knownPhones -> true
        // Only iPhones: the warm process held back every CarPlay start (up to 5 s, 2026-11-10).
        knownPhones.isNotEmpty() -> false
        else -> !memoryTight(signals) || (!signals.lowRamDevice && signals.totalRamMb >= THREE_GB_CLASS_MB)
    }
}
