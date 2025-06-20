/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed -> in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.konan.properties

import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.Configurables
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.util.*
import java.io.File

interface TargetableExternalStorage {
    fun targetString(key: String): String?
    fun targetList(key: String): List<String>
    fun hostString(key: String): String?
    fun hostList(key: String): List<String>
    fun hostTargetString(key: String): String?
    fun hostTargetList(key: String): List<String>
    fun absolute(value: String?): String
    fun downloadDependencies()
}

abstract class KonanPropertiesLoader(
    override val target: KonanTarget,
    val properties: Properties,
    private val dependenciesRoot: String?,
    private val host: KonanTarget = HostManager.host,
    private val progressCallback: ProgressCallback,
) : Configurables {


    private val predefinedLlvmDistributions: Set<String> =
        properties.propertyList("predefinedLlvmDistributions").toSet()

    private val predefinedLibffiVersions: Set<String> =
        properties.propertyList("predefinedLibffiVersions").toSet()

    private fun getPredefinedDependencyOrNull(
        dependencyName: String,
        dependencyAccessor: () -> String?,
        predefinedDependencies: Set<String>,
    ): String? {
        // Store into variable to avoid repeated resolve.
        val dependency = dependencyAccessor()
            ?: error("Undefined $dependencyName!")
        return when (dependency) {
            in predefinedDependencies -> dependency
            else -> null
        }
    }

    private fun compilerDependencies(): List<String> = listOfNotNull(
        getPredefinedDependencyOrNull("LLVM home", this::llvmHome, predefinedLlvmDistributions),
        getPredefinedDependencyOrNull("libffi version", this::libffiDir, predefinedLibffiVersions)
    )

    open val dependencies: List<String>
        get() = hostTargetList("dependencies") + hostList("commonDependencies") + compilerDependencies()

    override fun downloadDependencies() {
        dependencyProcessor!!.run()
    }

    fun downloadDependencies(archiveExtractor: ArchiveExtractor) {
        dependencyProcessor!!.run(archiveExtractor)
    }

    // TODO: We may want to add caching to avoid repeated resolve.
    override fun targetString(key: String): String? = properties.targetString(key, target)
    override fun targetList(key: String): List<String> = properties.targetList(key, target)
    override fun hostString(key: String): String? = properties.hostString(key, host)
    override fun hostList(key: String): List<String> = properties.hostList(key, host)
    override fun hostTargetString(key: String): String? = properties.hostTargetString(key, target, host)
    override fun hostTargetList(key: String): List<String> = properties.hostTargetList(key, target, host)

    override fun absolute(value: String?): String =
        dependencyProcessor!!.resolve(value!!).absolutePath

    private val dependencyProcessor by lazy {
        dependenciesRoot?.let {
            DependencyProcessor(
                dependenciesRoot = File(dependenciesRoot),
                properties = this,
                archiveType = defaultArchiveTypeByHost(host),
                customProgressCallback = progressCallback
            )
        }
    }
}

private fun defaultArchiveTypeByHost(host: KonanTarget): ArchiveType = when (host) {
    KonanTarget.LINUX_X64, KonanTarget.MACOS_X64, KonanTarget.MACOS_ARM64 -> ArchiveType.TAR_GZ
    KonanTarget.MINGW_X64 -> ArchiveType.ZIP
    else -> error("$host can't be a host platform!")
}