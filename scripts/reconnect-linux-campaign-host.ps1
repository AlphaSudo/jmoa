param(
    [Parameter(Mandatory)][string]$HostAddress,
    [string]$UserName = 'cm',
    [Parameter(Mandatory)][string]$IdentityFile,
    [Parameter(Mandatory)][string]$ExpectedEd25519Fingerprint,
    [Parameter(Mandatory)][string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
. (Join-Path $PSScriptRoot 'campaign-audit-common.ps1')

New-JmoaDirectory -Path $OutputDirectory
$ledger = Join-Path $OutputDirectory 'command-ledger'
$scannedHostKey = Join-Path $OutputDirectory 'scanned-ed25519-host-key'
$campaignKnownHosts = Join-Path $OutputDirectory 'known-hosts'
Initialize-CampaignAuditLedger -LedgerDirectory $ledger -Stage 'linux-host-reconnect' -Variant 'HYPERV_DEBIAN_FIXED_2G' `
    -Description 'Audited resumed-session host-key verification, key authentication, interruption classification, and fixed-resource identity checks.' | Out-Null

try {
    if (-not (Test-Path -LiteralPath $IdentityFile -PathType Leaf)) {
        throw "Campaign SSH identity file does not exist: $IdentityFile"
    }
    $keyscan = Invoke-AuditedExternal -Executable 'ssh-keyscan.exe' -Arguments @(
        '-T', '10', '-t', 'ed25519', $HostAddress
    ) -LedgerDirectory $ledger -Step 'scan recovered VM ED25519 host key'
    if ([string]::IsNullOrWhiteSpace($keyscan.stdout)) { throw 'ssh-keyscan returned no ED25519 host key.' }
    Write-JmoaText -Value $keyscan.stdout.Trim() -Path $scannedHostKey

    $fingerprintResult = Invoke-AuditedExternal -Executable 'ssh-keygen.exe' -Arguments @(
        '-lf', $scannedHostKey, '-E', 'sha256'
    ) -LedgerDirectory $ledger -Step 'derive recovered VM ED25519 SHA-256 fingerprint'
    $match = [regex]::Match($fingerprintResult.stdout, 'SHA256:[A-Za-z0-9+/]+')
    if (-not $match.Success) { throw 'Could not parse the recovered VM ED25519 fingerprint.' }
    $actualFingerprint = $match.Value
    if ($actualFingerprint -ne $ExpectedEd25519Fingerprint) {
        throw "Recovered VM host-key mismatch: expected $ExpectedEd25519Fingerprint, observed $actualFingerprint"
    }
    Write-JmoaText -Value $keyscan.stdout.Trim() -Path $campaignKnownHosts

    $sshBase = @(
        '-o', 'BatchMode=yes',
        '-o', 'IdentitiesOnly=yes',
        '-o', 'StrictHostKeyChecking=yes',
        '-o', "UserKnownHostsFile=$campaignKnownHosts",
        '-o', 'ConnectTimeout=15',
        '-i', $IdentityFile,
        "$UserName@$HostAddress"
    )
    $commands = [ordered]@{
        identity = 'id; hostname; hostname -I'
        uptime = 'uptime; cat /proc/uptime; cat /proc/sys/kernel/random/boot_id'
        virtualization = 'systemd-detect-virt'
        sshService = 'systemctl is-active ssh'
        processors = 'nproc'
        memory = 'free -b'
        meminfo = "grep -E '^(MemTotal|MemAvailable|SwapTotal|SwapFree):' /proc/meminfo"
        swaps = 'swapon --show --bytes'
        cgroup = 'stat -fc %T /sys/fs/cgroup; cat /sys/fs/cgroup/cgroup.controllers; cat /sys/fs/cgroup/memory.events; cat /sys/fs/cgroup/memory.swap.current 2>/dev/null || true'
        pressure = 'cat /proc/pressure/memory'
        podman = 'podman version; podman info --format json'
        java = 'java -version'
        maven = 'mvn -version'
        powershell = 'pwsh --version'
    }
    $records = [ordered]@{}
    foreach ($entry in $commands.GetEnumerator()) {
        $result = Invoke-AuditedExternal -Executable 'ssh.exe' -Arguments ($sshBase + @($entry.Value)) `
            -LedgerDirectory $ledger -Step "recovered VM $($entry.Key)" -AllowFailure
        $records[$entry.Key] = [ordered]@{
            exitCode = $result.exitCode
            stdout = $result.stdout.Trim()
            stderr = $result.stderr.Trim()
        }
    }
    $authenticationPassed = ([int]$records.identity.exitCode -eq 0)
    $report = [ordered]@{
        schemaVersion = 'jmoa-linux-host-reconnect-v1'
        capturedAt = [DateTime]::UtcNow.ToString('o')
        interruptionClassification = 'HOST_HIBERNATION_INTERRUPTION'
        hostAddress = $HostAddress
        userName = $UserName
        expectedEd25519Fingerprint = $ExpectedEd25519Fingerprint
        actualEd25519Fingerprint = $actualFingerprint
        fingerprintMatched = ($actualFingerprint -eq $ExpectedEd25519Fingerprint)
        campaignKeyAuthenticationPassed = $authenticationPassed
        records = $records
        passed = ($authenticationPassed -and ($actualFingerprint -eq $ExpectedEd25519Fingerprint))
    }
    Write-JmoaJson -Value $report -Path (Join-Path $OutputDirectory 'linux-host-reconnect.json')
    Write-JmoaText -Path (Join-Path $OutputDirectory 'linux-host-reconnect.md') -Value @"
# Linux Campaign Host Reconnect

- Interruption classification: **HOST_HIBERNATION_INTERRUPTION**
- Host: ``$HostAddress``
- ED25519 fingerprint matched: **$($report.fingerprintMatched)**
- Campaign-key authentication: **$authenticationPassed**
- Captured UTC: $($report.capturedAt)

Every command and complete response is preserved in the adjacent command ledger.
"@
    Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status $(if ($report.passed) { 'COMPLETE' } else { 'FAILED' }) `
        -Stage 'linux-host-reconnect' -Variant 'HYPERV_DEBIAN_FIXED_2G' | Out-Null
    if (-not $report.passed) { exit 2 }
    $report | ConvertTo-Json -Depth 12
} catch {
    if (-not (Test-Path -LiteralPath (Join-Path $ledger 'child-ledger-summary.json') -PathType Leaf)) {
        Complete-CampaignAuditLedger -LedgerDirectory $ledger -Status 'FAILED' -Stage 'linux-host-reconnect' -Variant 'HYPERV_DEBIAN_FIXED_2G' | Out-Null
    }
    throw
}
