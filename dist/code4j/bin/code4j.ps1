$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$appHome = Split-Path -Parent $scriptDir
$jarPath = Join-Path $appHome "lib\code4j.jar"
$javaExe = "java"

if ($env:JAVA_HOME21) {
    $candidate = Join-Path $env:JAVA_HOME21 "bin\java.exe"
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        $javaExe = $candidate
    }
} elseif ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        $javaExe = $candidate
    }
}

if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    Write-Error "Code4j launcher error: jar not found: $jarPath"
    exit 1
}

&amp; $javaExe -jar $jarPath @args
exit $LASTEXITCODE
