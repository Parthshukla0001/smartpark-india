# =============================================================================
# SmartPark India - Cache Hit vs. Cache Miss Deep Experiment
# =============================================================================

Write-Host ">>> Running Experiment 1: CACHE ENABLED (Redis Hits)..." -ForegroundColor Cyan

# Check Redis memory before
$redisMemBefore = docker exec smartpark-redis redis-cli info memory | Select-String "used_memory_human:"

docker run --rm `
    --network smartpark-india_smartpark-net `
    -v "${PWD}/performance:/performance" `
    -e TARGET_URL=http://backend:8080 `
    grafana/k6 run `
    --summary-export /performance/results/baseline/cache_enabled_hits.json `
    /performance/k6/cache-hit-miss-benchmark.js

$redisMemAfter = docker exec smartpark-redis redis-cli info memory | Select-String "used_memory_human:"

Write-Host "`n>>> Toggling CACHE_ENABLED=false (Direct PostgreSQL Queries)..." -ForegroundColor Yellow
$env:CACHE_ENABLED = "false"
docker compose up -d backend

# Wait for backend to be healthy
Write-Host "Waiting for backend to become healthy..." -ForegroundColor Gray
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    $status = docker inspect --format='{{.State.Health.Status}}' smartpark-backend
    if ($status -eq "healthy") {
        $healthy = $true
        break
    }
}
if (-not $healthy) {
    Write-Error "Backend failed to become healthy after disabling cache!"
    exit 1
}

Write-Host ">>> Running Experiment 2: CACHE DISABLED (Database Direct Misses)..." -ForegroundColor Cyan

docker run --rm `
    --network smartpark-india_smartpark-net `
    -v "${PWD}/performance:/performance" `
    -e TARGET_URL=http://backend:8080 `
    grafana/k6 run `
    --summary-export /performance/results/baseline/cache_disabled_misses.json `
    /performance/k6/cache-hit-miss-benchmark.js

Write-Host "`n>>> Restoring CACHE_ENABLED=true..." -ForegroundColor Green
$env:CACHE_ENABLED = "true"
docker compose up -d backend

for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 2
    $status = docker inspect --format='{{.State.Health.Status}}' smartpark-backend
    if ($status -eq "healthy") { break }
}

Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host "Cache Experiment Complete! Parsing Comparative Metrics..." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

$hitJson = Get-Content "performance/results/baseline/cache_enabled_hits.json" -Raw | ConvertFrom-Json
$missJson = Get-Content "performance/results/baseline/cache_disabled_misses.json" -Raw | ConvertFrom-Json

$comparison = @(
    [PSCustomObject]@{
        Metric = "Total Requests"
        "Cache Enabled (Redis Hit)" = $hitJson.metrics.http_reqs.count
        "Cache Disabled (Postgres DB)" = $missJson.metrics.http_reqs.count
    },
    [PSCustomObject]@{
        Metric = "Throughput (RPS)"
        "Cache Enabled (Redis Hit)" = [math]::Round($hitJson.metrics.http_reqs.rate, 2)
        "Cache Disabled (Postgres DB)" = [math]::Round($missJson.metrics.http_reqs.rate, 2)
    },
    [PSCustomObject]@{
        Metric = "P50 Latency (ms)"
        "Cache Enabled (Redis Hit)" = [math]::Round($hitJson.metrics.http_req_duration.med, 2)
        "Cache Disabled (Postgres DB)" = [math]::Round($missJson.metrics.http_req_duration.med, 2)
    },
    [PSCustomObject]@{
        Metric = "P90 Latency (ms)"
        "Cache Enabled (Redis Hit)" = [math]::Round($hitJson.metrics.http_req_duration.'p(90)', 2)
        "Cache Disabled (Postgres DB)" = [math]::Round($missJson.metrics.http_req_duration.'p(90)', 2)
    },
    [PSCustomObject]@{
        Metric = "P95 Latency (ms)"
        "Cache Enabled (Redis Hit)" = [math]::Round($hitJson.metrics.http_req_duration.'p(95)', 2)
        "Cache Disabled (Postgres DB)" = [math]::Round($missJson.metrics.http_req_duration.'p(95)', 2)
    },
    [PSCustomObject]@{
        Metric = "Average Latency (ms)"
        "Cache Enabled (Redis Hit)" = [math]::Round($hitJson.metrics.http_req_duration.avg, 2)
        "Cache Disabled (Postgres DB)" = [math]::Round($missJson.metrics.http_req_duration.avg, 2)
    },
    [PSCustomObject]@{
        Metric = "Max Latency (ms)"
        "Cache Enabled (Redis Hit)" = [math]::Round($hitJson.metrics.http_req_duration.max, 2)
        "Cache Disabled (Postgres DB)" = [math]::Round($missJson.metrics.http_req_duration.max, 2)
    },
    [PSCustomObject]@{
        Metric = "Failed Requests (%)"
        "Cache Enabled (Redis Hit)" = [math]::Round($hitJson.metrics.http_req_failed.rate * 100, 2)
        "Cache Disabled (Postgres DB)" = [math]::Round($missJson.metrics.http_req_failed.rate * 100, 2)
    }
)

$comparison | Format-Table -AutoSize
Write-Host "Redis Memory Before: $redisMemBefore"
Write-Host "Redis Memory After:  $redisMemAfter"
