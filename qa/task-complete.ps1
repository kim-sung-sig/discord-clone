param(
  [Parameter(Mandatory = $true)]
  [string] $TaskId,
  [Parameter(Mandatory = $true)]
  [string[]] $Paths,
  [string] $Message = '',
  [switch] $NoPush
)

$ErrorActionPreference = 'Stop'

if ($Paths.Count -eq 0) {
  throw 'At least one task-owned path is required.'
}

$Paths = $Paths |
  ForEach-Object { $_ -split ',' } |
  ForEach-Object { $_.Trim() } |
  Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

if ($Paths.Count -eq 0) {
  throw 'At least one task-owned path is required after normalization.'
}

if ([string]::IsNullOrWhiteSpace($Message)) {
  $Message = "chore($TaskId): complete task"
}

Write-Output '[task-complete] do not commit unrelated dirty work'
Write-Output "[task-complete] task-owned paths: $($Paths -join ', ')"

$status = & git status --short
if ($LASTEXITCODE -ne 0) {
  throw 'git status --short failed'
}
Write-Output $status

$addArgs = @('add', '--') + $Paths
& git @addArgs
if ($LASTEXITCODE -ne 0) {
  throw "git add -- failed for task-owned paths: $($Paths -join ', ')"
}

& git diff --cached --stat
if ($LASTEXITCODE -ne 0) {
  throw 'git diff --cached --stat failed'
}

$cachedNames = & git diff --cached --name-only
if ($LASTEXITCODE -ne 0) {
  throw 'git diff --cached --name-only failed'
}
if ([string]::IsNullOrWhiteSpace(($cachedNames -join ''))) {
  throw 'No staged changes for task-owned paths.'
}

& git commit -m $Message
if ($LASTEXITCODE -ne 0) {
  throw "git commit -m failed for task $TaskId"
}

$branch = (& git branch --show-current).Trim()
if ([string]::IsNullOrWhiteSpace($branch)) {
  throw 'Cannot determine current branch for push.'
}

if ($NoPush) {
  Write-Output "[task-complete] push skipped by -NoPush"
  return
}

& git push origin $branch
if ($LASTEXITCODE -ne 0) {
  $commit = (& git rev-parse HEAD).Trim()
  throw "git push origin $branch failed for commit $commit. Record the branch, commit hash, remote, and failure reason in Report or Feedback."
}

Write-Output "TASK_COMPLETE_PUSHED $TaskId $branch"
