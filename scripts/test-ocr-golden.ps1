$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$fixtures = Join-Path $root 'fixtures'
$check = Join-Path $root 'ocr-service/tests/golden_check.py'

Push-Location $root
try {
    docker compose run --rm --no-deps `
        --volume "${fixtures}:/fixtures:ro" `
        --volume "${check}:/checks/golden_check.py:ro" `
        ocr python /checks/golden_check.py
    if ($LASTEXITCODE -ne 0) { throw 'OCR golden checks failed.' }
} finally {
    Pop-Location
}
