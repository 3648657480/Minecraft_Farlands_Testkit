# Trustworthy Minecraft source generator (Mojang official mappings).
#
# WHY: the local `D:\Minecraft\.minecraft\versions\*-src` folders are JD-Core
# output and are NOT trustworthy - e.g. `PerlinNoise.wrap` was mis-decompiled to
# `return x` (no-op) while the bytecode and Vineflower both show the real modulo
# patch. Never calibrate against `*-src`.
#
# This script produces authoritative named sources for a given version:
#   Mojang manifest -> client jar + official ProGuard mappings
#   -> SpecialSource remap -> Vineflower decompile.
#
# Usage:
#   tools\gen-mc-sources.ps1 -Version 1.18.2
#
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$WorkDir = "$env:TEMP\mc-src-$Version",
    [string]$OutDir  = "$env:TEMP\mc-src-$Version\src"
)

$ErrorActionPreference = "Stop"
$java = "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe"
$vineflower = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.vineflower\vineflower" -Recurse -Filter "vineflower-*.jar" | Select-Object -First 1).FullName
if (-not $vineflower) { throw "Vineflower jar not found in gradle caches (run a loom genSources once)." }
$specialsource = "$WorkDir\SpecialSource-shaded.jar"
New-Item -ItemType Directory -Force -Path $WorkDir, $OutDir | Out-Null

Write-Host "[gen-mc-sources] fetching version manifest..."
$man = "$WorkDir\manifest.json"
Invoke-WebRequest "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json" -OutFile $man -UseBasicParsing
$v = (Get-Content $man -Raw | ConvertFrom-Json).versions | Where-Object { $_.id -eq $Version } | Select-Object -First 1
if (-not $v) { throw "version '$Version' not found in manifest" }

$vj = "$WorkDir\$Version.json"
Invoke-WebRequest $v.url -OutFile $vj -UseBasicParsing
$vd = Get-Content $vj -Raw | ConvertFrom-Json

$client = "$WorkDir\$Version-client.jar"
if (-not (Test-Path $client)) { Invoke-WebRequest $vd.downloads.client.url -OutFile $client -UseBasicParsing }
$map = "$WorkDir\$Version-client-mappings.txt"
if (-not (Test-Path $map)) { Invoke-WebRequest $vd.downloads.client_mappings.url -OutFile $map -UseBasicParsing }

if (-not (Test-Path $specialsource)) {
    Invoke-WebRequest "https://repo1.maven.org/maven2/net/md-5/SpecialSource/1.11.4/SpecialSource-1.11.4-shaded.jar" -OutFile $specialsource -UseBasicParsing
}

$named = "$WorkDir\$Version-named.jar"
Write-Host "[gen-mc-sources] remapping to Mojang names (SpecialSource)..."
& $java -jar $specialsource --in-jar $client --out-jar $named --srg-in $map | Out-Null

Write-Host "[gen-mc-sources] decompiling (Vineflower)... this takes a few minutes"
& $java -jar $vineflower -dgs=1 $named $OutDir | Out-Null

Write-Host "[gen-mc-sources] done -> $OutDir"
Write-Host "[gen-mc-sources] spot-check: PerlinNoise.wrap should be 'x - Mth.lfloor(x / 3.3554432E7 + 0.5) * 3.3554432E7'"
