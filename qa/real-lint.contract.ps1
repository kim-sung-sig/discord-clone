$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$rootPackagePath = Join-Path $repoRoot 'package.json'
$webPackagePath = Join-Path $repoRoot 'apps/web/package.json'
$eslintPath = Join-Path $repoRoot 'eslint.config.mjs'
$prettierPath = Join-Path $repoRoot '.prettierrc.json'
$checkstylePath = Join-Path $repoRoot 'config/checkstyle/checkstyle.xml'
$gradleWrapperLauncherPath = Join-Path $repoRoot 'qa/gradle-wrapper.mjs'
$gradlePath = Join-Path $repoRoot 'build.gradle.kts'
$harnessPath = Join-Path $PSScriptRoot 'agent-harness.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $eslintPath) 'eslint.config.mjs is missing'
Assert (Test-Path $prettierPath) '.prettierrc.json is missing'
Assert (Test-Path $checkstylePath) 'config/checkstyle/checkstyle.xml is missing'
Assert (Test-Path $gradleWrapperLauncherPath) 'qa/gradle-wrapper.mjs is missing'

$rootPackage = Get-Content -Path $rootPackagePath -Raw
$webPackage = Get-Content -Path $webPackagePath -Raw
$gradle = Get-Content -Path $gradlePath -Raw
$harness = Get-Content -Path $harnessPath -Raw

foreach ($snippet in @(
  '"lint"',
  '"lint:frontend"',
  '"lint:backend"',
  'node qa/gradle-wrapper.mjs checkstyleMain checkstyleTest',
  '"format:check"'
)) {
  Assert ($rootPackage.Contains($snippet)) "root package.json is missing script/dependency snippet: $snippet"
}

foreach ($snippet in @('eslint', 'prettier', '@eslint/js', 'typescript-eslint', 'eslint-plugin-vue')) {
  Assert ($rootPackage.Contains($snippet) -or $webPackage.Contains($snippet)) "package.json is missing lint dependency: $snippet"
}

foreach ($snippet in @(
  'checkstyle',
  'toolVersion',
  'config/checkstyle/checkstyle.xml'
)) {
  Assert ($gradle.Contains($snippet)) "build.gradle.kts is missing Checkstyle snippet: $snippet"
}

foreach ($toolId in @('frontend-lint', 'backend-lint', 'format-check', 'real-lint-contract')) {
  Assert ($harness.Contains("'$toolId'")) "qa/agent-harness.ps1 is missing lint tool id: $toolId"
}

Write-Output 'REAL_LINT_CONTRACT_PASS'
