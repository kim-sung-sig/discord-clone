# Toolchain Warning Scan

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
$artifactDir = Join-Path $PSScriptRoot "artifacts/toolchain"
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

$gradleLog = Join-Path $artifactDir "gradle-warning-mode-all.log"
$frontendLog = Join-Path $artifactDir "nuxt-build.log"

function Test-IsWindows {
    return [System.Environment]::OSVersion.Platform -eq "Win32NT"
}

function GradleWrapper {
    if (Test-IsWindows) {
        return Join-Path $repo "gradlew.bat"
    }
    return Join-Path $repo "gradlew"
}

Push-Location $repo
try {
    $gradle = GradleWrapper
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
    & $gradle test --warning-mode all *>&1 | Tee-Object -FilePath $gradleLog
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "Gradle warning scan failed with exit code $exitCode"
    }
}
finally {
    Pop-Location
}

Push-Location (Join-Path $repo "apps/web")
try {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
    & npm run build *>&1 | Tee-Object -FilePath $frontendLog
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        throw "Nuxt build scan failed with exit code $exitCode"
    }
}
finally {
    Pop-Location
}

Write-Output "Toolchain warning scan complete. Logs: $artifactDir"
