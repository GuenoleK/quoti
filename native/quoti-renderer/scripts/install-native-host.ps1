param(
  [Parameter(Mandatory = $true)]
  [string] $ExtensionId
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$hostRoot = Resolve-Path (Join-Path $scriptDir "..")
$hostCommand = Resolve-Path (Join-Path $hostRoot "bin\quoti-renderer.cmd")
$manifestPath = Join-Path $env:LOCALAPPDATA "Quoti\NativeMessagingHosts\com.quoti.renderer.json"
$registryPath = "HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.quoti.renderer"

$manifest = [ordered]@{
  name = "com.quoti.renderer"
  description = "Quoti native FFmpeg renderer"
  path = $hostCommand.Path
  type = "stdio"
  allowed_origins = @("chrome-extension://$ExtensionId/")
} | ConvertTo-Json -Depth 5

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $manifestPath) | Out-Null
$manifest | Set-Content -Encoding ascii -Path $manifestPath

New-Item -Force -Path $registryPath | Out-Null
Set-Item -Path $registryPath -Value $manifestPath

Write-Host "Registered com.quoti.renderer for chrome-extension://$ExtensionId/"
Write-Host $manifestPath
