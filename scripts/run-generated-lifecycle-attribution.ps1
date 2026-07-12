param(
    [Parameter(Mandatory)][string]$LifecycleManifest,
    [Parameter(Mandatory)][string]$StaticInventory,
    [string]$OutputDirectory = "",
    [string]$MavenExecutable = "mvn",
    [string]$PluginCoordinates = "com.yourorg.jmoa:jmoa-maven-plugin:1.0.0-SNAPSHOT",
    [switch]$FailOnStageError
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

if (-not (Test-Path -LiteralPath $LifecycleManifest -PathType Leaf)) {
    throw "Lifecycle manifest does not exist: $LifecycleManifest"
}
if (-not (Test-Path -LiteralPath $StaticInventory -PathType Leaf)) {
    throw "Static generated-class inventory does not exist: $StaticInventory"
}

$manifest = Get-Content -Raw -LiteralPath $LifecycleManifest | ConvertFrom-Json
$manifestRoot = Split-Path -Parent (Resolve-Path -LiteralPath $LifecycleManifest).Path
$inventoryPath = (Resolve-Path -LiteralPath $StaticInventory).Path
$target = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    Join-Path $manifestRoot 'v2v-stage-attribution'
} else {
    $OutputDirectory
}
New-JmoaDirectory -Path $target

$stages = @('startup', 'warmup', 'workload')
$stageResults = @()
foreach ($stage in $stages) {
    $stageNode = $manifest.stages.$stage
    $stageDir = if ($null -ne $stageNode.directory) {
        Join-Path $manifestRoot ([string]$stageNode.directory)
    } else {
        Join-Path $manifestRoot $stage
    }
    $classLoadLog = Join-Path $stageDir 'class-load.log'
    $classHistogram = Join-Path $stageDir 'class-histogram.txt'
    $stageOutput = Join-Path $target $stage
    New-JmoaDirectory -Path $stageOutput

    $missing = @(
        @('class-load.log', $classLoadLog),
        @('class-histogram.txt', $classHistogram)
    ) | Where-Object { -not (Test-Path -LiteralPath $_[1] -PathType Leaf) }
    $missing = @($missing)

    if ($missing.Count -gt 0) {
        $stageResults += [ordered]@{
            stage = $stage
            status = 'BLOCKED_INPUT_MISSING'
            missing = @($missing | ForEach-Object { $_[0] })
            attribution = ''
        }
        if ($FailOnStageError) {
            throw "Missing V2-V stage inputs for ${stage}: $($missing[0][0])"
        }
        continue
    }

    $arguments = @(
        '-N', "${PluginCoordinates}:analyze-generated-runtime",
        '-Djmoa.generatedRuntime.enabled=true',
        "-Djmoa.generatedRuntime.inventory=$inventoryPath",
        "-Djmoa.generatedRuntime.classLoadLog=$((Resolve-Path -LiteralPath $classLoadLog).Path)",
        "-Djmoa.generatedRuntime.classHistogram=$((Resolve-Path -LiteralPath $classHistogram).Path)",
        "-Djmoa.generatedRuntime.outputDir=$stageOutput"
    )
    $result = Invoke-JmoaExternal -Executable $MavenExecutable -Arguments $arguments
    Write-JmoaText -Value $result.output -Path (Join-Path $stageOutput 'attribution-command.log')
    $attribution = Join-Path $stageOutput 'generated-class-runtime-attribution.json'
    $status = if ($result.exitCode -eq 0 -and (Test-Path -LiteralPath $attribution -PathType Leaf)) {
        'ATTRIBUTION_WRITTEN'
    } else {
        'ATTRIBUTION_FAILED'
    }
    $stageResults += [ordered]@{
        stage = $stage
        status = $status
        exitCode = $result.exitCode
        attribution = if (Test-Path -LiteralPath $attribution -PathType Leaf) { $attribution } else { '' }
    }
    if ($status -eq 'ATTRIBUTION_FAILED' -and $FailOnStageError) {
        throw "V2-V stage attribution failed for $stage. See $(Join-Path $stageOutput 'attribution-command.log')."
    }
}

$manifestPayload = [ordered]@{}
foreach ($property in $manifest.PSObject.Properties) {
    $manifestPayload[$property.Name] = $property.Value
}
$manifestPayload.v2vAttribution = [ordered]@{
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    staticInventory = $inventoryPath
    outputDirectory = (Resolve-Path -LiteralPath $target).Path
    stages = $stageResults
}
$manifestPayload | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $manifestRoot 'generated-lifecycle-manifest.json') -Encoding utf8

$summary = @{
    metadataVersion = 'v2v-generated-lifecycle-attribution'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    lifecycleManifest = (Resolve-Path -LiteralPath $LifecycleManifest).Path
    staticInventory = $inventoryPath
    stages = $stageResults
    allStagesAttributed = @($stageResults | Where-Object status -eq 'ATTRIBUTION_WRITTEN').Count -eq 3
    diagnosticOnly = $true
    boundaries = @(
        'Stage attribution is diagnostic evidence and is not a V2-C memory pair.',
        'The V2-U identity tuple must still match before generated-family reconciliation.',
        'This helper never mutates generated classes or admits a prototype.'
    )
}
Write-JmoaJson -Value $summary -Path (Join-Path $target 'v2v-stage-attribution-summary.json')
$summaryLines = @(
    '# V2-V Lifecycle Attribution',
    '',
    ('- All stages attributed: `{0}`' -f $summary.allStagesAttributed),
    ''
)
$summaryLines += @($stageResults | ForEach-Object { '- {0}: `{1}`' -f $_.stage, $_.status })
Write-JmoaText -Value ($summaryLines -join [Environment]::NewLine) -Path (Join-Path $target 'v2v-stage-attribution-summary.md')

if (-not $summary.allStagesAttributed -and $FailOnStageError) { exit 2 }
Write-Host "V2-V lifecycle attribution summary written to $target"
