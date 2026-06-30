$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

$drive = "C:"
$slash = [string][char]92
$patterns = @(
    [regex]::Escape($drive + $slash + "Users"),
    [regex]::Escape($drive + $slash + "Java" + " Developer"),
    "(?i)\bpassword\s*=\s*(?![=])",
    "(?i)\bsecret\s*=\s*(?![=])",
    "(?i)\btoken\s*=\s*(?![=])",
    ("j" + "wt"),
    ("BEGIN " + "PRIVATE " + "KEY"),
    "\.env(\.|$)",
    ("patient" + "management" + "service"),
    ("doctor" + "-management" + "-service"),
    ("Hospital " + "Management " + "System")
)

$include = @("*.java", "*.xml", "*.md", "*.json", "*.yml", "*.yaml", "*.ps1", "*.sh", "*.properties")
$files = Get-ChildItem -Path $repoRoot -Recurse -File -Include $include |
    Where-Object {
        $_.FullName -notmatch "\\target\\" -and
        $_.FullName -notmatch "\\.git\\"
    }

$findings = New-Object System.Collections.Generic.List[string]
foreach ($file in $files) {
    $content = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction SilentlyContinue
    foreach ($pattern in $patterns) {
        if ($content -match $pattern) {
            $relative = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName)
            $findings.Add("$relative :: $pattern")
        }
    }
}

$forbiddenExtensions = @(".jar", ".war", ".ear", ".class", ".jsa", ".jfr", ".hprof", ".zip", ".tar")
$forbidden = Get-ChildItem -Path $repoRoot -Recurse -File |
    Where-Object {
        $forbiddenExtensions -contains $_.Extension.ToLowerInvariant() -and
        $_.FullName -notmatch "\\.git\\" -and
        $_.FullName -notmatch "\\target\\" -and
        $_.FullName -notmatch "\\out\\"
    }
foreach ($file in $forbidden) {
    $relative = [System.IO.Path]::GetRelativePath($repoRoot, $file.FullName)
    $findings.Add("$relative :: forbidden binary/evidence extension")
}

if ($findings.Count -gt 0) {
    Write-Error ("Publication safety scan failed:`n" + ($findings -join "`n"))
}

Write-Host "Publication safety scan passed."
