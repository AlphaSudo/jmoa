<#
.SYNOPSIS
    Same-artifact runtime-noise qualifier for the PetClinic campaign.

    Consumes reversed same-artifact pairs and proves the measurement floor is below the trusted-win
    thresholds. Each control (B0 and V2) carries its OWN artifactSha256 (review Issue #8) so the noise
    report can never be misattributed to the wrong artifact.

    Qualification (per control, all must hold):
      - median |ΔPSS|            <= MaxPssDriftKb            (default 1024 KB)
      - median |ΔPrivate_Dirty|  <= MaxPrivateDirtyDriftKb   (default 1024 KB)
      - median |Δmemory.current| <= MaxMemoryCurrentDriftBytes (default 2,097,152 bytes = 2 MiB;
        relaxed for cgroup accounting granularity, review Issue #7)
      - no systematic second-run advantage across the reversed pairs
      - zero semantic differences between the two same-artifact runs of every pair

    Input schema (v2): { schema:'jmoa-same-artifact-noise-input-v2', controls: [ {
        label, artifactSha256, pairs: [ {
            id, order,                       # order label, e.g. 'A->B' and reversed 'B->A'
            first:  { pssKb, privateDirtyKb, memoryCurrentBytes },
            second: { pssKb, privateDirtyKb, memoryCurrentBytes },
            semanticErrors                   # optional; same artifact must produce 0
        } ] } ] }

    Backward compatible with the legacy v1 input ({ artifactSha256, pairs:[{left,right}] }): it is
    wrapped as a single unlabelled control using left=first / right=second.
#>
param(
    [Parameter(Mandatory)][string]$InputPath,
    [string]$OutputDirectory = 'target/runtime-equivalence/noise',
    [int]$MaxPssDriftKb = 1024,
    [int]$MaxPrivateDirtyDriftKb = 1024,
    [long]$MaxMemoryCurrentDriftBytes = 2097152,
    [int]$MinReversedPairsPerControl = 2
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
New-JmoaDirectory $OutputDirectory

function Get-Prop { param($Object, [string]$Name) if ($null -eq $Object) { return $null }; $p = $Object.PSObject.Properties[$Name]; if ($p) { return $p.Value }; return $null }
function Median([double[]]$Values) { $s = @($Values | Sort-Object); if (-not $s.Count) { return 0 }; if ($s.Count % 2) { return $s[[int][math]::Floor($s.Count / 2)] }; ($s[$s.Count / 2 - 1] + $s[$s.Count / 2]) / 2 }

$inputDoc = Get-Content -LiteralPath $InputPath -Raw | ConvertFrom-Json

# Normalize legacy v1 (single artifact, pairs with left/right) into the v2 controls[] shape.
$controlsInput = Get-Prop $inputDoc 'controls'
if ($null -eq $controlsInput) {
    $legacyPairs = @(Get-Prop $inputDoc 'pairs') | ForEach-Object {
        [ordered]@{ id = (Get-Prop $_ 'id'); order = (Get-Prop $_ 'order'); first = (Get-Prop $_ 'left'); second = (Get-Prop $_ 'right'); semanticErrors = (Get-Prop $_ 'semanticErrors') }
    }
    $controlsInput = @([ordered]@{ label = 'DEFAULT'; artifactSha256 = (Get-Prop $inputDoc 'artifactSha256'); pairs = $legacyPairs })
}

$controlReports = New-Object System.Collections.Generic.List[object]
$allQualified = $true

foreach ($control in $controlsInput) {
    $label = [string](Get-Prop $control 'label')
    $artifactSha = [string](Get-Prop $control 'artifactSha256')
    $rawPairs = @(Get-Prop $control 'pairs')
    $reasons = New-Object System.Collections.Generic.List[string]

    if ([string]::IsNullOrWhiteSpace($artifactSha)) {
        $reasons.Add("control '$label' has no artifactSha256") | Out-Null
    }
    if ($rawPairs.Count -lt $MinReversedPairsPerControl) {
        $reasons.Add("control '$label' has $($rawPairs.Count) reversed pair(s); at least $MinReversedPairsPerControl required") | Out-Null
    }

    $pairs = New-Object System.Collections.Generic.List[object]
    $semanticErrorsTotal = 0
    foreach ($p in $rawPairs) {
        $first = Get-Prop $p 'first'
        $second = Get-Prop $p 'second'
        $pssAbs = [math]::Abs([double](Get-Prop $second 'pssKb') - [double](Get-Prop $first 'pssKb'))
        $pdAbs = [math]::Abs([double](Get-Prop $second 'privateDirtyKb') - [double](Get-Prop $first 'privateDirtyKb'))
        $mcAbs = [math]::Abs([double](Get-Prop $second 'memoryCurrentBytes') - [double](Get-Prop $first 'memoryCurrentBytes'))
        # Signed second-minus-first: a persistent same sign across reversed pairs would prove a
        # position (warm-cache / second-run) advantage that the reversed design should cancel.
        $pssSigned = [double](Get-Prop $second 'pssKb') - [double](Get-Prop $first 'pssKb')
        $semErr = [int](Get-Prop $p 'semanticErrors')
        $semanticErrorsTotal += $semErr
        $pairs.Add([ordered]@{
            id                     = [string](Get-Prop $p 'id')
            order                  = [string](Get-Prop $p 'order')
            pssAbsKb               = $pssAbs
            privateDirtyAbsKb      = $pdAbs
            memoryCurrentAbsBytes  = $mcAbs
            pssSecondMinusFirstKb  = $pssSigned
            semanticErrors         = $semErr
        }) | Out-Null
    }

    $pssMedianAbs = Median @($pairs.pssAbsKb)
    $pdMedianAbs = Median @($pairs.privateDirtyAbsKb)
    $mcMedianAbs = Median @($pairs.memoryCurrentAbsBytes)
    $signed = @($pairs.pssSecondMinusFirstKb)
    $pssSignedMedian = Median $signed
    # Systematic advantage: every reversed pair moves the same direction AND the median magnitude
    # exceeds the PSS noise threshold (i.e. it is a real, position-correlated shift, not sampling).
    $allSameSign = ($signed.Count -ge 2) -and (@($signed | Where-Object { $_ -gt 0 }).Count -eq $signed.Count -or @($signed | Where-Object { $_ -lt 0 }).Count -eq $signed.Count)
    $systematicSecondRunAdvantage = ($allSameSign -and [math]::Abs($pssSignedMedian) -gt $MaxPssDriftKb)

    if ($pssMedianAbs -gt $MaxPssDriftKb) { $reasons.Add("median |ΔPSS| $pssMedianAbs KB exceeds $MaxPssDriftKb KB") | Out-Null }
    if ($pdMedianAbs -gt $MaxPrivateDirtyDriftKb) { $reasons.Add("median |ΔPrivate_Dirty| $pdMedianAbs KB exceeds $MaxPrivateDirtyDriftKb KB") | Out-Null }
    if ($mcMedianAbs -gt $MaxMemoryCurrentDriftBytes) { $reasons.Add("median |Δmemory.current| $mcMedianAbs bytes exceeds $MaxMemoryCurrentDriftBytes bytes") | Out-Null }
    if ($systematicSecondRunAdvantage) { $reasons.Add("systematic second-run advantage detected (median signed ΔPSS $pssSignedMedian KB)") | Out-Null }
    if ($semanticErrorsTotal -ne 0) { $reasons.Add("$semanticErrorsTotal semantic difference(s) between same-artifact runs (must be 0)") | Out-Null }

    $controlQualified = ($reasons.Count -eq 0)
    if (-not $controlQualified) { $allQualified = $false }

    $controlReports.Add([ordered]@{
        label                        = $label
        artifactSha256               = $artifactSha
        reversedPairCount            = $pairs.Count
        pairs                        = $pairs.ToArray()
        medianPssAbsKb               = $pssMedianAbs
        medianPrivateDirtyAbsKb      = $pdMedianAbs
        medianMemoryCurrentAbsBytes  = $mcMedianAbs
        medianPssSecondMinusFirstKb  = $pssSignedMedian
        systematicSecondRunAdvantage = $systematicSecondRunAdvantage
        semanticErrorsTotal          = $semanticErrorsTotal
        qualified                    = $controlQualified
        reasons                      = @($reasons)
    }) | Out-Null
}

$report = [ordered]@{
    schemaVersion = 'jmoa-same-artifact-noise-v2'
    thresholds    = [ordered]@{
        maxPssDriftKb              = $MaxPssDriftKb
        maxPrivateDirtyDriftKb     = $MaxPrivateDirtyDriftKb
        maxMemoryCurrentDriftBytes = $MaxMemoryCurrentDriftBytes
        minReversedPairsPerControl = $MinReversedPairsPerControl
    }
    controls      = $controlReports.ToArray()
    qualified     = $allQualified
}
Write-JmoaJson $report (Join-Path $OutputDirectory 'same-artifact-noise.json')

$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine('# Same-Artifact Noise')
[void]$md.AppendLine('')
[void]$md.AppendLine("Qualified: **$($report.qualified)**")
[void]$md.AppendLine('')
foreach ($c in $report.controls) {
    [void]$md.AppendLine("## $($c.label) (artifact $($c.artifactSha256))")
    [void]$md.AppendLine('')
    [void]$md.AppendLine("- Reversed pairs: $($c.reversedPairCount)")
    [void]$md.AppendLine("- Median |ΔPSS|: $($c.medianPssAbsKb) KB")
    [void]$md.AppendLine("- Median |ΔPrivate_Dirty|: $($c.medianPrivateDirtyAbsKb) KB")
    [void]$md.AppendLine("- Median |Δmemory.current|: $($c.medianMemoryCurrentAbsBytes) bytes")
    [void]$md.AppendLine("- Median signed ΔPSS (second-first): $($c.medianPssSecondMinusFirstKb) KB")
    [void]$md.AppendLine("- Systematic second-run advantage: $($c.systematicSecondRunAdvantage)")
    [void]$md.AppendLine("- Semantic differences: $($c.semanticErrorsTotal)")
    [void]$md.AppendLine("- Qualified: **$($c.qualified)**")
    if ($c.reasons.Count -gt 0) { [void]$md.AppendLine("- Reasons: $($c.reasons -join '; ')") }
    [void]$md.AppendLine('')
}
Write-JmoaText $md.ToString() (Join-Path $OutputDirectory 'same-artifact-noise.md')
$report | ConvertTo-Json -Depth 12
