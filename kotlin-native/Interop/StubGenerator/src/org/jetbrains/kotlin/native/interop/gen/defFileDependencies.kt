package org.jetbrains.kotlin.native.interop.gen

import kotlinx.cinterop.usingJvmCInteropCallbacks
import org.jetbrains.kotlin.konan.util.DefFile
import org.jetbrains.kotlin.utils.usingNativeMemoryAllocator
import org.jetbrains.kotlin.native.interop.gen.jvm.KotlinPlatform
import org.jetbrains.kotlin.native.interop.gen.jvm.buildNativeLibrary
import org.jetbrains.kotlin.native.interop.gen.jvm.prepareTool
import org.jetbrains.kotlin.native.interop.indexer.NativeLibraryHeaders
import org.jetbrains.kotlin.native.interop.indexer.getHeaderPaths
import org.jetbrains.kotlin.native.interop.tool.CInteropArguments
import org.jetbrains.kotlin.utils.mapToSetOrEmpty
import java.io.File
import kotlin.streams.toList

fun defFileDependencies(args: Array<String>, runFromDaemon: Boolean) {
    val defFiles = mutableListOf<File>()
    val targets = mutableListOf<String>()

    var index = 0
    while (index < args.size) {
        val arg = args[index]

        ++index

        when (arg) {
            "-target" -> {
                targets += args[index]
                ++index
            }
            else -> {
                defFiles.add(File(arg))
            }
        }
    }
    usingNativeMemoryAllocator {
        usingJvmCInteropCallbacks {
            defFileDependencies(makeDependencyAssigner(targets, defFiles, runFromDaemon))
        }
    }
}

private fun makeDependencyAssigner(targets: List<String>, defFiles: List<File>, runFromDaemon: Boolean) =
        CompositeDependencyAssigner(targets.map { makeDependencyAssignerForTarget(it, defFiles, runFromDaemon) })

private fun makeDependencyAssignerForTarget(target: String, defFiles: List<File>, runFromDaemon: Boolean): SingleTargetDependencyAssigner {
    val cinteropArguments = CInteropArguments()
    cinteropArguments.argParser.parse(arrayOf())
    val tool = prepareTool(target, KotlinPlatform.NATIVE, runFromDaemon, konanDataDir = cinteropArguments.konanDataDir)
    val libraries = defFiles.parallelStream().map {
        it to buildNativeLibrary(
                tool,
                DefFile(it, tool.substitutions),
                cinteropArguments,
                ImportsImpl(emptyMap()),
        ).getHeaderPaths()
    }.toList().toMap()
    return SingleTargetDependencyAssigner(libraries)
}

private fun defFileDependencies(dependencyAssigner: DependencyAssigner) {
    val dependenciesExpander = DependenciesExpander()

    while (!dependencyAssigner.isDone()) {
        val (defFile, directDependencies) = dependencyAssigner.getReady().entries.minByOrNull { it.key }!!
        val expandedDependencies = dependenciesExpander.expand(defFile, directDependencies)
        dependencyAssigner.markDone(defFile)
        patchDepends(defFile, expandedDependencies.sorted())
        println("${defFile.name} done.")
    }
}

private fun patchDepends(file: File, newDepends: List<String>) {
    val defFileLines = file.readLines()
    val dependsLine = buildString {
        append("depends =")
        newDepends.forEach {
            append(" ")
            append(it)
        }
    }
    val newDefFileLines = listOf(dependsLine) + defFileLines.filter { !it.startsWith("depends =") }

    file.bufferedWriter().use { writer ->
        newDefFileLines.forEach { writer.appendLine(it) }
    }
}

private interface DependencyAssigner {
    fun isDone(): Boolean
    fun getReady(): Map<File, Set<String>>
    fun markDone(file: File)
}

private class CompositeDependencyAssigner(val dependencyAssigners: List<DependencyAssigner>) : DependencyAssigner {
    override fun isDone(): Boolean = dependencyAssigners.all { it.isDone() }

    override fun getReady(): Map<File, Set<String>> {
        return dependencyAssigners.map { it.getReady() }.reduce { left, right ->
            (left.keys intersect right.keys)
                    .associateWith { left.getValue(it) union right.getValue(it) }
        }.also {
            require(it.isNotEmpty()) { "incompatible dependencies" } // TODO: add more info.
        }
    }

    override fun markDone(file: File) {
        dependencyAssigners.forEach { it.markDone(file) }
    }
}

private class SingleTargetDependencyAssigner(
        defFilesToHeaders: Map<File, NativeLibraryHeaders<String>>
) : DependencyAssigner {
    private val pendingDefFilesToHeaders = defFilesToHeaders.toMutableMap()

    private val processedHeadersToDefFiles = mutableMapOf<String, File>()

    init {
        val ownedHeaders = pendingDefFilesToHeaders.values.flatMap { it.ownHeaders }
        val unownedHeadersToDefFiles = mutableMapOf<String, File>()

        pendingDefFilesToHeaders.forEach { (def, lib) ->
            (lib.importedHeaders - ownedHeaders).forEach {
                unownedHeadersToDefFiles.putIfAbsent(it, def)
            }
        }

        if (unownedHeadersToDefFiles.isNotEmpty()) {
            error("Unowned headers:\n" +
                    unownedHeadersToDefFiles.entries.joinToString("\n") { "${it.key}\n  imported by: ${it.value.name}" })
        }
    }

    override fun isDone(): Boolean = pendingDefFilesToHeaders.isEmpty()

    override fun getReady(): Map<File, Set<String>> {
        val result = mutableMapOf<File, Set<String>>()

        defFiles@ for ((defFile, headers) in pendingDefFilesToHeaders) {
            val depends = mutableSetOf<String>()

            headers@ for (header in (headers.ownHeaders + headers.importedHeaders)) {
                val dependency = processedHeadersToDefFiles[header]
                        ?: if (header in headers.ownHeaders) continue@headers else continue@defFiles

                depends.add(dependency.nameWithoutExtension)
            }
            result[defFile] = depends
        }

        if (result.isEmpty()) {
            pendingDefFilesToHeaders.entries.forEach { (def, headers) ->
                println(def.name)
                println("Own headers:")
                headers.ownHeaders.forEach { println(it) }
                println("Unowned imported headers:")
                headers.importedHeaders.forEach { if (it !in processedHeadersToDefFiles) println(it) }
                println()
            }
            error("Cyclic dependency? Remaining libs:\n" + pendingDefFilesToHeaders.keys.joinToString("\n") { it.name })
        }

        return result
    }

    override fun markDone(file: File) {
        val headers = pendingDefFilesToHeaders.remove(file)!!

        headers.ownHeaders.forEach {
            processedHeadersToDefFiles.putIfAbsent(it, file)
        }
    }
}

private class DependenciesExpander {
    private class Dependency(val defFile: File, val directDependencies: Set<Dependency>) {
        val name: String = defFile.nameWithoutExtension

        val allDependencies: Set<String> by lazy {
            buildSet {
                directDependencies.forEach { directDependency ->
                    this += directDependency.name
                    addAll(directDependency.allDependencies)
                }
            }
        }
    }

    private val cache = mutableMapOf<String, Dependency>()

    fun expand(defFile: File, directDepends: Set<String>): Set<String> {
        val dependency = Dependency(defFile, directDepends.mapToSetOrEmpty { cache.getValue(it) })
        check(cache.put(dependency.name, dependency) == null)
        return dependency.allDependencies
    }
}
