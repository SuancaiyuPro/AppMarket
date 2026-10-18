package com.app.market.viewmodel

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.jar.JarInputStream

private const val FDROID_INDEX_SIGNER_SHA256 = "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"

internal actual fun verifyFdroidIndexJar(bytes: ByteArray): String {
    val jar = JarInputStream(ByteArrayInputStream(bytes), true)
    var index: String? = null
    while (true) {
        val entry = jar.nextJarEntry ?: break
        val content = jar.readBytes()
        if (entry.name == "index-v1.json") {
            val certificates = entry.certificates ?: error("F-Droid index is not signed")
            val validSigner = certificates.any { certificate ->
                MessageDigest.getInstance("SHA-256")
                    .digest(certificate.encoded)
                    .joinToString("") { byte -> "%02X".format(byte) } == FDROID_INDEX_SIGNER_SHA256
            }
            check(validSigner) { "F-Droid index signer does not match the trusted repository key" }
            index = content.decodeToString()
        }
    }
    return index ?: error("Signed F-Droid index does not contain index-v1.json")
}

