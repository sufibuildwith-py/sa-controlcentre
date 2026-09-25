$ErrorActionPreference = "SilentlyContinue"

$root = "C:\Users\xtrar\Desktop\ERP"
Set-Location $root

Write-Host ""
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host "           STOPPING SA COMMAND DEMO" -ForegroundColor Yellow
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host ""

$services = @(
    @{ Port = 8081; Name = "Expo Metro" },
    @{ Port = 1420; Name = "Desktop UI" },
    @{ Port = 8080; Name = "SA Command Backend" },
    @{ Port = 8091; Name = "Navigator Gateway" }
)

foreach ($service in $services) {

    $connections = Get-NetTCPConnection `
        -LocalPort $service.Port `
        -State Listen `
        -ErrorAction SilentlyContinue

    if (-not $connections) {
        Write-Host "  OFF    $($service.Name) :$($service.Port)" -ForegroundColor DarkGray
        continue
    }

    $processIds = @(
        $connections |
        Select-Object -ExpandProperty OwningProcess -Unique
    )

    foreach ($processId in $processIds) {

        Write-Host "  STOP   $($service.Name) :$($service.Port) (PID $processId)" -ForegroundColor Yellow

        Stop-Process `
            -Id $processId `
            -Force `
            -ErrorAction SilentlyContinue
    }
}

Write-Host ""
Write-Host "Stopping Docker databases..." -ForegroundColor Cyan

docker compose stop postgres navigator-postgres

# Remove USB reverse tunnels if ADB/device is available
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

if (Test-Path $adb) {

    $devices = @(
        & $adb devices |
        Select-Object -Skip 1 |
        Where-Object { $_ -match "\sdevice$" }
    )

    if ($devices.Count -gt 0) {

        & $adb reverse --remove tcp:8091 *> $null
        & $adb reverse --remove tcp:8081 *> $null

        Write-Host "  OK     ADB reverse tunnels removed" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host "           SA COMMAND DEMO STOPPED" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host ""