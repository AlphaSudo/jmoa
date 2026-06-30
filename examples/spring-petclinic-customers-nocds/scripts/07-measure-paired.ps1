param(
    [int]$Pairs = 3,
    [string]$OutputDir = "./measurements"
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

Write-Host "Measurement scaffold created at $OutputDir."
Write-Host "Run fresh baseline and optimized containers/processes for each pair."
Write-Host "Capture PSS, Private_Dirty, memory.current, heap PSS, class counts, startup, and workload errors."
Write-Host "This scaffold intentionally does not fake a memory result."
