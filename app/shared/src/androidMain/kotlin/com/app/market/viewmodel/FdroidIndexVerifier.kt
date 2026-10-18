package com.app.market.viewmodel

import android.util.JsonReader
import android.util.JsonToken
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.jar.JarInputStream

private const val FDROID_INDEX_SIGNER_SHA256 = "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"

private data class AppInfo(val name: String, val summary: String, val description: String, val icon: String)
private data class ApkInfo(val name: String, val version: String, val code: Long, val size: Long, val hash: String)

/** Streams the large JSON entry from the signed JAR so Android never allocates one 128+ MiB byte array. */
internal actual fun parseVerifiedFdroidIndexJar(bytes: ByteArray, base: String): List<FdroidApp> {
    val jar = JarInputStream(ByteArrayInputStream(bytes), true)
    var apps = emptyMap<String, AppInfo>()
    val packages = mutableMapOf<String, ApkInfo>()
    var foundIndex = false
    while (true) {
        val entry = jar.nextJarEntry ?: break
        if (entry.name != "index-v1.json") {
            val buffer = ByteArray(8192)
            while (jar.read(buffer) >= 0) Unit
            continue
        }
        foundIndex = true
        val reader = JsonReader(InputStreamReader(jar, Charsets.UTF_8)).apply { isLenient = true }
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "apps" -> apps = readApps(reader)
                "packages" -> readPackages(reader, packages)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        // Reading through the end of the entry causes JarInputStream to verify its signature.
        while (jar.read() >= 0) Unit
        val certificates = entry.certificates ?: error("F-Droid index is not signed")
        check(certificates.any { cert ->
            MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                .joinToString("") { byte -> "%02X".format(byte) } == FDROID_INDEX_SIGNER_SHA256
        }) { "F-Droid index signer does not match the trusted repository key" }
    }
    check(foundIndex) { "Signed F-Droid index does not contain index-v1.json" }
    return packages.mapNotNull { (id, apk) ->
        val app = apps[id] ?: return@mapNotNull null
        val iconUrls = iconUrls(base, app.icon)
        FdroidApp(id, app.name.ifBlank { id }, app.summary, app.description, apk.version, apk.code,
            apk.name, apk.size, apk.hash, iconUrls.firstOrNull().orEmpty(), iconUrls.drop(1))
    }.sortedBy { it.name.lowercase() }
}

private fun readApps(reader: JsonReader): Map<String, AppInfo> = buildMap {
    reader.beginArray()
    while (reader.hasNext()) {
        var id = ""; var name = ""; var summary = ""; var description = ""; var icon = ""
        var fallbackName = ""; var fallbackSummary = ""; var fallbackDescription = ""; var fallbackIcon = ""
        reader.beginObject()
        while (reader.hasNext()) when (reader.nextName()) {
            "packageName" -> id = reader.nextStringOrEmpty()
            "name" -> name = reader.nextStringOrEmpty()
            "summary" -> summary = reader.nextStringOrEmpty()
            "description" -> description = reader.nextStringOrEmpty()
            "icon" -> icon = reader.nextStringOrEmpty()
            "localized" -> {
                reader.beginObject()
                while (reader.hasNext()) {
                    val locale = reader.nextName()
                    var localName = ""; var localSummary = ""; var localDescription = ""; var localIcon = ""
                    reader.beginObject()
                    while (reader.hasNext()) when (reader.nextName()) {
                        "name" -> localName = reader.nextStringOrEmpty()
                        "summary" -> localSummary = reader.nextStringOrEmpty()
                        "description" -> localDescription = reader.nextStringOrEmpty()
                        "icon" -> localIcon = reader.nextStringOrEmpty()
                        else -> reader.skipValue()
                    }
                    reader.endObject()
                    if (locale == "zh-CN") { name = localName; summary = localSummary; description = localDescription; icon = localIcon }
                    if (locale == "en-US") { fallbackName = localName; fallbackSummary = localSummary; fallbackDescription = localDescription; fallbackIcon = localIcon }
                    if (name.isBlank() && fallbackName.isBlank()) { fallbackName = localName; fallbackSummary = localSummary; fallbackDescription = localDescription; fallbackIcon = localIcon }
                }
                reader.endObject()
            }
            else -> reader.skipValue()
        }
        reader.endObject()
        if (id.isNotBlank()) put(id, AppInfo(
            name.ifBlank { fallbackName }, summary.ifBlank { fallbackSummary },
            description.ifBlank { fallbackDescription }, icon.ifBlank { fallbackIcon },
        ))
    }
    reader.endArray()
}

private fun readPackages(reader: JsonReader, packages: MutableMap<String, ApkInfo>) {
    reader.beginObject()
    while (reader.hasNext()) {
        val id = reader.nextName()
        var best: ApkInfo? = null
        reader.beginArray()
        while (reader.hasNext()) {
            var file = ""; var version = ""; var code = 0L; var size = 0L; var hash = ""; var hashType = "sha256"
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
                "apkName" -> file = reader.nextStringOrEmpty()
                "versionName" -> version = reader.nextStringOrEmpty()
                "versionCode" -> code = reader.nextLongOrZero()
                "size" -> size = reader.nextLongOrZero()
                "hash" -> hash = reader.nextStringOrEmpty()
                "hashType" -> hashType = reader.nextStringOrEmpty()
                else -> reader.skipValue()
            }
            reader.endObject()
            if (file.isNotBlank() && code > 0 && hashType.equals("sha256", true) && hash.isSha256() &&
                (best == null || code > best.code)) best = ApkInfo(file, version, code, size, hash)
        }
        reader.endArray()
        best?.let { packages[id] = it }
    }
    reader.endObject()
}

private fun String.isSha256() = length == 64 && all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
private fun JsonReader.nextStringOrEmpty(): String = if (peek() == JsonToken.NULL) { nextNull(); "" } else nextString()
private fun JsonReader.nextLongOrZero(): Long = if (peek() == JsonToken.NULL) { nextNull(); 0L } else nextLong()

private fun iconUrls(base: String, icon: String): List<String> {
    if (icon.isBlank()) return emptyList()
    val path = icon.removePrefix("/")
    if (path.startsWith("icons/") || path.startsWith("icons-160/") || path.startsWith("icons-320/") || path.startsWith("icons-640/")) return listOf(base + path)
    return listOf("icons-640", "icons-320", "icons-160", "icons").map { "$base$it/$path" }
}

