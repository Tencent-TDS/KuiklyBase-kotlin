/*
 * Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "ScopedThread.hpp"

#include <cstring>
#include <pthread.h>
#include <type_traits>

#include "Logging.hpp"

#if KONAN_ANDROID || KONAN_LINUX
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>
#endif

using namespace kotlin;

// region Tencent Code
#if KONAN_MACOSX || KONAN_IOS || KONAN_WATCHOS || KONAN_TVOS
#else
static void truncate_string(const char* input, char* output, size_t output_size) {
    // 确保 output 是有效的
    if (output == nullptr || output_size == 0) {
        return;
    }

    // 检查输入字符串是否以 "Dispatchers." 开头
    const char target_prefix[] = "Dispatchers.";
    if (strncmp(input, target_prefix, sizeof(target_prefix) - 1) == 0) {
        // 替换开头为 "DP"
        const char replace[] = "DP.";
        strcpy(output, replace);

        // 计算剩余部分的长度
        const char* remaining = input + sizeof(target_prefix) - 1;
        size_t remaining_length = strlen(remaining);

        // 确保不超过 output_size
        if (remaining_length > output_size - sizeof(replace) - 2) {
            remaining_length = output_size - sizeof(replace) - 2;
        }

        // 连接剩余部分
        strncat(output, remaining, remaining_length);
    } else {
        // 检查输入字符串长度
        size_t input_length = strlen(input);
        if (input_length >= output_size) {
            strncpy(output, input, output_size - 1);
            output[output_size - 1] = '\0';
        } else {
            strcpy(output, input);
        }
    }

    // 确保 output 以 null 结尾
    output[output_size - 1] = '\0';
}
#endif
// endregion

void internal::setCurrentThreadName(std::string_view name) noexcept {
#if KONAN_MACOSX || KONAN_IOS || KONAN_WATCHOS || KONAN_TVOS
    static_assert(std::is_invocable_r_v<void, decltype(pthread_setname_np), const char*>, "Invalid pthread_setname_np signature");
    pthread_setname_np(name.data());
#else
    static_assert(std::is_invocable_r_v<int, decltype(pthread_setname_np), pthread_t, const char*>, "Invalid pthread_setname_np signature");
    // TODO: On Linux the maximum thread name is 16 characters. Handle automatically?

    // region Tencent Code
    // int result = pthread_setname_np(pthread_self(), name.data());
    char truncate_name[16];
    truncate_string(name.data(), truncate_name, sizeof(truncate_name));
    int result = pthread_setname_np(pthread_self(), truncate_name);
    // endregion

    if (result != 0) {
        RuntimeLogWarning({logging::Tag::kRT}, "Failed to set thread name: %s", std::strerror(result));
    }
#endif
}

// region @Tencent: pthread_getname_np is not available below Android 26.
std::string internal::getCurrentThreadName() noexcept {
  char thread_name[100];
#if KONAN_ANDROID || KONAN_LINUX
  if (prctl(PR_GET_NAME, thread_name) != 0) {
    RuntimeLogWarning({ logging::Tag::kRT }, "Failed to get thread name: %s", std::strerror(errno));
    long tid = syscall(SYS_gettid);
    snprintf(thread_name, sizeof(thread_name), "tid-%lu", tid);
  }
#else
  static_assert(
            std::is_invocable_r_v<int, decltype(pthread_getname_np), pthread_t, char*, size_t>, "Invalid pthread_getname_np signature");
  pthread_getname_np(pthread_self(), thread_name, sizeof(thread_name));
#endif
  return std::string{thread_name};
}
// endregion