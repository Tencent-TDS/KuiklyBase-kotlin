/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin

import org.gradle.api.Named
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.reflect.Instantiator
import org.jetbrains.kotlin.dependencies.NativeDependenciesExtension
import org.jetbrains.kotlin.konan.target.Platform

class NamedNativeInteropConfig implements Named {

    private final Project project
    final String name


    private String interopStubsName
    private SourceSet interopStubs
    final JavaExec genTask

    private String flavor = "jvm"

    private String defFile

    private String pkg
    private String target

    private List<String> compilerOpts = []
    private List<String> headers;
    private String linker
    List<String> linkerOpts = []
    private FileCollection linkFiles;
    private List<String> linkTasks = []

    Configuration configuration

    void flavor(String value) {
        flavor = value
    }

    void defFile(String value) {
        defFile = value
        genTask.inputs.file(project.file(defFile))
    }

    void target(String value) {
        target = value
    }

    void pkg(String value) {
        pkg = value
    }

    void compilerOpts(List<String> values) {
        compilerOpts.addAll(values)
    }

    void compilerOpts(String... values) {
        compilerOpts.addAll(values)
    }

    void headers(FileCollection files) {
        dependsOnFiles(files)
        headers = headers + files.toSet().collect { it.absolutePath }
    }

    void headers(String... values) {
        headers = headers + values.toList()
    }

    void linker(String value) {
        linker = value
    }

    void linkerOpts(String... values) {
        this.linkerOpts(values.toList())
    }

    void linkerOpts(List<String> values) {
        linkerOpts.addAll(values)
    }

    void dependsOn(Object... deps) {
        // TODO: add all files to inputs
        genTask.dependsOn(deps)
    }

    private void dependsOnFiles(FileCollection files) {
        dependsOn(files)
        genTask.inputs.files(files)
    }

    void link(FileCollection files) {
        linkFiles = linkFiles + files
        dependsOnFiles(files)
    }

    void linkOutputs(Task task) {
        linkOutputs(task.name)
    }

    void linkOutputs(String task) {
        linkTasks += task
        dependsOn(task)

        final Project prj;
        String taskName;
        int index = task.lastIndexOf(':')
        if (index != -1) {
            prj = project.project(task.substring(0, index))
            taskName = task.substring(index + 1)
        } else {
            prj = project
            taskName = task
        }

        prj.tasks.matching { it.name == taskName }.all { // TODO: it is a hack
            this.dependsOnFiles(it.outputs.files)
        }
    }

    void includeDirs(String... values) {
        compilerOpts.addAll(values.collect {"-I$it"})
    }

    File getNativeLibsDir() {
        return project.layout.buildDirectory.dir("nativelibs/$target").get().asFile
    }

    File getGeneratedSrcDir() {
        return project.layout.buildDirectory.dir("nativeInteropStubs/$name/kotlin").get().asFile
    }

    File getTemporaryFilesDir() {
        return project.layout.buildDirectory.dir("interopTemp").get().asFile
    }

    String getLlvmDir() {
        return project.extensions.nativeDependencies.llvmPath
    }

    Platform getHostPlatform() {
        return project.extensions.nativeDependencies.hostPlatform
    }

    NamedNativeInteropConfig(Project project, String name, String target = null, String flavor = 'jvm') {
        this.name = name
        this.project = project
        this.flavor = flavor

        def platformManager = project.extensions.platformManager
        def targetManager = platformManager.targetManager(target)
        this.target = targetManager.targetName

        this.headers = []
        this.linkFiles = project.files()

        interopStubsName = name + "InteropStubs"
        genTask = project.task("gen" + interopStubsName.capitalize(), type: JavaExec)

        this.configure()
    }

    private void configure() {
        if (project.plugins.hasPlugin("kotlin")) {
            interopStubs = project.sourceSets.create(interopStubsName)
            configuration = project.configurations.create(interopStubs.name)
            project.tasks.getByName(interopStubs.getTaskName("compile", "Kotlin")) {
                compilerOptions.freeCompilerArgs.add("-Xskip-prerelease-check")
            }

            interopStubs.kotlin.srcDir(project.tasks.named(genTask.name).map { generatedSrcDir })

            project.dependencies {
                add interopStubs.getApiConfigurationName(), project(path: ':kotlin-native:Interop:Runtime')
                add interopStubs.getApiConfigurationName(), "org.jetbrains.kotlin:kotlin-stdlib:${project.bootstrapKotlinVersion}"
            }

            this.configuration.extendsFrom project.configurations[interopStubs.getRuntimeClasspathConfigurationName()]
            project.dependencies.add(this.configuration.name, interopStubs.output)
        }

        genTask.configure {
            notCompatibleWithConfigurationCache("This task uses Task.project at execution time")
            dependsOn project.extensions.nativeDependencies.hostPlatformDependency
            dependsOn project.extensions.nativeDependencies.llvmDependency
            dependsOn ":kotlin-native:Interop:Indexer:nativelibs"
            dependsOn ":kotlin-native:Interop:Indexer12:nativelibs"
            dependsOn ":kotlin-native:Interop:Runtime:nativelibs"
            classpath = project.configurations.interopStubGenerator
            mainClass = "org.jetbrains.kotlin.native.interop.gen.jvm.MainKt"
            jvmArgs '-ea'

            systemProperties "java.library.path" : project.files(
                    project.findProject(":kotlin-native:Interop:Indexer").layout.buildDirectory.dir("nativelibs"),
                    project.findProject(":kotlin-native:Interop:Runtime").layout.buildDirectory.dir("nativelibs"),
            ).asPath
            // Set the konan.home property because we run the cinterop tool not from a distribution jar
            // so it will not be able to determine this path by itself.
            systemProperties "konan.home": project.project(":kotlin-native").projectDir
            environment "LIBCLANG_DISABLE_CRASH_RECOVERY": "1"

            outputs.dir generatedSrcDir
            outputs.dir nativeLibsDir
            outputs.dir temporaryFilesDir

            // defer as much as possible
            doFirst {
                List<String> linkerOpts = this.linkerOpts

                linkTasks.each {
                    linkerOpts += project.tasks.getByPath(it).outputs.files.files
                }

                linkerOpts += linkFiles.files

                args '-generated', generatedSrcDir
                args '-natives', nativeLibsDir
                args '-Xtemporary-files-dir', temporaryFilesDir
                args '-flavor', this.flavor
                // Uncomment to debug.
                // args '-verbose', 'true'

                if (defFile != null) {
                    args '-def', project.file(defFile)
                }

                if (pkg != null) {
                    args '-pkg', pkg
                }

                if (linker != null) {
                    args '-linker', linker
                }

                if (target != null) {
                    args '-target', target
                }

                // TODO: the interop plugin should probably be reworked to execute clang from build scripts directly
                environment['PATH'] = project.files(project.extensions.platformManager.hostPlatform.clang.clangPaths).asPath +
                        File.pathSeparator + environment['PATH']

                args compilerOpts.collectMany { ['-compiler-option', it] }
                args linkerOpts.collectMany { ['-linker-option', it] }

                headers.each {
                    args '-header', it
                }

            }
        }
    }
}

class NativeInteropExtension extends AbstractNamedDomainObjectContainer<NamedNativeInteropConfig> {

    private final Project project
    private String target = null
    private String flavor = 'jvm'

    protected NativeInteropExtension(Project project) {
        super(NamedNativeInteropConfig, project.gradle.services.get(Instantiator), project.gradle.services.get(CollectionCallbackActionDecorator))
        this.project = project
    }

    @Override
    protected NamedNativeInteropConfig doCreate(String name) {
        def config = new NamedNativeInteropConfig(project, name, target, flavor)
        return config
    }

    public void target(String value) {
        this.target = value
    }

    public void flavor(String value) {
        this.flavor = value
    }
}

class NativeInteropPlugin implements Plugin<Project> {

    @Override
    void apply(Project prj) {
        prj.plugins.apply("platform-manager")
        prj.plugins.apply("native-dependencies")

        prj.extensions.add("kotlinNativeInterop", new NativeInteropExtension(prj))

        prj.configurations {
            interopStubGenerator
        }

        prj.dependencies {
            interopStubGenerator project(path: ":kotlin-native:Interop:StubGenerator")
            interopStubGenerator project(path: ":kotlin-native:endorsedLibraries:kotlinx.cli", configuration: "jvmRuntimeElements")
        }
    }
}
