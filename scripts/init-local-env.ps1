$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$target = Join-Path $root '.env'
if (Test-Path -LiteralPath $target) {
    Write-Host '.env already exists; no values were changed.'
    exit 0
}

function New-RandomBase64([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($buffer) } finally { $generator.Dispose() }
    return [Convert]::ToBase64String($buffer)
}

$dbPassword = New-RandomBase64 24
$minioPassword = New-RandomBase64 24
$masterKey = New-RandomBase64 32

$content = @"
POSTGRES_DB=spendify
POSTGRES_USER=spendify
POSTGRES_PASSWORD=$dbPassword
MINIO_ROOT_USER=spendify-local
MINIO_ROOT_PASSWORD=$minioPassword
SPENDIFY_MASTER_KEY_BASE64=$masterKey
SPENDIFY_AI_ENABLED=false
SPENDIFY_AI_MODEL=none
GOOGLE_API_KEY=
"@
[System.IO.File]::WriteAllText($target, $content, [System.Text.UTF8Encoding]::new($false))
Write-Host 'Created ignored .env with new local-only secrets.'
