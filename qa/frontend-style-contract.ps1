$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$frontendRoots = @(
  (Join-Path $repoRoot 'apps\web'),
  (Join-Path $repoRoot 'packages')
)

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

function RelativePath($path) {
  return $path.Replace($repoRoot + [System.IO.Path]::DirectorySeparatorChar, '')
}

function Is-TestOrPublicAsset($path) {
  return $path -like '*\tests\*' -or $path -like '*\public\*' -or $path -like '*.test.ts' -or $path -like '*.spec.ts'
}

$failures = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]

foreach ($root in $frontendRoots) {
  if (-not (Test-Path $root)) {
    continue
  }

  Get-ChildItem -Path $root -Recurse -Include '*.ts','*.vue','*.js' -File |
    Where-Object {
      $_.FullName -notlike '*\node_modules\*' -and
      $_.FullName -notlike '*\.nuxt\*' -and
      $_.FullName -notlike '*\.output\*'
    } |
    ForEach-Object {
      $relative = RelativePath $_.FullName
      $content = Get-Content -Path $_.FullName -Raw

      if ($relative -like 'apps\web*' -and ($content -match '@tauri|__TAURI__')) {
        $failures.Add("$relative imports or references a native desktop API from the web app.")
      }

      if (-not (Is-TestOrPublicAsset $_.FullName)) {
        if ($content -match 'localStorage\.setItem\([^)]*(access|refresh|token)' -or
            $content -match 'sessionStorage\.setItem\([^)]*(access|refresh)') {
          $failures.Add("$relative persists auth-like token material in browser storage.")
        }
      }

      $exportedFunctionMatches = [regex]::Matches($content, '(?m)^\s*export\s+(async\s+)?(function\s+\w+|\w+\s*=\s*(async\s*)?\(?)([^=\r\n{]*)\)')
      foreach ($match in $exportedFunctionMatches) {
        $signature = $match.Value
        $paramsMatch = [regex]::Match($signature, '\(([^)]*)\)')
        if (-not $paramsMatch.Success) {
          continue
        }
        $params = $paramsMatch.Groups[1].Value.Trim()
        if ([string]::IsNullOrWhiteSpace($params)) {
          continue
        }
        $paramCount = ($params -split ',').Count
        if ($paramCount -gt 4) {
          $failures.Add("$relative exports a function with $paramCount parameters. Prefer an options object or domain command.")
        } elseif ($paramCount -gt 3) {
          $warnings.Add("$relative exports a function with $paramCount parameters. Review the signature before extending it.")
        }
      }
    }
}

foreach ($warning in $warnings) {
  Write-Warning $warning
}

Assert ($failures.Count -eq 0) ("Frontend style contract failed:`n" + ($failures -join "`n"))

Write-Output 'FRONTEND_STYLE_CONTRACT_PASS'
