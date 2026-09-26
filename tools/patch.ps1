# FarLands G1 patcher helper.
# Finds a Java 21+ (the patcher is built for Java 21; the default `java` may be 1.8,
# which fails with UnsupportedClassVersionError) and runs patcher-cli with proper paths.
#
# Usage (any of):
#   1) Drag your Minecraft 26.2 client jar onto patch.bat
#   2) powershell -ExecutionPolicy Bypass -File patch.ps1 "D:\...\26.2-Fabric 0.19.3.jar"
#   3) Run this script and paste/enter the jar path when prompted
#
# ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI, so non-ASCII
# characters without a BOM break parsing.

param([Parameter(Position = 0)][string]$Jar)

$here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Get-JavaMajor([string]$exe) {
    # `java -version` prints to stderr; probe with Continue so it is not treated as fatal.
    $old = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = (& $exe -version 2>&1 | Out-String)
    } catch {
        $out = ""
    } finally {
        $ErrorActionPreference = $old
    }
    if ($out -match 'version "(?:1\.)?(\d+)') { return [int]$Matches[1] }
    return 0
}

function Find-Patcher {
    foreach ($c in @((Join-Path $here "patcher-cli-*.jar"),
                     (Join-Path $here "..\patcher-cli\build\libs\patcher-cli-*.jar"))) {
        $f = Get-ChildItem $c -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($f) { return $f.FullName }
    }
    return $null
}

function Find-Java {
    $cands = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $cands.Add((Join-Path $env:JAVA_HOME "bin\java.exe")) }
    foreach ($b in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java",
                     "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu",
                     "$env:ProgramFiles\Amazon Corretto",
                     "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")) {
        if (Test-Path $b) {
            Get-ChildItem $b -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $cands.Add((Join-Path $_.FullName "bin\java.exe"))
            }
        }
    }
    $pathJava = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($pathJava) { $cands.Add($pathJava) }
    foreach ($c in $cands) {
        if (-not $c -or -not (Test-Path $c)) { continue }
        if ((Get-JavaMajor $c) -ge 21) { return $c }
    }
    return $null
}

$patcher = Find-Patcher
if (-not $patcher) {
    Write-Host "[!] patcher-cli-*.jar not found next to this script." -ForegroundColor Red
    exit 1
}

$java = Find-Java
if (-not $java) {
    Write-Host "[!] No Java 21+ found. Install a JDK 21 or newer, or set JAVA_HOME." -ForegroundColor Red
    $pathJava = (Get-Command java -ErrorAction SilentlyContinue).Source
    Write-Host ("    Current java on PATH: {0} (major {1})" -f $pathJava, (Get-JavaMajor $pathJava))
    exit 1
}

if (-not $Jar) {
    $Jar = Read-Host "Drag your Minecraft 26.2 client jar here (or type its full path), Enter"
}
$Jar = "$Jar".Trim().Trim('"')
if (-not (Test-Path $Jar)) {
    Write-Host "[!] Input jar not found: $Jar" -ForegroundColor Red
    exit 1
}
$out = [System.IO.Path]::ChangeExtension($Jar, $null).TrimEnd('.') + "_fork.jar"

Write-Host "Java   : $java"
Write-Host "Patcher: $patcher"
Write-Host "Input  : $Jar"
Write-Host "Output : $out"
Write-Host ""
& $java "-Dfarlands.wide=true" "-Dfarlands.continuity=true" "-Dfarlands.epoch=true" `
    -jar $patcher --in $Jar --out $out
Write-Host ""
Write-Host "Done. Use the fork jar as this version's jar; put farlands-g1-mod-*.jar in mods\." -ForegroundColor Green
