// KIND: STANDALONE_NO_TR
// TARGET_BACKEND: NATIVE
// DISABLE_NATIVE: isAppleTarget=false
// DISABLE_NATIVE: gcType=NOOP
// DISABLE_NATIVE: gcScheduler=MANUAL
// The test checks GC Scheduler MutatorAssits recursive timing issue.
// FREE_COMPILER_ARGS: -opt-in=kotlin.native.internal.InternalForKotlinNative

// MODULE: cinterop
// FILE: kt76851.def
language = Objective-C
headers = kt76851.h

// FILE: kt76851.h
#import <Foundation/Foundation.h>

FOUNDATION_EXTERN NSArray<NSString *> *getNameList(NSString *prefix, int count);

// FILE: kt76851.m
#import "kt76851.h"

NSArray<NSString *> *getNameList(NSString *prefix, int count) {
    NSMutableArray<NSString *> *list = [NSMutableArray array];
    for (int i = 0; i < count; ++i) {
        [list addObject:[NSString stringWithFormat:@"%@_%d", prefix, i]];
    }
    return [list copy];
}

// MODULE: main(cinterop)
// FILE: main.kt
@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.native.concurrent.ObsoleteWorkersApi::class)

import kt76851.*

import kotlin.native.concurrent.Worker
import kotlin.native.internal.MemoryUsageInfo

private val gcList1: MutableList<String> = mutableListOf()
private val gcList2: MutableList<String> = mutableListOf()
private val gcList3: MutableList<String> = mutableListOf()

fun getKNameList(
    prefix: String,
    count: Int
): List<String> {
    val objcNameList = getNameList(prefix, count)
    return objcNameList?.map { it as String } ?: emptyList()
}

class PeakRSSChecker(private val rssDiffLimitBytes: Long) {
    // On Linux, the child process might immediately commit the same amount of memory as the parent.
    // So, measure difference between peak RSS measurements.
    private val initialBytes = MemoryUsageInfo.peakResidentSetSizeBytes.also {
        check(it != 0L) { "Error trying to obtain peak RSS. Check if current platform is supported" }
    }

    fun check(): Long {
        val diffBytes = MemoryUsageInfo.peakResidentSetSizeBytes - initialBytes
        check(diffBytes <= rssDiffLimitBytes) { "Increased peak RSS by $diffBytes bytes which is more than $rssDiffLimitBytes" }
        return diffBytes
    }
}

fun create() {
    // 400,000 Kotlin_Interop_CreateKStringFromNSString
    for (i in 0 until 200) {
        gcList1.addAll(getKNameList("gcList1", 2000))
    }

    // 500,000 Kotlin_Interop_CreateKStringFromNSString
    for (i in 0 until 250) {
        gcList2.addAll(getKNameList("gcList2", 2000))
    }

    // 200,000 Kotlin_Interop_CreateKStringFromNSString
    for (i in 0 until 100) {
        gcList3.addAll(getKNameList("gcList3", 2000))
    }
}

fun clear() {
    gcList1.clear()
    gcList2.clear()
    gcList3.clear()
}

fun main() {
    // ~400M, This check is to verify whether the test case can be executed without timing out.
    val peakRSSChecker = PeakRSSChecker(400_000_000L)

    create()
    // Make MutatorAssists work
    clear()
    create()
    clear()

    peakRSSChecker.check()
}
