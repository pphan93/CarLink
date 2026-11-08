package com.carlink.core.connection

import com.carlink.core.detect.AdapterFacts
import com.carlink.core.detect.ExternalBluetoothProbe
import com.carlink.core.detect.KnownPhoneBond
import com.carlink.core.detect.PortState
import com.carlink.core.detect.StereoBluetooth
import com.carlink.core.detect.StereoBluetoothReport
import com.carlink.core.detect.StereoBluetoothSignal
import org.junit.Assert.assertEquals
import org.junit.Test

/** Home's Bluetooth chooser wins over the probe, in both directions. */
class EffectiveStereoTest {
    private fun report(phoneBondedToAndroid: Boolean): StereoBluetoothReport {
        val signals = listOf(StereoBluetoothSignal.MODULE_SERVICE_ANSWERS, StereoBluetoothSignal.MODULE_SETTING)
        val adapter = AdapterFacts(true, true, true)
        val phones = listOf(KnownPhoneBond(com.carlink.core.core.PhoneKind.IPHONE, phoneBondedToAndroid))
        return StereoBluetoothReport(ExternalBluetoothProbe.classify(signals, adapter, phones), signals, null, emptyMap(),
            emptyMap(), PortState.OPEN, emptyList(), adapter, 1, phones)
    }

    @Test fun androidBluetoothIgnoresTheModule() {
        assertEquals(StereoBluetooth.NONE, DeviceEnvironment.effective(true, false, report(false)))
        assertEquals(StereoBluetooth.ZLINK_DAEMON, DeviceEnvironment.effective(false, false, report(false)))
    }

    @Test fun theStereosModuleIsUsedWhenChosenEvenWithTheIphoneBondedToAndroid() { // ZLink head unit, 2026-11-08
        assertEquals(StereoBluetooth.LIKELY_FALSE_POSITIVE, report(true).classification)
        assertEquals(StereoBluetooth.LIKELY_FALSE_POSITIVE, DeviceEnvironment.effective(false, false, report(true)))
        assertEquals(StereoBluetooth.ZLINK_DAEMON, DeviceEnvironment.effective(false, true, report(true)))
        assertEquals(StereoBluetooth.NONE, DeviceEnvironment.effective(true, false, report(true)))
    }
}
