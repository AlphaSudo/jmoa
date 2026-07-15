param(
    [Parameter(Mandatory)][string]$ApplicationJar,
    [Parameter(Mandatory)][string]$JmoaRepoRoot,
    [string]$OutputDir = "",
    [string]$MavenExecutable = "mvn",
    [string]$JavaHome = "",
    [string]$PluginCoordinates = "com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2"
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

if (-not (Test-Path -LiteralPath $ApplicationJar -PathType Leaf)) { throw "Application JAR does not exist: $ApplicationJar" }
if (-not (Test-Path -LiteralPath $JmoaRepoRoot -PathType Container)) { throw "JMOA repository does not exist: $JmoaRepoRoot" }
if ([string]::IsNullOrWhiteSpace($OutputDir)) { $OutputDir = Join-Path $JmoaRepoRoot 'target/v2-final-patient' }

function New-Directory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { Remove-Item -LiteralPath $Path -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $Path | Out-Null
}

function Get-Sha256([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToUpperInvariant()
}

function Get-Manifest([string]$Directory) {
    $entries = @(
        Get-ChildItem -LiteralPath $Directory -Filter '*.jar' -File |
            Sort-Object Name |
            ForEach-Object {
                [ordered]@{ name = $_.Name; bytes = [long]$_.Length; sha256 = Get-Sha256 $_.FullName }
            }
    )
    $bytes = [long](($entries | ForEach-Object { [long]$_.bytes } | Measure-Object -Sum).Sum)
    [ordered]@{
        count = $entries.Count
        bytes = $bytes
        entries = $entries
    }
}

function Extract-EmbeddedLibraries([string]$SourceJar, [string]$Destination) {
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    $archive = [IO.Compression.ZipFile]::OpenRead($SourceJar)
    try {
        $entries = @($archive.Entries | Where-Object { $_.FullName -like 'BOOT-INF/lib/*.jar' })
        if ($entries.Count -eq 0) { throw 'The V1 application artifact has no BOOT-INF/lib entries.' }
        foreach ($entry in $entries) {
            $destinationPath = Join-Path $Destination ([IO.Path]::GetFileName($entry.FullName))
            $input = $entry.Open()
            $output = [IO.File]::Open($destinationPath, [IO.FileMode]::Create, [IO.FileAccess]::Write, [IO.FileShare]::None)
            try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
        }
        return $entries.Count
    } finally { $archive.Dispose() }
}

function Invoke-Reducer([string]$InputDir, [string]$ReducedDir, [string]$LogPath) {
    New-Item -ItemType Directory -Force -Path $ReducedDir | Out-Null
    $oldJavaHome = $env:JAVA_HOME
    if (-not [string]::IsNullOrWhiteSpace($JavaHome)) { $env:JAVA_HOME = $JavaHome }
    try {
        Push-Location $JmoaRepoRoot
        try {
            $arguments = @(
                '-N', "${PluginCoordinates}:reduce-bytecode",
                '-Djmoa.reducer.enabled=true',
                '-Djmoa.reducer.reportOnly=false',
                '-Djmoa.reducer.optimize=true',
                '-Djmoa.reducer.profile=release-low-footprint',
                '-Djmoa.reducer.engine=raw',
                '-Djmoa.reducer.stripLocalVariableTable=true',
                '-Djmoa.reducer.stripLocalVariableTypeTable=true',
                "-Djmoa.reducer.inputDir=$InputDir",
                "-Djmoa.reducer.outputDir=$ReducedDir"
            )
            $output = & $MavenExecutable @arguments 2>&1 | Out-String
            $exitCode = $LASTEXITCODE
            Set-Content -LiteralPath $LogPath -Value $output -Encoding UTF8
            if ($exitCode -ne 0) { throw "Reducer failed with exit code $exitCode. See $LogPath" }
        } finally { Pop-Location }
    } finally {
        if ([string]::IsNullOrWhiteSpace($oldJavaHome)) { Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue }
        else { $env:JAVA_HOME = $oldJavaHome }
    }
}

function Repack-Application([string]$SourceJar, [string]$ReducedDir, [string]$DestinationJar) {
    $source = [IO.Compression.ZipFile]::OpenRead($SourceJar)
    try {
        $target = [IO.Compression.ZipFile]::Open($DestinationJar, [IO.Compression.ZipArchiveMode]::Create)
        try {
            $replacements = @{}
            Get-ChildItem -LiteralPath $ReducedDir -Filter '*.jar' -File | ForEach-Object { $replacements[$_.Name] = $_.FullName }
            $replaced = 0
            $originalLibraries = 0
            foreach ($entry in $source.Entries) {
                if ($entry.FullName -like 'BOOT-INF/lib/*.jar') {
                    $originalLibraries++
                    $name = [IO.Path]::GetFileName($entry.FullName)
                    if (-not $replacements.ContainsKey($name)) { throw "Reduced dependency is missing for embedded entry: $name" }
                    $replacement = $replacements[$name]
                    # Spring Boot's nested-jar loader requires BOOT-INF/lib entries to be STORED.
                    $newEntry = $target.CreateEntry($entry.FullName, [IO.Compression.CompressionLevel]::NoCompression)
                    $newEntry.LastWriteTime = $entry.LastWriteTime
                    $input = [IO.File]::OpenRead($replacement)
                    $output = $newEntry.Open()
                    try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
                    $replaced++
                } else {
                    $compression = if ($entry.Length -eq $entry.CompressedLength) {
                        [IO.Compression.CompressionLevel]::NoCompression
                    } else {
                        [IO.Compression.CompressionLevel]::Optimal
                    }
                    $newEntry = $target.CreateEntry($entry.FullName, $compression)
                    $newEntry.LastWriteTime = $entry.LastWriteTime
                    $input = $entry.Open()
                    $output = $newEntry.Open()
                    try { $input.CopyTo($output) } finally { $output.Dispose(); $input.Dispose() }
                }
            }
            if ($replaced -ne $originalLibraries) { throw "Only replaced $replaced of $originalLibraries embedded libraries." }
            return [ordered]@{ embeddedLibraries = $originalLibraries; replacedLibraries = $replaced }
        } finally { $target.Dispose() }
    } finally { $source.Dispose() }
}

function Write-JsonFile($Value, [string]$Path) {
    $Value | ConvertTo-Json -Depth 16 | Set-Content -LiteralPath $Path -Encoding UTF8
}

New-Directory $OutputDir
$v1Libraries = Join-Path $OutputDir 'v1-embedded-libs'
$v2Libraries = Join-Path $OutputDir 'v2-reduced-libs'
$v1Artifact = Join-Path $OutputDir 'v1-final.jar'
$v2Artifact = Join-Path $OutputDir 'v2-final.jar'
Copy-Item -LiteralPath $ApplicationJar -Destination $v1Artifact -Force
$embeddedCount = Extract-EmbeddedLibraries -SourceJar $ApplicationJar -Destination $v1Libraries
$v1Manifest = Get-Manifest $v1Libraries

$reducerLog = Join-Path $OutputDir 'reducer-command.log'
Invoke-Reducer -InputDir $v1Libraries -ReducedDir $v2Libraries -LogPath $reducerLog
$v2Manifest = Get-Manifest $v2Libraries
$repack = Repack-Application -SourceJar $ApplicationJar -ReducedDir $v2Libraries -DestinationJar $v2Artifact

$reducerReportPath = Join-Path $v2Libraries 'reducer-build-report.json'
$reducerReport = if (Test-Path -LiteralPath $reducerReportPath -PathType Leaf) { Get-Content -Raw -LiteralPath $reducerReportPath | ConvertFrom-Json } else { $null }
$artifactDelta = [long](Get-Item $v2Artifact).Length - [long](Get-Item $v1Artifact).Length
$dependencyDelta = [long]$v2Manifest.bytes - [long]$v1Manifest.bytes

$freeze = [ordered]@{
    metadataVersion = 'v2-patient-artifact-freeze-v1'
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    comparison = 'final V1 -> final V2'
    v1 = [ordered]@{
        artifact = (Get-Item $v1Artifact).FullName
        sha256 = Get-Sha256 $v1Artifact
        bytes = [long](Get-Item $v1Artifact).Length
        dependencyManifest = $v1Manifest
    }
    v2 = [ordered]@{
        artifact = (Get-Item $v2Artifact).FullName
        sha256 = Get-Sha256 $v2Artifact
        bytes = [long](Get-Item $v2Artifact).Length
        dependencyManifest = $v2Manifest
    }
    dependencyByteDelta = $dependencyDelta
    outerJarByteDelta = $artifactDelta
    replacedEmbeddedLibraries = $repack.replacedLibraries
    reducerReport = if ($reducerReport) { [ordered]@{ mutationEnabled = $reducerReport.mutationEnabled; engine = $reducerReport.engine; classes = $reducerReport.classCount; reducedClasses = [long](($reducerReport.artifacts | ForEach-Object { [long]$_.reducedClassCount } | Measure-Object -Sum).Sum); removedBytes = $reducerReport.totalRemovedBytes; estimatedRemovableBytes = $reducerReport.totalEstimatedRemovableBytes } } else { $null }
    runtimeStatus = 'NOT_RUN'
    claimBoundary = 'This freeze proves artifact identity and materialization only. Runtime acceptance requires semantic smoke, six valid runs, V2-C validation, and V2-D attribution.'
}
Write-JsonFile $freeze (Join-Path $OutputDir 'v2-patient-artifact-freeze.json')

$smoke = [ordered]@{
    metadataVersion = 'v2-patient-artifact-smoke-v1'
    generatedAt = $freeze.generatedAt
    status = if ($repack.replacedLibraries -eq $embeddedCount -and $v2Manifest.count -eq $v1Manifest.count) { 'PASSED' } else { 'FAILED' }
    v1ArtifactSha256 = $freeze.v1.sha256
    v2ArtifactSha256 = $freeze.v2.sha256
    embeddedLibraries = $embeddedCount
    replacedLibraries = $repack.replacedLibraries
    v1DependencyBytes = $v1Manifest.bytes
    v2DependencyBytes = $v2Manifest.bytes
    dependencyByteDelta = $dependencyDelta
    outerJarByteDelta = $artifactDelta
    reducerReport = $freeze.reducerReport
    failureBoundary = 'A successful artifact smoke is not a runtime memory claim.'
}
Write-JsonFile $smoke (Join-Path $OutputDir 'v2-patient-artifact-smoke.json')

$proof = [ordered]@{
    metadataVersion = 'v2-patient-materialization-proof-v1'
    generatedAt = $freeze.generatedAt
    status = $smoke.status
    launchMode = 'SPRING_BOOT_FAT_JAR'
    v1ArtifactSha256 = $freeze.v1.sha256
    v2ArtifactSha256 = $freeze.v2.sha256
    v1EmbeddedDependencyCount = $v1Manifest.count
    v2EmbeddedDependencyCount = $v2Manifest.count
    replacementCount = $repack.replacedLibraries
    originalShadowingCheck = 'PENDING_RUNTIME_ORIGIN_CAPTURE'
    runtimeStatus = 'NOT_RUN'
}
Write-JsonFile $proof (Join-Path $OutputDir 'v2-patient-materialization-proof.json')

@"
# Patient Artifact Freeze

Status: **$($smoke.status)**

Comparison: `final V1 -> final V2`

- V1 application SHA-256: `$($freeze.v1.sha256)`
- V2 application SHA-256: `$($freeze.v2.sha256)`
- Embedded dependency JARs: `$embeddedCount`
- Replaced embedded dependency JARs: `$($repack.replacedLibraries)`
- Dependency byte delta: `$dependencyDelta` bytes
- Outer fat-JAR byte delta after repacking: `$artifactDelta` bytes (packaging-compression observation, not the dependency reducer delta)
- Reducer engine: `raw`
- Reducer profile: `release-low-footprint`
- Mutated attributes: `LocalVariableTable`, `LocalVariableTypeTable`
- Preserved attributes: `LineNumberTable`, `SourceFile`, `StackMapTable`, annotations, `Signature`, `BootstrapMethods`

This is an artifact identity/materialization result. It does not claim a runtime win.
"@ | Set-Content -LiteralPath (Join-Path $OutputDir 'v2-patient-artifact-freeze.md') -Encoding UTF8

@"
# Patient Artifact Smoke

Status: **$($smoke.status)**

The V2 artifact replaced `$($repack.replacedLibraries)` of `$embeddedCount` embedded dependency JARs from the frozen V1 artifact. The dependency layer changed by `$dependencyDelta` bytes and the outer artifact changed by `$artifactDelta` bytes.

Runtime semantic and memory gates remain pending.
"@ | Set-Content -LiteralPath (Join-Path $OutputDir 'v2-patient-artifact-smoke.md') -Encoding UTF8

@"
# Patient Materialization Proof

Status: **$($proof.status)**

The outer Spring Boot artifact contains the same number of dependency entries after replacement, and every V1 embedded dependency has a V2 counterpart. Runtime-origin proof remains pending until the service is launched from this artifact.
"@ | Set-Content -LiteralPath (Join-Path $OutputDir 'v2-patient-materialization-proof.md') -Encoding UTF8

Write-Host "Patient artifact freeze completed: $OutputDir"
