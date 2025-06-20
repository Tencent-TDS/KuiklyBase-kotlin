/*
 * Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "concurrent/ScopedThread.hpp"

#include <array>
#include <atomic>
#include <cstring>
#include <pthread.h>
#include <type_traits>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include "Format.h"
#include "KAssert.h"

using namespace kotlin;

namespace {

// region @Tencent: parameter 'thread' is always 'pthread_self()' so it is safe to be ignored.
std::string threadName(pthread_t thread) {
  return kotlin::internal::getCurrentThreadName();
}
// endregion

__attribute__((format(printf, 1, 2))) std::string format(const char* format, ...) {
    std::array<char, 20> buffer;
    std::va_list args;
    va_start(args, format);
    VFormatToSpan(buffer, format, args);
    va_end(args);
    // `buffer` is guaranteed to be 0-terminated.
    return std::string(buffer.data());
}

} // namespace

TEST(ScopedThreadTest, Default) {
    // Do not check name by default, since the default may be set by the system.
    ScopedThread thread([] {});
}

TEST(ScopedThreadTest, ThreadName) {
    ScopedThread thread(ScopedThread::attributes().name("some thread"), [] { EXPECT_THAT(threadName(pthread_self()), "some thread"); });
}

TEST(ScopedThreadTest, DynamicThreadName) {
    ScopedThread thread(
            ScopedThread::attributes().name(format("thread %d", 42)), [] { EXPECT_THAT(threadName(pthread_self()), "thread 42"); });
}

TEST(ScopedThreadTest, EmptyThreadName) {
    ScopedThread thread(ScopedThread::attributes().name(""), [] { EXPECT_THAT(threadName(pthread_self()), ""); });
}

TEST(ScopedThreadTest, JoinsInDestructor) {
    // Not atomic for TSAN to complain if we didn't synchronize with join.
    bool exited = false;
    {
        ScopedThread([&exited] {
            // Give a chance for the outer scope to go on.
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            exited = true;
        });
    }
    EXPECT_THAT(exited, true);
}

TEST(ScopedThreadTest, ManualJoin) {
    // Not atomic for TSAN to complain if we didn't synchronize with join.
    bool exited = false;
    ScopedThread thread([&exited] {
        // Give a chance for the outer scope to go on.
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
        exited = true;
    });
    EXPECT_THAT(thread.joinable(), true);
    thread.join();
    EXPECT_THAT(thread.joinable(), false);
    EXPECT_THAT(exited, true);
}

TEST(ScopedThreadTest, Detach) {
    std::atomic<bool> doExit = false;
    std::atomic<bool> exited = false;
    {
        ScopedThread thread([&doExit, &exited] {
            while (!doExit.load()) {}
            exited = true;
        });
        EXPECT_THAT(thread.joinable(), true);
        thread.detach();
        EXPECT_THAT(thread.joinable(), false);
    }
    EXPECT_THAT(exited.load(), false);
    doExit = true;
    while (!exited.load()) {
    }
}
