param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [Parameter(Mandatory = $true)][string]$TestGen,
    [ValidateSet("mod", "vanilla")][string]$Rig = "mod",
    [string]$Wide = "true",
    [string]$Continuity = "true",
    [string]$Epoch = "true",
    [int]$Settle = 200,
    [int]$Delay = 0,
    [int]$BgThreads = 0,
    [string]$Extra = "",
    [string]$Dim = "",
    [int]$TimeoutMin = 15
)

# F0 experiment rig: generates a fixed chunk set headlessly, saves the world,
# halts the server, then moves the world into the evidence directory.
# The world is deleted first so the run starts from a clean generation.
# Rig=mod runs the patched dev server (subject); Rig=vanilla runs the
# unpatched harness server (reference). Both use the same testgen protocol.
$root = "C:\Project-G1"
if ($Rig -eq "vanilla") {
    $runDir = "$root\vanilla-rig\run"
    $task = ":vanilla-rig:runServer"
} else {
    $runDir = "$root\mod\run"
    $task = ":mod:runServer"
}
$log = "C:\Users\EASON\AppData\Local\Temp\opencode\exp-$Tag.log"
Remove-Item $log -ErrorAction SilentlyContinue
Remove-Item "$runDir\world" -Recurse -Force -ErrorAction SilentlyContinue

$bgArg = ""
if ($BgThreads -gt 0) { $bgArg = " -Dmax.bg.threads=$BgThreads" }
$dimArg = ""
if ($Dim -ne "") { $dimArg = " -Dfarlands.testgen.dim=$Dim" }
$cmd = "cd /d $root && .\gradlew.bat -Dfarlands.wide=$Wide -Dfarlands.continuity=$Continuity -Dfarlands.epoch=$Epoch -Dfarlands.testgen=$TestGen -Dfarlands.testgen.stop=true -Dfarlands.testgen.settle=$Settle -Dfarlands.testgen.delay=$Delay$bgArg$dimArg $Extra $task --no-daemon -q > `"$log`" 2>&1"
$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c $cmd" -WindowStyle Hidden -PassThru

$deadline = (Get-Date).AddMinutes($TimeoutMin)
$done = $false
$crash = $null
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    if (Test-Path $log) {
        if (Select-String -Path $log -Pattern "FarLands-Test\] DONE saved" -Quiet) { $done = $true; break }
        $m = Select-String -Path $log -Pattern "gen FAILED|Requested chunk unavailable|Exception generating" | Select-Object -First 1
        if ($m) { $crash = $m.Line; break }
    }
}

if ($done) {
    $waited = 0
    while (-not $proc.HasExited -and $waited -lt 90) { Start-Sleep -Seconds 3; $waited += 3 }
}
if (-not $proc.HasExited) { taskkill /PID $proc.Id /T /F 2>$null | Out-Null }
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.StartTime -gt (Get-Date).AddMinutes(-$TimeoutMin) } | Stop-Process -Force -ErrorAction SilentlyContinue

"FLAGS($Tag): rig=$Rig wide=$Wide continuity=$Continuity epoch=$Epoch"
"DONE: $done"
if ($crash) { "CRASH: $crash" }
if (Test-Path "$runDir\world") {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    if (Test-Path "$OutDir\world") { Remove-Item "$OutDir\world" -Recurse -Force }
    Move-Item "$runDir\world" "$OutDir\world"
    "WORLD: $OutDir\world"
} else {
    "NO WORLD PRODUCED"
}
"LOG: $log"
if (Test-Path $log) { Get-Content $log -Tail 10 }
