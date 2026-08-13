$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'routing-policy.ps1')

$utcDate = [datetime]::SpecifyKind([datetime]'2026-01-02T00:00:00', [DateTimeKind]::Utc)

if ((Get-ShardId -ChatRoomId 'room-1' -EventDate $utcDate -ShardCount 1) -ne 0) {
    throw 'initial shard must be 0'
}

$shard = Get-ShardId -ChatRoomId 'room-1' -EventDate $utcDate -ShardCount 16
if ($shard -notin 0..15) {
    throw 'shard must be within range'
}

$repeat = Get-ShardId -ChatRoomId 'room-1' -EventDate $utcDate -ShardCount 16
if ($shard -ne $repeat) {
    throw 'routing must be deterministic'
}

try {
    Get-ShardId -ChatRoomId '' -EventDate $utcDate -ShardCount 16 | Out-Null
    throw 'empty room accepted'
}
catch {
    if ($_.Exception.Message -eq 'empty room accepted') {
        throw
    }
    if ($_.Exception -isnot [ArgumentException] -and $_.Exception.InnerException -isnot [ArgumentException]) {
        throw 'empty room did not throw ArgumentException'
    }
}

foreach ($invalid in @(
    @{ Name = 'non-UTC date'; Room = 'room-1'; Date = [datetime]::SpecifyKind([datetime]'2026-01-02', [DateTimeKind]::Local); Count = 16 },
    @{ Name = 'zero shard count'; Room = 'room-1'; Date = $utcDate; Count = 0 },
    @{ Name = 'negative shard count'; Room = 'room-1'; Date = $utcDate; Count = -1 }
)) {
    try {
        Get-ShardId -ChatRoomId $invalid.Room -EventDate $invalid.Date -ShardCount $invalid.Count | Out-Null
        throw "$($invalid.Name) accepted"
    }
    catch {
        if ($_.Exception.Message -eq "$($invalid.Name) accepted") {
            throw
        }
        if ($_.Exception -isnot [ArgumentException] -and $_.Exception.InnerException -isnot [ArgumentException]) {
            throw "$($invalid.Name) did not throw ArgumentException"
        }
    }
}

$writeRoute = Get-ReadRoute -RequestType write -LagSeconds 99
if ($writeRoute.target -ne 'primary' -or $writeRoute.fallback_reason) { throw 'write must remain primary without fallback' }
$rawRoute = Get-ReadRoute -RequestType read-after-write -LagSeconds 99
if ($rawRoute.target -ne 'primary') { throw 'read-after-write must remain primary' }
$replicaRoute = Get-ReadRoute -RequestType history -LagSeconds 0
if ($replicaRoute.target -ne 'replica') { throw 'healthy history must use replica' }
$fallbackRoute = Get-ReadRoute -RequestType search -LagSeconds 2.001
if ($fallbackRoute.target -ne 'primary' -or $fallbackRoute.fallback_reason -ne 'replica_lag_gt_2s') { throw 'lag > 2s must fallback to primary' }
$limitedRoute = Get-ReadRoute -RequestType history -LagSeconds 30.001
if ($limitedRoute.target -ne 'primary' -or $limitedRoute.warning -ne 'history_page_limited') { throw 'lag > 30s must limit history' }

try { Get-ReadRoute -RequestType history -LagSeconds -1 | Out-Null; throw 'negative lag accepted' } catch { if ($_.Exception.Message -eq 'negative lag accepted') { throw } }

Write-Output 'C4_ROUTING_TEST_PASS'
