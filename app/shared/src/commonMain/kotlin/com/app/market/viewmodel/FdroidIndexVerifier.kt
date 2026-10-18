package com.app.market.viewmodel

/** Verifies the signed repository index and returns its authenticated JSON payload. */
internal expect fun verifyFdroidIndexJar(bytes: ByteArray): String

