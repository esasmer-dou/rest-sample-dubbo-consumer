param(
    [string]$Network = "rwrite-audit-net",
    [string]$BaseUrl = "http://consumer:8080",
    [int]$VirtualUsers = 16,
    [int]$Repeats = 3,
    [string]$Duration = "30s",
    [double]$MaxRunP99Ms = 200,
    [double]$MaxP99SpreadMs = 75,
    [double]$MaxFailurePercent = 0.10,
    [double]$MinAverageRps = 0,
    [string]$K6Image = "grafana/k6:0.55.0",
    [string]$OutputDirectory = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($Repeats -lt 3) {
    throw "Repeats must be at least 3 for a tail-latency gate."
}

$scriptDirectory = (Resolve-Path $PSScriptRoot).Path
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $runId = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutputDirectory = Join-Path ([System.IO.Path]::GetTempPath()) "dubbo-write-gate-$runId"
}
$resultDirectory = (New-Item -ItemType Directory -Force -Path $OutputDirectory).FullName

$rows = @()
for ($run = 1; $run -le $Repeats; $run++) {
    $name = "c$VirtualUsers-r$run"
    $logPath = Join-Path $resultDirectory "$name.log"
    Write-Host "Running $name ($Duration)"
    docker run --rm --network $Network `
        --mount "type=bind,source=$scriptDirectory,target=/scripts,readonly" `
        --mount "type=bind,source=$resultDirectory,target=/results" `
        -e "BASE_URL=$BaseUrl" `
        -e "MODE=mixed" `
        -e "VUS=$VirtualUsers" `
        -e "DURATION=$Duration" `
        -e "MAX_ERROR_RATE=1" `
        -e "P99_MS=60000" `
        $K6Image run --quiet --summary-export "/results/$name.json" /scripts/dubbo-write-gate.js `
        *> $logPath
    if ($LASTEXITCODE -ne 0) {
        throw "k6 failed for $name"
    }

    $summary = Get-Content (Join-Path $resultDirectory "$name.json") -Raw | ConvertFrom-Json
    $metrics = $summary.metrics
    $failureMetric = $metrics.PSObject.Properties["write_failures"]
    $failureCount = if ($null -eq $failureMetric) { 0 } else { [int]$failureMetric.Value.count }
    $rows += [pscustomobject]@{
        Run = $name
        Requests = [int]$metrics.http_reqs.count
        Rps = [double]$metrics.http_reqs.rate
        AverageMs = [double]$metrics.http_req_duration.avg
        P99Ms = [double]$metrics.http_req_duration.'p(99)'
        Failures = $failureCount
    }
    Start-Sleep -Seconds 5
}

$totalRequests = ($rows | Measure-Object Requests -Sum).Sum
$totalFailures = ($rows | Measure-Object Failures -Sum).Sum
$averageRps = ($rows | Measure-Object Rps -Average).Average
$maxP99 = ($rows | Measure-Object P99Ms -Maximum).Maximum
$minP99 = ($rows | Measure-Object P99Ms -Minimum).Minimum
$p99Spread = $maxP99 - $minP99
$failurePercent = if ($totalRequests -eq 0) { 100.0 } else { 100.0 * $totalFailures / $totalRequests }

$rows | Select-Object `
    Run,
    Requests,
    @{Name = "RPS"; Expression = { [math]::Round($_.Rps, 2) }},
    @{Name = "AvgMs"; Expression = { [math]::Round($_.AverageMs, 2) }},
    @{Name = "P99Ms"; Expression = { [math]::Round($_.P99Ms, 2) }},
    Failures | Format-Table -AutoSize

Write-Host ("Average RPS: {0:N2}" -f $averageRps)
Write-Host ("Maximum p99: {0:N2} ms" -f $maxP99)
Write-Host ("p99 spread: {0:N2} ms" -f $p99Spread)
Write-Host ("Failure rate: {0:N3}%" -f $failurePercent)
Write-Host "Results: $resultDirectory"

$violations = @()
if ($maxP99 -gt $MaxRunP99Ms) {
    $violations += "maximum p99 $([math]::Round($maxP99, 2)) ms exceeds $MaxRunP99Ms ms"
}
if ($p99Spread -gt $MaxP99SpreadMs) {
    $violations += "p99 spread $([math]::Round($p99Spread, 2)) ms exceeds $MaxP99SpreadMs ms"
}
if ($failurePercent -gt $MaxFailurePercent) {
    $violations += "failure rate $([math]::Round($failurePercent, 3))% exceeds $MaxFailurePercent%"
}
if ($averageRps -lt $MinAverageRps) {
    $violations += "average RPS $([math]::Round($averageRps, 2)) is below $MinAverageRps"
}

if ($violations.Count -gt 0) {
    throw "Dubbo write gate failed: $($violations -join '; ')"
}

Write-Host "Dubbo write gate passed."
