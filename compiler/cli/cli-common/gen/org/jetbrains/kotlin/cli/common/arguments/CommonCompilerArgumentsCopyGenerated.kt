@file:Suppress("unused", "DuplicatedCode")

// DO NOT EDIT MANUALLY!
// Generated by generators/tests/org/jetbrains/kotlin/generators/arguments/GenerateCompilerArgumentsCopy.kt
// To regenerate run 'generateCompilerArgumentsCopy' task

package org.jetbrains.kotlin.cli.common.arguments

@OptIn(org.jetbrains.kotlin.utils.IDEAPluginsCompatibilityAPI::class)
fun copyCommonCompilerArguments(from: CommonCompilerArguments, to: CommonCompilerArguments): CommonCompilerArguments {
    copyCommonToolArguments(from, to)

    to.allowAnyScriptsInSourceRoots = from.allowAnyScriptsInSourceRoots
    to.allowKotlinPackage = from.allowKotlinPackage
    to.apiVersion = from.apiVersion
    to.autoAdvanceApiVersion = from.autoAdvanceApiVersion
    to.autoAdvanceLanguageVersion = from.autoAdvanceLanguageVersion
    to.checkPhaseConditions = from.checkPhaseConditions
    to.checkStickyPhaseConditions = from.checkStickyPhaseConditions
    to.commonSources = from.commonSources?.copyOf()
    to.consistentDataClassCopyVisibility = from.consistentDataClassCopyVisibility
    to.contextReceivers = from.contextReceivers
    to.disableDefaultScriptingPlugin = from.disableDefaultScriptingPlugin
    to.disablePhases = from.disablePhases?.copyOf()
    to.dontWarnOnErrorSuppression = from.dontWarnOnErrorSuppression
    to.dumpDirectory = from.dumpDirectory
    to.dumpOnlyFqName = from.dumpOnlyFqName
    to.dumpPerf = from.dumpPerf
    to.enableBuilderInference = from.enableBuilderInference
    to.expectActualClasses = from.expectActualClasses
    to.experimental = from.experimental?.copyOf()
    to.explicitApi = from.explicitApi
    to.explicitReturnTypes = from.explicitReturnTypes
    to.extendedCompilerChecks = from.extendedCompilerChecks
    to.fragmentRefines = from.fragmentRefines?.copyOf()
    to.fragmentSources = from.fragmentSources?.copyOf()
    to.fragments = from.fragments?.copyOf()
    to.ignoreConstOptimizationErrors = from.ignoreConstOptimizationErrors
    to.incrementalCompilation = from.incrementalCompilation
    to.inferenceCompatibility = from.inferenceCompatibility
    to.inlineClasses = from.inlineClasses
    to.intellijPluginRoot = from.intellijPluginRoot
    to.kotlinHome = from.kotlinHome
    to.languageVersion = from.languageVersion
    to.legacySmartCastAfterTry = from.legacySmartCastAfterTry
    to.listPhases = from.listPhases
    to.metadataKlib = from.metadataKlib
    to.metadataVersion = from.metadataVersion
    to.multiDollarInterpolation = from.multiDollarInterpolation
    to.multiPlatform = from.multiPlatform
    to.newInference = from.newInference
    to.noCheckActual = from.noCheckActual
    to.noInline = from.noInline
    to.optIn = from.optIn?.copyOf()
    to.phasesToDump = from.phasesToDump?.copyOf()
    to.phasesToDumpAfter = from.phasesToDumpAfter?.copyOf()
    to.phasesToDumpBefore = from.phasesToDumpBefore?.copyOf()
    to.phasesToValidate = from.phasesToValidate?.copyOf()
    to.phasesToValidateAfter = from.phasesToValidateAfter?.copyOf()
    to.phasesToValidateBefore = from.phasesToValidateBefore?.copyOf()
    to.pluginClasspaths = from.pluginClasspaths?.copyOf()
    to.pluginConfigurations = from.pluginConfigurations?.copyOf()
    to.pluginOptions = from.pluginOptions?.copyOf()
    to.profilePhases = from.profilePhases
    to.progressiveMode = from.progressiveMode
    to.renderInternalDiagnosticNames = from.renderInternalDiagnosticNames
    to.reportAllWarnings = from.reportAllWarnings
    to.reportOutputFiles = from.reportOutputFiles
    to.reportPerf = from.reportPerf
    to.script = from.script
    to.selfUpperBoundInference = from.selfUpperBoundInference
    to.skipMetadataVersionCheck = from.skipMetadataVersionCheck
    to.skipPrereleaseCheck = from.skipPrereleaseCheck
    to.stdlibCompilation = from.stdlibCompilation
    to.suppressApiVersionGreaterThanLanguageVersionError = from.suppressApiVersionGreaterThanLanguageVersionError
    to.suppressVersionWarnings = from.suppressVersionWarnings
    to.unrestrictedBuilderInference = from.unrestrictedBuilderInference
    to.useExperimental = from.useExperimental?.copyOf()
    to.useFirExtendedCheckers = from.useFirExtendedCheckers
    to.useFirIC = from.useFirIC
    to.useFirLT = from.useFirLT
    to.useK2 = from.useK2
    to.verbosePhases = from.verbosePhases?.copyOf()
    to.verifyIr = from.verifyIr
    to.verifyIrVisibility = from.verifyIrVisibility
    to.verifyIrVisibilityAfterInlining = from.verifyIrVisibilityAfterInlining
    to.whenGuards = from.whenGuards
    // region Tencent Code
    to.objCExportConfigurationPath = from.objCExportConfigurationPath
    to.enableDefaultObjCExport = from.enableDefaultObjCExport
    // endregion
    return to
}
