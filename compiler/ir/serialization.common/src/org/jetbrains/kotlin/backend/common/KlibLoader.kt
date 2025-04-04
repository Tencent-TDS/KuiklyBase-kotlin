/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.backend.common.KlibLoaderResult.ProblemCase.IncompatibleAbiVersion
import org.jetbrains.kotlin.backend.common.KlibLoaderResult.ProblemCase.InvalidLibraryFormat
import org.jetbrains.kotlin.backend.common.KlibLoaderResult.ProblemCase.LibraryNotFound
import org.jetbrains.kotlin.backend.common.KlibLoaderResult.ProblemCase.PlatformCheckMismatch
import org.jetbrains.kotlin.backend.common.KlibLoaderResult.ProblematicLibrary
import org.jetbrains.kotlin.konan.file.ZipFileSystemAccessor
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.impl.KLIB_DEFAULT_COMPONENT_NAME
import org.jetbrains.kotlin.library.impl.createKotlinLibrary
import org.jetbrains.kotlin.library.isAnyPlatformStdlib
import org.jetbrains.kotlin.utils.addToStdlib.runIf
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import org.jetbrains.kotlin.konan.file.File as KFile

/**
 * The [KlibLoader] component helps to load [KotlinLibrary]s based on the given paths
 * [KlibLoaderSpec.libraryPaths] and the specified settings, ex: [KlibLoaderSpec.zipFileSystemAccessor].
 *
 * This component also performs the limited set of very basic KLIB checks, such as
 * - Checking a platform and a specific target, see [KlibLoaderSpec.platformChecker]
 * - Checking ABI version compatibility, see [KlibLoaderSpec.maxPermittedAbiVersion]
 *
 * Any other checks, if they are necessary, should be performed outside [KlibLoader].
 */
class KlibLoader(init: KlibLoaderSpec.() -> Unit) {
    private val libraryPaths = ArrayList<String>()
    private var platformChecker: KlibPlatformChecker? = null
    private var maxPermittedAbiVersion: KotlinAbiVersion? = null
    private var zipFileSystemAccessor: ZipFileSystemAccessor? = null

    private val spec = object : KlibLoaderSpec {
        override fun libraryPaths(paths: List<String>) {
            libraryPaths.addAll(paths)
        }

        override fun libraryPaths(vararg paths: String) {
            libraryPaths += paths
        }

        override fun platformChecker(checker: KlibPlatformChecker) {
            platformChecker = checker
        }

        override fun maxPermittedAbiVersion(abiVersion: KotlinAbiVersion) {
            maxPermittedAbiVersion = abiVersion
        }

        override fun zipFileSystemAccessor(accessor: ZipFileSystemAccessor) {
            zipFileSystemAccessor = accessor
        }
    }

    init {
        spec.init()
    }

    fun load(): KlibLoaderResult {
        // This is needed to avoid inspecting the same paths multiple times.
        // N.B. The original order of paths is preserved!
        val uniqueLibraryPaths = LinkedHashSet(libraryPaths)

        // And this is needed to avoid inspecting the same canonical paths more than once.
        // Note that transformation of a library path, which actually can be any form of
        // a relative path, to a canonical path happens after checking that the path is valid
        // and points to an existing file system object. That is why we have to keep both
        // [uniqueLibraryPaths] and [visitedCanonicalPaths] to have complete deduplication.
        val visitedCanonicalPaths = HashSet<Path>(uniqueLibraryPaths.size)

        val problematicLibraries = ArrayList<ProblematicLibrary>(uniqueLibraryPaths.size)
        val loadedLibrariesStdlibFirst = ArrayList<KotlinLibrary>(uniqueLibraryPaths.size)

        // Just to avoid accessing mutable nullable property, which does not work well with safe casts, in the loop below.
        val maxPermittedAbiVersion: KotlinAbiVersion? = maxPermittedAbiVersion

        for (rawPath in uniqueLibraryPaths) {
            val validPath: Path? = runIf(rawPath.isNotEmpty()) {
                try {
                    Paths.get(rawPath)
                } catch (_: InvalidPathException) {
                    null
                }
            }

            if (validPath == null || !validPath.exists()) {
                problematicLibraries += ProblematicLibrary(rawPath, LibraryNotFound)
                continue
            }

            val canonicalPath: Path = validPath.toRealPath()
            if (!visitedCanonicalPaths.add(canonicalPath))
                continue

            val library = createKotlinLibrary(
                KFile(canonicalPath),
                component = KLIB_DEFAULT_COMPONENT_NAME,
                zipAccessor = zipFileSystemAccessor
            )

            val abiVersion: KotlinAbiVersion? = try {
                // Important: We wrap the first read operation with try-catch, as this is the simplest way
                // to check the correctness of the library layout. If the manifest, which is the essential
                // part of KLIB, is not available or corrupted, we immediately treat this library as problematic.
                // All later reads can be done outside the try-catch block.
                library.versions.abiVersion
            } catch (_: Exception) {
                problematicLibraries += ProblematicLibrary(rawPath, InvalidLibraryFormat)
                continue
            }

            if (platformChecker?.check(library) == false) {
                problematicLibraries += ProblematicLibrary(rawPath, PlatformCheckMismatch)
                continue
            }

            if (abiVersion != null && maxPermittedAbiVersion != null) {
                if (!abiVersion.isAtMost(maxPermittedAbiVersion)) {
                    problematicLibraries += ProblematicLibrary(
                        rawPath,
                        IncompatibleAbiVersion(
                            actualAbiVersion = abiVersion,
                            maxPermittedAbiVersion = maxPermittedAbiVersion
                        )
                    )
                    continue
                }
            }

            // Stdlib is a special library. It many cases it should be the first to be deserialized
            // to make the necessary preparations (set up built-ins, etc.)
            // So, it's wise to explicitly set it to the first place even if sorting of libraries per se
            // is not the responsibility of [KlibLoader].
            if (library.isAnyPlatformStdlib)
                loadedLibrariesStdlibFirst.add(0, library)
            else
                loadedLibrariesStdlibFirst.add(library)
        }

        return KlibLoaderResult(loadedLibrariesStdlibFirst, problematicLibraries)
    }
}

interface KlibLoaderSpec {
    fun libraryPaths(paths: List<String>)
    fun libraryPaths(vararg paths: String)
    fun platformChecker(checker: KlibPlatformChecker)
    fun maxPermittedAbiVersion(abiVersion: KotlinAbiVersion)
    fun zipFileSystemAccessor(accessor: ZipFileSystemAccessor)
}

/**
 * All libraries in [librariesStdlibFirst] are preserved in their original order (CLI-order),
 * except for stdlib, which, if it is present, must be at the first position.
 */
class KlibLoaderResult(
    val librariesStdlibFirst: List<KotlinLibrary>,
    val problematicLibraries: List<ProblematicLibrary>
) {
    val isWithProblems: Boolean get() = problematicLibraries.isNotEmpty()

    class ProblematicLibrary(val libraryPath: String, val problemCase: ProblemCase)

    sealed interface ProblemCase {
        /**
         * The library is not found by the given path:
         * - the path is invalid
         * - or no file system object exists by that path
         */
        object LibraryNotFound : ProblemCase

        /**
         * The path points to some real file system object, but:
         * - either this is not a KLIB library
         * - or a corrupted KLIB library
         */
        object InvalidLibraryFormat : ProblemCase

        /**
         * The library has not passed the platform and target check by [KlibPlatformChecker] that was set
         * in [KlibLoaderSpec.platformChecker].
         */
        object PlatformCheckMismatch : ProblemCase

        /**
         * The library does not match the ABI version requirements that were set in [KlibLoaderSpec.maxPermittedAbiVersion].
         *
         * @property actualAbiVersion The actual ABI version in a KLIB.
         * @property maxPermittedAbiVersion The max permitted ABI version set in [KlibLoader] via
         *  [KlibLoaderSpec.maxPermittedAbiVersion] call. Note, this property is nullable because we
         *  might want to extend ABI version checks in the future by adding the min permitted ABI version.
         */
        class IncompatibleAbiVersion(
            val actualAbiVersion: KotlinAbiVersion,
            val maxPermittedAbiVersion: KotlinAbiVersion?,
        ) : ProblemCase
    }
}
