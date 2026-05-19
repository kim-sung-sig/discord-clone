$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot 'backend'
$methodSignatureBaselinePath = Join-Path $PSScriptRoot 'style-baseline/backend-method-signature-allowlist.txt'
$domainRoots = @(
  (Join-Path $backendRoot 'modules'),
  (Join-Path $backendRoot 'shared')
)

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

function RelativePath($path) {
  return $path.Replace($repoRoot + [System.IO.Path]::DirectorySeparatorChar, '')
}

$failures = New-Object System.Collections.Generic.List[string]
$warnings = New-Object System.Collections.Generic.List[string]
$methodSignatureBaseline = New-Object System.Collections.Generic.HashSet[string]

if (Test-Path $methodSignatureBaselinePath) {
  Get-Content -Path $methodSignatureBaselinePath |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) -and -not $_.TrimStart().StartsWith('#') } |
    ForEach-Object { [void] $methodSignatureBaseline.Add($_.Trim()) }
}

foreach ($root in $domainRoots) {
  if (-not (Test-Path $root)) {
    continue
  }

  Get-ChildItem -Path $root -Recurse -Filter '*.java' |
    Where-Object { $_.FullName -like '*\src\main\java\*' } |
    ForEach-Object {
      $relative = RelativePath $_.FullName
      $content = Get-Content -Path $_.FullName -Raw

      foreach ($forbiddenImport in @(
        'import org.springframework.',
        'import jakarta.persistence.',
        'import jakarta.validation.',
        'import org.springframework.web.'
      )) {
        if ($content.Contains($forbiddenImport)) {
          $failures.Add("$relative imports framework/runtime API in a domain/shared module: $forbiddenImport")
        }
      }

      if ($content -match '(?m)^\s*public\s+(record|class|interface)\s+\w*(Dto|DTO|Response)\b') {
        $failures.Add("$relative declares DTO/Response-shaped type in a domain/shared module. Use ubiquitous-language domain names or command/result names.")
      }

      if ($content -match '(?m)^\s*public\s+(record|class)\s+\w*Request\b') {
        $warnings.Add("$relative uses a Request suffix in a domain module. Prefer Command/Query/Intent when this is not a true domain term.")
      }
    }
}

Get-ChildItem -Path (Join-Path $backendRoot 'modules') -Directory | ForEach-Object {
  $mainPath = Join-Path $_.FullName 'src\main\java'
  $testPath = Join-Path $_.FullName 'src\test\java'
  if ((Test-Path $mainPath) -and -not (Test-Path $testPath)) {
    $warnings.Add("backend/modules/$($_.Name) has production code but no module-local src/test/java directory.")
  }
}

Get-ChildItem -Path $backendRoot -Recurse -Filter '*.java' |
  Where-Object { $_.FullName -like '*\src\main\java\*' } |
  ForEach-Object {
    $relative = RelativePath $_.FullName
    $content = Get-Content -Path $_.FullName -Raw
    $methodMatches = [regex]::Matches($content, '(?m)^\s*(public|protected|private)\s+(?!record\b|class\b|interface\b)[^{;=]+\(([^)]*)\)\s*(throws\s+[^{]+)?\{')
    foreach ($match in $methodMatches) {
      $params = $match.Groups[2].Value.Trim()
      if ([string]::IsNullOrWhiteSpace($params)) {
        continue
      }
      $paramCount = ($params -split ',').Count
      if ($paramCount -gt 5) {
        $signatureKey = "$relative :: $((($match.Value -replace '\s+', ' ').Trim()))"
        if ($methodSignatureBaseline.Contains($signatureKey)) {
          $warnings.Add("$relative has baseline method-signature debt with $paramCount parameters. Do not copy this shape into new code.")
        } else {
          $failures.Add("$relative has a method with $paramCount parameters. Prefer a command/query/value object signature.")
        }
      } elseif ($paramCount -gt 3) {
        $warnings.Add("$relative has a method with $paramCount parameters. Review whether a command/query/value object would be clearer.")
      }
    }
  }

foreach ($warning in $warnings) {
  Write-Warning $warning
}

Assert ($failures.Count -eq 0) ("Backend style contract failed:`n" + ($failures -join "`n"))

Write-Output 'BACKEND_STYLE_CONTRACT_PASS'
