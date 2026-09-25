param(
    [switch]$Mobile,
    [switch]$RebuildMobile,
    [switch]$RestartExisting,
    [switch]$NoBrowser,
    [switch]$EnableSimulator
)

$ErrorActionPreference = "Stop"

# ============================================================
# SA COMMAND + SA EMPLOYEE - UNIFIED DEMO STARTUP
# ============================================================

$root = "C:\Users\xtrar\Desktop\ERP"
$logDir = Join-Path $root "logs\demo"
$mobileDir = Join-Path $root "apps\navigator-mobile"
$demoApk = Join-Path $root "demo-artifacts\mobile\SA-Employee-v2-demo.apk"

$mobileRequested = [bool]($Mobile -or $RebuildMobile)

Set-Location $root
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# ============================================================
# SHARED CONFIG
# ============================================================

$navigatorKey = "sa-navigator-demo-key-2026"
$pairingPepper = "sa-navigator-demo-pairing-2026"
$organizationId = "6be51f4b-e5bd-4e91-8d60-4e41a72ad86f"

# Real-phone testing must not be contaminated by fake simulator
# devices unless explicitly requested.
$simulatorEnabled = if ($EnableSimulator) {
    "true"
}
elseif ($mobileRequested) {
    "false"
}
else {
    "true"
}

# Restart Navigator-aware services whenever their runtime mode
# must be guaranteed.
$restartRuntimeServices = [bool](
    $RestartExisting -or
    $mobileRequested -or
    $EnableSimulator
)

Write-Host ""
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host "        SA COMMAND + SA EMPLOYEE DEMO" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host ""

Write-Host "  Mobile requested : $mobileRequested"
Write-Host "  Rebuild mobile   : $([bool]$RebuildMobile)"
Write-Host "  Simulator        : $simulatorEnabled"
Write-Host ""

# ============================================================
# ENVIRONMENT
# ============================================================

# SA Command
$env:APP_MODE = "demo"
$env:DEMO_SEED = "true"
$financeWorkbook = Join-Path $PSScriptRoot "demo-artifacts/finance-workbook.xlsx"
if (Test-Path -LiteralPath $financeWorkbook) {
    $env:FINANCE_WORKBOOK_PATH = $financeWorkbook
}

$env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:5432/sa_command"
$env:DATABASE_USERNAME = "sa_command"
$env:DATABASE_PASSWORD = "sa_command_dev"

$env:MESSAGING_PROVIDER = "console"
$env:MESSAGING_WORKER_ENABLED = "true"

# Navigator integration
$env:NAVIGATOR_ENABLED = "true"
$env:NAVIGATOR_GATEWAY_URL = "http://127.0.0.1:8091"
$env:NAVIGATOR_ORGANIZATION_PUBLIC_ID = $organizationId
$env:NAVIGATOR_SERVICE_KEY = $navigatorKey
$env:NAVIGATOR_SIMULATOR_ENABLED = $simulatorEnabled

# Navigator Gateway
$env:NAVIGATOR_DATABASE_URL = "jdbc:postgresql://127.0.0.1:5433/navigator"
$env:NAVIGATOR_DATABASE_USERNAME = "navigator"
$env:NAVIGATOR_DATABASE_PASSWORD = "navigator_demo"

$env:NAVIGATOR_ADMIN_KEY = $navigatorKey
$env:NAVIGATOR_PAIRING_PEPPER = $pairingPepper
$env:NAVIGATOR_ALLOWED_ORIGINS = "http://localhost:1420"

# Desktop
$env:VITE_APP_MODE = "demo"
$env:VITE_NAVIGATOR_ENABLED = "true"

# SA Employee demo build.
#
# EXPO_PUBLIC_* is bundled into release builds, so this MUST be
# defined before -RebuildMobile runs.
#
# During USB field testing:
#
# phone 127.0.0.1:8091
#        ↓ ADB reverse
# PC    127.0.0.1:8091
#
$env:EXPO_PUBLIC_NAVIGATOR_GATEWAY_URL = "http://127.0.0.1:8091"
# This Gradle project property is consumed only by the local demo rebuild.
# Production builds retain Android's default cleartext prohibition.
$env:ORG_GRADLE_PROJECT_saEmployeeDemoCleartext = "true"

# ============================================================
# HELPERS
# ============================================================

function Test-Http {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Url
    )

    try {
        $response = Invoke-WebRequest `
            -Uri $Url `
            -UseBasicParsing `
            -TimeoutSec 2

        return (
            $response.StatusCode -ge 200 -and
            $response.StatusCode -lt 500
        )
    }
    catch {
        return $false
    }
}

function Get-PortPid {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    try {
        $connection = Get-NetTCPConnection `
            -LocalPort $Port `
            -State Listen `
            -ErrorAction Stop |
            Select-Object -First 1

        return $connection.OwningProcess
    }
    catch {
        return $null
    }
}

function Test-Port {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    return [bool](Get-PortPid -Port $Port)
}

function Stop-Port {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $processId = Get-PortPid -Port $Port

    if (-not $processId) {
        return
    }

    Write-Host `
        "  STOP   $Name on :$Port (PID $processId)" `
        -ForegroundColor Yellow

    Stop-Process `
        -Id $processId `
        -Force `
        -ErrorAction SilentlyContinue

    for ($i = 0; $i -lt 15; $i++) {
        if (-not (Test-Port -Port $Port)) {
            return
        }

        Start-Sleep -Milliseconds 300
    }

    if (Test-Port -Port $Port) {
        throw "$Name did not release port $Port."
    }
}

function Wait-Http {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$Url,

        [int]$Attempts = 30
    )

    for ($i = 1; $i -le $Attempts; $i++) {

        if (Test-Http -Url $Url) {
            Write-Host "  OK     $Name" -ForegroundColor Green
            return $true
        }

        Write-Host `
            "  WAIT   $Name ($i/$Attempts)" `
            -ForegroundColor DarkYellow

        Start-Sleep -Seconds 2
    }

    Write-Host "  FAIL   $Name" -ForegroundColor Red
    return $false
}

function Wait-Port {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [int]$Port,

        [int]$Attempts = 30
    )

    for ($i = 1; $i -le $Attempts; $i++) {

        if (Test-Port -Port $Port) {
            Write-Host "  OK     $Name" -ForegroundColor Green
            return $true
        }

        Write-Host `
            "  WAIT   $Name ($i/$Attempts)" `
            -ForegroundColor DarkYellow

        Start-Sleep -Seconds 1
    }

    Write-Host "  FAIL   $Name" -ForegroundColor Red
    return $false
}

function Start-LoggedProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList,

        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,

        [Parameter(Mandatory = $true)]
        [string]$LogName
    )

    $stdout = Join-Path $logDir ($LogName + ".out.log")
    $stderr = Join-Path $logDir ($LogName + ".err.log")

    Remove-Item $stdout -Force -ErrorAction SilentlyContinue
    Remove-Item $stderr -Force -ErrorAction SilentlyContinue

    return Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -PassThru
}

function Show-ServiceFailure {
    param(
        [string]$Name,
        [string]$LogName
    )

    Write-Host ""
    Write-Host "$Name failed." -ForegroundColor Red
    Write-Host ""

    $stdout = Join-Path $logDir ($LogName + ".out.log")
    $stderr = Join-Path $logDir ($LogName + ".err.log")

    if (Test-Path $stdout) {
        Write-Host "--- stdout ---" -ForegroundColor DarkGray

        Get-Content `
            $stdout `
            -Tail 50 `
            -ErrorAction SilentlyContinue
    }

    if (Test-Path $stderr) {
        Write-Host ""
        Write-Host "--- stderr ---" -ForegroundColor DarkGray

        Get-Content `
            $stderr `
            -Tail 50 `
            -ErrorAction SilentlyContinue
    }
}

function Get-MobilePackageId {
    param(
        [Parameter(Mandatory = $true)]
        [string]$MobileDirectory
    )

    $appJson = Join-Path $MobileDirectory "app.json"

    if (Test-Path $appJson) {
        try {
            $config = Get-Content $appJson -Raw | ConvertFrom-Json
            $packageId = $config.expo.android.package

            if (-not [string]::IsNullOrWhiteSpace($packageId)) {
                return [string]$packageId
            }
        }
        catch {
            Write-Host `
                "  WARN   Could not read Android package from app.json." `
                -ForegroundColor Yellow
        }
    }

    # Existing SA Employee / Navigator Android identity.
    # Only used if app.json cannot be read.
    return "in.saproductions.navigator"
}

function Test-AndroidPackageInstalled {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Adb,

        [Parameter(Mandatory = $true)]
        [string]$Serial,

        [Parameter(Mandatory = $true)]
        [string]$PackageId
    )

    $result = @(
        & $Adb `
            -s $Serial `
            shell pm list packages $PackageId `
            2>$null
    )

    # `pm list packages <filter>` performs a substring match. Require the
    # exact Android package so a suffix such as `.dev` cannot be mistaken
    # for the release-like SA Employee package.
    $expectedPackageLine = "^package:" + [regex]::Escape($PackageId) + "$"

    return [bool](
        $result |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -match $expectedPackageLine } |
        Select-Object -First 1
    )
}

function Start-AndroidPackage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Adb,

        [Parameter(Mandatory = $true)]
        [string]$Serial,

        [Parameter(Mandatory = $true)]
        [string]$PackageId
    )

    # `monkey -p <package> -c LAUNCHER 1` lets Android resolve the actual
    # launcher activity without hard-coding MainActivity. monkey normally
    # writes diagnostics to stderr; invoking adb through Start-Process keeps
    # that output from becoming NativeCommandError while this script uses
    # $ErrorActionPreference = "Stop".
    Write-Host `
        "  START  SA Employee" `
        -ForegroundColor Cyan

    $launchId = [guid]::NewGuid().ToString("N")
    $launchStdout = Join-Path $env:TEMP ("sa-employee-launch-" + $launchId + ".out.log")
    $launchStderr = Join-Path $env:TEMP ("sa-employee-launch-" + $launchId + ".err.log")

    try {
        $launchProcess = Start-Process `
            -FilePath $Adb `
            -ArgumentList @(
                "-s",
                $Serial,
                "shell",
                "monkey",
                "-p",
                $PackageId,
                "-c",
                "android.intent.category.LAUNCHER",
                "1"
            ) `
            -WindowStyle Hidden `
            -RedirectStandardOutput $launchStdout `
            -RedirectStandardError $launchStderr `
            -Wait `
            -PassThru

        $launchOutput = @()

        if (Test-Path $launchStdout) {
            $launchOutput += @(Get-Content $launchStdout -ErrorAction SilentlyContinue)
        }

        if (Test-Path $launchStderr) {
            $launchOutput += @(Get-Content $launchStderr -ErrorAction SilentlyContinue)
        }

        $launchText = ($launchOutput | Out-String)

        if (
            $launchProcess.ExitCode -ne 0 -or
            $launchText -match "No activities found" -or
            $launchText -match "monkey aborted"
        ) {
            Write-Host ""
            Write-Host "  Android launch output:" -ForegroundColor Yellow

            $launchOutput | ForEach-Object {
                Write-Host "    $_" -ForegroundColor DarkGray
            }

            throw `
                "Android could not launch SA Employee package '$PackageId'."
        }
    }
    finally {
        Remove-Item $launchStdout -Force -ErrorAction SilentlyContinue
        Remove-Item $launchStderr -Force -ErrorAction SilentlyContinue
    }
}

# ============================================================
# 0. TOOLS
# ============================================================

Write-Host "[0/6] Checking tools..." -ForegroundColor Cyan

$mvn = (Get-Command mvn.cmd -ErrorAction Stop).Source
$npm = (Get-Command npm.cmd -ErrorAction Stop).Source
$npx = $null

if ($RebuildMobile) {
    $npx = (Get-Command npx.cmd -ErrorAction Stop).Source
}

docker version *> $null

if ($LASTEXITCODE -ne 0) {
    throw `
        "Docker is not available or Docker Desktop is not running."
}

Write-Host "  OK     Docker" -ForegroundColor Green
Write-Host "  OK     Maven" -ForegroundColor Green
Write-Host "  OK     npm" -ForegroundColor Green

if ($RebuildMobile) {
    Write-Host "  OK     npx" -ForegroundColor Green
}

# ============================================================
# 1. DATABASES
# ============================================================

Write-Host ""
Write-Host "[1/6] Starting databases..." -ForegroundColor Cyan

docker compose up -d postgres navigator-postgres

if ($LASTEXITCODE -ne 0) {
    throw "Docker database startup failed."
}

Start-Sleep -Seconds 2

docker compose ps postgres navigator-postgres

# ============================================================
# 2. NAVIGATOR GATEWAY
# ============================================================

Write-Host ""
Write-Host "[2/6] Navigator Gateway..." -ForegroundColor Cyan

if ($restartRuntimeServices) {
    Stop-Port `
        -Port 8091 `
        -Name "Navigator Gateway"
}

if (
    -not $restartRuntimeServices -and
    (Test-Http -Url "http://127.0.0.1:8091/actuator/health")
) {
    Write-Host `
        "  REUSE  Navigator Gateway :8091" `
        -ForegroundColor DarkGreen
}
else {

    $existingGatewayPid = Get-PortPid -Port 8091

    if ($existingGatewayPid) {
        throw `
            "Port 8091 is occupied by PID $existingGatewayPid. Use -RestartExisting."
    }

    Write-Host "  START  Navigator Gateway :8091"

    Start-LoggedProcess `
        -FilePath $mvn `
        -ArgumentList @(
            "-f",
            "apps/navigator-gateway/pom.xml",
            "spring-boot:run"
        ) `
        -WorkingDirectory $root `
        -LogName "navigator-gateway" |
        Out-Null
}

$gatewayOk = Wait-Http `
    -Name "Navigator Gateway :8091" `
    -Url "http://127.0.0.1:8091/actuator/health" `
    -Attempts 30

if (-not $gatewayOk) {

    Show-ServiceFailure `
        -Name "Navigator Gateway" `
        -LogName "navigator-gateway"

    exit 1
}

# ============================================================
# 3. SA COMMAND BACKEND
# ============================================================

Write-Host ""
Write-Host "[3/6] SA Command Backend..." -ForegroundColor Cyan

if ($restartRuntimeServices) {
    Stop-Port `
        -Port 8080 `
        -Name "SA Command Backend"
}

if (
    -not $restartRuntimeServices -and
    (Test-Http -Url "http://127.0.0.1:8080/actuator/health")
) {
    Write-Host `
        "  REUSE  SA Command Backend :8080" `
        -ForegroundColor DarkGreen
}
else {

    $existingBackendPid = Get-PortPid -Port 8080

    if ($existingBackendPid) {
        throw `
            "Port 8080 is occupied by PID $existingBackendPid. Use -RestartExisting."
    }

    Write-Host "  START  SA Command Backend :8080"

    Start-LoggedProcess `
        -FilePath $mvn `
        -ArgumentList @(
            "-f",
            "apps/backend/pom.xml",
            "spring-boot:run"
        ) `
        -WorkingDirectory $root `
        -LogName "backend" |
        Out-Null
}

$backendOk = Wait-Http `
    -Name "SA Command Backend :8080" `
    -Url "http://127.0.0.1:8080/actuator/health" `
    -Attempts 30

if (-not $backendOk) {

    Show-ServiceFailure `
        -Name "SA Command Backend" `
        -LogName "backend"

    exit 1
}

# ============================================================
# 4. DESKTOP FRONTEND
# ============================================================

Write-Host ""
Write-Host "[4/6] SA Command Desktop..." -ForegroundColor Cyan

# Vite can bind localhost through IPv6 (::1), which can make a
# 127.0.0.1 HTTP health check fail even while Vite is healthy.
# Therefore readiness is checked by TCP port.

if ($RestartExisting) {
    Stop-Port `
        -Port 1420 `
        -Name "Desktop UI"
}

if (
    -not $RestartExisting -and
    (Test-Port -Port 1420)
) {
    Write-Host `
        "  REUSE  Desktop UI :1420" `
        -ForegroundColor DarkGreen
}
else {

    $existingFrontendPid = Get-PortPid -Port 1420

    if ($existingFrontendPid) {
        throw `
            "Port 1420 is occupied by PID $existingFrontendPid. Use -RestartExisting."
    }

    Write-Host "  START  Desktop UI :1420"

    Start-LoggedProcess `
        -FilePath $npm `
        -ArgumentList @(
            "--prefix",
            "apps/desktop",
            "run",
            "dev:demo"
        ) `
        -WorkingDirectory $root `
        -LogName "desktop" |
        Out-Null
}

$frontendOk = Wait-Port `
    -Name "Desktop UI :1420" `
    -Port 1420 `
    -Attempts 30

if (-not $frontendOk) {

    Show-ServiceFailure `
        -Name "Desktop frontend" `
        -LogName "desktop"

    exit 1
}

if (-not $NoBrowser) {
    Start-Process "http://localhost:1420"
}

# ============================================================
# CORE STATUS
# ============================================================

Write-Host ""
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host "          SA COMMAND CORE IS READY" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host ""

Write-Host "  Desktop           http://localhost:1420"
Write-Host "  Backend           http://localhost:8080"
Write-Host "  Navigator Gateway http://localhost:8091"
Write-Host "  SA Command DB     localhost:5432"
Write-Host "  Navigator DB      localhost:5433"
Write-Host "  Simulator         $simulatorEnabled"
Write-Host ""

# ============================================================
# 5. SA EMPLOYEE ANDROID
# ============================================================

Write-Host "[5/6] SA Employee Android..." -ForegroundColor Cyan

if (-not $mobileRequested) {

    Write-Host `
        "  SKIP   Mobile not requested." `
        -ForegroundColor DarkGray

    Write-Host `
        "         Use -Mobile to launch installed SA Employee."

    Write-Host `
        "         Use -RebuildMobile after mobile/native changes."
}
else {

    # --------------------------------------------------------
    # Android SDK / ADB
    # --------------------------------------------------------

    $sdk = Join-Path `
        $env:LOCALAPPDATA `
        "Android\Sdk"

    $adb = Join-Path `
        $sdk `
        "platform-tools\adb.exe"

    if (-not (Test-Path $adb)) {
        throw "ADB was not found at: $adb"
    }

    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk

    $platformTools = Join-Path `
        $sdk `
        "platform-tools"

    if ($env:Path -notlike "*$platformTools*") {
        $env:Path = `
            $env:Path + ";" + $platformTools
    }

    Write-Host `
        "  OK     Android SDK: $sdk" `
        -ForegroundColor Green

    # --------------------------------------------------------
    # Gradle SDK location
    # --------------------------------------------------------

    $localProperties = Join-Path `
        $mobileDir `
        "android\local.properties"

    $sdkForGradle = $sdk.Replace("\", "/")

    if (Test-Path (Split-Path $localProperties -Parent)) {

        Set-Content `
            -Path $localProperties `
            -Value ("sdk.dir=" + $sdkForGradle) `
            -Encoding ASCII `
            -NoNewline
    }

    # --------------------------------------------------------
    # ADB device discovery
    # --------------------------------------------------------

    & $adb start-server | Out-Null

    $adbOutput = @(
        & $adb devices -l
    )

    $authorizedDevices = @(
        $adbOutput |
        Select-Object -Skip 1 |
        Where-Object {
            $_ -match "^\S+\s+device(?:\s|$)"
        }
    )

    $unauthorizedDevices = @(
        $adbOutput |
        Select-Object -Skip 1 |
        Where-Object {
            $_ -match "^\S+\s+unauthorized(?:\s|$)"
        }
    )

    $offlineDevices = @(
        $adbOutput |
        Select-Object -Skip 1 |
        Where-Object {
            $_ -match "^\S+\s+offline(?:\s|$)"
        }
    )

    if ($authorizedDevices.Count -eq 0) {

        Write-Host ""

        if ($unauthorizedDevices.Count -gt 0) {

            Write-Host `
                "  WARN   Android phone connected but unauthorized." `
                -ForegroundColor Yellow

            Write-Host `
                "         Unlock it and accept 'Allow USB debugging'."
        }
        elseif ($offlineDevices.Count -gt 0) {

            Write-Host `
                "  WARN   Android phone is visible to ADB but offline." `
                -ForegroundColor Yellow

            Write-Host `
                "         Reconnect USB, unlock the phone, then retry."
        }
        else {

            Write-Host `
                "  WARN   No authorized Android phone detected." `
                -ForegroundColor Yellow

            Write-Host `
                "         Check USB data mode and USB debugging."
        }

        Write-Host ""
        & $adb devices -l
        Write-Host ""

        Write-Host `
            "  Core SA Command services are still running." `
            -ForegroundColor DarkGray
    }
    else {

        if ($authorizedDevices.Count -gt 1) {

            Write-Host `
                "  WARN   Multiple Android devices found; using first." `
                -ForegroundColor Yellow
        }

        $deviceSerial = (
            $authorizedDevices[0] -split "\s+"
        )[0]

        $deviceModel = (
            & $adb `
                -s $deviceSerial `
                shell getprop ro.product.model
        ).Trim()

        if ([string]::IsNullOrWhiteSpace($deviceModel)) {
            $deviceModel = $deviceSerial
        }

        Write-Host `
            "  OK     Android device: $deviceModel ($deviceSerial)" `
            -ForegroundColor Green

        # ----------------------------------------------------
        # Gateway bridge
        # ----------------------------------------------------
        #
        # SA Employee V2 release-like builds bundle JavaScript.
        # They do NOT need Metro / port 8081.
        #
        # Only the local demo Gateway needs a USB bridge.
        # ----------------------------------------------------

        & $adb `
            -s $deviceSerial `
            reverse tcp:8091 tcp:8091

        if ($LASTEXITCODE -ne 0) {
            throw `
                "Failed to create ADB reverse tunnel for Navigator Gateway :8091."
        }

        Write-Host `
            "  OK     ADB reverse: phone :8091 -> PC :8091" `
            -ForegroundColor Green

        # ----------------------------------------------------
        # Android package identity
        # ----------------------------------------------------

        $mobilePackage = Get-MobilePackageId `
            -MobileDirectory $mobileDir

        Write-Host `
            "  INFO   Android package: $mobilePackage" `
            -ForegroundColor DarkGray

        # ----------------------------------------------------
        # OPTIONAL REBUILD
        # ----------------------------------------------------
        #
        # This is the ONLY path that compiles the Android app.
        #
        # Normal -Mobile startup never rebuilds.
        #
        # Release variant embeds the JS bundle, so the installed
        # app can run without Metro.
        # ----------------------------------------------------

        if ($RebuildMobile) {

            Write-Host ""
            Write-Host `
                "  REBUILD SA Employee release-like Android app..." `
                -ForegroundColor Cyan

            Write-Host `
                "          JavaScript will be bundled into the app." `
                -ForegroundColor DarkGray

            Write-Host `
                "          Metro will not be required afterward." `
                -ForegroundColor DarkGray

            Write-Host ""

            Push-Location $mobileDir

            try {

                & $npx expo run:android `
                    --variant release `
                    --device $deviceModel

                if ($LASTEXITCODE -ne 0) {
                    throw `
                        "SA Employee Android release build/install failed."
                }
            }
            finally {
                Pop-Location
            }
        }

        # ----------------------------------------------------
        # CHECK INSTALLED APP
        # ----------------------------------------------------

        $installed = Test-AndroidPackageInstalled `
            -Adb $adb `
            -Serial $deviceSerial `
            -PackageId $mobilePackage

        # ----------------------------------------------------
        # FALLBACK: INSTALL EXISTING V2 APK
        # ----------------------------------------------------
        #
        # If SA Employee is not installed, do NOT rebuild just
        # because of that. Use the already-built V2 APK first.
        # ----------------------------------------------------

        if (-not $installed) {

            if (Test-Path $demoApk) {

                Write-Host `
                    "  INSTALL SA Employee V2 demo APK..." `
                    -ForegroundColor Cyan

                & $adb `
                    -s $deviceSerial `
                    install -r $demoApk

                if ($LASTEXITCODE -ne 0) {
                    throw `
                        "Failed to install SA Employee APK: $demoApk"
                }

                $installed = Test-AndroidPackageInstalled `
                    -Adb $adb `
                    -Serial $deviceSerial `
                    -PackageId $mobilePackage
            }
            else {

                throw `
                    "SA Employee is not installed and the V2 APK was not found. Run with -RebuildMobile."
            }
        }

        if (-not $installed) {

            throw `
                "SA Employee package '$mobilePackage' is still not installed."
        }

        Write-Host `
            "  OK     SA Employee installed" `
            -ForegroundColor Green

        # ----------------------------------------------------
        # LAUNCH INSTALLED SA EMPLOYEE
        # ----------------------------------------------------
        #
        # No Gradle.
        # No Expo rebuild.
        # No Metro.
        # ----------------------------------------------------

        Start-AndroidPackage `
            -Adb $adb `
            -Serial $deviceSerial `
            -PackageId $mobilePackage

        Write-Host `
            "  OK     SA Employee launched" `
            -ForegroundColor Green

        Write-Host `
            "  INFO   Gateway available through ADB reverse :8091" `
            -ForegroundColor DarkGray

        if (-not $EnableSimulator) {

            Write-Host `
                "  INFO   Navigator simulator disabled for real-phone test." `
                -ForegroundColor DarkGray
        }
    }
}

# ============================================================
# 6. FINAL STATUS
# ============================================================

Write-Host ""
Write-Host "[6/6] Status" -ForegroundColor Cyan
Write-Host ""

Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host "             SA COMMAND IS READY" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor DarkGray
Write-Host ""

Write-Host "Desktop:"
Write-Host "  http://localhost:1420"
Write-Host ""

Write-Host "Backend:"
Write-Host "  http://localhost:8080"
Write-Host ""

Write-Host "Navigator Gateway:"
Write-Host "  http://localhost:8091"
Write-Host ""

Write-Host "Databases:"
Write-Host "  SA Command :5432"
Write-Host "  Navigator  :5433"
Write-Host ""

Write-Host "Navigator simulator:"
Write-Host "  $simulatorEnabled"
Write-Host ""

if ($mobileRequested) {

    Write-Host "SA Employee:"
    Write-Host "  Gateway through ADB reverse :8091"
    Write-Host "  Metro not required for release-like build"
    Write-Host ""
}

Write-Host "Demo Login:"
Write-Host "  owner@saproduction.local"
Write-Host "  SADemo!2026"
Write-Host ""

Write-Host "Logs:"
Write-Host "  $logDir"
Write-Host ""

Write-Host "Gateway log:"
Write-Host "  Get-Content '$logDir\navigator-gateway.out.log' -Wait"
Write-Host ""

Write-Host "Backend log:"
Write-Host "  Get-Content '$logDir\backend.out.log' -Wait"
Write-Host ""

Write-Host "Desktop log:"
Write-Host "  Get-Content '$logDir\desktop.out.log' -Wait"
Write-Host ""

Write-Host "Commands:" -ForegroundColor Cyan
Write-Host ""

Write-Host "  Core only:"
Write-Host "    .\start-demo.ps1"
Write-Host ""

Write-Host "  Core + installed SA Employee:"
Write-Host "    .\start-demo.ps1 -Mobile"
Write-Host ""

Write-Host "  Rebuild/install SA Employee, then launch:"
Write-Host "    .\start-demo.ps1 -RebuildMobile"
Write-Host ""

Write-Host "  Restart all core services:"
Write-Host "    .\start-demo.ps1 -RestartExisting"
Write-Host ""

Write-Host "  Desktop simulator mode:"
Write-Host "    .\start-demo.ps1 -EnableSimulator"
Write-Host ""
