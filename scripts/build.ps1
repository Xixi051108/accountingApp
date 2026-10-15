param([switch]$TestsOnly, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    if (Test-Path -LiteralPath "$projectRoot/.tools/jdk/bin/java.exe") {
        $env:JAVA_HOME = "$projectRoot/.tools/jdk"
    }
    $env:GRADLE_USER_HOME = "$projectRoot/.tools/gradle-home"
    $env:ANDROID_USER_HOME = "$projectRoot/.tools/android-user"
    New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
    if (-not $TestsOnly -and -not (Test-Path -LiteralPath "$projectRoot/.tools/debug.keystore")) {
        $keytool = if ($env:JAVA_HOME) { "$env:JAVA_HOME/bin/keytool.exe" } else { 'keytool' }
        & $keytool -genkeypair -keystore "$projectRoot/.tools/debug.keystore" -storepass android -keypass android `
            -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'Unable to create the local Android debug signing key.' }
    }
    $buildArguments = @(':app:testDebugUnitTest', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process')
    if (-not $TestsOnly) { $buildArguments += @(':app:lintDebug', ':app:assembleDebug') }
    if ($Offline) { $buildArguments += '--offline' }
    & './gradlew.bat' @buildArguments
    $buildExit = $LASTEXITCODE
} finally { Pop-Location }
exit $buildExit
