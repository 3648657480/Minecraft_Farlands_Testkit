# FarLands G1 打补丁助手
# 作用：自动找一个 Java 21+，用正确的路径运行 patcher-cli，生成 fork jar。
# 用法（任选）：
#   1) 把 Minecraft 26.2 客户端 jar 拖到 patch.bat 上
#   2) powershell -ExecutionPolicy Bypass -File patch.ps1 "D:\...\26.2-Fabric 0.19.3.jar"
#   3) 直接运行本脚本，按提示拖入/输入 jar 路径
#
# 说明：patcher 是 Java 21 编译的；系统默认 java 若是 1.8 会报
#       UnsupportedClassVersionError，所以这里主动挑 Java 21+。

param([Parameter(Position = 0)][string]$Jar)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Find-Patcher {
    $cands = @(
        (Join-Path $here "patcher-cli-*.jar"),
        (Join-Path $here "..\patcher-cli\build\libs\patcher-cli-*.jar")
    )
    foreach ($c in $cands) {
        $f = Get-ChildItem $c -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($f) { return $f.FullName }
    }
    return $null
}

function Find-Java {
    $cands = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $cands.Add((Join-Path $env:JAVA_HOME "bin\java.exe")) }
    $bases = @(
        "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu",
        "$env:ProgramFiles\Amazon Corretto", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium"
    )
    foreach ($b in $bases) {
        if (Test-Path $b) {
            Get-ChildItem $b -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $cands.Add((Join-Path $_.FullName "bin\java.exe"))
            }
        }
    }
    $cands.Add((Get-Command java -ErrorAction SilentlyContinue).Source)  # PATH 兜底
    foreach ($c in $cands) {
        if (-not $c -or -not (Test-Path $c)) { continue }
        try { $first = (& $c -version 2>&1 | Select-Object -First 1) } catch { continue }
        if ($first -match 'version "(?:1\.)?(\d+)') {
            if ([int]$Matches[1] -ge 21) { return $c }
        }
    }
    return $null
}

$patcher = Find-Patcher
if (-not $patcher) {
    Write-Host "[!] 找不到 patcher-cli-*.jar。请把本脚本与 patcher-cli-*.jar 放在同一目录。" -ForegroundColor Red
    exit 1
}
$java = Find-Java
if (-not $java) {
    Write-Host "[!] 找不到 Java 21+。请安装 JDK 21 或更新版本，或设置 JAVA_HOME。" -ForegroundColor Red
    Write-Host "    当前 PATH 上的 java：" -NoNewline; (Get-Command java -ErrorAction SilentlyContinue).Source
    & java -version 2>&1 | Select-Object -First 1
    exit 1
}

if (-not $Jar) {
    $Jar = Read-Host "把 Minecraft 26.2 客户端 jar 拖进来（或输入完整路径），回车"
}
$Jar = "$Jar".Trim().Trim('"')
if (-not (Test-Path $Jar)) {
    Write-Host "[!] 找不到输入 jar：$Jar" -ForegroundColor Red
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
Write-Host "完成：把 `"$out`" 作为该版本的 jar；farlands-g1-mod-*.jar 放进 mods/。" -ForegroundColor Green
