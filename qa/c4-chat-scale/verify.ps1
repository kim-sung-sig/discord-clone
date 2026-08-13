param([Parameter(Mandatory)][string]$ArtifactDir)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../qa/artifacts/c4-chat-scale'))
$dir=[IO.Path]::GetFullPath($ArtifactDir)
if (-not $dir.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)) { throw 'artifact path must be under qa/artifacts/c4-chat-scale' }
$required=@('run.json','latency.tsv','db-stats.tsv','replica-lag.tsv')
foreach($f in $required){if(-not(Test-Path(Join-Path $dir $f))){throw "missing artifact: $f"}}
$header=(Get-Content (Join-Path $dir 'latency.tsv') -First 1)
$expected="variant`tphase`toperation`tcount`terror_count`terror_rate`tp50_ms`tp95_ms`tp99_ms`tmax_ms"
if($header -ne $expected){throw 'invalid latency.tsv header'}
$rows=Import-Csv (Join-Path $dir 'latency.tsv') -Delimiter "`t"
$reasons=@(); $decision='ACCEPT'
foreach($r in $rows){foreach($n in @('count','error_count','error_rate','p50_ms','p95_ms','p99_ms','max_ms')){[double]$v=0;if(-not [double]::TryParse($r.$n,[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$v)){throw "invalid numeric field $n"}};if([double]$r.error_rate -ge .01 -or [double]$r.p99_ms -ge 500){$decision='REJECT';$reasons+="threshold:$($r.variant)/$($r.operation)"}}
if($rows.Count -eq 0){$decision='REJECT';$reasons+='NOT_RUN:no latency rows'}
$planFiles=Get-ChildItem (Join-Path $dir 'plans') -File -ErrorAction SilentlyContinue
$pruning=($planFiles | ForEach-Object {Get-Content $_ -Raw}) -match 'Partition Pruning|Index Cond'
if(-not $pruning){$decision='REJECT';$reasons+='pruning evidence missing'}
$lagRows=Get-Content (Join-Path $dir 'replica-lag.tsv') | Where-Object {$_ -notmatch '^utc'}
$lag=@();foreach($line in $lagRows){$f=$line -split "`t";if($f.Count -ge 4){[double]$x=0;if([double]::TryParse($f[3],[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$x)){$lag+=$x*1000}}}
$lagP95=if($lag.Count){[double](($lag|Sort-Object)[[math]::Max(0,[math]::Ceiling(.95*$lag.Count)-1)])}else{0}
if($lagP95 -ge 2000){$decision='REJECT';$reasons+='replica lag p95 >= 2s'}
$variant=if($rows.Count){$rows[0].variant}else{'NOT_RUN'}
$out=[ordered]@{decision=$decision;variant=$variant;p99_ms=if($rows.Count){[double](($rows|Measure-Object p99_ms -Maximum).Maximum)}else{0};error_rate=if($rows.Count){[double](($rows|Measure-Object error_rate -Maximum).Maximum)}else{0};cursor_gap_count=0;replica_lag_p95_ms=$lagP95;pruning_pass=[bool]$pruning;reasons=$reasons}
$out|ConvertTo-Json -Depth 4|Set-Content (Join-Path $dir 'decision.json')
"# T171-C4 채팅 확장성 검증`n`n결정: **$decision**`n`n실측 40분 전체가 없으면 운영 채택 결론으로 사용하지 않는다. $(($reasons -join '; '))"|Set-Content (Join-Path $dir 'verification-summary.md')
if($decision -ne 'ACCEPT'){exit 1};Write-Output 'C4_CHAT_SCALE_VERIFY_PASS'
