# EVE Intelligence Verification Script
$loginBody = @{ email = 'owner@saproduction.local'; password = 'SADemo!2026' } | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$token = $loginRes.data.token
Write-Host "Logged in successfully. Token length: $($token.Length)"

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

function New-EveSession($title) {
    $body = @{ title = $title } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/eve/sessions' -Method Post -Headers $headers -Body $body
    return $res.data.id
}

function Invoke-EveQuery($sessionId, $prompt) {
    $body = @{ prompt = $prompt; sessionId = $sessionId } | ConvertTo-Json
    $res = Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/eve/query' -Method Post -Headers $headers -Body $body
    return $res.data
}

Write-Host "`n=== SCENARIO A: SCREENSHOT CLARIFICATION RESOLUTION ==="
$sessionA = New-EveSession "Scenario A - Screenshot Clarification"
Write-Host "Created Session A: $sessionA"

$turnA1 = Invoke-EveQuery $sessionA "Aur uska equipment?"
Write-Host "Turn 1 Prompt: Aur uska equipment?"
Write-Host "Turn 1 Status: $($turnA1.status)"
Write-Host "Turn 1 Content: $($turnA1.message.content)"

$turnA2 = Invoke-EveQuery $sessionA "Mips wala event"
Write-Host "`nTurn 2 Prompt: Mips wala event"
Write-Host "Turn 2 Status: $($turnA2.status)"
Write-Host "Turn 2 Content: $($turnA2.message.content)"

$turnA3 = Invoke-EveQuery $sessionA "aur usme kitne log kaam kar rahe hain?"
Write-Host "`nTurn 3 Prompt: aur usme kitne log kaam kar rahe hain?"
Write-Host "Turn 3 Status: $($turnA3.status)"
Write-Host "Turn 3 Content: $($turnA3.message.content)"

Write-Host "`n=== SCENARIO B: MULTI-TURN TOPIC SWITCHING ==="
$sessionB = New-EveSession "Scenario B - Topic Switching"
Write-Host "Created Session B: $sessionB"

$turnB1 = Invoke-EveQuery $sessionB "Cultural Event MIPS ka client kaun hai?"
Write-Host "Turn 1 Prompt: Cultural Event MIPS ka client kaun hai?"
Write-Host "Turn 1 Status: $($turnB1.status)"
Write-Host "Turn 1 Content: $($turnB1.message.content)"

$turnB2 = Invoke-EveQuery $sessionB "Achha Kabir Singh ko kitna dena hai?"
Write-Host "`nTurn 2 Prompt: Achha Kabir Singh ko kitna dena hai?"
Write-Host "Turn 2 Status: $($turnB2.status)"
Write-Host "Turn 2 Content: $($turnB2.message.content)"

$turnB3 = Invoke-EveQuery $sessionB "Uska production kaunsa hai?"
Write-Host "`nTurn 3 Prompt: Uska production kaunsa hai?"
Write-Host "Turn 3 Status: $($turnB3.status)"
Write-Host "Turn 3 Content: $($turnB3.message.content)"

$turnB4 = Invoke-EveQuery $sessionB "Usme kaun kaam kar raha hai?"
Write-Host "`nTurn 4 Prompt: Usme kaun kaam kar raha hai?"
Write-Host "Turn 4 Status: $($turnB4.status)"
Write-Host "Turn 4 Content: $($turnB4.message.content)"

Write-Host "`n=== SCENARIO C: FRESH SESSION PRONOUN QUERY SAFETY ==="
$sessionC = New-EveSession "Scenario C - Fresh Pronoun Safety"
Write-Host "Created Session C: $sessionC"

$turnC1 = Invoke-EveQuery $sessionC "uska kaunsa task open hai?"
Write-Host "Fresh Session Pronoun Prompt: uska kaunsa task open hai?"
Write-Host "Turn 1 Status: $($turnC1.status)"
Write-Host "Turn 1 Content: $($turnC1.message.content)"

Write-Host "`n=== SCENARIO D: CANDIDATE DISAMBIGUATION ==="
$sessionD = New-EveSession "Scenario D - Disambiguation"
Write-Host "Created Session D: $sessionD"

$turnD1 = Invoke-EveQuery $sessionD "led event ka equipment dikhao"
Write-Host "Turn 1 Prompt: led event ka equipment dikhao"
Write-Host "Turn 1 Status: $($turnD1.status)"
Write-Host "Turn 1 Content: $($turnD1.message.content)"
Write-Host "Turn 1 Candidates Count: $($turnD1.candidates.Count)"

if ($turnD1.candidates.Count -gt 1) {
    $turnD2 = Invoke-EveQuery $sessionD "haan second wala"
    Write-Host "`nTurn 2 Prompt: haan second wala"
    Write-Host "Turn 2 Status: $($turnD2.status)"
    Write-Host "Turn 2 Content: $($turnD2.message.content)"
}

Write-Host "`n=== ALL LIVE VERIFICATION CHECKS COMPLETED ==="
