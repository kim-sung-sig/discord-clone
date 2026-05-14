param(
  [string]$BaseUrl = "http://127.0.0.1:8080",
  [string]$RequestId = "t17-runtime-correlation",
  [Parameter(Mandatory = $true)]
  [string]$LogFile
)

$ErrorActionPreference = "Stop"

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

$headers = @{ "X-Request-Id" = $RequestId }
$response = Invoke-WebRequest -UseBasicParsing -Method GET -Uri "$BaseUrl/api/premium/catalog" -Headers $headers

Assert ($response.Headers["X-Request-Id"] -eq $RequestId) "request id was not echoed"

$deadline = (Get-Date).AddSeconds(10)
$needle = '"request_id":"' + $RequestId + '"'
$matched = $false
while ((Get-Date) -lt $deadline) {
  if ((Test-Path -LiteralPath $LogFile) -and (Select-String -LiteralPath $LogFile -Pattern $needle -SimpleMatch -Quiet)) {
    $matched = $true
    break
  }
  Start-Sleep -Milliseconds 250
}

Assert $matched "request id was not found in structured backend logs"

$logText = Get-Content -LiteralPath $LogFile -Raw
Assert ($logText -notmatch "correct horse battery staple") "sensitive password appeared in logs"
Assert ($logText -notmatch "Bearer\s+") "authorization bearer token appeared in logs"

Write-Output "OBSERVABILITY_SMOKE_PASS requestId=$RequestId"
