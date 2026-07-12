param(
    [string]$BaseUrl = "http://localhost:8088",
    [int]$Iterations = 20,
    [int]$Warmup = 2,
    [int]$TimeoutSec = 10,
    [string]$Account = "demo001@example.com",
    [string]$Password = "123456",
    [switch]$IncludeAmap
)

$ErrorActionPreference = "Stop"

function Invoke-HmApiPerformanceTest {
    param(
        [string]$BaseUrl = "http://localhost:8088",
        [int]$Iterations = 20,
        [int]$Warmup = 2,
        [int]$TimeoutSec = 10,
        [string]$Account = "demo001@example.com",
        [string]$Password = "123456",
        [switch]$IncludeAmap
    )

    $base = $BaseUrl.TrimEnd("/")
    $jsonHeaders = @{ "Content-Type" = "application/json"; "Accept" = "application/json" }
    $token = $null

    function Invoke-ApiRequest {
        param(
            [string]$Method,
            [string]$Path,
            [object]$Body,
            [bool]$Auth
        )

        $headers = @{} + $jsonHeaders
        if ($Auth -and $token) {
            $headers["Authorization"] = "Bearer $token"
        }
        $uri = "$base$Path"
        if ($null -eq $Body) {
            return Invoke-WebRequest -UseBasicParsing -Uri $uri -Method $Method -Headers $headers -TimeoutSec $TimeoutSec
        }
        return Invoke-WebRequest -UseBasicParsing -Uri $uri -Method $Method -Headers $headers -Body ($Body | ConvertTo-Json -Depth 8) -TimeoutSec $TimeoutSec
    }

    function Convert-ToApiBody {
        param([object]$Response)
        try {
            return $Response.Content | ConvertFrom-Json
        } catch {
            return $null
        }
    }

    try {
        $loginResponse = Invoke-ApiRequest -Method "POST" -Path "/auth/login" -Body @{ account = $Account; password = $Password } -Auth $false
        $loginBody = Convert-ToApiBody $loginResponse
        if ($loginBody -and $loginBody.code -eq 0) {
            $token = $loginBody.data.token
        }
    } catch {
        Write-Warning "登录性能测试账号失败，将跳过需要登录的接口：$($_.Exception.Message)"
    }

    $tests = @(
        @{ Name = "sports"; Method = "GET"; Path = "/sports"; Auth = $false },
        @{ Name = "equipment_list"; Method = "GET"; Path = "/items/2?sport=badminton&page=1&size=12"; Auth = $false },
        @{ Name = "venue_rank_items"; Method = "GET"; Path = "/items/1?sport=badminton&placeRank=1&limit=6"; Auth = $false },
        @{ Name = "venue_inventory"; Method = "GET"; Path = "/items/1/1/inventories"; Auth = $false },
        @{ Name = "blogs_recommend"; Method = "GET"; Path = "/blogs?channel=recommend&page=1&size=10"; Auth = $false },
        @{ Name = "social_activities"; Method = "GET"; Path = "/social/activities?sport=badminton&city=%E8%A5%BF%E5%AE%89&page=1&size=12"; Auth = $false },
        @{ Name = "agent_status"; Method = "GET"; Path = "/agent/status"; Auth = $false }
    )

    if ($token) {
        $tests += @(
            @{ Name = "auth_me"; Method = "GET"; Path = "/auth/me"; Auth = $true },
            @{ Name = "cart"; Method = "GET"; Path = "/cart"; Auth = $true },
            @{ Name = "orders_venue"; Method = "GET"; Path = "/orders/1"; Auth = $true },
            @{ Name = "orders_equipment"; Method = "GET"; Path = "/orders/2"; Auth = $true },
            @{ Name = "follow_feed"; Method = "GET"; Path = "/blogs/of/follow"; Auth = $true }
        )
    }

    if ($IncludeAmap) {
        $tests += @(
            @{ Name = "amap_places_nearby"; Method = "GET"; Path = "/places/nearby?sport=badminton&city=%E8%A5%BF%E5%AE%89&lng=108.9402&lat=34.3416&page=1&size=10"; Auth = $false },
            @{ Name = "amap_regeo"; Method = "GET"; Path = "/places/regeo?lng=108.9402&lat=34.3416"; Auth = $false }
        )
    }

    $results = New-Object System.Collections.Generic.List[object]

    foreach ($test in $tests) {
        for ($i = 0; $i -lt $Warmup; $i++) {
            try { [void](Invoke-ApiRequest -Method $test.Method -Path $test.Path -Body $null -Auth $test.Auth) } catch { }
        }

        $samples = New-Object System.Collections.Generic.List[double]
        $errors = 0
        for ($i = 0; $i -lt $Iterations; $i++) {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                $response = Invoke-ApiRequest -Method $test.Method -Path $test.Path -Body $null -Auth $test.Auth
                $body = Convert-ToApiBody $response
                if (-not $body -or $body.code -ne 0) {
                    $errors++
                }
            } catch {
                $errors++
            } finally {
                $sw.Stop()
                $samples.Add($sw.Elapsed.TotalMilliseconds)
            }
        }

        $ordered = @($samples | Sort-Object)
        $count = $ordered.Count
        $p95Index = [Math]::Max(0, [Math]::Min($count - 1, [Math]::Ceiling($count * 0.95) - 1))
        $avg = if ($count -gt 0) { ($samples | Measure-Object -Average).Average } else { 0 }
        $min = if ($count -gt 0) { $ordered[0] } else { 0 }
        $max = if ($count -gt 0) { $ordered[$count - 1] } else { 0 }
        $p95 = if ($count -gt 0) { $ordered[$p95Index] } else { 0 }

        $results.Add([pscustomobject]@{
            name = $test.Name
            method = $test.Method
            path = $test.Path
            auth = $test.Auth
            iterations = $Iterations
            errors = $errors
            minMs = [Math]::Round($min, 2)
            avgMs = [Math]::Round($avg, 2)
            p95Ms = [Math]::Round($p95, 2)
            maxMs = [Math]::Round($max, 2)
        })
    }

    $logsDir = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..")) "logs"
    New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $jsonPath = Join-Path $logsDir "api-performance-$timestamp.json"
    $results | ConvertTo-Json -Depth 6 | Set-Content -Path $jsonPath -Encoding UTF8

    $results | Format-Table name, iterations, errors, minMs, avgMs, p95Ms, maxMs -AutoSize
    Write-Host "Result saved: $jsonPath"
    return $results
}

Invoke-HmApiPerformanceTest `
    -BaseUrl $BaseUrl `
    -Iterations $Iterations `
    -Warmup $Warmup `
    -TimeoutSec $TimeoutSec `
    -Account $Account `
    -Password $Password `
    -IncludeAmap:$IncludeAmap
