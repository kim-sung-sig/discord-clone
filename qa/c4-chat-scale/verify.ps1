param([Parameter(Mandatory)][string]$ArtifactDir)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../qa/artifacts/c4-chat-scale'))
$dir=[IO.Path]::GetFullPath($ArtifactDir)
if (-not ($dir.StartsWith($root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or $dir -eq $root)) { throw 'artifact path must be under qa/artifacts/c4-chat-scale' }
$required=@('run.json','latency.tsv','db-stats.tsv','replica-lag.tsv','cursor-gaps.tsv')
foreach($f in $required){if(-not(Test-Path(Join-Path $dir $f))){throw "missing artifact: $f"}}
$header=(Get-Content (Join-Path $dir 'latency.tsv') -First 1)
$expected="variant`tphase`toperation`tcount`terror_count`terror_rate`tp50_ms`tp95_ms`tp99_ms`tmax_ms"
if($header -ne $expected){throw 'invalid latency.tsv header'}
$dbHeader=(Get-Content (Join-Path $dir 'db-stats.tsv') -First 1)
if($dbHeader -ne "utc`t table`t live`t dead`t vacuum`trelation_size_bytes`tindex_size_bytes"){throw 'invalid db-stats.tsv header'}
$routingHeader=(Get-Content (Join-Path $dir 'routing.tsv') -First 1)
if($routingHeader -ne "scenario`trequest_type`tlag_seconds`tselected_target`tfallback_reason`tstale_read_count`twarning"){throw 'invalid routing.tsv header'}
$runMeta=Get-Content (Join-Path $dir 'run.json') -Raw | ConvertFrom-Json
if ($runMeta.PSObject.Properties.Name -match '(?i)password|secret|dsn|token|body') { throw 'run.json contains sensitive fields' }
$rows=Import-Csv (Join-Path $dir 'latency.tsv') -Delimiter "`t"
$reasons=@(); $decision='ACCEPT'; $candidateDecision='DEFER'; $selectedVariant='NONE'
foreach($r in $rows){foreach($n in @('count','error_count','error_rate','p50_ms','p95_ms','p99_ms','max_ms')){[double]$v=0;if(-not [double]::TryParse($r.$n,[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$v) -or [double]::IsNaN($v) -or [double]::IsInfinity($v) -or $v -lt 0){throw "invalid numeric field $n"}};$count=[double]$r.count;$errors=[double]$r.error_count;$expectedRate=if($count -eq 0){0}else{$errors/$count};if($errors -gt $count -or [math]::Abs(([double]$r.error_rate)-$expectedRate) -gt 0.000001 -or [double]$r.p50_ms -gt [double]$r.p95_ms -or [double]$r.p95_ms -gt [double]$r.p99_ms -or [double]$r.p99_ms -gt [double]$r.max_ms){$decision='REJECT';$reasons+="invalid metric ordering:$($r.variant)/$($r.operation)"};if([double]$r.error_rate -ge 0.01){$decision='REJECT';$reasons+="error-rate-threshold:$($r.variant)/$($r.operation)"}}
if($rows.Count -eq 0){$decision='REJECT';$reasons+='NOT_RUN:no latency rows'}
$variants=@($rows.variant | Sort-Object -Unique)
$allowedVariants=@('baseline','date_range','date_hash')
if($variants.Count -ne 3 -or (@($allowedVariants | Where-Object {$_ -notin $variants}).Count) -or (@($variants | Where-Object {$_ -notin $allowedVariants}).Count)){$decision='REJECT';$reasons+='NOT_RUN:all three variants are required and no unknown variants allowed'}
$expectedRows=12 * [math]::Max(1,$variants.Count)
if($rows.Count -ne $expectedRows){$decision='REJECT';$reasons+="NOT_RUN:expected $expectedRows latency rows"}
$expectedPhases=@('ramp','steady','hot-room','recovery'); $expectedOperations=@('write','history','search')
if(@($rows | Where-Object {$_.phase -notin $expectedPhases -or $_.operation -notin $expectedOperations} | Select-Object -First 1).Count){$decision='REJECT';$reasons+='NOT_RUN:invalid phase or operation evidence'}
if(@($rows | ForEach-Object { "$($_.variant)/$($_.phase)/$($_.operation)" } | Sort-Object -Unique).Count -ne $rows.Count){$decision='REJECT';$reasons+='NOT_RUN:duplicate phase or operation evidence'}
if([int]$runMeta.durationMinutes -lt 40){$decision='REJECT';$reasons+='NOT_RUN:40-minute run required'}
$planFiles=Get-ChildItem (Join-Path $dir 'plans') -File -ErrorAction SilentlyContinue
$expectedPlans=@(); foreach($variantName in @('baseline','date_range','date_hash')) { foreach($op in @('write','history','search')) { $expectedPlans += "$variantName-$op.txt" } }
$missingPlans=@($expectedPlans | Where-Object { -not (Test-Path (Join-Path $dir "plans/$_")) })
$badPlans=@($planFiles | Where-Object { (Get-Content $_ -Raw) -notmatch 'Partition Pruning|Index Cond' })
$pruning=($missingPlans.Count -eq 0 -and $badPlans.Count -eq 0)
if(-not $pruning){$decision='REJECT';$reasons+='pruning evidence missing or incomplete'}
$lagRows=Get-Content (Join-Path $dir 'replica-lag.tsv') | Where-Object {$_ -notmatch '^utc'}
$lag=@();foreach($line in $lagRows){$f=$line -split "`t";if($f.Count -lt 4){$decision='REJECT';$reasons+='NOT_RUN:invalid replica lag row';continue};[double]$x=0;if(-not [double]::TryParse($f[3],[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$x) -or [double]::IsNaN($x) -or [double]::IsInfinity($x) -or $x -lt 0){$decision='REJECT';$reasons+='NOT_RUN:invalid replica lag sample';continue};$lag+=$x*1000}
$lagP95=if($lag.Count){[double](($lag|Sort-Object)[[math]::Max(0,[math]::Ceiling(.95*$lag.Count)-1)])}else{0}
if($lag.Count -eq 0){$decision='REJECT';$reasons+='NOT_RUN:replica lag samples missing'}
if($lagP95 -ge 2000){$decision='REJECT';$reasons+='replica lag p95 >= 2s'}
$variant=if($rows.Count){$rows[0].variant}else{'NOT_RUN'}
$cursorPath=Join-Path $dir 'cursor-gaps.tsv'
$cursorGap=-1
$cursorRows=@(Get-Content $cursorPath | Select-Object -Skip 1 | Where-Object {$_})
$cursorValues=@{}
foreach($cursorLine in $cursorRows){$f=$cursorLine -split "`t";[int]$n=0;if($f.Count -ne 2 -or $f[0] -notin @('baseline','date_range','date_hash') -or -not [int]::TryParse($f[1],[ref]$n)){ $decision='REJECT';$reasons+='NOT_RUN:invalid cursor gap evidence';continue };if($cursorValues.ContainsKey($f[0])){$decision='REJECT';$reasons+='NOT_RUN:duplicate cursor variant';continue};$cursorValues[$f[0]]=$n;if($n -ne 0){$decision='REJECT';$reasons+="cursor gap:$($f[0])"}}
if($cursorValues.Count -ne 3 -or (@('baseline','date_range','date_hash') | Where-Object { -not $cursorValues.ContainsKey($_) }).Count){$decision='REJECT';$reasons+='NOT_RUN:cursor gap evidence missing'}else{$cursorGap=($cursorValues.Values|Measure-Object -Maximum).Maximum}
$dbRows=@(Get-Content (Join-Path $dir 'db-stats.tsv') | Select-Object -Skip 1 | Where-Object {$_}); $dbStable=$true; $previousVacuumByTable=@{}
foreach($dbLine in $dbRows){$f=$dbLine -split "`t"; if($f.Count -ne 7){$dbStable=$false;continue}; [long]$live=0;[long]$dead=0;[long]$vacuum=0;[long]$relationSize=0;[long]$indexSize=0; $tableKey=$f[1]; $previousVacuum=if($previousVacuumByTable.ContainsKey($tableKey)){[long]$previousVacuumByTable[$tableKey]}else{0L}; if(-not [long]::TryParse($f[2],[ref]$live) -or -not [long]::TryParse($f[3],[ref]$dead) -or -not [long]::TryParse($f[4],[ref]$vacuum) -or -not [long]::TryParse($f[5],[ref]$relationSize) -or -not [long]::TryParse($f[6],[ref]$indexSize) -or $live -lt 0 -or $dead -lt 0 -or $vacuum -lt 0 -or $relationSize -lt 0 -or $indexSize -lt 0 -or $relationSize -gt 50GB -or $indexSize -gt 50GB -or $vacuum -lt $previousVacuum -or ($live -gt 0 -and $dead -gt ($live * 0.10))){$dbStable=$false};$previousVacuumByTable[$tableKey]=$vacuum}
if($dbRows.Count -eq 0 -or -not $dbStable){$decision='REJECT';$reasons+='NOT_RUN:relation size or vacuum stability evidence failed'}
$routingRows=@(Get-Content (Join-Path $dir 'routing.tsv') | Select-Object -Skip 1 | Where-Object {$_}); $routingPass=$true; $routingByScenario=@{}
foreach($routingLine in $routingRows){$f=$routingLine -split "`t"; [double]$lagSeconds=0; [int]$staleCount=0; if($f.Count -ne 7 -or $f[0] -notin @('write','read-after-write','history','history-fallback','history-severe-policy') -or $routingByScenario.ContainsKey($f[0]) -or -not [double]::TryParse($f[2],[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$lagSeconds) -or [double]::IsNaN($lagSeconds) -or [double]::IsInfinity($lagSeconds) -or $lagSeconds -lt 0 -or $f[3] -notin @('primary','replica') -or -not [int]::TryParse($f[5],[ref]$staleCount) -or $staleCount -lt 0){$routingPass=$false;continue};$routingByScenario[$f[0]]=[pscustomobject]@{request_type=$f[1];lag_seconds=$lagSeconds;target=$f[3];fallback_reason=$f[4];stale=$staleCount;warning=$f[6]}}
$expectedRouting=@{write=@('write','primary','',0,'');'read-after-write'=@('read-after-write','primary','',0,'');history=@('history','replica','',0,'');'history-fallback'=@('history','primary','replica_lag_gt_2s',0,'');'history-severe-policy'=@('history','primary','replica_lag_gt_30s',0,'history_page_limited')}
foreach($scenario in $expectedRouting.Keys){$expected=$expectedRouting[$scenario];if(-not $routingByScenario.ContainsKey($scenario)){$routingPass=$false;continue};$actual=$routingByScenario[$scenario];if($actual.request_type -ne $expected[0] -or $actual.target -ne $expected[1] -or $actual.fallback_reason -ne $expected[2] -or $actual.stale -ne $expected[3] -or $actual.warning -ne $expected[4]){$routingPass=$false}}
if($routingRows.Count -ne $expectedRouting.Count -or -not $routingPass){$decision='REJECT';$reasons+='NOT_RUN:read routing or fallback evidence failed'}
$hotWriteSeconds=if([int]$runMeta.durationMinutes -eq 1){1}else{450}; $maxP99ByVariant=@{}; $maxWriteHotByVariant=@{}; $hotRoomWriteP99ByVariant=@{}; $steadyHotWriteP99ByVariant=@{}; $hotRoomWriteQpsByVariant=@{}; $maxErrorByVariant=@{}; foreach($variantKey in @('baseline','date_range','date_hash')) { $vr=@($rows | Where-Object {$_.variant -eq $variantKey}); $maxP99ByVariant[$variantKey]=if($vr.Count){[double](($vr|Measure-Object p99_ms -Maximum).Maximum)}else{[double]::PositiveInfinity}; $maxErrorByVariant[$variantKey]=if($vr.Count){[double](($vr|Measure-Object error_rate -Maximum).Maximum)}else{[double]::PositiveInfinity}; $wr=@($vr | Where-Object {$_.operation -eq 'write' -and $_.phase -in @('steady','hot-room','recovery')}); $maxWriteHotByVariant[$variantKey]=if($wr.Count){[double](($wr|Measure-Object p99_ms -Maximum).Maximum)}else{[double]::PositiveInfinity}; $sh=@($vr | Where-Object {$_.operation -eq 'write' -and $_.phase -in @('steady','hot-room')}); $steadyHotWriteP99ByVariant[$variantKey]=if($sh.Count){[double](($sh|Measure-Object p99_ms -Maximum).Maximum)}else{[double]::PositiveInfinity}; $hr=@($vr | Where-Object {$_.operation -eq 'write' -and $_.phase -eq 'hot-room'}); $hotRoomWriteP99ByVariant[$variantKey]=if($hr.Count){[double]$hr[0].p99_ms}else{[double]::PositiveInfinity}; [long]$hotCount=0; $hotRoomWriteQpsByVariant[$variantKey]=if($hr.Count -and [long]::TryParse([string]$hr[0].count,[ref]$hotCount)){[double]$hotCount / $hotWriteSeconds}else{[double]::PositiveInfinity} }
$baselinePass=($steadyHotWriteP99ByVariant['baseline'] -lt 500)
$rangeNoWorse=$true; foreach($baselineRow in @($rows | Where-Object {$_.variant -eq 'baseline'})){ $rangeRow=@($rows | Where-Object {$_.variant -eq 'date_range' -and $_.phase -eq $baselineRow.phase -and $_.operation -eq $baselineRow.operation}); if($rangeRow.Count -ne 1 -or [double]$rangeRow[0].p99_ms -gt [double]$baselineRow.p99_ms){$rangeNoWorse=$false;break} }
$dateRangeCandidate=($rangeNoWorse -and $maxWriteHotByVariant['date_range'] -le $maxWriteHotByVariant['baseline'] -and $maxP99ByVariant['date_range'] -le $maxP99ByVariant['baseline'])
$hashImprovement=if($hotRoomWriteP99ByVariant['date_range'] -gt 0){1 - ($hotRoomWriteP99ByVariant['date_hash'] / $hotRoomWriteP99ByVariant['date_range'])}else{0}
$hashLoadGate=($hotRoomWriteQpsByVariant['date_range'] -ge 1000 -or $hotRoomWriteP99ByVariant['date_range'] -ge 500)
$hashCandidate=(-not $baselinePass -and $hashLoadGate -and $hashImprovement -ge 0.20 -and $maxErrorByVariant['date_hash'] -le ($maxErrorByVariant['date_range'] * 1.10) -and $lagP95 -lt 2000)
if($decision -eq 'ACCEPT'){if($baselinePass){$candidateDecision='DEFER';$selectedVariant='baseline';$reasons+='baseline meets hot-write threshold; partition deferred'}elseif($dateRangeCandidate){$candidateDecision='ADOPT';$selectedVariant='date_range';$reasons+='date_range candidate meets baseline comparison'}elseif($hashCandidate){$candidateDecision='ADOPT';$selectedVariant='date_hash';$reasons+="date_hash hot-write improvement=$([math]::Round($hashImprovement*100,2))%"}else{$candidateDecision='DEFER';$reasons+='no partition candidate met adoption rule'}}
$out=[ordered]@{decision=$decision;candidate_decision=$candidateDecision;selected_variant=$selectedVariant;variant=$variant;p99_ms=if($rows.Count){[double](($rows|Measure-Object p99_ms -Maximum).Maximum)}else{0};error_rate=if($rows.Count){[double](($rows|Measure-Object error_rate -Maximum).Maximum)}else{0};cursor_gap_count=$cursorGap;replica_lag_p95_ms=$lagP95;pruning_pass=[bool]$pruning;db_stability_pass=[bool]$dbStable;routing_pass=[bool]$routingPass;variant_max_p99_ms=$maxP99ByVariant;variant_hot_write_p99_ms=$maxWriteHotByVariant;steady_hot_write_p99_ms=$steadyHotWriteP99ByVariant;hot_room_write_p99_ms=$hotRoomWriteP99ByVariant;hot_room_write_qps=$hotRoomWriteQpsByVariant;hash_load_gate=[bool]$hashLoadGate;variant_max_error_rate=$maxErrorByVariant;reasons=$reasons}
$out|ConvertTo-Json -Depth 4|Set-Content (Join-Path $dir 'decision.json')
"# T171-C4 채팅 확장성 검증`n`n결정: **$decision**`n`n실측 40분 전체가 없으면 운영 채택 결론으로 사용하지 않는다. $(($reasons -join '; '))"|Set-Content (Join-Path $dir 'verification-summary.md')
if($decision -ne 'ACCEPT'){exit 1};Write-Output 'C4_CHAT_SCALE_VERIFY_PASS'
