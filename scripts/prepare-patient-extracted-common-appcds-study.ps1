param(
    [string]$EvidenceRoot = 'target/v2-patient-root-cause',
    [string]$StudyDirectory = '',
    [string]$V1StartupLog = '',
    [string]$V2StartupLog = '',
    [string]$JdkImage = 'eclipse-temurin:26-jdk-jammy',
    [string]$ContainerCli = 'podman',
    [string]$MavenExecutable = 'mvn',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')

if ([string]::IsNullOrWhiteSpace($StudyDirectory)) { $StudyDirectory = Join-Path $EvidenceRoot 'runtime/extracted-common-appcds-study' }
if ([string]::IsNullOrWhiteSpace($V1StartupLog)) { $V1StartupLog = Join-Path $EvidenceRoot 'runtime/appcds-study/v1-startup-a-training.log' }
if ([string]::IsNullOrWhiteSpace($V2StartupLog)) { $V2StartupLog = Join-Path $EvidenceRoot 'runtime/appcds-study/v2-startup-a-training.log' }

$artifacts = @{
    v1 = Join-Path $EvidenceRoot 'artifacts/v1-final.jar'
    v2 = Join-Path $EvidenceRoot 'artifacts/v2-final.jar'
}
$proofPath = Join-Path $EvidenceRoot 'artifacts/v2-patient-materialization-proof.json'
$runtimeRoot = Join-Path $EvidenceRoot 'runtime'
foreach ($path in @($artifacts.v1, $artifacts.v2, $proofPath, $V1StartupLog, $V2StartupLog)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Required input does not exist: $path" }
}

New-JmoaDirectory $StudyDirectory
$StudyDirectory = (Resolve-Path -LiteralPath $StudyDirectory).Path
$proof = Get-Content -Raw -LiteralPath $proofPath | ConvertFrom-Json
if ((Get-JmoaSha256 $artifacts.v1) -ne $proof.v1ArtifactSha256) { throw 'Accepted V1 artifact hash does not match its materialization proof.' }
if ((Get-JmoaSha256 $artifacts.v2) -ne $proof.v2ArtifactSha256) { throw 'Accepted V2 artifact hash does not match its materialization proof.' }

function Invoke-Checked([string]$Executable, [string[]]$Arguments, [string]$FailureMessage) {
    $result = Invoke-JmoaExternal -Executable $Executable -Arguments $Arguments
    if ($result.exitCode -ne 0) { throw "$FailureMessage`n$($result.output)" }
    return $result
}

function Get-ManifestClassPath([string]$JarPath) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $JarPath).Path)
    try {
        $entry = $archive.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $entry) { throw "Missing manifest: $JarPath" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
    $unfolded = $text -replace "`r?`n ", ''
    if ($unfolded -notmatch '(?m)^Class-Path:\s*(.+)$') { throw "Extracted app manifest has no Class-Path: $JarPath" }
    return @($Matches[1].Trim() -split '\s+' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-ManifestAttribute([string]$JarPath, [string]$AttributeName) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $JarPath).Path)
    try {
        $entry = $archive.GetEntry('META-INF/MANIFEST.MF')
        if ($null -eq $entry) { throw "Missing manifest: $JarPath" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
    $unfolded = $text -replace "`r?`n ", ''
    $pattern = '(?m)^' + [regex]::Escape($AttributeName) + ':\s*(.+)$'
    if ($unfolded -notmatch $pattern) { throw "Extracted app manifest has no ${AttributeName}: $JarPath" }
    return $Matches[1].Trim()
}

function Get-EligibleClasses([string]$LogPath) {
    $classes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in [IO.File]::ReadLines((Resolve-Path -LiteralPath $LogPath).Path)) {
        if ($line -notmatch '\[class,load\]\s+(\S+)\s+source:\s+(.+)$') { continue }
        $name = $Matches[1]
        $source = $Matches[2]
        if ($source -notmatch '(?i)(app\.jar|BOOT-INF|\.jar)') { continue }
        if ($name -match '/0x|\$\$Lambda|\$Proxy|^jdk\.proxy|\$\$SpringCGLIB|CGLIB|ByteBuddy|HibernateProxy|^\[|^org\.springframework\.boot\.loader\.') { continue }
        [void]$classes.Add($name)
    }
    return $classes
}

function Write-Generated([string]$Value, [string]$Path) { Set-Content -LiteralPath $Path -Value $Value -Encoding utf8 }
function New-Launch([string]$Template, [string]$Overlay, [string]$Destination) {
    $text = Get-Content -Raw -LiteralPath $Template
    $updated = [regex]::Replace($text, "-OverlayFile\s+'[^']+'", ("-OverlayFile '" + $Overlay.Replace("'", "''") + "'"))
    if ($updated -eq $text) { throw "Could not replace OverlayFile in launch template: $Template" }
    Write-Generated $updated $Destination
}

$toolPom = Join-Path (Split-Path $PSScriptRoot -Parent) 'tools/common-class-archive-preloader/pom.xml'
Invoke-Checked $MavenExecutable @('-q', '-f', $toolPom, 'clean', 'test', 'package') 'Common-class preloader build failed.' | Out-Null
$builtTool = Join-Path (Split-Path $toolPom -Parent) 'target/jmoa-common-class-archive-preloader-2.0.0-rc2.jar'
if (-not (Test-Path -LiteralPath $builtTool -PathType Leaf)) { throw "Expected preloader JAR was not built: $builtTool" }
$toolPath = Join-Path $StudyDirectory 'common-class-archive-preloader.jar'
if ($Force -or -not (Test-Path -LiteralPath $toolPath -PathType Leaf)) {
    Copy-Item -LiteralPath $builtTool -Destination $toolPath -Force
}

foreach ($version in @('v1', 'v2')) {
    $layout = Join-Path $StudyDirectory "$version-extracted"
    if ($Force -and (Test-Path -LiteralPath $layout)) { Remove-Item -LiteralPath $layout -Recurse -Force }
    New-JmoaDirectory $layout
    if (-not (Test-Path -LiteralPath (Join-Path $layout 'app.jar') -PathType Leaf)) {
        $artifactMount = (Resolve-Path -LiteralPath $artifacts[$version]).Path.Replace('\', '/') + ':/input/app.jar:ro'
        $layoutMount = (Resolve-Path -LiteralPath $layout).Path.Replace('\', '/') + ':/out:rw'
        Invoke-Checked $ContainerCli @('run', '--rm', '-v', $artifactMount, '-v', $layoutMount, $JdkImage,
            'java', '-Djarmode=tools', '-jar', '/input/app.jar', 'extract', '--destination', '/out', '--force') "Spring Boot extraction failed for $version." | Out-Null
    }
}

$v1Classes = Get-EligibleClasses $V1StartupLog
$v2Classes = Get-EligibleClasses $V2StartupLog
$common = @($v1Classes | Where-Object { $v2Classes.Contains($_) } | Sort-Object)
if ($common.Count -lt 100) { throw "Common class derivation produced an implausibly small set: $($common.Count)" }
$classListPath = Join-Path $StudyDirectory 'patient-common-startup-classes.txt'
Write-Generated (($common -join "`n") + "`n") $classListPath

$classpathRecords = [ordered]@{}
$applicationMainClasses = [ordered]@{}
foreach ($version in @('v1', 'v2')) {
    $layout = Join-Path $StudyDirectory "$version-extracted"
    $manifestEntries = Get-ManifestClassPath (Join-Path $layout 'app.jar')
    $applicationMainClasses[$version] = Get-ManifestAttribute (Join-Path $layout 'app.jar') 'Start-Class'
    foreach ($entry in $manifestEntries) {
        if (-not (Test-Path -LiteralPath (Join-Path $layout $entry) -PathType Leaf)) { throw "Manifest classpath entry is absent for ${version}: $entry" }
    }
    $containerEntries = @('/application/app.jar') + @($manifestEntries | ForEach-Object { '/application/' + $_ }) + @('/opt/leyden/extracted-common-appcds-study/common-class-archive-preloader.jar')
    $classpath = $containerEntries -join ':'
    [IO.File]::WriteAllText((Join-Path $StudyDirectory "$version.classpath"), $classpath, [Text.UTF8Encoding]::new($false))
    $classpathRecords[$version] = [ordered]@{
        entryCount = $containerEntries.Count
        fingerprint = Get-JmoaTextSha256 $classpath
        entries = $containerEntries
    }
}

$inspectionRecords = @()
foreach ($version in @('v1', 'v2')) {
    $layout = (Resolve-Path -LiteralPath (Join-Path $StudyDirectory "$version-extracted")).Path
    $classpath = (Get-Content -Raw -LiteralPath (Join-Path $StudyDirectory "$version.classpath")).Trim()
    foreach ($copy in @('a', 'b')) {
        $stem = "$version-common-$copy"
        $archive = Join-Path $StudyDirectory "$stem.jsa"
        $trainingLog = Join-Path $StudyDirectory "$stem-training.log"
        $trainingOutput = Join-Path $StudyDirectory "$stem-training-output.txt"
        $layoutMount = $layout.Replace('\', '/') + ':/application:ro'
        $studyMount = $StudyDirectory.Replace('\', '/') + ':/opt/leyden/extracted-common-appcds-study:rw'
        $canReuse = -not $Force -and (Test-Path -LiteralPath $archive -PathType Leaf) `
            -and (Test-Path -LiteralPath $trainingOutput -PathType Leaf) `
            -and (Get-Content -Raw -LiteralPath $trainingOutput) -match "JMOA_PRELOAD requested=$($common.Count) loaded=$($common.Count) failed=0"
        if (-not $canReuse) {
            if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
            if (Test-Path -LiteralPath $trainingLog) { Remove-Item -LiteralPath $trainingLog -Force }
            $result = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('run', '--rm', '-v', $layoutMount, '-v', $studyMount,
                '-w', '/application', $JdkImage, 'java', '-XX:+UseCompactObjectHeaders', '-Xshare:on',
                "-XX:ArchiveClassesAtExit=/opt/leyden/extracted-common-appcds-study/$stem.jsa",
                "-Xlog:cds+dynamic=debug,class+load=info:file=/opt/leyden/extracted-common-appcds-study/$stem-training.log",
                '-cp', $classpath, 'jmoa.tools.CommonClassArchivePreloader', '/opt/leyden/extracted-common-appcds-study/patient-common-startup-classes.txt')
            Write-JmoaText $result.output $trainingOutput
            if ($result.exitCode -ne 0 -or $result.output -notmatch "JMOA_PRELOAD requested=$($common.Count) loaded=$($common.Count) failed=0") {
                throw "Common-class archive training failed for $stem. See $trainingOutput"
            }
        }

        $printPath = Join-Path $StudyDirectory "$stem-print.txt"
        $print = Invoke-JmoaExternal -Executable $ContainerCli -Arguments @('run', '--rm', '-v', $layoutMount, '-v', $studyMount,
            '-w', '/application', $JdkImage, 'java', '-XX:+UseCompactObjectHeaders', "-XX:SharedArchiveFile=/opt/leyden/extracted-common-appcds-study/$stem.jsa",
            '-XX:+PrintSharedArchiveAndExit', '-cp', $classpath, $applicationMainClasses[$version])
        Write-JmoaText $print.output $printPath
        if ($print.exitCode -ne 0 -or $print.output -notmatch 'Dynamic archive name:') { throw "Archive inspection failed for $stem." }
        $dynamic = $false
        $archived = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach ($line in ($print.output -split "`r?`n")) {
            if ($line -match '^Dynamic archive name:') { $dynamic = $true; continue }
            if ($dynamic -and $line -match '^Archived TrainingData Dictionary') { $dynamic = $false }
            if ($dynamic -and $line -match '^\s*\d+:\s+(\S+)\s+\S+_loader\s*$') {
                $normalized = $Matches[1] -replace '/0x[0-9a-fA-F]+$', '/<hidden>' -replace '\$\$Lambda\$\d+', '$$Lambda$<generated>'
                [void]$archived.Add($normalized)
            }
        }
        $inspectionRecords += [ordered]@{
            version = $version.ToUpperInvariant(); copy = $copy.ToUpperInvariant(); archiveFile = "$stem.jsa"
            archiveSha256 = Get-JmoaSha256 $archive; archiveBytes = [long](Get-Item $archive).Length
            dynamicArchivedClassCount = $archived.Count; normalizedDynamicArchivedClasses = @($archived | Sort-Object)
        }
    }
}

function Get-Jaccard($Left, $Right) {
    $a = [Collections.Generic.HashSet[string]]::new([string[]]$Left, [StringComparer]::Ordinal)
    $b = [Collections.Generic.HashSet[string]]::new([string[]]$Right, [StringComparer]::Ordinal)
    $intersection = [Collections.Generic.HashSet[string]]::new($a, [StringComparer]::Ordinal); $intersection.IntersectWith($b)
    $union = [Collections.Generic.HashSet[string]]::new($a, [StringComparer]::Ordinal); $union.UnionWith($b)
    if ($union.Count -eq 0) { return 1.0 }
    return [double]$intersection.Count / [double]$union.Count
}

$reproducibility = @()
foreach ($version in @('V1', 'V2')) {
    $a = @($inspectionRecords | Where-Object { $_.version -eq $version -and $_.copy -eq 'A' })[0]
    $b = @($inspectionRecords | Where-Object { $_.version -eq $version -and $_.copy -eq 'B' })[0]
    $sizeDeltaPercent = 100.0 * [Math]::Abs($a.archiveBytes - $b.archiveBytes) / [Math]::Max($a.archiveBytes, $b.archiveBytes)
    $jaccard = Get-Jaccard $a.normalizedDynamicArchivedClasses $b.normalizedDynamicArchivedClasses
    $reproducibility += [ordered]@{ version = $version; archivedClassJaccard = $jaccard; archiveSizeDeltaPercent = $sizeDeltaPercent; passed = $jaccard -ge 0.999 -and $sizeDeltaPercent -le 1.0 }
}
if ($reproducibility | Where-Object { -not $_.passed }) { throw 'Archive A/B structural reproducibility gate failed.' }

$templateOverlay = @{
    v1 = Join-Path $runtimeRoot 'overlay-v1-nocds.yml'
    v2 = Join-Path $runtimeRoot 'overlay-v2-nocds.yml'
}
$templateLaunch = @{
    v1 = Join-Path $runtimeRoot 'patient-v1-nocds-launch.ps1'
    v2 = Join-Path $runtimeRoot 'patient-v2-nocds-launch.ps1'
}
foreach ($version in @('v1', 'v2')) {
    $layout = (Resolve-Path -LiteralPath (Join-Path $StudyDirectory "$version-extracted")).Path.Replace('\', '/')
    $base = Get-Content -Raw -LiteralPath $templateOverlay[$version]
    $base = [regex]::Replace($base, "(?m)^\s+-\s+'[^']+/$version-final\.jar:/app/app\.jar:ro'\s*$", "      - '${layout}:/application:ro'")
    $base = [regex]::Replace($base, "(?m)^[ \t]+-[ \t]+'[^']+:/application/BOOT-INF/lib:ro'[ \t]*`r?`n", '')
    $base = $base.Replace('-Xshare:off', '-Xshare:on')
    $requiredAlias = $env:JMOA_REQUIRED_ENVIRONMENT_ALIAS
    $requiredSourceKey = $env:JMOA_REQUIRED_ENVIRONMENT_SOURCE_KEY
    if (-not [string]::IsNullOrWhiteSpace($requiredAlias)) {
        if ([string]::IsNullOrWhiteSpace($requiredSourceKey)) {
            throw 'JMOA_REQUIRED_ENVIRONMENT_SOURCE_KEY is required when JMOA_REQUIRED_ENVIRONMENT_ALIAS is set.'
        }
        $aliasPattern = '(?m)^\s+' + [regex]::Escape($requiredAlias) + ':'
        $sourcePattern = '(?m)^(\s+)' + [regex]::Escape($requiredSourceKey) + ':\s*(\S+)\s*$'
        if ($base -notmatch $aliasPattern) {
            $base = [regex]::Replace($base, $sourcePattern, ('$1' + $requiredSourceKey + ': $2' + "`n" + '$1' + $requiredAlias + ': $2'))
        }
        if ($base -notmatch $aliasPattern) { throw "Could not add required environment alias '$requiredAlias'." }
    }
    $command = '["sh", "-lc", "exec java -cp \"$$(cat /opt/leyden/extracted-common-appcds-study/' + $version + '.classpath)\" ' + $applicationMainClasses[$version] + '"]'
    $base = [regex]::Replace($base, '(?m)^\s*command:\s*\["java",\s*"-jar",\s*"/app/app\.jar"\]\s*$', '    command: ' + $command)
    if ($base -match '/app/app\.jar|BOOT-INF/lib|Xshare:off' -or $base -notmatch '/application:ro') { throw "Extracted BASE overlay validation failed for $version." }
    $basePath = Join-Path $StudyDirectory "overlay-$version-extracted-base.yml"
    Write-Generated $base $basePath
    New-Launch $templateLaunch[$version] $basePath (Join-Path $StudyDirectory "patient-$version-extracted-base-launch.ps1")
    foreach ($copy in @('a', 'b')) {
        $archiveContainerPath = "/opt/leyden/extracted-common-appcds-study/$version-common-$copy.jsa"
        $app = $base.Replace('-Xshare:on', "-Xshare:on -XX:SharedArchiveFile=$archiveContainerPath")
        $appPath = Join-Path $StudyDirectory "overlay-$version-extracted-common-$copy-app.yml"
        Write-Generated $app $appPath
        New-Launch $templateLaunch[$version] $appPath (Join-Path $StudyDirectory "patient-$version-extracted-common-$copy-app-launch.ps1")
    }
}

$manifest = [ordered]@{
    metadataVersion = 'patient-extracted-common-appcds-preparation-v1'
    status = 'PREPARED'
    archiveMechanism = 'DYNAMIC_TOP_ARCHIVE_BY_CONTROLLED_NON_INITIALIZING_PRELOAD'
    dynamicSharedClassListProbe = 'UNSUPPORTED_FILTER_SHARED_CLASS_LIST_WAS_IGNORED'
    acceptedArtifacts = [ordered]@{ v1Sha256 = Get-JmoaSha256 $artifacts.v1; v2Sha256 = Get-JmoaSha256 $artifacts.v2 }
    extractedArtifacts = [ordered]@{
        v1AppSha256 = Get-JmoaSha256 (Join-Path $StudyDirectory 'v1-extracted/app.jar')
        v2AppSha256 = Get-JmoaSha256 (Join-Path $StudyDirectory 'v2-extracted/app.jar')
    }
    commonClassSet = [ordered]@{
        source = 'intersection of the accepted V1/V2 startup class-load logs'
        predeclaredProfiles = 1
        classCount = $common.Count
        sha256 = Get-JmoaSha256 $classListPath
        excludedFamilies = @('JDK/base classes', 'hidden classes', 'lambda classes', 'JDK proxies', 'Spring CGLIB', 'ByteBuddy', 'Hibernate proxies', 'fat-JAR-only Spring Boot loader classes')
    }
    classpaths = $classpathRecords
    archives = $inspectionRecords | ForEach-Object { [ordered]@{ version=$_.version;copy=$_.copy;archiveFile=$_.archiveFile;archiveSha256=$_.archiveSha256;archiveBytes=$_.archiveBytes;dynamicArchivedClassCount=$_.dynamicArchivedClassCount } }
    reproducibility = $reproducibility
    fixedArtifactGateRequired = $true
    generatedAt = [DateTime]::UtcNow.ToString('o')
}
Write-JmoaJson $manifest (Join-Path $StudyDirectory 'preparation-manifest.json')
Write-Host "Prepared extracted common-class AppCDS study: $StudyDirectory"
