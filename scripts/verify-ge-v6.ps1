$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot

try
{
    Write-Host "[1/2] Running GE Auto-Trader V6 + GE Bridge verification..."
    & .\gradlew.bat :client:test `
        --tests "net.runelite.client.plugins.geautotrader.GeAutoTraderV6EndToEndTest" `
        --tests "net.runelite.client.plugins.gebridge.*"

    if ($LASTEXITCODE -ne 0)
    {
        throw "GE Auto-Trader V6 verification tests failed with exit code $LASTEXITCODE."
    }

    Write-Host "[2/2] Building shaded RuneLite jar..."
    & .\gradlew.bat :client:shadowJar

    if ($LASTEXITCODE -ne 0)
    {
        throw "RuneLite shaded-jar build failed with exit code $LASTEXITCODE."
    }

    Write-Host ""
    Write-Host "GE Auto-Trader V6 verification passed."
    Write-Host "Jar: runelite-client\build\libs\client-1.12.39-SNAPSHOT-shaded.jar"
}
finally
{
    Pop-Location
}
