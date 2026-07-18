param(
    [string]$StudyDirectory = 'target/v2-patient-root-cause/runtime/extracted-common-appcds-study',
    [string]$OutputDirectory = 'docs/runtime-policy-studies'
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
$preparationPath = Join-Path $StudyDirectory 'preparation-manifest.json'
$gatesPath = Join-Path $StudyDirectory 'fixed-artifact-gates.json'
foreach ($path in @($preparationPath, $gatesPath)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing required study result: $path" }
}
$preparation = Get-Content -Raw -LiteralPath $preparationPath | ConvertFrom-Json
$gates = Get-Content -Raw -LiteralPath $gatesPath | ConvertFrom-Json
if ($preparation.status -ne 'PREPARED') { throw 'Preparation did not pass.' }
if ($gates.status -ne 'PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED') { throw "Unexpected terminal status: $($gates.status)" }

New-JmoaDirectory $OutputDirectory
$archiveRecords = @($preparation.archives | ForEach-Object {
    [ordered]@{
        artifact = $_.version
        copy = $_.copy
        archiveBytes = [long]$_.archiveBytes
        dynamicArchivedClassCount = [int]$_.dynamicArchivedClassCount
    }
})
$gateRecords = @($gates.gates | ForEach-Object {
    [ordered]@{
        artifact = $_.version
        status = $_.status
        medianAppMinusBasePssKb = [long]$_.medianAppMinusBasePssKb
        medianAppMinusBaseMemoryCurrentBytes = [long]$_.medianAppMinusBaseMemoryCurrentBytes
        samePssDirectionInBothOrders = [bool]$_.samePssDirectionInBothOrders
        mappedPssDeltaPercent = [double]$_.mappedPssDeltaPercent
        pairs = @($_.pairs | ForEach-Object {
            [ordered]@{
                copy = $_.copy
                order = $_.order
                pssKb = [long]$_.deltas.pssKb
                privateDirtyKb = [long]$_.deltas.privateDirtyKb
                memoryCurrentBytes = [long]$_.deltas.memoryCurrentBytes
                archiveMappedPssKb = [long]$_.deltas.archiveMappedPssKb
            }
        })
    }
})
$report = [ordered]@{
    metadataVersion = 'patient-extracted-common-appcds-study-v1'
    status = 'PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED'
    classification = @('POST_V2', 'V3_INVESTIGATION', 'TERMINAL')
    service = 'patient-service'
    topology = 'SINGLE_REPLICA'
    launchMode = 'EXTRACTED_CLASSPATH'
    mechanism = 'DYNAMIC_APP_CDS_COMMON_CLASS_TOP_ARCHIVE'
    optimizerArtifactsChanged = $false
    commonClassProfileCount = 1
    requestedCommonClassCount = [int]$preparation.commonClassSet.classCount
    normalizedDynamicArchivedClassCount = [int]$archiveRecords[0].dynamicArchivedClassCount
    classpathEntryCount = [int]$preparation.classpaths.v1.entryCount
    dynamicClassListFilterSupportedByJdk = $false
    archiveMethodCorrection = 'Java 26 ignores SharedClassListFile during ArchiveClassesAtExit. A deterministic non-initializing preloader generated a true dynamic top archive over the stock JDK base while preserving identical BASE/APP classpaths.'
    reproducibility = @($preparation.reproducibility | ForEach-Object {
        [ordered]@{ artifact=$_.version;archivedClassJaccard=[double]$_.archivedClassJaccard;archiveSizeDeltaPercent=[double]$_.archiveSizeDeltaPercent;passed=[bool]$_.passed }
    })
    archives = $archiveRecords
    fixedArtifactGates = $gateRecords
    workload = [ordered]@{ requestsPerRun=600;runCount=8;totalRequests=4800;errors=0 }
    v1ToV2AppCdsScreenRun = $false
    v1ToV2AppCdsConfirmationRun = $false
    permanentStop = $true
    productDecision = 'Keep Patient on NO_CDS_LOW_DIRTY. Do not perform further single-replica Patient AppCDS studies.'
    claimBoundary = 'This fixed-artifact study measures marginal application-archive economics. It does not alter the accepted Patient V1-to-V2 no-CDS win and does not imply CDS is universally harmful.'
}
Write-JmoaJson $report (Join-Path $OutputDirectory 'patient-extracted-common-appcds-study.json')

$v1 = @($gateRecords | Where-Object artifact -eq 'V1')[0]
$v2 = @($gateRecords | Where-Object artifact -eq 'V2')[0]
$reproRows = ($report.reproducibility | ForEach-Object { "| $($_.artifact) | $([Math]::Round($_.archivedClassJaccard, 6)) | $([Math]::Round($_.archiveSizeDeltaPercent, 4))% | $($_.passed) |" }) -join "`n"
$pairRows = ($gateRecords | ForEach-Object { $artifact=$_.artifact; $_.pairs | ForEach-Object { "| $artifact | $($_.copy) | $($_.order) | $($_.pssKb) KB | $($_.privateDirtyKb) KB | $($_.memoryCurrentBytes) B | $($_.archiveMappedPssKb) KB |" } }) -join "`n"
$md = @"
# Patient Extracted-Layout Common-Class AppCDS Study

Status: ``PATIENT_SINGLE_REPLICA_APP_CDS_REJECTED``

This was the one authorized post-V2 attempt materially different from the
rejected broad fat-JAR archive study. The accepted Patient V1 and V2 bytecode
artifacts were unchanged. Both were materialized as Spring Boot extracted
layouts and launched on explicit, frozen 164-entry classpaths.

## Method Correction

Java 26 does not use ``SharedClassListFile`` as a filter for
``ArchiveClassesAtExit``. A direct probe showed the option was ignored. A
small deterministic preloader therefore loaded the single predeclared common
set without class initialization. It was present on both BASE and APP
classpaths, allowing separate V1/V2 dynamic top archives over the same stock
JDK base archive without changing application or dependency bytes.

- Predeclared common profiles: ``1``
- Requested common classes: ``$($report.requestedCommonClassCount)``
- Normalized dynamic archived classes: ``$($report.normalizedDynamicArchivedClassCount)``
- AOT cache: not used
- Runtime javaagent: absent

## Archive Reproducibility

| Artifact | A/B class Jaccard | Size delta | Passed |
| --- | ---: | ---: | --- |
$reproRows

Both variants met the declared ``>= 0.999`` class-set and ``<= 1%`` archive-size
requirements. Runtime archive mapped-PSS variance was below ``0.1%``.

## Balanced Fixed-Artifact Results

| Artifact | Copy | Order | APP-BASE PSS | APP-BASE Private_Dirty | APP-BASE memory.current | APP archive mapped PSS |
| --- | --- | --- | ---: | ---: | ---: | ---: |
$pairRows

| Artifact | Median APP-BASE PSS | Median APP-BASE memory.current | Direction stable | Admission |
| --- | ---: | ---: | --- | --- |
| V1 | $($v1.medianAppMinusBasePssKb) KB | $($v1.medianAppMinusBaseMemoryCurrentBytes) B | $($v1.samePssDirectionInBothOrders) | ``REJECTED`` |
| V2 | $($v2.medianAppMinusBasePssKb) KB | $($v2.medianAppMinusBaseMemoryCurrentBytes) B | $($v2.samePssDirectionInBothOrders) | ``REJECTED`` |

All eight JVM runs were valid and all ``4,800`` requests completed with zero
errors. V1 regressed PSS and ``memory.current`` in both orders. V2 had an
order-sensitive PSS result, while ``memory.current`` regressed by more than
82 MB in both orders. Neither artifact satisfies the ``<= +1 MiB`` fixed-
artifact admission gate.

## Terminal Decision

The V1-to-V2 APP screen and confirmation were not run because the fixed-
artifact gate failed first. Patient remains on its confirmed
``NO_CDS_LOW_DIRTY`` policy.

No further single-replica Patient AppCDS work is authorized. This does not
alter the accepted Patient V1-to-V2 no-CDS median PSS win, does not contradict
Doctor's artifact-specific CDS result, and does not make a universal claim
about CDS.

References: [Java 26 launcher/CDS options](https://docs.oracle.com/en/java/javase/26/docs/specs/man/java.html),
[Spring Boot efficient deployments](https://docs.spring.io/spring-boot/reference/packaging/efficient.html).
"@
Write-JmoaText $md (Join-Path $OutputDirectory 'patient-extracted-common-appcds-study.md')
Write-Host 'Published sanitized Patient extracted common-class AppCDS terminal report.'
