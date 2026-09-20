$ErrorActionPreference = "Stop"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$body = @{ email = "owner@saproduction.local"; password = "SADemo!2026" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -ContentType "application/json" -Body $body -WebSession $session | Out-Null
$result = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/demo/reset" -Method Post -ContentType "application/json" -WebSession $session
$result.data | ConvertTo-Json
