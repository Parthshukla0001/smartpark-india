$adminAuth = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"email":"admin@smartpark.in","password":"Password123!"}'
$headers = @{ Authorization = "Bearer " + $adminAuth.accessToken }

$hActive = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/hikaricp.connections.active" -Headers $headers).measurements[0].value
$hIdle = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/hikaricp.connections.idle" -Headers $headers).measurements[0].value
$hMax = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/hikaricp.connections.max" -Headers $headers).measurements[0].value
$bCreated = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/smartpark.bookings.created" -Headers $headers).measurements[0].value
$bConflicts = (Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/smartpark.bookings.conflicts" -Headers $headers).measurements[0].value
$memUsed = [math]::Round(((Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/jvm.memory.used" -Headers $headers).measurements[0].value / 1MB), 2)
$memMax = [math]::Round(((Invoke-RestMethod -Uri "http://localhost:8080/actuator/metrics/jvm.memory.max" -Headers $headers).measurements[0].value / 1MB), 2)

Write-Host "=== SmartPark Live Metrics Snapshot ==="
Write-Host "HikariCP Active Connections: $hActive"
Write-Host "HikariCP Idle Connections:   $hIdle"
Write-Host "HikariCP Max Connections:    $hMax"
Write-Host "Total Bookings Created:      $bCreated"
Write-Host "Total Booking Conflicts:     $bConflicts"
Write-Host "JVM Memory Used:             $memUsed MB / $memMax MB"
