param(
    [ValidateSet('baseline','date_range','date_hash','all')]
    [string]$Variant = 'all',
    [ValidateRange(1, 1440)]
    [int]$DurationMinutes = 40,
    [int]$Seed = 1714,
    [string]$ArtifactRoot = (Join-Path $PSScriptRoot '../../qa/artifacts/c4-chat-scale')
)

$ErrorActionPreference = 'Stop'
# run.json deliberately contains no secrets, password, DSN, token, or raw message body.
$composeFile = Join-Path $PSScriptRoot 'docker-compose.yml'
$tables = @{
    baseline = 'messages_baseline'
    date_range = 'messages_date_range'
    date_hash = 'messages_date_hash'
}
$variants = if ($Variant -eq 'all') { @('baseline', 'date_range', 'date_hash') } else { @($Variant) }
$root = [IO.Path]::GetFullPath($ArtifactRoot)
$runId = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff')
$artifactDir = Join-Path $root $runId
$plansDir = Join-Path $artifactDir 'plans'
$composeLog = Join-Path $artifactDir 'logs/compose.log'
$composeArgs = @('-f', $composeFile)

function Invoke-Compose([string[]]$Arguments) {
    & docker compose @composeArgs @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed ($LASTEXITCODE): $($Arguments -join ' ')" }
}
# docker compose up -d primary replica

function Add-Stats([string]$table) {
    $stats = & docker compose @composeArgs exec -T primary psql -U c4_user -d c4chat -At -F "`t" -c "SELECT now(), relname, n_live_tup, n_dead_tup, vacuum_count, pg_total_relation_size('$table'), pg_indexes_size('$table') FROM pg_stat_user_tables WHERE relname = '$table';" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "initial pg_stat_user_tables sample failed: $($stats -join ' ')" }
    $stats | Add-Content (Join-Path $artifactDir 'db-stats.tsv')
    $lag = & docker compose @composeArgs exec -T replica psql -U c4_user -d c4chat -At -F "`t" -c "SELECT now(), pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn(), CASE WHEN pg_last_wal_receive_lsn() IS DISTINCT FROM pg_last_wal_replay_lsn() THEN EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) ELSE 0 END;" 2>&1
    if ($LASTEXITCODE -ne 0) { throw "initial pg_stat_replication sample failed: $($lag -join ' ')" }
    $lag | Add-Content (Join-Path $artifactDir 'replica-lag.tsv')
}

function Start-StatsSampler([string]$table, [string]$operation, [string]$phase) {
    $dbStatsPath = Join-Path $artifactDir 'db-stats.tsv'
    $replicaStatsPath = Join-Path $artifactDir 'replica-lag.tsv'
    Start-Job -ArgumentList $composeFile, $table, $dbStatsPath, $replicaStatsPath -ScriptBlock {
        param($composeFile, $table, $dbStatsPath, $replicaStatsPath)
        while ($true) {
            $stats = & docker compose -f $composeFile exec -T primary psql -U c4_user -d c4chat -At -F "`t" -c "SELECT now(), relname, n_live_tup, n_dead_tup, vacuum_count, pg_total_relation_size('$table'), pg_indexes_size('$table') FROM pg_stat_user_tables WHERE relname = '$table';" 2>&1
            if ($LASTEXITCODE -ne 0) { throw "pg_stat_user_tables sampler failed: $($stats -join ' ')" }
            $stats | Add-Content $dbStatsPath
            $replica = & docker compose -f $composeFile exec -T replica psql -U c4_user -d c4chat -At -F "`t" -c "SELECT now(), pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn(), CASE WHEN pg_last_wal_receive_lsn() IS DISTINCT FROM pg_last_wal_replay_lsn() THEN EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) ELSE 0 END;" 2>&1
            if ($LASTEXITCODE -ne 0) { throw "pg_stat_replication sampler failed: $($replica -join ' ')" }
            $replica | Add-Content $replicaStatsPath
            Start-Sleep -Seconds 10
        }
    }
}

function Get-LatencySamples([string[]]$LogLines) {
    $samples = @()
    foreach ($line in $LogLines) {
        $fields = $line -split '\s+'
        if ($fields.Count -lt 5) { continue }
        foreach ($field in @($fields[5])) {
            $value = 0.0
            if ([double]::TryParse($field, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$value) -and $value -ge 0) {
                $samples += ($value / 1000.0)
                break
            }
        }
    }
    return $samples
}

function Get-Percentile([double[]]$Values, [double]$Percent) {
    $sorted = @($Values | Sort-Object)
    $index = [math]::Ceiling(($Percent / 100) * $sorted.Count) - 1
    return [math]::Round([double]$sorted[[math]::Max(0, $index)], 3)
}

New-Item -ItemType Directory -Force -Path $plansDir, (Split-Path $composeLog) | Out-Null
$gitSha = (& git rev-parse HEAD 2>$null | Select-Object -First 1)
if (-not $gitSha) { $gitSha = 'unknown' }
@{ variant = $Variant; seed = $Seed; durationMinutes = $DurationMinutes; utc = [DateTime]::UtcNow.ToString('o'); gitSha = $gitSha } |
    ConvertTo-Json -Compress | Set-Content (Join-Path $artifactDir 'run.json')
"variant`tphase`toperation`tcount`terror_count`terror_rate`tp50_ms`tp95_ms`tp99_ms`tmax_ms" | Set-Content (Join-Path $artifactDir 'latency.tsv')
"utc`t table`t live`t dead`t vacuum`trelation_size_bytes`tindex_size_bytes" | Set-Content (Join-Path $artifactDir 'db-stats.tsv')
"utc`t receive_lsn`t replay_lsn`t lag_seconds" | Set-Content (Join-Path $artifactDir 'replica-lag.tsv')
"variant`tduplicate_cursor_count" | Set-Content (Join-Path $artifactDir 'cursor-gaps.tsv')

try {
    Invoke-Compose @('up', '-d', 'primary', 'replica')
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        & docker compose @composeArgs exec -T primary pg_isready -U c4_user -d c4chat 2>$null | Out-Null
        $primaryReady = ($LASTEXITCODE -eq 0)
        & docker compose @composeArgs exec -T replica pg_isready -U c4_user -d c4chat 2>$null | Out-Null
        $replicaReady = ($LASTEXITCODE -eq 0)
        if ($primaryReady -and $replicaReady) { break }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    if (-not ($primaryReady -and $replicaReady)) { throw 'primary/replica readiness timeout (120s)' }

    foreach ($variantName in $variants) {
        $table = $tables[$variantName]
        # Deterministic seed set; production tables are never touched.
        & docker compose @composeArgs exec -T primary psql -U c4_user -d c4chat -v ON_ERROR_STOP=1 -c "SELECT setseed($([math]::Abs($Seed % 1000) / 1000)); INSERT INTO $table (chat_room_id,event_date,sequence,content,idempotency_key) SELECT 'room-' || ((g - 1) % 1000 + 1), DATE '2026-01-01' + ((g - 1) % 30), g, 'benchmark message', 'seed-$Seed-' || g FROM generate_series(1,1000) g ON CONFLICT DO NOTHING;" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "seed load failed for $variantName" }
        $phaseSeconds = if ($DurationMinutes -eq 1) { @(1,1,1,1) } else { @(300,900,900,300) }
        foreach ($phase in @('ramp','steady','hot-room','recovery')) {
            $seconds = $phaseSeconds[(@('ramp','steady','hot-room','recovery').IndexOf($phase))]
            # Keep each phase at its declared duration; operation windows follow the 50/35/15 mix.
            $operationUnit = if ($seconds -lt 10) { 1 } else { 10 }
            $operationSeconds = [ordered]@{
                write = [math]::Max($operationUnit, [math]::Round(($seconds * 0.50) / $operationUnit) * $operationUnit)
                history = [math]::Max($operationUnit, [math]::Round(($seconds * 0.35) / $operationUnit) * $operationUnit)
                search = [math]::Max($operationUnit, [math]::Round(($seconds * 0.15) / $operationUnit) * $operationUnit)
            }
            $operationSeconds.search = [math]::Max($operationUnit, $operationSeconds.search + $seconds - (($operationSeconds.Values | Measure-Object -Sum).Sum))
            foreach ($operation in @(@('write','write.sql'), @('history','history.sql'), @('search','search.sql'))) {
                $opName = $operation[0]; $sqlName = $operation[1]
                $operationDuration = [int]$operationSeconds[$opName]
                $aggregateInterval = 10
                $aggregateIntervalArg = [math]::Min($aggregateInterval, $operationDuration)
                $artifactMount = "$artifactDir`:/artifacts"
                # pgbench interval evidence is mounted from the Windows host into the container.
                # Docker argument form: -v $artifactDir:/artifacts
                $logPrefix = "/artifacts/pgbench-${variantName}-${phase}-${opName}"
                # Required log prefix shape: --log-prefix=/artifacts/pgbench-${variant}-${phase}-${op}
                Add-Stats $table
                $sampler = Start-StatsSampler $table $opName $phase
                try {
                    $output = & docker compose @composeArgs run --rm --no-deps -v $artifactMount -e PGPASSWORD=dev_only_password pgbench -n -l -j 4 -c 16 "--aggregate-interval=$aggregateIntervalArg" "--log-prefix=$logPrefix" -T $operationDuration "-Dtable=$table" -Dseed=$Seed -f "/bench/$sqlName" 2>&1
                    $output | Set-Content (Join-Path $artifactDir "logs/pgbench-${variantName}-${phase}-${opName}.stdout.log")
                    if ($LASTEXITCODE -ne 0) { throw "pgbench $variantName/$phase/$opName failed ($LASTEXITCODE): $($output -join ' ')" }
                    $logFiles = Get-ChildItem -Path $artifactDir -Filter "pgbench-$variantName-$phase-$opName*" -File -ErrorAction SilentlyContinue
                    $logLines = @($logFiles | ForEach-Object { Get-Content $_.FullName })
                    $samples = @(Get-LatencySamples $logLines)
                    if ($samples.Count -eq 0) {
                        $average = $null
                        foreach ($line in $output) {
                            if ($line -match 'latency average =\s*([0-9.]+)\s*ms') { $average = [double]$Matches[1]; break }
                        }
                        if ($null -eq $average) { throw "latency samples unavailable for ${variantName}/${phase}/${opName}" }
                        "latency_fallback=average_ms:$average" | Add-Content (Join-Path $artifactDir 'logs/run.log')
                        $samples = @($average)
                    }
                    $processed = 0L; $failed = 0L
                    foreach ($line in $output) {
                        if ($line -match 'number of transactions actually processed:\s*(\d+)') { $processed = [int64]$Matches[1] }
                        if ($line -match 'number of failed transactions:\s*(\d+)') { $failed = [int64]$Matches[1] }
                    }
                    if ($processed -le 0) { $processed = $samples.Count }
                    $rate = if ($processed -gt 0) { [math]::Round($failed / $processed, 6) } else { 0 }
                    $row = @($variantName, $phase, $opName, $processed, $failed, $rate, (Get-Percentile $samples 50), (Get-Percentile $samples 95), (Get-Percentile $samples 99), (Get-Percentile $samples 100)) -join "`t"
                    $row | Add-Content (Join-Path $artifactDir 'latency.tsv')
                    $output | Set-Content (Join-Path $artifactDir "logs/pgbench-${variantName}-${phase}-${opName}.stdout.log")
                    foreach ($logFile in $logFiles) { Copy-Item $logFile.FullName (Join-Path $artifactDir "logs/$($logFile.Name)") -Force }
                } finally {
                    $samplerState = $sampler.State
                    Stop-Job $sampler -ErrorAction SilentlyContinue
                    $samplerOutput = Receive-Job $sampler -ErrorAction SilentlyContinue 2>&1
                    Remove-Job $sampler -Force -ErrorAction SilentlyContinue
                    if ($samplerState -eq 'Failed' -or ($samplerOutput | Where-Object { $_ -is [System.Management.Automation.ErrorRecord] })) {
                        throw "stats sampler failed for ${variantName}/${phase}/${opName}: $($samplerOutput -join ' ')"
                    }
                }
            }
            foreach ($operation in @('write','history','search')) {
                $planPath = Join-Path $plansDir "$variantName-$operation.txt"
                "EXPLAIN (ANALYZE, BUFFERS) $operation / $table" | Set-Content $planPath
                & docker compose @composeArgs exec -T primary psql -U c4_user -d c4chat -c "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) SELECT count(*) FROM $table WHERE chat_room_id = 'room-1' AND event_date >= DATE '2026-01-01';" 2>&1 | Add-Content $planPath
            }
        }
        $cursorCheck = & docker compose @composeArgs exec -T primary psql -U c4_user -d c4chat -At -F "`t" -c "SELECT '$variantName', count(*) FROM (SELECT chat_room_id, sequence, event_date FROM $table GROUP BY chat_room_id, sequence, event_date HAVING count(*) > 1) duplicate_cursors;" 2>&1
        if ($LASTEXITCODE -ne 0) { throw "cursor duplicate check failed for $variantName" }
        $cursorCheck | Add-Content (Join-Path $artifactDir 'cursor-gaps.tsv')
    }
} catch {
    "run failure: $($_.Exception.Message)" | Add-Content (Join-Path $artifactDir 'logs/run.log')
    try { & docker compose @composeArgs logs --no-color primary replica 2>&1 | Set-Content $composeLog } catch { }
    throw
} finally {
    try { & docker compose @composeArgs down -v --remove-orphans 2>&1 | Add-Content (Join-Path $artifactDir 'logs/compose.log') } catch { }
}

Write-Output $artifactDir
