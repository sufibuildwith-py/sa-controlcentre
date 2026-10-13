<#
.SYNOPSIS
    Interactive Google Drive Authorization Setup for SA Command Backups
.DESCRIPTION
    Runs rclone OAuth authorization using the owner's dedicated Google Cloud OAuth client,
    verifies access to SA_COMMAND_DB_BACKUPS folder, creates required subfolders,
    and copies the rclone config to the clipboard for GitHub Actions secret configuration.
#>

param(
    [string]$ClientId = $env:GDRIVE_CLIENT_ID,
    [string]$ClientSecret = $env:GDRIVE_CLIENT_SECRET
)

$ErrorActionPreference = "Stop"

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " SA COMMAND — GOOGLE DRIVE BACKUP AUTHORIZATION SETUP" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. Locate rclone
$rcloneExe = "rclone"
if (-not (Get-Command "rclone" -ErrorAction SilentlyContinue)) {
    $portable = "c:\Users\xtrar\tools\rclone\rclone.exe"
    if (Test-Path $portable) {
        $rcloneExe = $portable
    } else {
        Write-Error "rclone binary not found. Please ensure rclone is installed."
        exit 1
    }
}

Write-Host "`n[1/4] Using rclone binary: $rcloneExe" -ForegroundColor Green

# 2. OAuth Client Details
if (-not $ClientId) {
    $ClientId = Read-Host "Enter Google OAuth Client ID"
}
if (-not $ClientSecret) {
    $ClientSecret = Read-Host "Enter Google OAuth Client Secret"
}

if (-not $ClientId -or -not $ClientSecret) {
    Write-Error "Client ID and Client Secret are required."
    exit 1
}

Write-Host "`n[2/4] Starting OAuth Authorization..." -ForegroundColor Yellow
Write-Host "  -> A browser window will open shortly." -ForegroundColor Gray
Write-Host "  -> Sign in to your Google Account." -ForegroundColor Gray
Write-Host "  -> If you see 'Google hasn't verified this app', click 'Advanced' > 'Go to SA Command Backups (unsafe)'." -ForegroundColor Gray
Write-Host "  -> Click 'Continue' to grant Google Drive backup access." -ForegroundColor Gray

& $rcloneExe config create gdrive drive `
    client_id="$ClientId" `
    client_secret="$ClientSecret" `
    scope="drive"

if ($LASTEXITCODE -ne 0) {
    Write-Error "rclone authorization failed or was cancelled."
    exit 1
}

Write-Host "`n[3/4] Testing Google Drive connection..." -ForegroundColor Yellow

# Test listing
$remotes = & $rcloneExe listremotes
if ($remotes -notcontains "gdrive:") {
    Write-Error "Remote 'gdrive:' was not found after configuration."
    exit 1
}

# Verify and create backup folders
Write-Host "  -> Initializing directory structure in Google Drive..." -ForegroundColor Gray
& $rcloneExe mkdir "gdrive:SA_COMMAND_DB_BACKUPS/daily"
& $rcloneExe mkdir "gdrive:SA_COMMAND_DB_BACKUPS/pre-migration"
& $rcloneExe mkdir "gdrive:SA_COMMAND_DB_BACKUPS/manifests"

$dirs = & $rcloneExe lsd "gdrive:SA_COMMAND_DB_BACKUPS"
Write-Host "  -> Verified directories in SA_COMMAND_DB_BACKUPS:" -ForegroundColor Green
$dirs | ForEach-Object { Write-Host "     $_" -ForegroundColor Cyan }

# 4. Prepare GitHub Actions Secret
Write-Host "`n[4/4] Preparing GitHub Actions Secret..." -ForegroundColor Yellow
$confPath = "$env:APPDATA\rclone\rclone.conf"
if (-not (Test-Path $confPath)) {
    $confPath = "$env:USERPROFILE\.config\rclone\rclone.conf"
}

if (Test-Path $confPath) {
    $confContent = Get-Content -Raw $confPath
    Set-Clipboard -Value $confContent
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host " SUCCESS! rclone Google Drive authorization is complete." -ForegroundColor Green
    Write-Host "======================================================================" -ForegroundColor Green
    Write-Host "`nThe rclone configuration has been copied directly to your CLIPBOARD." -ForegroundColor Cyan
    Write-Host "`nNext Step (10 seconds):" -ForegroundColor White
    Write-Host "1. Go to: https://github.com/sufibuildwith-py/sa-controlcentre/settings/secrets/actions" -ForegroundColor White
    Write-Host "2. Click 'New repository secret'" -ForegroundColor White
    Write-Host "3. Name:  GDRIVE_RCLONE_CONFIG" -ForegroundColor Yellow
    Write-Host "4. Value: Paste your clipboard (Ctrl+V)" -ForegroundColor Yellow
    Write-Host "5. Click 'Add secret'." -ForegroundColor White
    Write-Host "======================================================================" -ForegroundColor Green
} else {
    Write-Warning "Could not locate rclone.conf automatically at $confPath."
}
