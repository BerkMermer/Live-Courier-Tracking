# UTF-8-safe wrapper — prefer the Node seed (PowerShell JSON mangled Turkish text).
param([string]$BaseUrl = "http://127.0.0.1:18080")
$ErrorActionPreference = "Stop"
$env:DEMO_URL = $BaseUrl
node "$PSScriptRoot\seed-demo.mjs"
