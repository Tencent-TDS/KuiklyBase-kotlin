# Package the Kotlin compiler and Kotlin/Native prebuilt into build/repo for local development.
# Usage:
#   .\publish-local.ps1 [-Targets <filter>]

param(
    [Parameter(Position = 0)]
    [string]$Channel = "external",
    [string]$Targets = $env:KUIKLY_TARGETS
)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
Set-Location $RootDir
Write-Host "cd $RootDir"

$DeployVersion = if ($env:DEPLOY_VERSION) { $env:DEPLOY_VERSION } else { "2.4.255-SNAPSHOT" }
$BuildNumber = if ($env:BUILD_NUMBER) { $env:BUILD_NUMBER } else { "1" }
$BootstrapLocal = if ($env:BOOTSTRAP_LOCAL) { $env:BOOTSTRAP_LOCAL } else { "true" }

function Restore-VersionFiles {
    & git -C $RootDir rev-parse --is-inside-work-tree *> $null
    if ($LASTEXITCODE -ne 0) { return }
    $files = @("libraries/stdlib/src/kotlin/util/KotlinVersion.kt")
    $poms = & git -C $RootDir ls-files "libraries" | Where-Object { $_ -match '/pom\.xml$' }
    if ($poms) { $files += $poms }
    & git -C $RootDir checkout HEAD -- @files
}

function Restore-LocalState {
    $localProps = Join-Path $RootDir "local.properties"
    $localPropsBk = Join-Path $RootDir "local.properties.bk"
    if (Test-Path $localPropsBk) {
        Move-Item -Force $localPropsBk $localProps
    } elseif (Test-Path $localProps) {
        Remove-Item -Force $localProps
    }
    Restore-VersionFiles
}

function Stop-OrExit([string]$Message) {
    if ($LASTEXITCODE -ne 0) {
        Write-Host $Message
        Restore-LocalState
        exit $LASTEXITCODE
    }
}

function Invoke-GradleNative([string[]]$Tasks) {
    $opts = @(
        "--stacktrace",
        "-PdeployVersion=$DeployVersion",
        "-Pbuild.number=$BuildNumber",
        "-Pversions.kotlin-native=$DeployVersion",
        "-PkonanVersion=$DeployVersion",
        "-Pkotlin.native.enabled=true"
    )
    if ($BootstrapLocal -eq "true") {
        $opts += "-Pbootstrap.local=true"
        $opts += "-Pbootstrap.local.version=$DeployVersion"
    } else {
        $opts += "-Pbootstrap.local=false"
    }
    if ($Channel -eq "internal") {
        $opts += "-Pkuikly.internal=true"
    }
    if ($Targets) {
        $opts += "-Pkuikly.targets=$Targets"
    }
    & .\gradlew.bat @opts @Tasks
    Stop-OrExit "Failed to build Kotlin/Native."
}

$repoPath = ($RootDir -replace '\\', '/')
$localRepo = "file:///$repoPath/build/repo"

$localProps = Join-Path $RootDir "local.properties"
$localPropsBk = Join-Path $RootDir "local.properties.bk"
if ((Test-Path $localProps) -and -not (Test-Path $localPropsBk)) {
    Move-Item -Force $localProps $localPropsBk
}
Set-Content -Path $localProps -Value "kotlin.build.isObsoleteJdkOverrideEnabled=true" -Encoding ASCII
& .\gradlew.bat --stop

Write-Host ":::: Set Maven module version to $DeployVersion"
& .\libraries\mvnw.cmd `
    "-DnewVersion=$DeployVersion" `
    "-DgenerateBackupPoms=false" `
    "-DprocessAllModules=true" `
    "-f" "$RootDir\libraries\pom.xml" `
    versions:set
Stop-OrExit "Failed to set the Maven module version."

Write-Host ":::: Publish bootstrap Kotlin libs to $localRepo."
& .\gradlew.bat --stacktrace `
    publish install `
    "-Pkotlin.native.enabled=false" `
    "-PdeployVersion=$DeployVersion" `
    "-Pbuild.number=$BuildNumber" `
    "-Pversions.kotlin-native=$DeployVersion" `
    "-PkonanVersion=$DeployVersion" `
    "-Pbootstrap.local=false"
Stop-OrExit "Failed to publish bootstrap Kotlin libs."

Write-Host ":::: Build maven part and install to $localRepo."
& .\libraries\mvnw.cmd `
    "-f" "$RootDir\libraries\pom.xml" `
    clean install `
    "-DskipTests" `
    "-Ddeploy-url=$localRepo" `
    "-Ddeploy-snapshot-repo=local" `
    "-Ddeploy-snapshot-url=$localRepo"
Stop-OrExit "Failed to install Maven modules."

& .\gradlew.bat --stop
Write-Host ":::: Clean, bundle and publish Kotlin/Native to $localRepo."
if (Test-Path ".\kotlin-native\dist") {
    Remove-Item -Recurse -Force ".\kotlin-native\dist"
}
Invoke-GradleNative @(
    ":kotlin-native:clean",
    ":kotlin-native:bundle",
    ":kotlin-native:publishBundlePrebuiltPublicationToMavenRepository"
)

Restore-LocalState
Write-Host ":::: Published locally to $RootDir\build\repo."
