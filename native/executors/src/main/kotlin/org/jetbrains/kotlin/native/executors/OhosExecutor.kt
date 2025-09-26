/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.native.executors

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Paths
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

/**
 * [Executor] that runs the process on a HarmonyOS device using hdc commands.
 */
@OptIn(ExperimentalTime::class)
class OhosExecutor : Executor {
    private val logger = Logger.getLogger(OhosExecutor::class.java.name)
    private val hostExecutor: Executor = HostExecutor()
    private val hdcPath = "hdc"
    private val deviceExePath = "/data/local/tmp/exe"

    private val LD_PRELOAD="LD_PRELOAD=/data/app/el1/bundle/public/com.huawei.hmos.location/libs/arm64/libc++_shared.so"

    override fun execute(request: ExecuteRequest): ExecuteResponse {
        val localExePath = request.executableAbsolutePath
        val workingDirectory = request.workingDirectory ?: File(localExePath).parentFile

        logger.info("Starting HarmonyOS execution flow for $localExePath")
        
        // Step 1: Remove existing executable on device
        executeHdcCommand("shell", "rm", deviceExePath)
        
        // Step 2: Send the executable to device
        executeHdcCommand("file", "send", localExePath, deviceExePath)
        
        // Step 3: Make the executable runnable
        executeHdcCommand("shell", "chmod", "a+x", deviceExePath)
        
        // Step 4: Execute the program with LDPARAM
        val args = mutableListOf("shell", LD_PRELOAD, deviceExePath)
        args.addAll(request.args)
        
        val executionRequest = ExecuteRequest(
            executableAbsolutePath = hdcPath,
            args = args,
            workingDirectory = workingDirectory,
            stdin = request.stdin,
            stdout = request.stdout,
            stderr = request.stderr,
            environment = request.environment,
            timeout = request.timeout
        )
        
        return hostExecutor.execute(executionRequest)
    }
    
    private fun executeHdcCommand(vararg commandArgs: String) {
        val request = ExecuteRequest(
            executableAbsolutePath = hdcPath,
            args = commandArgs.toMutableList(),
            workingDirectory = Paths.get("").toAbsolutePath().toFile(),

//            timeout = Duration.INFINITE
        )
        
        val response = hostExecutor.execute(request)
        response.assertSuccess()
    }
}