/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.driver.phases

import llvm.LLVMDumpModule
import llvm.LLVMModuleRef
import llvm.LLVMWriteBitcodeToFile
import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.backend.common.phaser.createSimpleNamedCompilerPhase
import org.jetbrains.kotlin.backend.konan.*
import org.jetbrains.kotlin.backend.konan.NativeGenerationState
import org.jetbrains.kotlin.backend.konan.checkLlvmModuleExternalCalls
import org.jetbrains.kotlin.backend.konan.createLTOFinalPipelineConfig
import org.jetbrains.kotlin.backend.konan.driver.BasicPhaseContext
import org.jetbrains.kotlin.backend.konan.driver.PhaseContext
import org.jetbrains.kotlin.backend.konan.driver.PhaseEngine
import org.jetbrains.kotlin.backend.konan.driver.utilities.LlvmIrHolder
import org.jetbrains.kotlin.backend.konan.driver.utilities.getDefaultLlvmModuleActions
import org.jetbrains.kotlin.backend.konan.insertAliasToEntryPoint
import org.jetbrains.kotlin.backend.konan.llvm.verifyModule
import org.jetbrains.kotlin.backend.konan.optimizations.RemoveRedundantSafepointsPass
import org.jetbrains.kotlin.backend.konan.optimizations.removeMultipleThreadDataLoads
import org.jetbrains.kotlin.konan.target.SanitizerKind
import java.io.File
import kotlin.coroutines.*
import kotlinx.coroutines.*
import java.io.IOException
import llvm.*
import org.jetbrains.kotlin.backend.konan.driver.utilities.createTempFiles
import org.jetbrains.kotlin.backend.konan.llvm.parseBitcodeFile
import kotlinx.cinterop.*

internal data class WriteBitcodeFileInput(
        override val llvmModule: LLVMModuleRef,
        val outputFile: File,
) : LlvmIrHolder

/**
 * Write in-memory LLVM module to filesystem as a bitcode.
 */
internal val WriteBitcodeFilePhase = createSimpleNamedCompilerPhase<PhaseContext, WriteBitcodeFileInput>(
        "WriteBitcodeFile",
        "Write bitcode file",
) { context, (llvmModule, outputFile) ->
    // Insert `_main` after pipeline, so we won't worry about optimizations corrupting entry point.
    insertAliasToEntryPoint(context, llvmModule)
    LLVMWriteBitcodeToFile(llvmModule, outputFile.canonicalPath)
}

internal val CheckExternalCallsPhase = createSimpleNamedCompilerPhase<NativeGenerationState, Unit>(
        name = "CheckExternalCalls",
        description = "Check external calls",
        postactions = getDefaultLlvmModuleActions(),
) { context, _ ->
    checkLlvmModuleExternalCalls(context)
}

internal val RewriteExternalCallsCheckerGlobals = createSimpleNamedCompilerPhase<NativeGenerationState, Unit>(
        name = "RewriteExternalCallsCheckerGlobals",
        description = "Rewrite globals for external calls checker after optimizer run",
        postactions = getDefaultLlvmModuleActions(),
) { context, _ ->
    addFunctionsListSymbolForChecker(context)
}

internal class OptimizationState(
        konanConfig: KonanConfig,
        val llvmConfig: LlvmPipelineConfig
) : BasicPhaseContext(konanConfig)

internal fun optimizationPipelinePass(name: String, description: String, pipeline: (LlvmPipelineConfig, LoggingContext) -> LlvmOptimizationPipeline) =
        createSimpleNamedCompilerPhase<OptimizationState, LLVMModuleRef>(
                name = name,
                description = description,
                postactions = getDefaultLlvmModuleActions(),
        ) { context, module ->
            pipeline(context.llvmConfig, context).use {
                it.execute(module)
            }
        }


internal val MandatoryBitcodeLLVMPostprocessingPhase = optimizationPipelinePass(
        name = "MandatoryBitcodeLLVMPostprocessingPhase",
        description = "Mandatory bitcode llvm postprocessing",
        pipeline = ::MandatoryOptimizationPipeline,
)

internal val ModuleBitcodeOptimizationPhase = optimizationPipelinePass(
        name = "ModuleBitcodeOptimization",
        description = "Optimize bitcode",
        pipeline = ::ModuleOptimizationPipeline,
)

internal val LTOBitcodeOptimizationPhase = optimizationPipelinePass(
        name = "LTOBitcodeOptimization",
        description = "Runs llvm lto pipeline",
        pipeline = ::LTOOptimizationPipeline
)

internal val ThreadSanitizerPhase = optimizationPipelinePass(
        name = "ThreadSanitizer",
        description = "Prepare to run with thread sanitizer",
        pipeline = ::ThreadSanitizerPipeline,
)

internal val RemoveRedundantSafepointsPhase = createSimpleNamedCompilerPhase<BitcodePostProcessingContext, Unit>(
        name = "RemoveRedundantSafepoints",
        description = "Remove function prologue safepoints inlined to another function",
        postactions = getDefaultLlvmModuleActions(),
        op = { context, _ ->
            RemoveRedundantSafepointsPass().runOnModule(
                    module = context.llvm.module,
                    isSafepointInliningAllowed = context.shouldInlineSafepoints()
            )
        }
)

internal val OptimizeTLSDataLoadsPhase = createSimpleNamedCompilerPhase<BitcodePostProcessingContext, Unit>(
        name = "OptimizeTLSDataLoads",
        description = "Optimize multiple loads of thread data",
        postactions = getDefaultLlvmModuleActions(),
        op = { context, _ -> removeMultipleThreadDataLoads(context) }
)

internal val CStubsPhase = createSimpleNamedCompilerPhase<NativeGenerationState, Unit>(
        name = "CStubs",
        description = "C stubs compilation",
        postactions = getDefaultLlvmModuleActions(),
        op = { context, _ -> produceCStubs(context) }
)

internal val LinkBitcodeDependenciesPhase = createSimpleNamedCompilerPhase<NativeGenerationState, List<File>>(
        name = "LinkBitcodeDependencies",
        description = "Link bitcode dependencies",
        postactions = getDefaultLlvmModuleActions(),
        op = { context, input -> linkBitcodeDependencies(context, input) }
)

internal val VerifyBitcodePhase = createSimpleNamedCompilerPhase<PhaseContext, LLVMModuleRef>(
        name = "VerifyBitcode",
        description = "Verify bitcode",
        op = { _, llvmModule -> verifyModule(llvmModule) }
)

internal val PrintBitcodePhase = createSimpleNamedCompilerPhase<PhaseContext, LLVMModuleRef>(
        name = "PrintBitcode",
        description = "Print bitcode",
        op = { _, llvmModule -> LLVMDumpModule(llvmModule) }
)

internal fun <T : BitcodePostProcessingContext> PhaseEngine<T>.runBitcodePostProcessing() {
    val optimizationConfig = createLTOFinalPipelineConfig(
            context,
            context.llvm.targetTriple,
            closedWorld = context.config.isFinalBinary,
            timePasses = context.config.flexiblePhaseConfig.needProfiling,
    )
    useContext(OptimizationState(context.config, optimizationConfig)) {
        val module = this@runBitcodePostProcessing.context.llvmModule
        // it.runPhase(MandatoryBitcodeLLVMPostprocessingPhase, module)
        // it.runPhase(ModuleBitcodeOptimizationPhase, module)
        // it.runPhase(LTOBitcodeOptimizationPhase, module)
        val optPhase1 = context.config.optPhase1
        if (optPhase1) {
            it.runPhase(MandatoryBitcodeLLVMPostprocessingPhase, module)
        }
        val optPhase2 = context.config.optPhase2
        if (optPhase2) {
            it.runPhase(ModuleBitcodeOptimizationPhase, module)
        }
        val optPhase3 = context.config.optPhase3
        if (optPhase3) {
            it.runPhase(LTOBitcodeOptimizationPhase, module)
        }
        when (context.config.sanitizer) {
            SanitizerKind.THREAD -> it.runPhase(ThreadSanitizerPhase, module)
            SanitizerKind.ADDRESS -> context.reportCompilationError("Address sanitizer is not supported yet")
            null -> {}
        }
    }
    if (context.config.memoryModel == MemoryModel.EXPERIMENTAL) {
        runPhase(RemoveRedundantSafepointsPhase)
    }
    if (context.config.optimizationsEnabled) {
        runPhase(OptimizeTLSDataLoadsPhase)
    }
}

internal fun linkBitcodeFilesWithLlvmLink(inputFiles: List<String>, outputFile: String) {
    // val llvmLinkPath = "/home/user/.konan/dependencies/llvm-12.0.1-linux-x86_64-20250722/bin/llvm-link"
    val config = context.config
    val platform = config.platform 
    val llvmLinkPath = "${platform.absolute(platform.hostString("llvm12"))}/bin/llvm-link"

    val command = mutableListOf<String>().apply {
        add(llvmLinkPath)
        add("-o")
        add(outputFile)
        addAll(inputFiles)
    }
    
    println("=== llvm-link Command ===")
    println("Command: ${command.joinToString(" ")}")
    println("Input files:")
    inputFiles.forEach { file ->
        val exists = File(file).exists()
        val size = if (exists) File(file).length() else 0
        println("  - $file: exists=$exists, size=$size bytes")
    }
    
    val processBuilder = ProcessBuilder(command)
    processBuilder.redirectErrorStream(true)
    
    val process = processBuilder.start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    
    println("llvm-link exit code: $exitCode")
    if (output.isNotEmpty()) {
        println("llvm-link output: $output")
    }
    
    if (exitCode != 0) {
        throw RuntimeException("llvm-link failed with exit code $exitCode: $output")
    }
    
    val outputExists = File(outputFile).exists()
    val outputSize = if (outputExists) File(outputFile).length() else 0
    println("Linked output: $outputFile, exists=$outputExists, size=$outputSize bytes")
    
    if (!outputExists || outputSize == 0L) {
        throw RuntimeException("llvm-link produced empty or missing output file")
    }
}

private fun preserveWeakSymbols(module: LLVMModuleRef) {
    println("Preserving weak symbols before split...")
    
    var preservedCount = 0
    var func = LLVMGetFirstFunction(module)
    while (func != null) {
        val linkage = LLVMGetLinkage(func)
        if (linkage == LLVMLinkage.LLVMWeakAnyLinkage || linkage == LLVMLinkage.LLVMWeakODRLinkage) {
            val funcName = LLVMGetValueName(func)?.toKString() ?: "unknown"
            if (
                funcName.contains("unordered_map") ||
                funcName.contains("~") ||
                funcName.contains("vector")
            ) {
                LLVMSetLinkage(func, LLVMLinkage.LLVMInternalLinkage)
                preservedCount++
                println("Preserved weak symbol: $funcName")
            }
        }
        func = LLVMGetNextFunction(func)
    }
    
    println("Preserved $preservedCount weak symbols")
}

internal fun splitBitcodeFile(context: BitcodePostProcessingContext, inputBitcodePath: String, numPartitions: UInt, outputPrefix: String) {
    // val llvmSplitPath = "/home/user/.konan/dependencies/llvm-12.0.1-linux-x86_64-20250722/bin/llvm-split"
    val config = context.config
    val platform = config.platform
    val llvmSplitPath = "${platform.absolute(platform.hostString("llvm12"))}/bin/llvm-split"

    val command = listOf(  
        llvmSplitPath,
        "-j=$numPartitions",
        "-o=$outputPrefix",
        "--preserve-locals",
        inputBitcodePath
    )
    
    val processBuilder = ProcessBuilder(command)
    processBuilder.redirectErrorStream(true)
    
    try {
        val process = processBuilder.start()
        val exitCode = process.waitFor()
        
        if (exitCode != 0) {
            val errorOutput = process.inputStream.bufferedReader().readText()
            throw RuntimeException("llvm-split failed with exit code $exitCode: $errorOutput")
        }
    } catch (e: IOException) {
        throw RuntimeException("Failed to execute llvm-split: ${e.message}", e)
    }
}

internal fun <T : BitcodePostProcessingContext> PhaseEngine<T>.runBitcodePostProcessingCoroutines(bitcodeFileOri: java.io.File) {  
    val optimizationConfig = createLTOFinalPipelineConfig(
            context,
            context.llvm.targetTriple,
            closedWorld = context.config.isFinalBinary,
            timePasses = context.config.flexiblePhaseConfig.needProfiling,
    )
    
    var bitcodeFile: File? = null
    useContext(OptimizationState(context.config, optimizationConfig)) { bitcodeEngine ->
        val tempFiles = createTempFiles(context.config, null)
        val bitcodeFiletmp = tempFiles.create(context.config.shortModuleName ?: "tmp_ori", ".bc")
        bitcodeFile = File(bitcodeFiletmp.toString())        
        val module = this@runBitcodePostProcessingCoroutines.context.llvmModule
        val preserveWeakEnabled = context.config.preserveWeakSymbols
        val optPhase1 = context.config.optPhase1
        if (optPhase1) {
            bitcodeEngine.runPhase(MandatoryBitcodeLLVMPostprocessingPhase, module)
        }
        bitcodeEngine.runPhase(WriteBitcodeFilePhase, WriteBitcodeFileInput(module, bitcodeFile!!))

        println("Created BC file: ${bitcodeFile!!.absolutePath} (${bitcodeFile!!.length()} bytes)")

        val outputPrefix = bitcodeFile!!.absolutePath.removeSuffix(".bc") + "_part_"
        splitBitcodeFile(context, bitcodeFile!!.absolutePath, 2u, outputPrefix)

        println("Checking partition files:")
        for (i in 0 until 2) {
            val partFile = "${bitcodeFile!!.absolutePath.removeSuffix(".bc")}_part_$i"
            val exists = File(partFile).exists()
            val length = if (exists) File(partFile).length() else 0
            println("  - Partition $i: exists=$exists, size=$length bytes")
        }
    }

    val processedModules = runBlocking {
        val jobs = (0 until 2).map { i ->
            async(Dispatchers.Default) {
                val partFile = "${bitcodeFile?.absolutePath?.removeSuffix(".bc") ?: "unknown"}_part_$i"
                val independentContext = LLVMContextCreate()!!
                try {
                    val optimizationConfig = createLTOFinalPipelineConfig(
                            context,
                            context.llvm.targetTriple,
                            closedWorld = context.config.isFinalBinary,
                            timePasses = context.config.flexiblePhaseConfig.needProfiling,
                    )
                    useContext(OptimizationState(context.config, optimizationConfig)) { bitcodeEngine ->
                        if (File(partFile).exists()) {
                            val partModule = parseBitcodeFile(independentContext, partFile)
                            val optPhase2 = context.config.optPhase2
                            if (optPhase2) {
                                bitcodeEngine.runPhase(ModuleBitcodeOptimizationPhase, partModule)
                            }
                            val optPhase3 = context.config.optPhase3
                            if (optPhase3) {
                                bitcodeEngine.runPhase(LTOBitcodeOptimizationPhase, partModule)
                            }
                            when (context.config.sanitizer) {
                                SanitizerKind.THREAD -> bitcodeEngine.runPhase(ThreadSanitizerPhase, partModule)
                                SanitizerKind.ADDRESS -> context.reportCompilationError("Address sanitizer is not supported yet")
                                null -> {}
                            }

                            val tempFile = "${bitcodeFile!!.absolutePath.removeSuffix(".bc")}_opt_temp_$i.bc"
                            println("Writing processed module $i to: $tempFile")
                            LLVMWriteBitcodeToFile(partModule, tempFile)
                            tempFile
                        } else {
                            println("Partition file not found: $partFile")
                            null
                        }
                    }
                } finally {
                    LLVMContextDispose(independentContext)
                }
            }
        }
        jobs.awaitAll().filterNotNull()
    }

    if (processedModules.isNotEmpty()) {
        println("=== Using bitcode serialization linking approach ===")

        val tempBitcodeFiles = mutableListOf<String>()
        processedModules.forEachIndexed { index, processedModule ->
            val tempFile = "${bitcodeFile!!.absolutePath.removeSuffix(".bc")}_opt_temp_$index.bc"
            println("Writing processed module $index to: $tempFile")
            tempBitcodeFiles.add(tempFile)

            val exists = File(tempFile).exists()
            val size = if (exists) File(tempFile).length() else 0
            println("  Written: exists=$exists, size=$size bytes")
        }

        val linkedBitcodeFile = "${bitcodeFile!!.absolutePath.removeSuffix(".bc")}_link.bc"
        linkBitcodeFilesWithLlvmLink(tempBitcodeFiles, linkedBitcodeFile)
        var bitcodeFileOriFinal: File? = null
        bitcodeFileOriFinal = File(bitcodeFileOri.toString())
        linkBitcodeFilesWithLlvmLink(tempBitcodeFiles, bitcodeFileOriFinal.absolutePath)

        println("Reloading linked module from: $bitcodeFileOri")

        // // clear temp file
        // tempBitcodeFiles.forEach { tempFile ->
        //     try {
        //         File(tempFile).delete()
        //         println("Cleaned up temp file: $tempFile")
        //     } catch (e: Exception) {
        //         println("Warning: Failed to clean up $tempFile: ${e.message}")
        //     }
        // }
        // File(linkedBitcodeFile).delete()
        // bitcodeFile?.delete()

        // // clear
        // for (i in 0 until 8) {
        //     val partFile = "${bitcodeFile!!.absolutePath.removeSuffix(".bc")}_part_$i"
        //     File(partFile).delete()
        // }

        println("Cleaned up all temporary files")
    } else {
        println("No processed modules to link, using original module")
    }
}