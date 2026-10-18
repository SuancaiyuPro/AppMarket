package com.app.market.viewmodel

/** Verifies the signed repository index and extracts its catalog. Android implementations stream-parse the large index. */
internal expect fun parseVerifiedFdroidIndexJar(bytes: ByteArray, base: String): List<FdroidApp>

