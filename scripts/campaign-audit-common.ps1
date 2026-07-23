<#
.SYNOPSIS
    Shared, stateless child-ledger audited execution context for the PetClinic performance campaign.

    Issue #1 (campaign-readiness review): every internal podman / curl / jcmd / HTTP command executed by
    the launch, workload, capture, and teardown scripts must be reconstructable. Those scripts run as
    separate `& <script>` invocations, so a module-scoped ledger variable cannot be shared. Instead this
    library is DIRECTORY-BASED: a child ledger is a directory (passed down as -LedgerDirectory) that holds

        commands.ndjson        one JSON record per external/HTTP command (append-only)
        command-ledger.md      human-readable transcript (append-only)
        raw/                    verbatim stdout/stderr/body files, each SHA-256 verified
        .seq                    monotonic sequence counter (execution is strictly serial)

    Every child script routes its commands through Invoke-AuditedExternal / Invoke-AuditedHttp with the
    same -LedgerDirectory, so a single child ledger contains the complete internal command stream for a
    stage (network create, podman run config/discovery/customers, health probes, 81 HTTP requests, smaps
    capture, NMT capture, teardown). The parent campaign later hashes each child ledger (child-ledger
    integrity) and references it, so "Screen pair 1 executed" is backed by every internal command.

    These functions are intentionally stateless (no $script: ledger state) so they work identically across
    process/scope boundaries. When -LedgerDirectory is empty they fall back to a plain external call so the
    child scripts remain usable stand-alone.

    Depends on runtime-automation-common.ps1 (dot-source it first).
#>
Set-StrictMode -Version Latest

function ConvertTo-CampaignAuditIndented {
    param([AllowEmptyString()][string]$Value)
    if ([string]::IsNullOrEmpty($Value)) { return '    <empty>' }
    return (($Value -split '\r?\n') | ForEach-Object { "    $_" }) -join "`n"
}

# Deterministic content hash of a directory tree (relative path + file bytes). Reused for the config
# repository freeze and for child-ledger integrity of the raw/ directory.
function Get-CampaignTreeSha256 {
    param([Parameter(Mandatory)][string]$Root, [string[]]$ExcludeRegex = @('(\\|/)\.git(\\|/)'))
    if (-not (Test-Path -LiteralPath $Root -PathType Container)) { return '' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $fullRoot = (Resolve-Path -LiteralPath $Root).Path
        $files = Get-ChildItem -LiteralPath $fullRoot -Recurse -File |
            Where-Object { $f = $_.FullName; -not ($ExcludeRegex | Where-Object { $f -match $_ }) } |
            Sort-Object FullName
        foreach ($file in $files) {
            $rel = ($file.FullName.Substring($fullRoot.Length).TrimStart('\', '/')) -replace '\\', '/'
            $relBytes = [Text.Encoding]::UTF8.GetBytes($rel)
            [void]$sha.TransformBlock($relBytes, 0, $relBytes.Length, $null, 0)
            $fileHash = [Convert]::FromHexString((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash)
            [void]$sha.TransformBlock($fileHash, 0, $fileHash.Length, $null, 0)
        }
        [void]$sha.TransformFinalBlock([byte[]]::new(0), 0, 0)
        return [BitConverter]::ToString($sha.Hash).Replace('-', '')
    } finally { $sha.Dispose() }
}

# Initializes (or re-uses) a child ledger directory and writes/refreshes its header.
function Initialize-CampaignAuditLedger {
    param(
        [Parameter(Mandatory)][string]$LedgerDirectory,
        [string]$Stage = 'stage',
        [string]$Variant = '',
        [string]$Description = ''
    )
    if ([string]::IsNullOrWhiteSpace($LedgerDirectory)) { return $null }
    New-JmoaDirectory -Path $LedgerDirectory
    New-JmoaDirectory -Path (Join-Path $LedgerDirectory 'raw')
    $markdownPath = Join-Path $LedgerDirectory 'command-ledger.md'
    if (-not (Test-Path -LiteralPath $markdownPath -PathType Leaf)) {
        $header = @"
# Child Command Ledger

- Stage: $Stage
- Variant: $Variant
- Started UTC: $([DateTime]::UtcNow.ToString('o'))

$Description

Every external process (podman / curl / jcmd) and every HTTP request executed for this stage is
appended below with its exact argument vector, exit/status, and SHA-256 of its raw output. The parent
campaign ledger references this child ledger by directory hash (child-ledger integrity).
"@
        Set-Content -LiteralPath $markdownPath -Value $header -Encoding utf8
    }
    return [IO.Path]::GetFullPath($LedgerDirectory)
}

function Get-CampaignAuditSequence {
    param([Parameter(Mandatory)][string]$LedgerDirectory)
    $seqPath = Join-Path $LedgerDirectory '.seq'
    $current = 0
    if (Test-Path -LiteralPath $seqPath -PathType Leaf) {
        [void][int]::TryParse((Get-Content -Raw -LiteralPath $seqPath).Trim(), [ref]$current)
    }
    $next = $current + 1
    Set-Content -LiteralPath $seqPath -Value ([string]$next) -Encoding ascii
    return $next
}

function Add-CampaignAuditRecord {
    param(
        [Parameter(Mandatory)][string]$LedgerDirectory,
        [Parameter(Mandatory)][System.Collections.IDictionary]$Record,
        [Parameter(Mandatory)][string]$MarkdownBlock
    )
    $ndjsonPath = Join-Path $LedgerDirectory 'commands.ndjson'
    Add-Content -LiteralPath $ndjsonPath -Value ($Record | ConvertTo-Json -Depth 8 -Compress) -Encoding utf8
    Add-Content -LiteralPath (Join-Path $LedgerDirectory 'command-ledger.md') -Value $MarkdownBlock -Encoding utf8
}

# Runs an external process (verbatim, no shell) and, when a ledger directory is supplied, records the
# exact argument vector, exit code, and SHA-256-addressed raw stdout/stderr. Returns a shape compatible
# with Invoke-JmoaExternal (.executable/.arguments/.exitCode/.output) plus separated stdout/stderr and
# raw file provenance.
function Invoke-AuditedExternal {
    param(
        [Parameter(Mandatory)][string]$Executable,
        [string[]]$Arguments = @(),
        [string]$LedgerDirectory = '',
        [string]$Step = '',
        [string]$WorkingDirectory = (Get-Location).Path,
        [hashtable]$Environment = @{},
        [switch]$AllowFailure
    )
    $started = [DateTime]::UtcNow
    $psi = [Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = $Executable
    $psi.WorkingDirectory = [IO.Path]::GetFullPath($WorkingDirectory)
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    foreach ($argument in $Arguments) { [void]$psi.ArgumentList.Add($argument) }
    foreach ($entry in $Environment.GetEnumerator()) { $psi.Environment[$entry.Key] = [string]$entry.Value }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $psi
    $stdout = ''
    $stderr = ''
    $exitCode = 127
    try {
        [void]$process.Start()
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $stdout = $stdoutTask.GetAwaiter().GetResult()
        $stderr = $stderrTask.GetAwaiter().GetResult()
        $exitCode = $process.ExitCode
    } catch {
        $stderr = $_.Exception.ToString()
    } finally {
        $process.Dispose()
    }
    $ended = [DateTime]::UtcNow
    $combined = (($stdout, $stderr | Where-Object { -not [string]::IsNullOrEmpty($_) }) -join "`n").Trim()

    if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
        $ledgerFull = Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory
        $sequence = Get-CampaignAuditSequence -LedgerDirectory $ledgerFull
        $rawPrefix = 'cmd-{0:D4}' -f $sequence
        $stdoutFile = "$rawPrefix-stdout.txt"
        $stderrFile = "$rawPrefix-stderr.txt"
        $stdoutRaw = Join-Path $ledgerFull "raw\$stdoutFile"
        $stderrRaw = Join-Path $ledgerFull "raw\$stderrFile"
        [IO.File]::WriteAllText($stdoutRaw, [string]$stdout, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($stderrRaw, [string]$stderr, [Text.UTF8Encoding]::new($false))
        $stdoutSha = (Get-FileHash -LiteralPath $stdoutRaw -Algorithm SHA256).Hash
        $stderrSha = (Get-FileHash -LiteralPath $stderrRaw -Algorithm SHA256).Hash
        $commandLine = @($Executable) + @($Arguments | ForEach-Object { if ($_ -match '[\s"`$]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
        $record = [ordered]@{
            sequence             = $sequence
            kind                 = 'PROCESS'
            step                 = $Step
            startedUtc           = $started.ToString('o')
            endedUtc             = $ended.ToString('o')
            durationMilliseconds = [math]::Round(($ended - $started).TotalMilliseconds)
            workingDirectory     = $psi.WorkingDirectory
            executable           = $Executable
            arguments            = @($Arguments)
            commandLine          = $commandLine
            exitCode             = $exitCode
            failureAllowed       = [bool]$AllowFailure
            hardFailure          = ($exitCode -ne 0 -and -not $AllowFailure)
            rawStdoutPath        = "raw/$stdoutFile"
            rawStdoutSha256      = $stdoutSha
            rawStderrPath        = "raw/$stderrFile"
            rawStderrSha256      = $stderrSha
        }
        $md = @"

## Command ${sequence}: $Step

- Started UTC: $($record.startedUtc)
- Executable: $Executable
- Exit code: $exitCode (nonzero allowed: $([bool]$AllowFailure))
- Argument vector: $(@($Arguments) | ConvertTo-Json -Compress)
- Command: ``$commandLine``
- stdout: raw/$stdoutFile (SHA-256: $stdoutSha)
- stderr: raw/$stderrFile (SHA-256: $stderrSha)

stdout:

$(ConvertTo-CampaignAuditIndented -Value $stdout)

stderr:

$(ConvertTo-CampaignAuditIndented -Value $stderr)
"@
        Add-CampaignAuditRecord -LedgerDirectory $ledgerFull -Record $record -MarkdownBlock $md
    }

    if ($exitCode -ne 0 -and -not $AllowFailure) {
        throw "Audited command failed at '$Step' (exit $exitCode): $combined"
    }
    return [pscustomobject]@{
        executable = $Executable
        arguments  = @($Arguments)
        exitCode   = $exitCode
        output     = $combined
        stdout     = $stdout
        stderr     = $stderr
    }
}

# Audited HTTP request. Records method/uri/status/error and SHA-256-addressed raw response body plus, when
# supplied, a canonical body (Issue #7). Returns status/body/error and raw+canonical provenance.
function Invoke-AuditedHttp {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Uri,
        [string]$LedgerDirectory = '',
        [string]$Step = '',
        [string]$Body = $null,
        [string]$ContentType = 'application/json',
        [int]$TimeoutSeconds = 30,
        [scriptblock]$CanonicalizeBody = $null,
        [string]$CanonicalRuleId = ''
    )
    $started = [DateTime]::UtcNow
    $status = 0
    $responseBody = $null
    $requestError = $null
    try {
        $params = @{
            Uri                = $Uri
            Method             = $Method
            SkipHttpErrorCheck = $true
            TimeoutSec         = $TimeoutSeconds
            MaximumRedirection = 0
        }
        if (-not [string]::IsNullOrEmpty($Body)) {
            $params.ContentType = $ContentType
            $params.Body = $Body
        }
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $responseBody = [string]$response.Content
    } catch {
        $requestError = $_.Exception.Message
    }
    $ended = [DateTime]::UtcNow

    $rawBodyRel = ''
    $rawBodySha = ''
    $canonicalRel = ''
    $canonicalSha = ''
    if (-not [string]::IsNullOrWhiteSpace($LedgerDirectory)) {
        $ledgerFull = Initialize-CampaignAuditLedger -LedgerDirectory $LedgerDirectory
        $sequence = Get-CampaignAuditSequence -LedgerDirectory $ledgerFull
        $rawPrefix = 'http-{0:D4}' -f $sequence
        $rawBodyRel = "raw/$rawPrefix-body.txt"
        $rawBodyPath = Join-Path $ledgerFull "raw\$rawPrefix-body.txt"
        [IO.File]::WriteAllText($rawBodyPath, [string]$responseBody, [Text.UTF8Encoding]::new($false))
        $rawBodySha = (Get-FileHash -LiteralPath $rawBodyPath -Algorithm SHA256).Hash
        if ($null -ne $CanonicalizeBody -and -not [string]::IsNullOrEmpty($responseBody)) {
            try {
                $canonical = [string](& $CanonicalizeBody $responseBody)
                $canonicalRel = "raw/$rawPrefix-canonical.json"
                $canonicalPath = Join-Path $ledgerFull "raw\$rawPrefix-canonical.json"
                [IO.File]::WriteAllText($canonicalPath, $canonical, [Text.UTF8Encoding]::new($false))
                $canonicalSha = (Get-FileHash -LiteralPath $canonicalPath -Algorithm SHA256).Hash
            } catch {
                $canonicalRel = ''
            }
        }
        $record = [ordered]@{
            sequence             = $sequence
            kind                 = 'HTTP'
            step                 = $Step
            startedUtc           = $started.ToString('o')
            endedUtc             = $ended.ToString('o')
            durationMilliseconds = [math]::Round(($ended - $started).TotalMilliseconds)
            method               = $Method
            uri                  = $Uri
            requestBody          = $Body
            status               = $status
            error                = $requestError
            rawBodyPath          = $rawBodyRel
            rawBodySha256        = $rawBodySha
            canonicalBodyPath    = $canonicalRel
            canonicalBodySha256  = $canonicalSha
            canonicalRuleId      = $CanonicalRuleId
        }
        $md = @"

## HTTP ${sequence}: $Step

- $Method $Uri -> status $status$(if ($requestError) { " (error: $requestError)" })
- raw body: $rawBodyRel (SHA-256: $rawBodySha)
$(if ($canonicalRel) { "- canonical body: $canonicalRel (SHA-256: $canonicalSha, rule: $CanonicalRuleId)" })

response body:

$(ConvertTo-CampaignAuditIndented -Value ([string]$responseBody))
"@
        Add-CampaignAuditRecord -LedgerDirectory $ledgerFull -Record $record -MarkdownBlock $md
    }
    return [pscustomobject]@{
        method              = $Method
        uri                 = $Uri
        status              = $status
        body                = $responseBody
        error               = $requestError
        rawBodyPath         = $rawBodyRel
        rawBodySha256       = $rawBodySha
        canonicalBodyPath   = $canonicalRel
        canonicalBodySha256 = $canonicalSha
    }
}

# Finalizes a child ledger: writes child-ledger-integrity.json (SHA-256 of md + ndjson + every raw file)
# and child-ledger-summary.json, and returns a descriptor the parent references + hashes.
function Complete-CampaignAuditLedger {
    param(
        [Parameter(Mandatory)][string]$LedgerDirectory,
        [string]$Status = 'COMPLETE',
        [string]$Stage = '',
        [string]$Variant = ''
    )
    if ([string]::IsNullOrWhiteSpace($LedgerDirectory) -or -not (Test-Path -LiteralPath $LedgerDirectory -PathType Container)) {
        return $null
    }
    $ledgerFull = [IO.Path]::GetFullPath($LedgerDirectory)
    $markdownPath = Join-Path $ledgerFull 'command-ledger.md'
    $ndjsonPath = Join-Path $ledgerFull 'commands.ndjson'
    $entries = New-Object System.Collections.Generic.List[object]
    foreach ($core in @(@{ path = $markdownPath; kind = 'markdown-ledger' }, @{ path = $ndjsonPath; kind = 'ndjson-ledger' })) {
        if (Test-Path -LiteralPath $core.path -PathType Leaf) {
            $entries.Add([ordered]@{ path = ([IO.Path]::GetFileName($core.path)); kind = $core.kind; sha256 = (Get-FileHash -LiteralPath $core.path -Algorithm SHA256).Hash; bytes = (Get-Item -LiteralPath $core.path).Length }) | Out-Null
        }
    }
    $rawDir = Join-Path $ledgerFull 'raw'
    if (Test-Path -LiteralPath $rawDir -PathType Container) {
        foreach ($rawFile in (Get-ChildItem -LiteralPath $rawDir -File | Sort-Object Name)) {
            $entries.Add([ordered]@{ path = "raw/$($rawFile.Name)"; kind = 'raw'; sha256 = (Get-FileHash -LiteralPath $rawFile.FullName -Algorithm SHA256).Hash; bytes = $rawFile.Length }) | Out-Null
        }
    }
    $commandCount = 0
    if (Test-Path -LiteralPath $ndjsonPath -PathType Leaf) {
        $commandCount = @(Get-Content -LiteralPath $ndjsonPath | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
    }
    $integrity = [ordered]@{
        schemaVersion = 'jmoa-child-ledger-integrity-v1'
        stage         = $Stage
        variant       = $Variant
        status        = $Status
        generatedUtc  = [DateTime]::UtcNow.ToString('o')
        commandCount  = $commandCount
        fileCount     = $entries.Count
        files         = $entries.ToArray()
    }
    $integrityPath = Join-Path $ledgerFull 'child-ledger-integrity.json'
    $integrity | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $integrityPath -Encoding utf8
    $integritySha = (Get-FileHash -LiteralPath $integrityPath -Algorithm SHA256).Hash
    $summary = [ordered]@{
        schemaVersion = 'jmoa-child-ledger-summary-v1'
        stage         = $Stage
        variant       = $Variant
        status        = $Status
        ledgerDirectory = $ledgerFull
        commandCount  = $commandCount
        fileCount     = $entries.Count
        integrityPath = 'child-ledger-integrity.json'
        integritySha256 = $integritySha
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $ledgerFull 'child-ledger-summary.json') -Encoding utf8
    return [pscustomobject]$summary
}
