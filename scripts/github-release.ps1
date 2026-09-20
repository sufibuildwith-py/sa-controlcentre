param([ValidateSet('Dispatch','Status','Download')][string]$Action='Status',[string]$Ref='main',[long]$RunId=0)
$ErrorActionPreference='Stop'
$inputCredential="protocol=https`nhost=github.com`n`n"
$credential=($inputCredential | git credential fill) | ConvertFrom-StringData
if(!$credential.password){throw 'GitHub authentication unavailable.'}
$headers=@{Authorization=('Bearer '+$credential.password);Accept='application/vnd.github+json';'X-GitHub-Api-Version'='2022-11-28'}
$base='https://api.github.com/repos/sufibuildwith-py/sa-controlcentre/actions'
if($Action -eq 'Dispatch') {
  Invoke-RestMethod "$base/workflows/production-release.yml/dispatches" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{ref=$Ref}|ConvertTo-Json)
  Write-Output 'Production installer workflow dispatched.'
} elseif($Action -eq 'Status') {
  $runs=Invoke-RestMethod "$base/workflows/production-release.yml/runs?per_page=3" -Headers $headers
  $runs.workflow_runs | Select-Object id,head_sha,status,conclusion,html_url | ConvertTo-Json -Compress
  if($RunId){$jobs=Invoke-RestMethod "$base/runs/$RunId/jobs" -Headers $headers; $jobs.jobs | Select-Object name,status,conclusion,steps | ConvertTo-Json -Depth 5 -Compress}
} else {
  if(!$RunId){throw 'A completed workflow run ID is required.'}
  $artifacts=Invoke-RestMethod "$base/runs/$RunId/artifacts" -Headers $headers
  $artifact=$artifacts.artifacts | Where-Object name -eq 'SA-Command-1.2.0-macOS-unsigned' | Select-Object -First 1
  if(!$artifact){throw 'The macOS artifact is unavailable.'}
  $target=Join-Path $PSScriptRoot '../.release-tools/macos-artifact.zip'
  Invoke-WebRequest "$base/artifacts/$($artifact.id)/zip" -Headers $headers -OutFile $target
  $extracted=Join-Path $PSScriptRoot '../.release-tools/macos-artifact'
  Expand-Archive -LiteralPath $target -DestinationPath $extracted -Force
  $dmg=Get-ChildItem $extracted -Filter '*.dmg' -Recurse | Select-Object -First 1
  if(!$dmg){throw 'No DMG exists in the downloaded artifact.'}
  Copy-Item -LiteralPath $dmg.FullName -Destination (Join-Path $PSScriptRoot '../release/macos/SA-Command.dmg')
  Write-Output 'macOS DMG downloaded.'
}
