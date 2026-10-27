$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

$jdk = Get-ChildItem (Join-Path $root '.tools/jdk21') -Directory | Select-Object -First 1
$maven = Get-ChildItem (Join-Path $root '.tools/maven') -Directory | Select-Object -First 1
if (-not $jdk -or -not $maven) {
    throw 'Portable Java/Maven tools are unavailable. Follow docs/local-development.md.'
}

$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($jdk.FullName)\bin;$($maven.FullName)\bin;$env:Path"

& (Join-Path $maven.FullName 'bin/mvn.cmd') -q -f (Join-Path $root 'backend/pom.xml') test
if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed.' }

$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
Push-Location (Join-Path $root 'frontend')
try {
    & $npm run lint
    if ($LASTEXITCODE -ne 0) { throw 'Frontend lint failed.' }
    & $npm test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    & $npm run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }

Push-Location (Join-Path $root 'ocr-service')
try {
    & .\.venv\Scripts\python.exe -m pytest -q
    if ($LASTEXITCODE -ne 0) { throw 'OCR API tests failed.' }
} finally { Pop-Location }

Write-Host 'All available local checks passed.'
