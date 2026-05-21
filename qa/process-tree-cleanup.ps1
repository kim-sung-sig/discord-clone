$ErrorActionPreference = 'Stop'

function Test-QaIsWindows {
  return [System.Environment]::OSVersion.Platform -eq 'Win32NT'
}

function Write-QaCleanupStep([string] $Message, [string] $LogPrefix = 'qa-cleanup') {
  Write-Output "[$LogPrefix] $Message"
}

function Stop-QaProcessTree([int] $ProcessId, [string] $Label = 'process', [string] $LogPrefix = 'qa-cleanup') {
  $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if ($null -eq $process) {
    return
  }

  if (Test-QaIsWindows) {
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId=$ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
      Stop-QaProcessTree -ProcessId ([int] $child.ProcessId) -Label "$Label child" -LogPrefix $LogPrefix
    }
  }

  $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
  if ($null -ne $process) {
    Write-QaCleanupStep "stopping $Label process $ProcessId" $LogPrefix
    Stop-Process -Id $ProcessId -Force
  }
}

function Stop-QaListeningProcessByPort(
  [int] $Port,
  [string] $ExpectedCommandLinePattern,
  [string] $Label = 'port listener',
  [string] $LogPrefix = 'qa-cleanup'
) {
  if (-not (Test-QaIsWindows)) {
    return
  }

  $listeners = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
    Where-Object { $_.State -eq 'Listen' } |
    Select-Object -ExpandProperty OwningProcess -Unique

  foreach ($owningProcess in $listeners) {
    if ($owningProcess -le 0) {
      continue
    }

    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$owningProcess" -ErrorAction SilentlyContinue
    if ($null -ne $process -and $process.CommandLine -like $ExpectedCommandLinePattern) {
      Write-QaCleanupStep "stopping $Label child process $owningProcess on port $Port" $LogPrefix
      Stop-QaProcessTree -ProcessId ([int] $owningProcess) -Label $Label -LogPrefix $LogPrefix
    }
  }
}
