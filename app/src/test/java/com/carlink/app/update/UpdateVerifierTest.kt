package com.carlink.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateVerifierTest {
    @get:Rule val temp = TemporaryFolder()

    private val pin = ReleaseKey.normalized
    private val good = ApkFacts(Updates.RELEASE_PACKAGE, 10100L, setOf(pin))

    private fun apk(bytes: String = "apk bytes") = temp.newFile().apply { writeText(bytes) }
    private fun verifier(facts: ApkFacts?, installed: Long = 10000L) =
        UpdateVerifier(JavaSha256, { facts }, Updates.RELEASE_PACKAGE, installed)

    @Test fun sha256OfAKnownInput() {
        // printf 'abc' | sha256sum
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            JavaSha256.sha256Hex(temp.newFile().apply { writeText("abc") }))
    }

    @Test fun pinnedFingerprintNormalizes() {
        assertEquals(64, pin.length)
        assertEquals("edeb2d19b8c64d53d00a013246fe173976936381871bfd565b64c936ce757038", pin)
        assertEquals(pin, ReleaseKey.normalize("ed eb 2d 19 b8 c6 4d 53 d0 0a 01 32 46 fe 17 39 76 93 63 81 87 1b fd 56 5b 64 c9 36 ce 75 70 38"))
    }

    @Test fun passesWhenAllThreeChecksPass() {
        val file = apk()
        assertNull(verifier(good).verify(file, JavaSha256.sha256Hex(file).uppercase()))
    }

    @Test fun checksumMismatch() {
        val file = apk()
        assertEquals(VerifyFailure.CHECKSUM_MISMATCH, verifier(good).verify(file, "0".repeat(64)))
    }

    @Test fun unreadableApk() {
        val file = apk()
        assertEquals(VerifyFailure.UNREADABLE, verifier(null).verify(file, JavaSha256.sha256Hex(file)))
        assertEquals(VerifyFailure.UNREADABLE, verifier(good).verify(java.io.File(temp.root, "missing.apk"), "0".repeat(64)))
    }

    @Test fun wrongPackage() {
        val file = apk()
        assertEquals(VerifyFailure.WRONG_PACKAGE,
            verifier(good.copy(packageName = "com.carlink.app.hudtest")).verify(file, JavaSha256.sha256Hex(file)))
    }

    @Test fun notNewer() {
        val file = apk()
        val sha = JavaSha256.sha256Hex(file)
        assertEquals(VerifyFailure.NOT_NEWER, verifier(good.copy(versionCode = 10000L)).verify(file, sha))
        assertEquals(VerifyFailure.NOT_NEWER, verifier(good.copy(versionCode = 9999L)).verify(file, sha))
    }

    @Test fun wrongOrExtraSigner() {
        val file = apk()
        val sha = JavaSha256.sha256Hex(file)
        val other = "f".repeat(64)
        assertEquals(VerifyFailure.WRONG_SIGNER, verifier(good.copy(signerSha256 = setOf(other))).verify(file, sha))
        assertEquals(VerifyFailure.WRONG_SIGNER, verifier(good.copy(signerSha256 = setOf(pin, other))).verify(file, sha))
        assertEquals(VerifyFailure.WRONG_SIGNER, verifier(good.copy(signerSha256 = emptySet())).verify(file, sha))
    }

    @Test fun inspectorThatThrowsIsUnreadable() {
        val file = apk()
        val verifier = UpdateVerifier(JavaSha256, { error("broken zip") }, Updates.RELEASE_PACKAGE, 10000L)
        assertEquals(VerifyFailure.UNREADABLE, verifier.verify(file, JavaSha256.sha256Hex(file)))
    }
}
