/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan

import org.jetbrains.kotlin.backend.konan.serialization.CacheMetadata
import org.jetbrains.kotlin.backend.konan.serialization.CacheMetadataSerializer
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.konan.target.KonanTarget

/**
 * From cache root extract and parse metadata.properties file.
 */
internal val File.cacheMetadataFile: CacheMetadata?
    get() {
        require(isDirectory) { "$this must be cache root" }
        val metadata = child(CachedLibraries.METADATA_FILE_NAME)
        if (!metadata.isFile)
            return null // Some caches may not have metadata.
        return CacheMetadataSerializer.deserialize(metadata.bufferedReader())
    }

internal sealed interface MetadataCheckResult {
    object Ok : MetadataCheckResult
    class Fail(val description: String) : MetadataCheckResult

    val ok: Boolean
        get() = this is Ok
}

internal fun CacheMetadata.checkMetadataFits(
        target: KonanTarget,
        compilerFingerprint: String,
        runtimeFingerprint: String?,
): MetadataCheckResult {
    if (this.target != target) {
        return MetadataCheckResult.Fail("target mismatch: expected=$target actual=${this.target}")
    }
    if (this.compilerFingerprint != compilerFingerprint) {
        return MetadataCheckResult.Fail("compiler fingerprint mismatch: expected=$compilerFingerprint actual=${this.compilerFingerprint}")
    }
    this.runtimeFingerprint?.let { actualRuntimeFingerprint ->
        if (actualRuntimeFingerprint != runtimeFingerprint) {
            return MetadataCheckResult.Fail("$target target fingerprint mismatch: expected=$runtimeFingerprint actual=$actualRuntimeFingerprint")
        }
    }
    return MetadataCheckResult.Ok
}

internal fun CacheMetadata.checkMetadataFits(other: CacheMetadata) = checkMetadataFits(
        target = other.target,
        compilerFingerprint = other.compilerFingerprint,
        runtimeFingerprint = other.runtimeFingerprint,
)