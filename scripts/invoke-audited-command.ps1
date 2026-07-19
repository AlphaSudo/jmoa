param(
    [Parameter(Mandatory)][string]$CommandId,
    [Parameter(Mandatory)][string]$Executable,
    [string[]]$ArgumentList = @(),
    [string]$WorkingDirectory = (Get-Location).Path,
    [string]$AuditDirectory = 'target/runtime-equivalence/commands',
    [hashtable]$Environment = @{},
    [string[]]$InputPath = @(),
    [string[]]$OutputPath = @(),
    [string[]]$EnvironmentAllowlist = @('JAVA_HOME','MAVEN_OPTS','MALLOC_ARENA_MAX','PATH'),
    [string[]]$SecretNamePattern = @('TOKEN','PASSWORD','SECRET','KEY','CREDENTIAL'),
    [switch]$AcceptedEvidence,
    [switch]$NoThrow
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

function Get-PathRecord([string]$Path) {
    $resolved = if (Test-Path -LiteralPath $Path) { (Resolve-Path -LiteralPath $Path).Path } else { [IO.Path]::GetFullPath($Path, $WorkingDirectory) }
    [ordered]@{
        path = $resolved
        exists = Test-Path -LiteralPath $resolved -PathType Leaf
        sha256 = Get-JmoaSha256 -Path $resolved
        bytes = if (Test-Path -LiteralPath $resolved -PathType Leaf) { (Get-Item -LiteralPath $resolved).Length } else { $null }
    }
}

function Protect-EnvironmentValue([string]$Name, [string]$Value) {
    foreach ($pattern in $SecretNamePattern) {
        if ($Name -match $pattern) { return '<REDACTED>' }
    }
    return $Value
}

New-JmoaDirectory -Path $AuditDirectory
$stdoutPath = Join-Path $AuditDirectory "$CommandId.stdout.txt"
$stderrPath = Join-Path $AuditDirectory "$CommandId.stderr.txt"
$ledgerPath = Join-Path $AuditDirectory 'commands.ndjson'
$transcriptPath = Join-Path $AuditDirectory 'console-transcript.txt'
$started = [DateTime]::UtcNow
$gitRevision = ''
try { $gitRevision = (& git -C $WorkingDirectory rev-parse HEAD 2>$null | Select-Object -First 1).Trim() } catch {}

$effectiveEnvironment = [ordered]@{}
foreach ($name in $EnvironmentAllowlist) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ($null -ne $value) { $effectiveEnvironment[$name] = Protect-EnvironmentValue -Name $name -Value $value }
}
foreach ($entry in $Environment.GetEnumerator()) {
    $effectiveEnvironment[$entry.Key] = Protect-EnvironmentValue -Name $entry.Key -Value ([string]$entry.Value)
}

$psi = [Diagnostics.ProcessStartInfo]::new()
$psi.FileName = $Executable
$psi.WorkingDirectory = (Resolve-Path -LiteralPath $WorkingDirectory).Path
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
foreach ($argument in $ArgumentList) { [void]$psi.ArgumentList.Add($argument) }
foreach ($entry in $Environment.GetEnumerator()) { $psi.Environment[$entry.Key] = [string]$entry.Value }

$process = [Diagnostics.Process]::new()
$process.StartInfo = $psi
$exitCode = 127
$stdout = ''
$stderr = ''
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
[IO.File]::WriteAllText([IO.Path]::GetFullPath($stdoutPath), $stdout, [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText([IO.Path]::GetFullPath($stderrPath), $stderr, [Text.UTF8Encoding]::new($false))
$pathJava = (& where.exe java 2>$null | Select-Object -First 1)
$pathJavac = (& where.exe javac 2>$null | Select-Object -First 1)
$pathMaven = (& where.exe mvn 2>$null | Select-Object -First 1)
$javaHomeJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { '' }
$mavenVersion = ''
if ($pathMaven) { try { $mavenVersion = (& $pathMaven -version 2>&1 | Out-String).Trim() } catch { $mavenVersion = $_.Exception.Message } }

$record = [ordered]@{
    schemaVersion = 'jmoa-audited-command-v1'
    commandId = $CommandId
    purpose = $CommandId
    workingDirectory = $psi.WorkingDirectory
    executable = $Executable
    arguments = @($ArgumentList)
    environment = $effectiveEnvironment
    javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME')
    toolResolution = [ordered]@{
        javaHomeJava = $javaHomeJava
        pathJava = $pathJava
        pathJavac = $pathJavac
        pathMaven = $pathMaven
        mavenVersion = $mavenVersion
    }
    gitRevision = $gitRevision
    startedUtc = $started.ToString('o')
    endedUtc = $ended.ToString('o')
    durationMilliseconds = [math]::Round(($ended - $started).TotalMilliseconds)
    exitCode = $exitCode
    stdoutFile = (Resolve-Path -LiteralPath $stdoutPath).Path
    stderrFile = (Resolve-Path -LiteralPath $stderrPath).Path
    inputHashes = @($InputPath | ForEach-Object { Get-PathRecord $_ })
    outputHashes = @($OutputPath | ForEach-Object { Get-PathRecord $_ })
    acceptedEvidence = ([bool]$AcceptedEvidence -and $exitCode -eq 0)
}
$jsonLine = $record | ConvertTo-Json -Depth 10 -Compress
Add-Content -LiteralPath $ledgerPath -Value $jsonLine -Encoding utf8
$display = "[$($started.ToString('o'))] $CommandId`n  cwd: $($psi.WorkingDirectory)`n  exec: $Executable $($ArgumentList -join ' ')`n  exit: $exitCode`n  stdout: $stdoutPath`n  stderr: $stderrPath`n"
Add-Content -LiteralPath $transcriptPath -Value $display -Encoding utf8
$campaignRecords = @(Get-Content -LiteralPath $ledgerPath | Where-Object { $_.Trim() } | ForEach-Object { $_ | ConvertFrom-Json })
$summaryRows = @($campaignRecords | ForEach-Object { "| $($_.commandId) | $($_.exitCode) | $($_.durationMilliseconds) | $($_.acceptedEvidence) |" })
$summary = "# Audited Campaign Summary`n`nCommands recorded: **$($campaignRecords.Count)**`n`n| Command | Exit | Duration ms | Accepted evidence |`n|---|---:|---:|---:|`n$($summaryRows -join "`n")`n"
Write-JmoaText -Value $summary -Path (Join-Path $AuditDirectory 'campaign-summary.md')
$record | ConvertTo-Json -Depth 10
if ($exitCode -ne 0 -and -not $NoThrow) { throw "Audited command '$CommandId' failed with exit code $exitCode. See $stderrPath" }
