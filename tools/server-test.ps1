param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [string]$Wide = "true",
    [string]$Continuity = "true",
    [string]$Epoch = "true",
    [Parameter(Mandatory = $true)][string]$TestGen,
    [int]$TimeoutMin = 12
)

$root = "C:\Project-G1"
$log = "C:\Users\EASON\AppData\Local\Temp\opencode\server-$Tag.log"
Remove-Item $log -ErrorAction SilentlyContinue

$cmd = "cd /d $root && .\gradlew.bat -Dfarlands.wide=$Wide -Dfarlands.continuity=$Continuity -Dfarlands.epoch=$Epoch -Dfarlands.testgen=$TestGen :mod:runServer --no-daemon -q > `"$log`" 2>&1"
$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c $cmd" -WindowStyle Hidden -PassThru

$deadline = (Get-Date).AddMinutes($TimeoutMin)
$result = $null
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    if (Test-Path $log) {
        $m = Select-String -Path $log -Pattern "FarLands-Test" | Select-Object -First 1
        if ($m) { $result = $m.Line; break }
        $crash = Select-String -Path $log -Pattern "Requested chunk unavailable|acquireGeneration" | Select-Object -First 1
        if ($crash) { $result = "CRASH: " + $crash.Line; break }
    }
}

taskkill /PID $proc.Id /T /F 2>$null | Out-Null
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.StartTime -gt (Get-Date).AddMinutes(-$TimeoutMin) } | Stop-Process -Force -ErrorAction SilentlyContinue

"RESULT($Tag): $result"
"LOG: $log"
if (-not $result) {
    if (Test-Path $log) { Get-Content $log -Tail 8 } else { "no log produced" }
}
