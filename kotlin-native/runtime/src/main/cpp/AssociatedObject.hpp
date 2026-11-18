/*
 * Tencent is pleased to support the open source community by making TDS-KuiklyBase available.
 * Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#if !KONAN_OBJC_INTEROP

#include "Utils.hpp"

namespace kotlin::mm {

class AssociatedObject : private Pinned {
public:
    AssociatedObject() noexcept = default;
    virtual ~AssociatedObject() = default;

    AssociatedObject(const AssociatedObject&) = delete;
    AssociatedObject& operator=(const AssociatedObject&) = delete;
    AssociatedObject(AssociatedObject&&) = delete;
    AssociatedObject& operator=(AssociatedObject&&) = delete;
};

class ExternalBufferAssociatedObject final : public AssociatedObject {
public:
    using FreeCallback = void (*)(void* mem, void* context);

    ExternalBufferAssociatedObject(void* data, FreeCallback freeCallback, void* freeContext) noexcept
        : data_(data), freeCallback_(freeCallback), freeContext_(freeContext) {}

    ExternalBufferAssociatedObject(const ExternalBufferAssociatedObject&) = delete;
    ExternalBufferAssociatedObject& operator=(const ExternalBufferAssociatedObject&) = delete;
    ExternalBufferAssociatedObject(ExternalBufferAssociatedObject&&) = delete;
    ExternalBufferAssociatedObject& operator=(ExternalBufferAssociatedObject&&) = delete;

    ~ExternalBufferAssociatedObject() override {
        if (data_ != nullptr && freeCallback_ != nullptr) {
            freeCallback_(data_, freeContext_);
        }
    }

    [[nodiscard]] void* data() const noexcept {
        return data_;
    }

private:
    void* data_;
    FreeCallback freeCallback_;
    void* freeContext_;
};

} // namespace kotlin::mm

#endif // !KONAN_OBJC_INTEROP
