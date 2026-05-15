# Toolchain Warning Scan

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$artifactDir = Join-Path $PSScriptRoot "artifacts/toolchain"
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

$gradleLog = Join-Path $artifactDir "gradle-warning-mode-all.log"
$frontendLog = Join-Path $artifactDir "nuxt-build.log"

Push-Location $repo
try {
    & .\gradlew.bat test --warning-mode all *>&1 | Tee-Object -FilePath $gradleLog
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle warning scan failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Push-Location (Join-Path $repo "apps/web")
try {
    & npm run build *>&1 | Tee-Object -FilePath $frontendLog
    if ($LASTEXITCODE -ne 0) {
        throw "Nuxt build scan failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Output "Toolchain warning scan complete. Logs: $artifactDir"
