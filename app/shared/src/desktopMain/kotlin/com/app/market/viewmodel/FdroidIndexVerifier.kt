package com.app.market.viewmodel

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.jar.JarInputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val FDROID_INDEX_SIGNER_SHA256 = "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"

internal actual fun parseVerifiedFdroidIndexJar(bytes: ByteArray, base: String): List<FdroidApp> {
    val jar = JarInputStream(ByteArrayInputStream(bytes), true)
    var json: String? = null
    while (true) {
        val entry = jar.nextJarEntry ?: break
        if (entry.name != "index-v1.json") {
            val buffer = ByteArray(8192)
            while (jar.read(buffer) >= 0) Unit
            continue
        }
        val content = jar.readBytes()
        val certs = entry.certificates ?: error("F-Droid index is not signed")
        check(certs.any { cert -> MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02X".format(it) } == FDROID_INDEX_SIGNER_SHA256 }) {
            "F-Droid index signer does not match the trusted repository key"
        }
        json = content.decodeToString()
    }
    val root = Json.parseToJsonElement(json ?: error("Signed F-Droid index does not contain index-v1.json")).jsonObject
    val apps = (root["apps"] as? JsonArray).orEmpty().associateBy { it.str("packageName") }
    val packages = root["packages"]?.jsonObject ?: error("F-Droid index has no packages")
    return packages.mapNotNull { (id, element) ->
        val metadata = apps[id] as? JsonObject ?: return@mapNotNull null
        val version = (element as? JsonArray)?.mapNotNull { it as? JsonObject }?.filter {
            it.str("apkName").isNotBlank() && it.long("versionCode") > 0 && it.str("hashType", "sha256").equals("sha256", true) && it.str("hash").isSha256()
        }?.maxByOrNull { it.long("versionCode") } ?: return@mapNotNull null
        val localized = metadata["localized"] as? JsonObject
        val locale = localized?.get("zh-CN") as? JsonObject ?: localized?.get("en-US") as? JsonObject
        val icon = locale?.str("icon").takeUnless { it.isNullOrBlank() } ?: metadata.str("icon")
        val urls = iconUrls(base, icon)
        FdroidApp(id, locale?.str("name").takeUnless { it.isNullOrBlank() } ?: metadata.str("name", id),
            locale?.str("summary").takeUnless { it.isNullOrBlank() } ?: metadata.str("summary"),
            locale?.str("description").takeUnless { it.isNullOrBlank() } ?: metadata.str("description"),
            version.str("versionName"), version.long("versionCode"), version.str("apkName"), version.long("size"),
            version.str("hash"), urls.firstOrNull().orEmpty(), urls.drop(1))
    }.sortedBy { it.name.lowercase() }
}

private fun JsonElement?.str(key: String, default: String = "") = (this as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull ?: default
private fun JsonElement?.long(key: String) = str(key).toLongOrNull() ?: 0L
private fun String.isSha256() = length == 64 && all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
private fun iconUrls(base: String, icon: String): List<String> {
    if (icon.isBlank()) return emptyList()
    val path = icon.removePrefix("/")
    if (path.startsWith("icons/") || path.startsWith("icons-160/") || path.startsWith("icons-320/") || path.startsWith("icons-640/")) return listOf(base + path)
    return listOf("icons-640", "icons-320", "icons-160", "icons").map { "$base$it/$path" }
}

