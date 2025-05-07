
# the minimum version of CMake.
cmake_minimum_required(VERSION 3.4.1)
project(kotlin-native-runtime)
set(CMAKE_CXX_STANDARD 17)
set(SOURCE_ROOT ${CMAKE_SOURCE_DIR}/../../src)

include_directories(${SOURCE_ROOT}/main/cpp
        ${SOURCE_ROOT}/mm/cpp
        ${SOURCE_ROOT}/comiler_interface/cpp
        ${SOURCE_ROOT}/custom_alloc/cpp
        ${SOURCE_ROOT}/exceptions_support/cpp
        ${SOURCE_ROOT}/gc/cms/cpp
        ${SOURCE_ROOT}/gc/common/cpp
        ${SOURCE_ROOT}/gcScheduler/common/cpp
        ${SOURCE_ROOT}/gcScheduler/adaptive/cpp
        #${SOURCE_ROOT}/gcScheduler/aggressive/cpp
        #${SOURCE_ROOT}/gcScheduler/manual/cpp
        ${SOURCE_ROOT}/profile_runtime/cpp
        #${SOURCE_ROOT}/relaxed/cpp
        ${SOURCE_ROOT}/source_info/cpp
        #${SOURCE_ROOT}/std_alloc/cpp
        #${SOURCE_ROOT}/strict/cpp
)

# customGC 详细配置参见 KonanConfig.kt

file(
        GLOB_RECURSE SOURCES
        ${SOURCE_ROOT}/ktbenchmark/*.cpp
        ${SOURCE_ROOT}/tools/*.cpp
        ${SOURCE_ROOT}/kntest/*.cpp

        #-----jemalloc
        #${SOURCE_ROOT}/third_party/*.cpp
        #-----jemalloc


        #------
        ${SOURCE_ROOT}/main/cpp/*.cpp
        ${SOURCE_ROOT}/comiler_interface/cpp/*.cpp
        ${SOURCE_ROOT}/custom_alloc/cpp/*.cpp
        ${SOURCE_ROOT}/debug/cpp/*.cpp
        ${SOURCE_ROOT}/exceptions_support/cpp/*.cpp
        ${SOURCE_ROOT}/gc/cms/*.cpp
        ${SOURCE_ROOT}/gc/common/*.cpp
        ${SOURCE_ROOT}/gcScheduler/common/cpp/*.cpp
        ${SOURCE_ROOT}/gcScheduler/adaptive/cpp/*.cpp
        ${SOURCE_ROOT}/mm/cpp/*.cpp
        ${SOURCE_ROOT}/profile_runtime/cpp/*.cpp
        ${SOURCE_ROOT}/source_info/cpp/*.cpp
        ${SOURCE_ROOT}/test_support/cpp/*.cpp

        #------
        #${SOURCE_ROOT}/gcScheduler/manual/cpp/*.cpp
        #${SOURCE_ROOT}/gcScheduler/aggressive/cpp/*.cpp
        #${SOURCE_ROOT}/launcher/cpp/*.cpp
        #${SOURCE_ROOT}/legacymm/cpp/*.cpp
        #${SOURCE_ROOT}/main/mimalloc/cpp/*.cpp
        #${SOURCE_ROOT}/main/opt_alloc/cpp/*.cpp
        #${SOURCE_ROOT}/relaxed/cpp/*.cpp
        #${SOURCE_ROOT}/std_alloc/cpp/*.cpp
        #${SOURCE_ROOT}/strict/cpp/*.cpp
)

add_compile_options(-O3)
add_compile_options(-fno-emulated-tls)
#add_compile_options(-funwind-tables)

add_library(kotlin-native-runtime SHARED ${SOURCES})

#target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_OSX=0")
#target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_MACOSX=1")
target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_OHOS=1")
target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_ARM64=1")
target_compile_definitions(kotlin-native-runtime PUBLIC "-DCUSTOM_ALLOCATOR=1")
target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_NO_DEBUG_API=0")
target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_TARGET_HAS_ADDRESS_DEPENDENCY=1")
#target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_X64=1")
#target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_OBJC_INTEROP")
target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_CORE_SYMBOLICATION=1")
target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_HAS_CXX11_EXCEPTION_FUNCTIONS=1")
#target_compile_definitions(kotlin-native-runtime PUBLIC "-DKONAN_REPORT_BACKTRACE_TO_IOS_CRASH_LOG=1")