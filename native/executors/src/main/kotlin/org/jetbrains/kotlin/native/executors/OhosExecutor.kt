package org.jetbrains.kotlin.native.executors

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths
import kotlin.random.Random
import kotlin.ranges.random
import kotlin.time.ExperimentalTime

/**
 * [Executor] that runs the process on a HarmonyOS device using hdc commands.
 */
@OptIn(ExperimentalTime::class)
class OhosExecutor : Executor {
    private val hostExecutor: Executor = HostExecutor()
    private val LD_PRELOAD = "LD_PRELOAD=/data/app/el1/bundle/public/com.huawei.hmos.location/libs/arm64/libc++_shared.so"

    override fun execute(request: ExecuteRequest): ExecuteResponse {
        val localExePath = request.executableAbsolutePath
        val workingDirectory = request.workingDirectory ?: File(localExePath).parentFile

        // ✅ 1. 随机生成 deviceExePath
        val deviceExePath = generateRandomDevicePath()

        try {
            // 上传文件
            executeHdcCommand("shell", "rm", "-f", deviceExePath)
            executeHdcCommand("file", "send", localExePath, deviceExePath)
            executeHdcCommand("shell", "chmod", "a+x", deviceExePath)

            val args = mutableListOf("shell", LD_PRELOAD, deviceExePath)
            args.addAll(request.args)

            // ✅ 执行命令
            return executeHdcCommand(
                ExecuteRequest(
                    executableAbsolutePath = "hdc",
                    args = args,
                    workingDirectory = workingDirectory,
                    stdin = request.stdin,
                    stdout = request.stdout,
                    stderr = request.stderr,
                    environment = request.environment,
                    timeout = request.timeout
                )
            )
        } finally {
            // 无论成功失败都删除临时文件，防止堆积
//            executeHdcCommand("shell", "rm", "-f", deviceExePath)
        }
    }

    private fun generateRandomDevicePath(): String {
        val randomSuffix = (1..64)
            .map { ('a'..'z').random() }
            .joinToString("")
        return "/data/local/tmp/$randomSuffix"
    }

    private fun executeHdcCommand(vararg args: String) {
        val request = ExecuteRequest(
            executableAbsolutePath = "hdc",
            args = args.toMutableList(),
            workingDirectory = Paths.get("").toAbsolutePath().toFile(),
        )
        executeHdcCommand(request)
    }

    private fun executeHdcCommand(request: ExecuteRequest): ExecuteResponse {
//        val isShell = request.args.getOrNull(0) == "shell"
        val isShell = false
        val wrappedArgs = if (isShell) {
            val originalCmd = request.args.drop(1).joinToString(" ")
            mutableListOf(
                "shell",
                "output=\$($originalCmd 2>&1); ret=\$?; " +
                        "echo \"\$output\"; " +
                        "if echo \"\$output\" | grep -qE 'Exception|Error|##teamcity\\[testFailed'; then " +
                        "  echo hdc_shell_command_failed; " +
                        "elif [ \$ret -ne 0 ]; then " +
                        "  echo hdc_shell_command_failed; " +
                        "else " +
                        "  echo hdc_shell_command_succeed; " +
                        "fi"
            )
        } else {
            request.args
        }

        val outputStream = ByteArrayOutputStream()
        val execReq = request.copy(
            args = wrappedArgs.toMutableList(),
            stdout = outputStream
        )

        val resp = hostExecutor.execute(execReq)
        resp.assertSuccess()

        val output = outputStream.toString(Charsets.UTF_8.name())
        if (isShell) {
            require("hdc_shell_command_succeed" in output) {
                "hdc command failed: hdc ${wrappedArgs.joinToString(" ")}\nOutput:\n$output"
            }
        }

        return resp
    }



}
