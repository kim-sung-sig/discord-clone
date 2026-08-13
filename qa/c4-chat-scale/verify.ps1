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
$runMeta=Get-Content (Join-Path $dir 'run.json') -Raw | ConvertFrom-Json
if ($runMeta.PSObject.Properties.Name -match '(?i)password|secret|dsn|token|body') { throw 'run.json contains sensitive fields' }
$rows=Import-Csv (Join-Path $dir 'latency.tsv') -Delimiter "`t"
$reasons=@(); $decision='ACCEPT'
foreach($r in $rows){foreach($n in @('count','error_count','error_rate','p50_ms','p95_ms','p99_ms','max_ms')){[double]$v=0;if(-not [double]::TryParse($r.$n,[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$v)){throw "invalid numeric field $n"}};if([double]$r.error_rate -ge .01 -or [double]$r.p99_ms -ge 500){$decision='REJECT';$reasons+="threshold:$($r.variant)/$($r.operation)"}}
if($rows.Count -eq 0){$decision='REJECT';$reasons+='NOT_RUN:no latency rows'}
$variants=@($rows.variant | Sort-Object -Unique)
if($variants.Count -lt 3 -or (@('baseline','date_range','date_hash') | Where-Object {$_ -notin $variants}).Count){$decision='REJECT';$reasons+='NOT_RUN:all three variants are required'}
$expectedRows=12 * [math]::Max(1,$variants.Count)
if($rows.Count -ne $expectedRows){$decision='REJECT';$reasons+="NOT_RUN:expected $expectedRows latency rows"}
$expectedPhases=@('ramp','steady','hot-room','recovery'); $expectedOperations=@('write','history','search')
if(@($rows | Where-Object {$_.phase -notin $expectedPhases -or $_.operation -notin $expectedOperations} | Select-Object -First 1).Count){$decision='REJECT';$reasons+='NOT_RUN:invalid phase or operation evidence'}
if(@($rows | ForEach-Object { "$($_.variant)/$($_.phase)/$($_.operation)" } | Sort-Object -Unique).Count -ne $rows.Count){$decision='REJECT';$reasons+='NOT_RUN:duplicate phase or operation evidence'}
if([int]$runMeta.durationMinutes -lt 40){$decision='REJECT';$reasons+='NOT_RUN:40-minute run required'}
$planFiles=Get-ChildItem (Join-Path $dir 'plans') -File -ErrorAction SilentlyContinue
$pruning=($planFiles | ForEach-Object {Get-Content $_ -Raw}) -match 'Partition Pruning|Index Cond'
if(-not $pruning){$decision='REJECT';$reasons+='pruning evidence missing'}
$lagRows=Get-Content (Join-Path $dir 'replica-lag.tsv') | Where-Object {$_ -notmatch '^utc'}
$lag=@();foreach($line in $lagRows){$f=$line -split "`t";if($f.Count -lt 4){$decision='REJECT';$reasons+='NOT_RUN:invalid replica lag row';continue};[double]$x=0;if(-not [double]::TryParse($f[3],[Globalization.NumberStyles]::Float,[Globalization.CultureInfo]::InvariantCulture,[ref]$x)){$decision='REJECT';$reasons+='NOT_RUN:invalid replica lag sample';continue};$lag+=$x*1000}
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
$out=[ordered]@{decision=$decision;variant=$variant;p99_ms=if($rows.Count){[double](($rows|Measure-Object p99_ms -Maximum).Maximum)}else{0};error_rate=if($rows.Count){[double](($rows|Measure-Object error_rate -Maximum).Maximum)}else{0};cursor_gap_count=$cursorGap;replica_lag_p95_ms=$lagP95;pruning_pass=[bool]$pruning;reasons=$reasons}
$out|ConvertTo-Json -Depth 4|Set-Content (Join-Path $dir 'decision.json')
"# T171-C4 채팅 확장성 검증`n`n결정: **$decision**`n`n실측 40분 전체가 없으면 운영 채택 결론으로 사용하지 않는다. $(($reasons -join '; '))"|Set-Content (Join-Path $dir 'verification-summary.md')
if($decision -ne 'ACCEPT'){exit 1};Write-Output 'C4_CHAT_SCALE_VERIFY_PASS'
