param(
  [string]$GrafanaUrl = 'http://127.0.0.1:3001',
  [string]$PrometheusUrl = 'http://127.0.0.1:9090',
  [string]$LokiUrl = 'http://127.0.0.1:3100',
  [string]$RequestId = '',
  [switch]$RequireBackendTargetUp
)

$ErrorActionPreference = 'Stop'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

function Get-Json($url) {
  $response = Invoke-WebRequest -UseBasicParsing -Method GET -Uri $url
  return $response.Content | ConvertFrom-Json
}

$grafanaHealth = Get-Json "$GrafanaUrl/api/health"
Assert ($grafanaHealth.database -eq 'ok') 'Grafana health endpoint is not healthy'

$datasources = @(Get-Json "$GrafanaUrl/api/datasources")
Assert (@($datasources | Where-Object { $_.uid -eq 'prometheus' -and $_.type -eq 'prometheus' }).Count -gt 0) 'Prometheus datasource is not provisioned'
Assert (@($datasources | Where-Object { $_.uid -eq 'loki' -and $_.type -eq 'loki' }).Count -gt 0) 'Loki datasource is not provisioned'

$prometheusReady = Invoke-WebRequest -UseBasicParsing -Method GET -Uri "$PrometheusUrl/-/ready"
Assert ($prometheusReady.StatusCode -eq 200) 'Prometheus is not ready'

$targets = Get-Json "$PrometheusUrl/api/v1/targets"
$backendTargets = @($targets.data.activeTargets | Where-Object {
  $_.labels.job -eq 'backend' -and $_.scrapeUrl -match '/actuator/prometheus$'
})
Assert ($backendTargets.Count -gt 0) 'Prometheus backend actuator target is missing'

if ($RequireBackendTargetUp) {
  Assert (@($backendTargets | Where-Object { $_.health -eq 'up' }).Count -gt 0) 'Prometheus backend actuator target is not up'
}

$lokiReady = Invoke-WebRequest -UseBasicParsing -Method GET -Uri "$LokiUrl/ready"
Assert ($lokiReady.StatusCode -eq 200) 'Loki is not ready'

if (-not [string]::IsNullOrWhiteSpace($RequestId)) {
  $query = [System.Uri]::EscapeDataString('{service="discord-api"} |= "' + $RequestId + '"')
  $lokiResult = Get-Json "$LokiUrl/loki/api/v1/query_range?query=$query&limit=20"
  Assert ($lokiResult.status -eq 'success') 'Loki query failed'
  Assert (@($lokiResult.data.result).Count -gt 0) "Loki did not return backend logs for request id $RequestId"

  $logLines = @($lokiResult.data.result | ForEach-Object { $_.values } | ForEach-Object { $_[1] })
  $traceId = $null
  foreach ($line in $logLines) {
    if ($line -match '"trace_id":"([a-f0-9]{32})"') {
      $traceId = $Matches[1]
      break
    }
  }

  Assert (-not [string]::IsNullOrWhiteSpace($traceId)) "Loki log for request id $RequestId did not include a trace id"

  $traceQuery = [System.Uri]::EscapeDataString('{service="discord-api"} |= "' + $traceId + '"')
  $traceResult = Get-Json "$LokiUrl/loki/api/v1/query_range?query=$traceQuery&limit=20"
  Assert ($traceResult.status -eq 'success') 'Loki trace id query failed'
  Assert (@($traceResult.data.result).Count -gt 0) "Loki did not return backend logs for trace id $traceId"
}

Write-Output 'OBSERVABILITY_STACK_SMOKE_PASS'
