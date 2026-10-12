param(
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$version = (Get-Content -LiteralPath "$root\VERSION" -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw "bad VERSION: '$version'" }

if (-not $SkipBuild) {
    & "$root\gradlew.bat" :mod:build :patcher-cli:build --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "gradle build failed (exit $LASTEXITCODE)" }
}

$modJar = "$root\mod\build\libs\farlands-g1-mod-$version.jar"
$cliJar = "$root\patcher-cli\build\libs\patcher-cli-$version.jar"
foreach ($j in @($modJar, $cliJar)) {
    if (-not (Test-Path -LiteralPath $j)) { throw "missing build product: $j (version changed without rebuild?)" }
}

$stage = "$root\build\bundle\FarLands-G1-$version"
if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path "$stage\docs" -Force | Out-Null

Copy-Item -LiteralPath $modJar, $cliJar, "$root\LICENSE.txt", "$root\README.md", "$root\README.zh-CN.md", "$root\tools\patch.bat", "$root\tools\patch.ps1" -Destination $stage
Copy-Item -Path "$root\docs\*.md" -Destination "$stage\docs"

$latin1 = [Text.Encoding]::GetEncoding(28591)
$install = $latin1.GetString([IO.File]::ReadAllBytes("$PSScriptRoot\bundle\INSTALL.md")).Replace('@VERSION@', $version)
[IO.File]::WriteAllBytes("$stage\INSTALL.md", $latin1.GetBytes($install))

$zip = "$root\build\bundle\FarLands-G1-$version.zip"
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path $stage -DestinationPath $zip

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zipObj = [IO.Compression.ZipFile]::OpenRead($zip)
$count = $zipObj.Entries.Count
$zipObj.Dispose()
$sha = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash
Write-Host "bundle: $zip"
Write-Host "entries: $count"
Write-Host "sha256: $sha"
