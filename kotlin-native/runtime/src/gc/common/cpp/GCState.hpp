/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#pragma once

#include <condition_variable>
#include <mutex>
#include <atomic>
#include <optional>

#include "KAssert.h"
#include "Utils.hpp"

class GCStateHolder {
public:
    // region Tencent Code
    // GC 挂起状态标志
    static constexpr int64_t GC_SUSPENDED = -1;
    // GC 挂起最大时长，单位为秒
    static constexpr int32_t MAX_SUSPEND_TIME = 10;
    // endregion

    int64_t schedule() {
        std::unique_lock lock(mutex_);
        if (*scheduledEpoch <= *startedEpoch) {
            scheduledEpoch.set(lock, *startedEpoch + 1);
        }
        return *scheduledEpoch;
    }

    void shutdown() {
        std::unique_lock lock(mutex_);
        shutdownFlag_ = true;
        startedEpoch.notify();
        finishedEpoch.notify();
        scheduledEpoch.notify();
        finalizedEpoch.notify();
        // region Tencent Code
        resumeGCEpoch.notify();
        // endregion
    }

    void start(int64_t epoch) {
        startedEpoch.set(epoch);
        // region Tencent Code
        if (*resumeGCEpoch != GC_SUSPENDED) {
            resumeGCEpoch.set(epoch);
        }
        // endregion
    }

    void finish(int64_t epoch) { finishedEpoch.set(epoch); }

    void finalized(int64_t epoch) { finalizedEpoch.set(epoch); }

    void waitEpochFinished(int64_t epoch) {
        finishedEpoch.wait([this, epoch] { return *finishedEpoch >= epoch || shutdownFlag_; });
    }

    void waitEpochFinalized(int64_t epoch) {
        finalizedEpoch.wait([this, epoch] { return *finalizedEpoch >= epoch || shutdownFlag_; });
    }

    std::optional<int64_t> waitScheduled() {
        int64_t result = scheduledEpoch.wait([this] { return *scheduledEpoch > *finishedEpoch || shutdownFlag_; });
        if (shutdownFlag_) return std::nullopt;
        return result;
    }

    // region Tencent Code
    // 等待 GC 恢复，等待 GC 恢复时间不超过 MAX_SUSPEND_TIME 秒
    void waitResumed() {
        resumeGCEpoch.wait_for([this] { return *resumeGCEpoch != GC_SUSPENDED || shutdownFlag_; }, std::chrono::seconds(MAX_SUSPEND_TIME));
    }

    // 挂起 GC
    void suspend() {
        resumeGCEpoch.set(GC_SUSPENDED);
    }

    // 恢复 GC
    void resume() {
        resumeGCEpoch.set(*startedEpoch);
    }
    // endregion

private:
    template <typename T>
    struct ValueWithCondVar : kotlin::Pinned {
        explicit ValueWithCondVar(T initializer, std::mutex& mutex) noexcept : value_(initializer), mutex_(mutex){};

        const T& operator*() const { return value_; }

        void set(T newValue) {
            std::unique_lock lock(mutex_);
            set(lock, newValue);
        }

        void set(std::unique_lock<std::mutex>& lock, T newValue) {
            RuntimeAssert(lock.owns_lock() && lock.mutex() == &mutex_, "Required the mutex to be locked");
            value_ = newValue;
            cond_.notify_all();
        }

        void notify() { cond_.notify_all(); }

        template <class Predicate>
        const T& wait(Predicate stop_waiting) {
            std::unique_lock lock(mutex_);
            cond_.wait(lock, stop_waiting);
            return value_;
        }

        template <class Predicate>
        const T& wait_for(Predicate stop_waiting, std::chrono::seconds timeout) {
            std::unique_lock lock(mutex_);
            cond_.wait_for(lock, timeout, stop_waiting);
            return value_;
        }

    private:
        T value_;
        std::mutex& mutex_;
        std::condition_variable cond_;
    };

    std::mutex mutex_;
    // Use a separate conditional variable for each counter to mitigate a winpthreads bug (see KT-50948 for details).
    ValueWithCondVar<int64_t> startedEpoch{0, mutex_};
    ValueWithCondVar<int64_t> finishedEpoch{0, mutex_};
    ValueWithCondVar<int64_t> scheduledEpoch{0, mutex_};
    ValueWithCondVar<int64_t> finalizedEpoch{0, mutex_};
    // region Tencent Code
    ValueWithCondVar<int64_t> resumeGCEpoch{0, mutex_};
    // endregion
    bool shutdownFlag_ = false;
};
