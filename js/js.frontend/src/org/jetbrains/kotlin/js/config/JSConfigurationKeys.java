/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.config;

import org.jetbrains.kotlin.config.CompilerConfigurationKey;
import org.jetbrains.kotlin.incremental.js.IncrementalDataProvider;
import org.jetbrains.kotlin.incremental.js.IncrementalNextRoundChecker;
import org.jetbrains.kotlin.incremental.js.IncrementalResultsConsumer;
import org.jetbrains.kotlin.konan.file.ZipFileSystemAccessor;
import org.jetbrains.kotlin.serialization.js.ModuleKind;

import java.io.File;
import java.util.List;
import java.util.Map;

public class JSConfigurationKeys {
    public static final CompilerConfigurationKey<List<String>> TRANSITIVE_LIBRARIES =
            CompilerConfigurationKey.create("library files for transitive dependencies");

    public static final CompilerConfigurationKey<List<String>> LIBRARIES =
            CompilerConfigurationKey.create("library file paths");

    public static final CompilerConfigurationKey<Boolean> SOURCE_MAP =
            CompilerConfigurationKey.create("generate source map");

    public static final CompilerConfigurationKey<File> OUTPUT_DIR =
            CompilerConfigurationKey.create("output directory");

    public static final CompilerConfigurationKey<String> SOURCE_MAP_PREFIX =
            CompilerConfigurationKey.create("prefix to add to paths in source map");

    public static final CompilerConfigurationKey<List<String>> SOURCE_MAP_SOURCE_ROOTS =
            CompilerConfigurationKey.create("base directories used to calculate relative paths for source map");

    public static final CompilerConfigurationKey<SourceMapSourceEmbedding> SOURCE_MAP_EMBED_SOURCES =
            CompilerConfigurationKey.create("embed source files into source map");

    public static final CompilerConfigurationKey<SourceMapNamesPolicy> SOURCEMAP_NAMES_POLICY = CompilerConfigurationKey.create(
            "a policy to generate a mapping from generated identifiers to their corresponding original names");

    public static final CompilerConfigurationKey<Boolean> SOURCE_MAP_INCLUDE_MAPPINGS_FROM_UNAVAILABLE_FILES = CompilerConfigurationKey.create(
            "insert source mappings from libraries even if their sources are unavailable on the end-user machine");

    public static final CompilerConfigurationKey<Boolean> META_INFO =
            CompilerConfigurationKey.create("generate .meta.js and .kjsm files");

    public static final CompilerConfigurationKey<EcmaVersion> TARGET =
            CompilerConfigurationKey.create("ECMA version target");

    public static final CompilerConfigurationKey<ModuleKind> MODULE_KIND =
            CompilerConfigurationKey.create("module kind");

    public static final CompilerConfigurationKey<Boolean> TYPED_ARRAYS_ENABLED =
            CompilerConfigurationKey.create("TypedArrays enabled");

    public static final CompilerConfigurationKey<IncrementalDataProvider> INCREMENTAL_DATA_PROVIDER =
            CompilerConfigurationKey.create("incremental data provider");

    public static final CompilerConfigurationKey<IncrementalResultsConsumer> INCREMENTAL_RESULTS_CONSUMER =
            CompilerConfigurationKey.create("incremental results consumer");

    public static final CompilerConfigurationKey<IncrementalNextRoundChecker> INCREMENTAL_NEXT_ROUND_CHECKER =
            CompilerConfigurationKey.create("incremental compilation next round checker");

    public static final CompilerConfigurationKey<Boolean> FRIEND_PATHS_DISABLED =
            CompilerConfigurationKey.create("disable support for friend paths");

    public static final CompilerConfigurationKey<List<String>> FRIEND_PATHS =
            CompilerConfigurationKey.create("friend module paths");

    public static final CompilerConfigurationKey<Boolean> METADATA_ONLY =
            CompilerConfigurationKey.create("generate .meta.js and .kjsm files only");

    public static final CompilerConfigurationKey<Boolean> DEVELOPER_MODE =
            CompilerConfigurationKey.create("enables additional checkers");

    public static final CompilerConfigurationKey<Boolean> GENERATE_COMMENTS_WITH_FILE_PATH =
            CompilerConfigurationKey.create("generate comments with file path at the start of each file block");

    public static final CompilerConfigurationKey<Boolean> GENERATE_POLYFILLS =
            CompilerConfigurationKey.create("generate polyfills for newest properties, methods and classes from ES6+");

    public static final CompilerConfigurationKey<String> DEFINE_PLATFORM_MAIN_FUNCTION_ARGUMENTS =
            CompilerConfigurationKey.create("provide platform specific args as a parameter of the main function");

    public static final CompilerConfigurationKey<Boolean> GENERATE_DTS =
            CompilerConfigurationKey.create("generate TypeScript definition file");

    public static final CompilerConfigurationKey<Boolean> COMPILE_SUSPEND_AS_JS_GENERATOR =
            CompilerConfigurationKey.create("force suspend functions compilation int JS generator functions");

    public static final CompilerConfigurationKey<Boolean> GENERATE_REGION_COMMENTS =
            CompilerConfigurationKey.create("generate special comments at the start and the end of each file block, " +
                                            "it allows to fold them and navigate to them in the IDEA");

    public static final CompilerConfigurationKey<Map<String, String>> FILE_PATHS_PREFIX_MAP =
            CompilerConfigurationKey.create("this map used to shorten/replace prefix of paths in comments with file paths, " +
                                            "including region comments");

    public static final CompilerConfigurationKey<Boolean> PRINT_REACHABILITY_INFO =
            CompilerConfigurationKey.create("print declarations' reachability info during performing DCE");

    public static final CompilerConfigurationKey<String> DUMP_REACHABILITY_INFO_TO_FILE =
            CompilerConfigurationKey.create("dump declarations' reachability info to file during performing DCE");

    public static final CompilerConfigurationKey<Boolean> FAKE_OVERRIDE_VALIDATOR =
            CompilerConfigurationKey.create("IR fake override validator");

    public static final CompilerConfigurationKey<ErrorTolerancePolicy> ERROR_TOLERANCE_POLICY =
            CompilerConfigurationKey.create("set up policy to ignore compilation errors");

    public static final CompilerConfigurationKey<Boolean> PROPERTY_LAZY_INITIALIZATION =
            CompilerConfigurationKey.create("perform lazy initialization for properties");

    public static final CompilerConfigurationKey<Boolean> GENERATE_INLINE_ANONYMOUS_FUNCTIONS =
            CompilerConfigurationKey.create("translate lambdas into in-line anonymous functions");

    public static final CompilerConfigurationKey<Boolean> GENERATE_STRICT_IMPLICIT_EXPORT =
            CompilerConfigurationKey.create("enable strict implicitly exported entities types inside d.ts files");

    public static final CompilerConfigurationKey<ZipFileSystemAccessor> ZIP_FILE_SYSTEM_ACCESSOR =
            CompilerConfigurationKey.create("zip file system accessor, used for klib reading");

    public static final CompilerConfigurationKey<Boolean> OPTIMIZE_GENERATED_JS =
            CompilerConfigurationKey.create("perform additional optimizations on the generated JS code");

    public static final CompilerConfigurationKey<Boolean> USE_ES6_CLASSES =
            CompilerConfigurationKey.create("perform ES6 class usage");
}
