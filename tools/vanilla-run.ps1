param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [int]$DelaySec = 30,
    [int]$SettleSec = 60,
    [int]$TimeoutMin = 20
)

# F0-2 reference rig runner: starts the no-mod/no-patch dev server, forces the
# fixed chunk set via RCON (forceload), settles, saves, stops, then moves the
# world into the evidence directory. Always single-threaded (deterministic).
$root = "C:\Project-G1"
$log = "C:\Users\EASON\AppData\Local\Temp\opencode\vanilla-$Tag.log"
$RconPort = 25575
$RconPassword = "farlands"

Remove-Item $log -ErrorAction SilentlyContinue
Remove-Item "$root\vanilla-rig\run\world" -Recurse -Force -ErrorAction SilentlyContinue

function Read-Exact {
    param([System.Net.Sockets.NetworkStream]$Stream, [int]$Count)
    $buf = New-Object byte[] $Count
    $read = 0
    while ($read -lt $Count) {
        $n = $Stream.Read($buf, $read, $Count - $read)
        if ($n -le 0) { return $null }
        $read += $n
    }
    return $buf
}

function Send-RconPacket {
    param([System.Net.Sockets.NetworkStream]$Stream, [int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    $len = 4 + 4 + $bodyBytes.Length + 2
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    $bw.Write([int]$len)
    $bw.Write([int]$Id)
    $bw.Write([int]$Type)
    $bw.Write($bodyBytes)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Flush()
    $bytes = $ms.ToArray()
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()
}

function Read-RconPacket {
    param([System.Net.Sockets.NetworkStream]$Stream)
    $lenBytes = Read-Exact $Stream 4
    if ($null -eq $lenBytes) { return $null }
    $len = [System.BitConverter]::ToInt32($lenBytes, 0)
    if ($len -lt 10) { return $null }
    $payload = Read-Exact $Stream $len
    if ($null -eq $payload) { return $null }
    $id = [System.BitConverter]::ToInt32($payload, 0)
    $type = [System.BitConverter]::ToInt32($payload, 4)
    $body = [System.Text.Encoding]::ASCII.GetString($payload, 8, $len - 10)
    return @{ Id = $id; Type = $type; Body = $body }
}

function Invoke-RconCommand {
    param([string]$Command)
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect("127.0.0.1", $RconPort)
    $stream = $client.GetStream()
    Send-RconPacket $stream 1 3 $RconPassword
    $auth = Read-RconPacket $stream
    if ($null -eq $auth -or $auth.Id -eq -1) {
        $client.Close()
        return "RCON AUTH FAILED"
    }
    Send-RconPacket $stream 2 2 $Command
    $resp = Read-RconPacket $stream
    $client.Close()
    if ($null -eq $resp) { return "" }
    return $resp.Body
}

$cmd = "cd /d $root && .\gradlew.bat -Dmax.bg.threads=1 :vanilla-rig:runServer --no-daemon -q > `"$log`" 2>&1"
$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c $cmd" -WindowStyle Hidden -PassThru

$deadline = (Get-Date).AddMinutes($TimeoutMin)
$started = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    if (Test-Path $log) {
        if (Select-String -Path $log -Pattern "Done \(" -Quiet) { $started = $true; break }
        if (Select-String -Path $log -Pattern "FAILED|Exception in thread" -Quiet) { break }
    }
}

$results = New-Object System.Collections.Generic.List[string]
if ($started) {
    Start-Sleep -Seconds $DelaySec
    foreach ($point in @("8 8", "1000 1000", "1000008 1000008", "10000008 10000008", "29000008 29000008")) {
        $r = Invoke-RconCommand "forceload add $point $point"
        $results.Add("forceload $point -> $r")
    }
    Start-Sleep -Seconds $SettleSec
    $results.Add("save -> " + (Invoke-RconCommand "save-all flush"))
    $results.Add("stop -> " + (Invoke-RconCommand "stop"))
}

if ($started) {
    $waited = 0
    while (-not $proc.HasExited -and $waited -lt 120) { Start-Sleep -Seconds 3; $waited += 3 }
}
if (-not $proc.HasExited) { taskkill /PID $proc.Id /T /F 2>$null | Out-Null }
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.StartTime -gt (Get-Date).AddMinutes(-$TimeoutMin) } | Stop-Process -Force -ErrorAction SilentlyContinue

"STARTED: $started"
$results | ForEach-Object { $_ }
if (Test-Path "$root\vanilla-rig\run\world") {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    if (Test-Path "$OutDir\world") { Remove-Item "$OutDir\world" -Recurse -Force }
    Move-Item "$root\vanilla-rig\run\world" "$OutDir\world"
    "WORLD: $OutDir\world"
} else {
    "NO WORLD PRODUCED"
}
"LOG: $log"
if (Test-Path $log) { Get-Content $log -Tail 8 }
