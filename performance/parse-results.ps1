$results = Get-ChildItem performance/results/baseline/*.json -Exclude *meta* | ForEach-Object {
    $raw = Get-Content $_.FullName -Raw | ConvertFrom-Json
    $m = $raw.metrics
    $dur = $m.http_req_duration
    $reqs = $m.http_reqs
    $fail = $m.http_req_failed

    [PSCustomObject]@{
        Benchmark = $_.BaseName
        Requests  = if ($reqs) { $reqs.count } else { 0 }
        RPS       = if ($reqs) { [math]::Round($reqs.rate, 2) } else { 0 }
        Avg_ms    = if ($dur) { [math]::Round($dur.avg, 2) } else { 0 }
        Med_ms    = if ($dur) { [math]::Round($dur.med, 2) } else { 0 }
        P90_ms    = if ($dur) { [math]::Round($dur.'p(90)', 2) } else { 0 }
        P95_ms    = if ($dur) { [math]::Round($dur.'p(95)', 2) } else { 0 }
        Max_ms    = if ($dur) { [math]::Round($dur.max, 2) } else { 0 }
        Fail_Pct  = if ($fail) { [math]::Round($fail.rate * 100, 2) } else { 0 }
    }
}
$results | Format-Table -AutoSize
