$ErrorActionPreference = "Stop"

$manifestPath = Join-Path $env:LOCALAPPDATA "Quoti\NativeMessagingHosts\com.quoti.renderer.json"
$registryPath = "HKCU:\Software\Google\Chrome\NativeMessagingHosts\com.quoti.renderer"

if (Test-Path $registryPath) {
  Remove-Item -Recurse -Force -Path $registryPath
}

if (Test-Path $manifestPath) {
  Remove-Item -Force -Path $manifestPath
}

Write-Host "Unregistered com.quoti.renderer"
