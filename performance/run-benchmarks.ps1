# =============================================================================
# SmartPark India - Automated Benchmark Runner
# Phase 14: Performance & Load Testing
# =============================================================================

param (
    [string]$TargetDir = "performance/results/baseline",
    [string]$TargetUrl = "http://backend:8080"
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "SmartPark India - Phase 14 Performance & Load Testing" -ForegroundColor Cyan
Write-Host "Target Environment: Containerized ($TargetUrl)" -ForegroundColor Cyan
Write-Host "Output Directory: $TargetDir" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Ensure output directory exists
if (-not (Test-Path $TargetDir)) {
    New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null
}

function Get-ActuatorSnapshot {
    try {
        $adminAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"admin@smartpark.in","password":"Password123!"}' -TimeoutSec 5
        $headers = @{ Authorization = "Bearer " + $adminAuth.accessToken }
        
        $metrics = @{
            "jvm_memory_used_mb" = [math]::Round(((Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/jvm.memory.used" -Headers $headers).measurements[0].value / 1MB), 2)
            "hikaricp_active" = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/hikaricp.connections.active" -Headers $headers).measurements[0].value
            "hikaricp_idle" = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/hikaricp.connections.idle" -Headers $headers).measurements[0].value
            "bookings_created" = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/smartpark.bookings.created" -Headers $headers).measurements[0].value
            "bookings_conflicts" = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/smartpark.bookings.conflicts" -Headers $headers).measurements[0].value
            "rate_limit_rejected" = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/smartpark.rate_limit.rejected" -Headers $headers).measurements[0].value
        }
        return $metrics
    } catch {
        return @{ "error" = $_.Exception.Message }
    }
}

function Run-K6Test {
    param (
        [string]$ScriptName,
        [string]$OutputFileBase
    )

    Write-Host "`n----------------------------------------------------------" -ForegroundColor Yellow
    Write-Host "Running Benchmark: $ScriptName" -ForegroundColor Yellow
    Write-Host "----------------------------------------------------------" -ForegroundColor Yellow

    $preMetrics = Get-ActuatorSnapshot
    $preMetricsJson = $preMetrics | ConvertTo-Json -Compress
    Write-Host "Pre-test Metrics: $preMetricsJson" -ForegroundColor Gray

    $scriptPath = "/performance/k6/$ScriptName"
    $jsonOut = "/$TargetDir/${OutputFileBase}.json"
    $summaryOut = "$TargetDir/${OutputFileBase}_summary.txt"

    # Run k6 container attached to compose network
    docker run --rm `
        --network smartpark-india_smartpark-net `
        -v "${PWD}/performance:/performance" `
        -e TARGET_URL=$TargetUrl `
        grafana/k6 run `
        --summary-export $jsonOut `
        $scriptPath | Tee-Object -FilePath $summaryOut

    $postMetrics = Get-ActuatorSnapshot
    $postMetricsJson = $postMetrics | ConvertTo-Json -Compress
    Write-Host "Post-test Metrics: $postMetricsJson" -ForegroundColor Gray

    # Save snapshot metadata
    $meta = @{
        "test" = $ScriptName
        "timestamp" = (Get-Date).ToString("o")
        "pre_metrics" = $preMetrics
        "post_metrics" = $postMetrics
    }
    $meta | ConvertTo-Json -Depth 5 | Out-File "$TargetDir/${OutputFileBase}_meta.json" -Encoding utf8
}

# 1. Warm-up Phase (priming JIT, thread pool, connection pool, Redis)
Write-Host "`n>>> Step 0: Executing Warm-up Phase (10s)..." -ForegroundColor Green
docker run --rm `
    --network smartpark-india_smartpark-net `
    -v "${PWD}/performance:/performance" `
    -e TARGET_URL=$TargetUrl `
    grafana/k6 run `
    --vus 5 --duration 10s `
    /performance/k6/parking.js > $null

Write-Host "Warm-up complete. System ready for measurement." -ForegroundColor Green

# 2. Concurrency Race Test
Run-K6Test -ScriptName "booking-concurrency.js" -OutputFileBase "concurrency_race"

# 3. Cache Benchmark
Run-K6Test -ScriptName "cache-comparison.js" -OutputFileBase "cache_comparison"

# 4. Authentication Benchmark
Run-K6Test -ScriptName "auth.js" -OutputFileBase "auth_load"

# 5. Parking Discovery Benchmark
Run-K6Test -ScriptName "parking.js" -OutputFileBase "parking_discovery"

# 6. Booking Lifecycle Benchmark
Run-K6Test -ScriptName "booking.js" -OutputFileBase "booking_lifecycle"

# 7. Mixed Operational Workload Benchmark (Staged 10 -> 25 -> 50 -> 100 VUs)
Run-K6Test -ScriptName "mixed-workload.js" -OutputFileBase "mixed_workload_staged"

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "All Benchmark Scenarios Executed Successfully!" -ForegroundColor Cyan
Write-Host "Raw Results Stored in: $TargetDir" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
