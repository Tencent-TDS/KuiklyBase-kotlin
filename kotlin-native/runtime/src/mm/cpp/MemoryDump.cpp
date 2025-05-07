/*
* Copyright 2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
* that can be found in the LICENSE file.
*/

#include "MemoryDump.hpp"

#include <unistd.h>
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <unordered_set>
#include <queue>
#include <cinttypes>

#include "Porting.h"
#include "TypeInfo.h"
#include "KString.h"
#include "ObjectTraversal.hpp"
#include "GlobalData.hpp"
#include "RootSet.hpp"
#include "ThreadData.hpp"
#include "std_support/Span.hpp"

namespace kotlin::mm {

// region Tencent Code
static std::atomic_bool isDumping(false);
static const std::string fixedCachePathStr("/fixedCache");
static const std::string otherCachePathStr("/otherCache.dat");
// endregion
class MemoryDumper {
public:
   explicit MemoryDumper(FILE* file) : file_(file) {}

    // region Tencent Code
    explicit MemoryDumper(FILE *file, std::string cacheDir) : file_(file), asyncCache(std::move(cacheDir)) {}

    void initCacheFile() {
        // 初始化fixed缓存文件
        fixedCaches.reserve(kFixedCacheCount);
        fixedBlockPageCounts.resize(kFixedCacheCount, 0);
        std::string pathPrefix = asyncCache + fixedCachePathStr;
        for (int i = 0; i < kFixedCacheCount; ++i) {
            std::string path = pathPrefix + std::to_string(i+1) + ".dat";
            remove(path.c_str());
            FILE* file = fopen(path.c_str(), "w+");
            if (!file) {
                throw std::runtime_error("Failed to open cache file: " + path);
            }
            fixedCaches.push_back(file);
        }
        // 初始化其他缓存
        std::string otherCachePath = asyncCache + otherCachePathStr;
        remove(otherCachePath.c_str());
        otherCache = fopen(otherCachePath.c_str(), "w+");
        if (!otherCache) {
            throw std::runtime_error("Failed to open other cache file");
        }
    }

    void resetCacheFile() const {
        for (FILE* file : fixedCaches) {
            fflush(file);
            fseek(file, 0, SEEK_SET);
        }
        fflush(otherCache);
        fseek(otherCache, 0, SEEK_SET);
    }

    void closeAndDeleteCacheFile() const {
        std::string pathPrefix = asyncCache + fixedCachePathStr;
        for (size_t i = 0; i < fixedCaches.size(); ++i) {
            fclose(fixedCaches[i]);
            std::string path = pathPrefix + std::to_string(i+1) + ".dat";
            remove(path.c_str());
        }
        // 处理其他缓存
        fclose(otherCache);
        std::string otherCachePath = asyncCache + otherCachePathStr;
        remove(otherCachePath.c_str());
    }

    void dumpFixedFromCache() {
        void *fixPage = malloc(fixedBlockPageSize);
        // 遍历所有固定缓存
        for (size_t i = 0; i < fixedCaches.size(); ++i) {
            while (fixedBlockPageCounts[i] > 0) {
                memset(fixPage, 0, fixedBlockPageSize);
                size_t readCount = fread(fixPage, sizeof(char), fixedBlockPageSize, fixedCaches[i]);
                if (readCount != fixedBlockPageSize) {
                    konan::consolePrintf("readFixedCache%d! error:%s!", static_cast<int>(i + 1), strerror(errno));
                    break;
                }
                GlobalData::Instance().allocator().TraverseFixedBlockPageObjects(
                    fixPage, [&](auto obj, uintptr_t originPtr) {
                        DumpTransitivelyFromFile(obj, originPtr);
                    });
                fixedBlockPageCounts[i]--;
            }
        }
        free(fixPage);
    }

    void dumpNextFitFromCache() {
        void *nextFitPage = malloc(nextFitPageSize);
        while (nextFitPageCount > 0) {
            memset(nextFitPage, 0, nextFitPageSize);
            size_t readCount = fread(nextFitPage, sizeof(char), nextFitPageSize, otherCache);
            if (readCount != nextFitPageSize) {
                konan::consolePrintf("readNextFitCache! error:%s!", strerror(errno));
                break;
            }
            GlobalData::Instance().allocator().TraverseNextFitPageObjects(nextFitPage, [&](auto obj, uintptr_t originPtr) {
                DumpTransitivelyFromFile(obj, originPtr);
            });
            nextFitPageCount--;
        }
        free(nextFitPage);
    }

    void dumpSinglePageFromCache() {
        size_t count = singlePageSizeArray.size();
        for (size_t i = 0; i < count; i++) {
            size_t pageSize = singlePageSizeArray[i];
            void *singlePage = malloc(pageSize);
            memset(singlePage, 0, pageSize);
            size_t readCount = fread(singlePage, sizeof(char), pageSize, otherCache);
            if (readCount != pageSize) {
                konan::consolePrintf("readSingleCache! error:%s!", strerror(errno));
                break;
            }
            GlobalData::Instance().allocator().TraverseSingleObjPageObjects(singlePage, [&](auto obj, uintptr_t originPtr) {
                DumpTransitivelyFromFile(obj, originPtr);
            });
            free(singlePage);
        }
    }

    void asyncDumpFromCacheFile() {
        uint64_t start = konan::getTimeMillis();

        resetCacheFile();
        dumpFixedFromCache();
        dumpNextFitFromCache();
        dumpSinglePageFromCache();

		fflush(file_);
        fclose(file_);
        closeAndDeleteCacheFile();
        uint64_t end = konan::getTimeMillis();
        konan::consolePrintf("Dump async from cache file, totalDuration:%" PRIu64 " ms", end - start);
        isDumping = false;
    }

    void pageToFileFixed(int index) {
        FILE* cacheFile = fixedCaches[index];
        setvbuf(cacheFile, nullptr, _IOFBF, 2 * 1024 * 1024);
        GlobalData::Instance().allocator().TraverseFixedPagesPart(
            index, [&](void *page, int pageType, size_t pageSize) {
                fixedBlockPageToFile(index, page, pageSize);
            });
        setvbuf(cacheFile, nullptr, _IOFBF, 4 * 1024);
    }

    void pageToFileSingleAndNextFit() {
        setvbuf(otherCache, nullptr, _IOFBF, 2 * 1024 * 1024);
        GlobalData::Instance().allocator().TraverseNextFitAndSinglePages(
            [&](void *page, int pageType, size_t pageSize) {
                if (pageType == 2) {
                    nextFitPageToFile(page, pageSize);
                } else if (pageType == 3) {
                    singlePageToFile(page, pageSize);
                }
            });
        setvbuf(otherCache, nullptr, _IOFBF, 4 * 1024);
    }

    void startAsyncCache(std::vector<std::thread>& threads) {
        initCacheFile();
        std::atomic_thread_fence(std::memory_order_release);

        // fixed拆分成多个线程并发处理
        for (int i = 0; i < kFixedCacheCount; ++i) {
            threads.emplace_back(&MemoryDumper::pageToFileFixed, this, i);
        }
        threads.emplace_back(&MemoryDumper::pageToFileSingleAndNextFit, this);
    }

    void DumpAsync() {
        uint64_t start = konan::getTimeMillis();
        // 先启动并发写缓存文件的线程
        std::vector<std::thread> threads;
        startAsyncCache(threads);
        uint64_t time2 = konan::getTimeMillis();
        // 同步写入ROOT部分，耗时很少
        DumpStr("Kotlin/Native dump 1.0.8");
        DumpBool(konan::isLittleEndian());
        DumpU8(sizeof(void *));
        // Dump global roots.
        for (auto value: mm::GlobalRootSet()) {
            DumpTransitively(value);
        }
        // Dump threads and thread roots.
        for (auto &thread: mm::GlobalData::Instance().threadRegistry().LockForIter()) {
            DumpThread(thread);
            for (auto value: mm::ThreadRootSet(thread)) {
                DumpTransitively(thread, value);
            }
        }
        GlobalData::Instance().allocator().TraverseAllocatedExtraObjects([&](auto extraObj) {
            DumpTransitively(extraObj);
        });
        DumpEnqueuedObjectsFromHeap();
        uint64_t time6 = konan::getTimeMillis();

        for (auto &t: threads) {
            t.join();
        }

        auto self = std::shared_ptr<MemoryDumper>(this);
        std::thread([self]() {
            try {
                self->asyncDumpFromCacheFile();
            } catch (const std::exception& e) {
                RuntimeLogError({kTagGC}, "Async dump failed: %s", e.what());
            }
        }).detach();

        uint64_t end = konan::getTimeMillis();

        konan::consolePrintf(
            "Dump async and cache file duration, startThreads:%" PRIu64 " ms, sync dump roots:%" PRIu64 " ms,"
            " waitAsyncDump:%" PRIu64 " ms, total:%" PRIu64 " ms",
            time2 - start, time6 - time2, end - time6, end - start);
    }

    void fixedBlockPageToFile(int index, void* page, size_t pageSize) {
        FILE* cacheFile = fixedCaches[index];
        size_t written = fwrite(page, sizeof(char), pageSize, cacheFile);
        if (written != pageSize) {
            konan::consolePrintf("fixedBlockPage%dToFile fail! fwrite error:%s!",
                               index+1, strerror(errno));
            throw std::system_error(errno, std::generic_category());
        }
        if (fixedBlockPageSize == 0) {
            fixedBlockPageSize = pageSize;
        }
        fixedBlockPageCounts[index]++;
    }

    void nextFitPageToFile(void* page, size_t pageSize) {
        size_t written = fwrite(page, sizeof(char), pageSize, otherCache);
        if (written != pageSize) {
            konan::consolePrintf("nextFitPageToFile! fwrite error:%s!", strerror(errno));
            throw std::system_error(errno, std::generic_category());
        }
        if (nextFitPageSize == 0) {
            nextFitPageSize = pageSize;
        }
        nextFitPageCount++;
    }

    void singlePageToFile(void* page, size_t pageSize) {
        size_t written = fwrite(page, sizeof(char), pageSize, otherCache);
        if (written != pageSize) {
            konan::consolePrintf("singlePageToFile! fwrite error:%s!", strerror(errno));
            throw std::system_error(errno, std::generic_category());
        }
        singlePageSizeArray.emplace_back(pageSize);
    }
    // endregion

   // Dumps the memory and returns the success flag.
   void Dump() {
       DumpStr("Kotlin/Native dump 1.0.8");
       DumpBool(konan::isLittleEndian());
       DumpU8(sizeof(void*));

       // Dump global roots.
       for (auto value : mm::GlobalRootSet()) {
           DumpTransitively(value);
       }

       // Dump threads and thread roots.
       for (auto& thread : mm::GlobalData::Instance().threadRegistry().LockForIter()) {
           DumpThread(thread);
           for (auto value : mm::ThreadRootSet(thread)) {
               DumpTransitively(thread, value);
           }
       }

       // Dump objects from the heap.
       GlobalData::Instance().allocator().TraverseAllocatedObjects([&](auto obj) { DumpTransitively(obj); });

       // Dump extra objects from the heap.
       GlobalData::Instance().allocator().TraverseAllocatedExtraObjects([&](auto extraObj) { DumpTransitively(extraObj); });

       DumpEnqueuedObjects();
   }

private:
   template <typename T>
   void DumpSpan(std_support::span<T> span) {
       size_t written = fwrite(span.data(), sizeof(T), span.size(), file_);
       if (written != span.size()) {
           throw std::system_error(errno, std::generic_category());
       }
   }

   template <typename T>
   void DumpValue(T value) {
       DumpSpan(std_support::span<T>(&value, 1));
   }

   void DumpId(const void* ptr) { DumpValue(ptr); }

   void DumpBool(bool b) { DumpU8(b ? 1 : 0); }

   void DumpU8(uint8_t i) { DumpValue(i); }

   void DumpU32(uint32_t i) { DumpValue(i); }

   void DumpStr(const char* str) { DumpSpan(std_support::span<const char>(str, strlen(str) + 1)); }

   void DumpString(ObjHeader* obj) {
       char* str = CreateCStringFromString(obj);
       DumpStr(str);
       DisposeCString(str);
   }

   void DumpStringOrEmptyIfNull(ObjHeader* obj) {
       if (obj) {
           DumpString(obj);
       } else {
           DumpStr("");
       }
   }

   void DumpThread(ThreadData& thread) {
       DumpU8(TAG_THREAD);
       DumpId(&thread);
   }

   void DumpGlobalRoot(GlobalRootSet::Value& value) {
       DumpU8(TAG_GLOBAL_ROOT);
       DumpU8(UInt8(value.source));
       DumpId(value.object);
   }

   void DumpThreadRoot(ThreadData& thread, ThreadRootSet::Value& value) {
       DumpU8(TAG_THREAD_ROOT);
       DumpId(&thread);
       DumpU8(UInt8(value.source));
       DumpId(value.object);
   }

   void DumpObject(const TypeInfo* type, ObjHeader* obj) {
       DumpU8(TAG_OBJECT);
       DumpId(obj);
       DumpId(type);

       size_t size = type->instanceSize_;
       size_t dataOffset = sizeof(TypeInfo*);
       size_t dataSize = size - dataOffset;
       uint8_t* data = reinterpret_cast<uint8_t*>(obj) + dataOffset;

       DumpU32(dataSize);
       DumpSpan(std_support::span<uint8_t>(data, dataSize));
   }

   void DumpArray(const TypeInfo* type, ArrayHeader* arr) {
       DumpU8(TAG_ARRAY);
       DumpId(arr);
       DumpId(type);

       uint32_t count = arr->count_;
       DumpU32(count);

       int32_t elementSize = -type->instanceSize_;
       size_t dataOffset = alignUp(sizeof(ArrayHeader), elementSize);
       size_t dataSize = elementSize * count;
       DumpU32(dataSize);

       uint8_t* data = reinterpret_cast<uint8_t*>(arr) + dataOffset;
       DumpSpan(std_support::span<uint8_t>(data, dataSize));
   }

   void DumpObjectOrArray(ObjHeader* obj) {
       const TypeInfo* type = obj->type_info();
       if (type->IsArray()) {
           DumpArray(type, obj->array());
       } else {
           DumpObject(type, obj);
       }
   }

    // region Tencent Code
    void DumpObjectFromFile(const TypeInfo* type, ObjHeader* obj, uintptr_t originPtr) {
       DumpU8(TAG_OBJECT);
       DumpId(reinterpret_cast<void *>(originPtr));
       DumpId(type);

       size_t size = type->instanceSize_;
       size_t dataOffset = sizeof(TypeInfo*);
       size_t dataSize = size - dataOffset;
       uint8_t* data = reinterpret_cast<uint8_t*>(obj) + dataOffset;

       DumpU32(dataSize);
       DumpSpan(std_support::span<uint8_t>(data, dataSize));
   }

    void DumpArrayFromFile(const TypeInfo *type, ArrayHeader *arr, uintptr_t originPtr) {
        DumpU8(TAG_ARRAY);
        DumpId(reinterpret_cast<void *>(originPtr));
        DumpId(type);

        uint32_t count = arr->count_;
        DumpU32(count);

        int32_t elementSize = -type->instanceSize_;
        size_t dataSize = elementSize * count;
        DumpU32(dataSize);

        size_t dataOffset = alignUp(sizeof(ArrayHeader), elementSize);
        uint8_t *data = reinterpret_cast<uint8_t *>(arr) + dataOffset;
        DumpSpan(std_support::span<uint8_t>(data, dataSize));
    }

    void DumpTransitivelyFromFile(ObjHeader *obj, uintptr_t originPtr) {
        if (dumpedObjs_.insert(reinterpret_cast<ObjHeader *>(originPtr)).second) {
            DumpTransitively(obj->type_info());

            const TypeInfo *type = obj->type_info();
            if (type->IsArray()) {
                DumpArrayFromFile(type, obj->array(), originPtr);
            } else {
                DumpObjectFromFile(type, obj, originPtr);
            }
        }
    }

    void DumpTransitivelyFromHeap(ObjHeader *obj) {
        if (dumpedObjs_.insert(obj).second) {
            DumpTransitively(obj->type_info());
            DumpObjectOrArray(obj);
        }
    }

    void DumpEnqueuedObjectsFromHeap() {
        while (!objQueue_.empty()) {
            auto obj = objQueue_.front();
            objQueue_.pop();
            DumpTransitivelyFromHeap(obj);
        }
    }
    // endregion

   void DumpType(const TypeInfo* type) {
       DumpU8(TAG_TYPE);
       DumpId(type);

       bool isArray = type->IsArray();
       bool isExtended = type->extendedInfo_ != nullptr;
       bool isObjectArray = type == theArrayTypeInfo;
       uint8_t flags =
               (isArray ? TYPE_FLAG_ARRAY : 0) | (isExtended ? TYPE_FLAG_EXTENDED : 0) | (isObjectArray ? TYPE_FLAG_OBJECT_ARRAY : 0);
       DumpU8(flags);

       DumpId(type->superType_);

       DumpStringOrEmptyIfNull(type->packageName_);
       DumpStringOrEmptyIfNull(type->relativeName_);

       if (type->IsArray()) {
           DumpArrayInfo(type);
       } else {
           DumpObjectInfo(type);
       }
   }

   void DumpArrayInfo(const TypeInfo* type) {
       int32_t elementSize = -type->instanceSize_;
       DumpU32(elementSize);

       if (type->extendedInfo_ != nullptr) {
           DumpArrayInfo(type->extendedInfo_);
       }
   }

   void DumpArrayInfo(const ExtendedTypeInfo* extendedInfo) {
       uint8_t elementType = -extendedInfo->fieldsCount_;
       DumpU8(elementType);
   }

   void DumpObjectInfo(const TypeInfo* type) {
       size_t dataOffset = sizeof(TypeInfo*);

       DumpU32(type->instanceSize_ - dataOffset);
       DumpOffsets(type, dataOffset);

       if (type->extendedInfo_ != nullptr) {
           DumpObjectInfo(type->extendedInfo_, dataOffset);
       }
   }

   void DumpOffsets(const TypeInfo* type, size_t dataOffset) {
       int32_t count = type->objOffsetsCount_;
       DumpU32(count);
       for (int32_t i = 0; i < count; i++) {
           DumpU32(type->objOffsets_[i] - dataOffset);
       }
   }

   void DumpObjectInfo(const ExtendedTypeInfo* extendedInfo, size_t dataOffset) {
       int32_t fieldsCount = extendedInfo->fieldsCount_;
       DumpU32(fieldsCount);
       for (int32_t i = 0; i < fieldsCount; i++) {
           DumpU32(extendedInfo->fieldOffsets_[i] - dataOffset);
           DumpU8(extendedInfo->fieldTypes_[i]);
           DumpStr(extendedInfo->fieldNames_[i]);
       }
   }

   void DumpTransitively(const TypeInfo* type) {
       if (dumpedTypes_.insert(type).second) {
           // Dump super-type recursively, as the depth is not going to be a problem.
           if (type->superType_ != nullptr) {
               DumpTransitively(type->superType_);
           }

           DumpType(type);
       }
   }

   void DumpTransitively(ObjHeader* obj) {
       if (dumpedObjs_.insert(obj).second) {
           DumpTransitively(obj->type_info());

           DumpObjectOrArray(obj);

           // Enqueue referred objects to dump, as dumping them recursively may cause
           // stack overflow.
           traverseReferredObjects(obj, [&](auto refObj) { Enqueue(refObj); });
       }
   }

   void DumpTransitively(ExtraObjectData* extraObj) {
       DumpU8(TAG_EXTRA_OBJECT);
       DumpId(extraObj);

       ObjHeader* baseObj = extraObj->GetBaseObject();
       DumpId(baseObj);

       if (!isNullOrMarker(baseObj)) {
           Enqueue(baseObj);
       }

       void* associatedObject =
#ifdef KONAN_OBJC_INTEROP
               extraObj->AssociatedObject();
#else
               nullptr;
#endif
       DumpId(associatedObject);
   }

   void DumpTransitively(GlobalRootSet::Value& value) {
       ObjHeader* obj = value.object;
       if (isNullOrMarker(obj)) {
           return;
       }

       DumpGlobalRoot(value);

       Enqueue(obj);
   }

   void DumpTransitively(ThreadData& thread, ThreadRootSet::Value& value) {
       ObjHeader* obj = value.object;
       if (isNullOrMarker(obj)) {
           return;
       }

       DumpThreadRoot(thread, value);

       Enqueue(obj);
   }

   void Enqueue(ObjHeader* obj) { objQueue_.push(obj); }

   void DumpEnqueuedObjects() {
       while (!objQueue_.empty()) {
           auto obj = objQueue_.front();
           objQueue_.pop();
           DumpTransitively(obj);
       }
   }

   uint8_t UInt8(GlobalRootSet::Source source) {
       switch (source) {
           case GlobalRootSet::Source::kGlobal:
               return 1;
           case GlobalRootSet::Source::kStableRef:
               return 2;
       }
   }

   uint8_t UInt8(ThreadRootSet::Source source) {
       switch (source) {
           case ThreadRootSet::Source::kStack:
               return 1;
           case ThreadRootSet::Source::kTLS:
               return 2;
       }
   }

   const uint8_t TAG_TYPE = 0x01;
   const uint8_t TAG_OBJECT = 0x02;
   const uint8_t TAG_ARRAY = 0x03;
   const uint8_t TAG_EXTRA_OBJECT = 0x04;
   const uint8_t TAG_THREAD = 0x05;
   const uint8_t TAG_GLOBAL_ROOT = 0x06;
   const uint8_t TAG_THREAD_ROOT = 0x07;

   const uint8_t TYPE_FLAG_ARRAY = 1 << 0;
   const uint8_t TYPE_FLAG_EXTENDED = 1 << 1;
   const uint8_t TYPE_FLAG_OBJECT_ARRAY = 1 << 2;

   // Target file.
   FILE* file_;

   // A set of already dumped type pointers.
   std::unordered_set<const TypeInfo*> dumpedTypes_;

   // A set of already dumped objects.
   std::unordered_set<ObjHeader*> dumpedObjs_;

   // A queue of objects to dump transitively.
   std::queue<ObjHeader*> objQueue_;

    // region Tencent Code
    std::string asyncCache;

    static constexpr int kFixedCacheCount = 3; // 修改此值调整fixed缓存的并发线程数量
    std::vector<FILE *> fixedCaches;
    std::vector<size_t> fixedBlockPageCounts;
    FILE *otherCache;
    size_t fixedBlockPageSize = 0;
    size_t nextFitPageCount = 0;
    size_t nextFitPageSize = 0;
    std::vector<size_t> singlePageSizeArray;
    // endregion
};

void PrepareForMemoryDump() {
   mm::GlobalData::Instance().threadRegistry().PublishAll();
}

void DumpMemoryOrThrow(int fd) {
   FILE* file = fdopen(fd, "w");
   if (file == nullptr) {
       throw std::system_error(errno, std::generic_category());
   }

   MemoryDumper(file).Dump();

   if (fflush(file) == EOF) {
       throw std::system_error(errno, std::generic_category());
   }
}

// region Tencent Code
void DumpMemoryOrThrowAsync(int fd, KRef dirPath) {
   FILE* file = fdopen(fd, "w");
   if (file == nullptr) {
       throw std::system_error(errno, std::generic_category());
   }
   char *asyncCache = CreateCStringFromString(dirPath);
   if (asyncCache == nullptr) {
       throw std::runtime_error("Cache path is null.");
   }
   auto *dumper = new MemoryDumper(file, std::string(asyncCache));
   dumper->DumpAsync();
   DisposeCString(asyncCache);
}

bool isAsyncDumping() noexcept {
    return isDumping;
}

bool DumpMemoryAsync(int fd, KRef asyncCacheDir) noexcept {
    bool expected = false;
    if (!isDumping.compare_exchange_strong(expected, true, std::memory_order_acq_rel)) {
        return false;
    }
    PrepareForMemoryDump();
    bool success = true;
    try {
        DumpMemoryOrThrowAsync(fd, asyncCacheDir);
    } catch (const std::system_error& e) {
        success = false;
        RuntimeLogError({kTagGC}, "Memory dump async error: %s", e.what());
    }
    return success;
}
// endregion

bool DumpMemory(int fd) noexcept {
   PrepareForMemoryDump();

   bool success = true;
   try {
#ifndef KONAN_OHOS
       DumpMemoryOrThrow(fd);
#else
       if (fork() == 0) {
            DumpMemoryOrThrow(fd);
            exit(-1);
    }
#endif
   } catch (const std::system_error& e) {
       success = false;
       RuntimeLogError({kTagGC}, "Memory dump error: %s", e.what());
   }

   return success;
}

} // namespace kotlin::mm
