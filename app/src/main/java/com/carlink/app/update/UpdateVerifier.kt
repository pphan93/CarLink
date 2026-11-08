package com.carlink.app.update

import java.io.File
import java.security.MessageDigest

/** CarLink's release signing key. An update is installed only when its APK is signed by it. */
object ReleaseKey {
    /** SHA-256 of the release certificate (`apksigner verify --print-certs`, scripts/release.sh). */
    const val CERT_SHA256 = "ED:EB:2D:19:B8:C6:4D:53:D0:0A:01:32:46:FE:17:39:76:93:63:81:87:1B:FD:56:5B:64:C9:36:CE:75:70:38"

    /** Lower-case hex without separators, so `03:74:…` and `0374…` compare equal. */
    fun normalize(fingerprint: String): String = fingerprint.filter { it.isLetterOrDigit() }.lowercase()

    val normalized: String = normalize(CERT_SHA256)
}

/** Hex SHA-256 of a file. */
fun interface Sha256Digest {
    fun sha256Hex(file: File): String
}

object JavaSha256 : Sha256Digest {
    override fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

/** What Android reads from an APK file: its package, versionCode and current signers. */
data class ApkFacts(
    val packageName: String,
    val versionCode: Long,
    /** SHA-256 of each current signing certificate, normalized by [ReleaseKey.normalize]. */
    val signerSha256: Set<String>,
)

/** Reads [ApkFacts] from an APK file; null when it is not a readable APK. */
fun interface ApkInspector {
    fun inspect(file: File): ApkFacts?
}

/** Why a downloaded APK was refused. */
enum class VerifyFailure { CHECKSUM_MISMATCH, UNREADABLE, WRONG_PACKAGE, NOT_NEWER, WRONG_SIGNER }

/**
 * The three checks before an update is installed, all of which must pass:
 * (a) the file's SHA-256 equals the release's `.sha256` asset;
 * (b) the APK is [packageName] with a versionCode above [installedVersionCode];
 * (c) its only current signer is the pinned release key [pinnedSha256].
 */
class UpdateVerifier(
    private val digest: Sha256Digest,
    private val inspector: ApkInspector,
    private val packageName: String,
    private val installedVersionCode: Long,
    private val pinnedSha256: String = ReleaseKey.CERT_SHA256,
) {
    /** Null when [file] passed every check. */
    fun verify(file: File, expectedSha256: String): VerifyFailure? {
        if (!file.isFile) return VerifyFailure.UNREADABLE
        val actual = runCatching { digest.sha256Hex(file) }.getOrNull() ?: return VerifyFailure.UNREADABLE
        if (!actual.equals(expectedSha256.trim(), ignoreCase = true)) return VerifyFailure.CHECKSUM_MISMATCH
        val facts = runCatching { inspector.inspect(file) }.getOrNull() ?: return VerifyFailure.UNREADABLE
        if (facts.packageName != packageName) return VerifyFailure.WRONG_PACKAGE
        if (facts.versionCode <= installedVersionCode) return VerifyFailure.NOT_NEWER
        if (facts.signerSha256 != setOf(ReleaseKey.normalize(pinnedSha256))) return VerifyFailure.WRONG_SIGNER
        return null
    }
}
