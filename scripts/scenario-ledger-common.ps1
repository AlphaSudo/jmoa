Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ScenarioLedger = $null

function ConvertTo-ScenarioCommandLine {
    param([string]$Executable, [string[]]$Arguments)
    $parts = @($Executable) + @($Arguments | ForEach-Object {
        if ($_ -match '[\s"`$]') { '"' + ($_ -replace '"','\"') + '"' } else { $_ }
    })
    return $parts -join ' '
}

function Add-ScenarioMarkdown {
    param([string]$Value)
    Add-Content -LiteralPath $script:ScenarioLedger.markdownPath -Value $Value -Encoding utf8
}

function ConvertTo-IndentedLog {
    param([AllowEmptyString()][string]$Value)
    if ([string]::IsNullOrEmpty($Value)) { return '    <empty>' }
    return (($Value -split '\r?\n') | ForEach-Object { "    $_" }) -join "`n"
}

function Start-ScenarioLedger {
    param(
        [Parameter(Mandatory)][string]$ScenarioId,
        [Parameter(Mandatory)][string]$OutputDirectory,
        [Parameter(Mandatory)][string]$Description
    )
    New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
    $rawDirectory = Join-Path $OutputDirectory 'raw'
    New-Item -ItemType Directory -Force -Path $rawDirectory | Out-Null
    $script:ScenarioLedger = [ordered]@{
        scenarioId = $ScenarioId
        outputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
        markdownPath = Join-Path $OutputDirectory "$ScenarioId-command-ledger.md"
        ndjsonPath = Join-Path $OutputDirectory "$ScenarioId-commands.ndjson"
        summaryPath = Join-Path $OutputDirectory "$ScenarioId-summary.json"
        integrityPath = Join-Path $OutputDirectory "$ScenarioId-ledger-integrity.json"
        rawDirectory = [IO.Path]::GetFullPath($rawDirectory)
        startedUtc = [DateTime]::UtcNow
        sequence = 0
        commandCount = 0
        hardFailedCommands = 0
        allowedNonZeroCommands = 0
    }
    $header = @"
# Scenario Command Ledger: $ScenarioId

$Description

Started UTC: $($script:ScenarioLedger.startedUtc.ToString('o'))

This is the complete step ledger for this scenario. Every external command is
listed with its exact argument vector, working directory, exit code, stdout,
and stderr. Reused inputs are declared separately and are never described as
freshly trained or built.
"@
    Set-Content -LiteralPath $script:ScenarioLedger.markdownPath -Value $header -Encoding utf8
    if (Test-Path -LiteralPath $script:ScenarioLedger.ndjsonPath) { Remove-Item -LiteralPath $script:ScenarioLedger.ndjsonPath -Force }
}

function Add-ScenarioAsset {
    param(
        [Parameter(Mandatory)][string]$Role,
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][ValidateSet('BUILT_IN_SCENARIO','REUSED_FROZEN_INPUT','REUSED_SUPPORT_IMAGE','GENERATED_IN_SCENARIO')][string]$Provenance,
        [string]$Note = ''
    )
    $exists = Test-Path -LiteralPath $Path -PathType Leaf
    $hash = if ($exists) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash } else { '' }
    $bytes = if ($exists) { (Get-Item -LiteralPath $Path).Length } else { $null }
    Add-ScenarioMarkdown "`n## Asset: $Role`n`n- Provenance: $Provenance`n- Path: $Path`n- Present: $exists`n- SHA-256: $hash`n- Bytes: $bytes`n- Note: $Note`n"
    return [pscustomobject]@{role=$Role;path=$Path;provenance=$Provenance;exists=$exists;sha256=$hash;bytes=$bytes;note=$Note}
}

function Add-ScenarioNote {
    param([Parameter(Mandatory)][string]$Title,[Parameter(Mandatory)][string]$Text)
    Add-ScenarioMarkdown "`n## $Title`n`n$Text`n"
}

function Invoke-ScenarioCommand {
    param(
        [Parameter(Mandatory)][string]$Step,
        [Parameter(Mandatory)][string]$Executable,
        [string[]]$Arguments = @(),
        [string]$WorkingDirectory = (Get-Location).Path,
        [hashtable]$Environment = @{},
        [switch]$AllowFailure
    )
    if ($null -eq $script:ScenarioLedger) { throw 'Start-ScenarioLedger must be called first.' }
    $script:ScenarioLedger.sequence++
    $script:ScenarioLedger.commandCount++
    $sequence = $script:ScenarioLedger.sequence
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
    $failureAllowed = [bool]$AllowFailure
    if ($exitCode -ne 0) {
        if ($failureAllowed) { $script:ScenarioLedger.allowedNonZeroCommands++ }
        else { $script:ScenarioLedger.hardFailedCommands++ }
    }
    # Raw preservation: persist verbatim stdout (for curl steps this is the HTTP response body)
    # and stderr as separate files, each with its own SHA-256, linked from the ndjson and Markdown.
    $rawPrefix = 'cmd-{0:D4}' -f $sequence
    $stdoutFileName = "$rawPrefix-stdout.txt"
    $stderrFileName = "$rawPrefix-stderr.txt"
    $stdoutRawPath = Join-Path $script:ScenarioLedger.rawDirectory $stdoutFileName
    $stderrRawPath = Join-Path $script:ScenarioLedger.rawDirectory $stderrFileName
    [IO.File]::WriteAllText($stdoutRawPath, [string]$stdout, [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($stderrRawPath, [string]$stderr, [Text.UTF8Encoding]::new($false))
    $stdoutSha256 = (Get-FileHash -LiteralPath $stdoutRawPath -Algorithm SHA256).Hash
    $stderrSha256 = (Get-FileHash -LiteralPath $stderrRawPath -Algorithm SHA256).Hash
    $record = [ordered]@{
        sequence = $sequence
        step = $Step
        startedUtc = $started.ToString('o')
        endedUtc = $ended.ToString('o')
        durationMilliseconds = [math]::Round(($ended-$started).TotalMilliseconds)
        workingDirectory = $psi.WorkingDirectory
        executable = $Executable
        arguments = @($Arguments)
        environmentOverrides = $Environment
        commandLine = ConvertTo-ScenarioCommandLine -Executable $Executable -Arguments $Arguments
        exitCode = $exitCode
        failureAllowed = $failureAllowed
        hardFailure = ($exitCode -ne 0 -and -not $failureAllowed)
        stdout = $stdout
        stderr = $stderr
        rawStdoutPath = $stdoutRawPath
        rawStdoutSha256 = $stdoutSha256
        rawStderrPath = $stderrRawPath
        rawStderrSha256 = $stderrSha256
    }
    Add-Content -LiteralPath $script:ScenarioLedger.ndjsonPath -Value ($record | ConvertTo-Json -Depth 8 -Compress) -Encoding utf8
    $markdown = @"

## Command ${sequence}: $Step

- Started UTC: $($record.startedUtc)
- Working directory: $($record.workingDirectory)
- Executable: $Executable
- Exit code: $exitCode
- Nonzero exit allowed: $failureAllowed
- Duration: $($record.durationMilliseconds) ms
- Argument vector: $(@($Arguments) | ConvertTo-Json -Compress)

Command:

    $($record.commandLine)

Raw artifacts:

- stdout: raw/$stdoutFileName (SHA-256: $stdoutSha256)
- stderr: raw/$stderrFileName (SHA-256: $stderrSha256)

stdout:

$(ConvertTo-IndentedLog $stdout)

stderr:

$(ConvertTo-IndentedLog $stderr)
"@
    Add-ScenarioMarkdown $markdown
    if ($exitCode -ne 0 -and -not $AllowFailure) { throw "Scenario command failed at '$Step' with exit code $exitCode." }
    return [pscustomobject]$record
}

function Complete-ScenarioLedger {
    param([string]$Status = 'COMPLETE',[hashtable]$Result = @{})
    $ended = [DateTime]::UtcNow
    # Finalize the Markdown ledger first so its hash covers the complete document.
    Add-ScenarioMarkdown "`n## Scenario Result`n`n- Status: **$Status**`n- Commands: $($script:ScenarioLedger.commandCount)`n- Hard failed commands: $($script:ScenarioLedger.hardFailedCommands)`n- Allowed nonzero commands: $($script:ScenarioLedger.allowedNonZeroCommands)`n- Ended UTC: $($ended.ToString('o'))`n"

    # Ledger integrity: SHA-256 of the Markdown ledger, the ndjson ledger, and every raw file, plus an index.
    $integrityEntries = New-Object System.Collections.Generic.List[object]
    foreach ($core in @(
        @{ path = $script:ScenarioLedger.markdownPath; kind = 'markdown-ledger' },
        @{ path = $script:ScenarioLedger.ndjsonPath; kind = 'ndjson-ledger' }
    )) {
        if (Test-Path -LiteralPath $core.path -PathType Leaf) {
            $integrityEntries.Add([ordered]@{ path = [IO.Path]::GetFullPath($core.path); kind = $core.kind; sha256 = (Get-FileHash -LiteralPath $core.path -Algorithm SHA256).Hash; bytes = (Get-Item -LiteralPath $core.path).Length }) | Out-Null
        }
    }
    if (Test-Path -LiteralPath $script:ScenarioLedger.rawDirectory -PathType Container) {
        foreach ($rawFile in (Get-ChildItem -LiteralPath $script:ScenarioLedger.rawDirectory -File | Sort-Object Name)) {
            $integrityEntries.Add([ordered]@{ path = $rawFile.FullName; kind = 'raw'; sha256 = (Get-FileHash -LiteralPath $rawFile.FullName -Algorithm SHA256).Hash; bytes = $rawFile.Length }) | Out-Null
        }
    }
    $integrity = [ordered]@{
        schemaVersion = 'jmoa-ledger-integrity-v1'
        scenarioId = $script:ScenarioLedger.scenarioId
        generatedUtc = $ended.ToString('o')
        fileCount = $integrityEntries.Count
        files = $integrityEntries.ToArray()
    }
    $integrity | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $script:ScenarioLedger.integrityPath -Encoding utf8

    $summary = [ordered]@{
        schemaVersion = 'jmoa-scenario-ledger-v1'
        scenarioId = $script:ScenarioLedger.scenarioId
        status = $Status
        startedUtc = $script:ScenarioLedger.startedUtc.ToString('o')
        endedUtc = $ended.ToString('o')
        durationMilliseconds = [math]::Round(($ended-$script:ScenarioLedger.startedUtc).TotalMilliseconds)
        commandCount = $script:ScenarioLedger.commandCount
        hardFailedCommands = $script:ScenarioLedger.hardFailedCommands
        allowedNonZeroCommands = $script:ScenarioLedger.allowedNonZeroCommands
        markdownLedger = $script:ScenarioLedger.markdownPath
        ndjsonLedger = $script:ScenarioLedger.ndjsonPath
        rawDirectory = $script:ScenarioLedger.rawDirectory
        integrityIndex = $script:ScenarioLedger.integrityPath
        result = $Result
    }
    $summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $script:ScenarioLedger.summaryPath -Encoding utf8
    return [pscustomobject]$summary
}
