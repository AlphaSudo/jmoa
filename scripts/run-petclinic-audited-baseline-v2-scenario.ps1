param(
    [string]$OutputRoot = 'C:\jmoa-runs\petclinic-baseline-v2',
    [Parameter(Mandatory)][string]$PetclinicSource,
    [Parameter(Mandatory)][string]$ConfigRepository,
    [string]$Revision = '305a1f13e4f961001d4e6cb50a9db51dc3fc5967',
    [string]$ProfilePath = 'target/v2-clean-clone/jmoa/target/v2-release/petclinic-customers-profile.json',
    [string]$AdmissionPath = 'target/v2-clean-clone/jmoa/target/v2-release/petclinic-customers-admission.txt',
    [string]$SafeSamsPath = 'target/v2-clean-clone/jmoa/target/v2-release/jmoa-additional-safe-sams.txt',
    [Parameter(Mandatory)][string]$BuildJavaHome,
    [Parameter(Mandatory)][string]$RuntimeJavaHome,
    [Parameter(Mandatory)][string]$Maven,
    [int]$WarmupSeconds = 20,
    [int]$SettleSeconds = 5
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'scenario-ledger-common.ps1')

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runId = 'petclinic-baseline-v2-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$resolvedOutputRoot = if ([IO.Path]::IsPathRooted($OutputRoot)) { [IO.Path]::GetFullPath($OutputRoot) } else { [IO.Path]::GetFullPath((Join-Path $repositoryRoot $OutputRoot)) }
$runRoot = Join-Path $resolvedOutputRoot $runId
$workRoot = Join-Path $runRoot 'work'
$sourceRoot = Join-Path $workRoot 'spring-petclinic-microservices'
$serviceRoot = Join-Path $sourceRoot 'spring-petclinic-customers-service'
$artifactRoot = Join-Path $runRoot 'artifacts'
$baselineExploded = Join-Path $runRoot 'exploded-baseline'
$v2Exploded = Join-Path $runRoot 'exploded-v2'
$runtimeJar = Join-Path $repositoryRoot 'jmoa-runtime-lib\target\jmoa-runtime-lib-2.0.0-rc2.jar'
$pluginCoordinates = 'com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2'
$baselineImage = "localhost/jmoa-scenario-petclinic-b0:$($runId.ToLowerInvariant())"
$v2Image = "localhost/jmoa-scenario-petclinic-v2:$($runId.ToLowerInvariant())"
$configImage = 'localhost/pc33-config-server:latest'
$discoveryImage = 'localhost/pc33-discovery-server:latest'
$configImageRef = $configImage
$discoveryImageRef = $discoveryImage
$java17 = Join-Path $RuntimeJavaHome 'bin\java.exe'
$buildEnvironment = @{ JAVA_HOME = $BuildJavaHome; PATH = "$(Join-Path $BuildJavaHome 'bin');$env:PATH" }
$runtimeResult = [ordered]@{}
$completed = $false

New-Item -ItemType Directory -Force -Path $workRoot,$artifactRoot | Out-Null
Start-ScenarioLedger -ScenarioId $runId -OutputDirectory $runRoot -Description @'
Fresh PetClinic customers-service baseline-to-V2 screen. The baseline is built,
launched, warmed, worked, and measured before JMOA mutation. The JMOA optimizer
and raw reducer then run fresh, after which V2 is launched under the same stack,
flags, workload, warmup, settle, and capture commands.
'@

function Invoke-Step {
    param([string]$Step,[string]$Executable,[string[]]$Arguments=@(),[string]$WorkingDirectory=$repositoryRoot,[hashtable]$Environment=@{},[switch]$AllowFailure)
    Invoke-ScenarioCommand -Step $Step -Executable $Executable -Arguments $Arguments -WorkingDirectory $WorkingDirectory -Environment $Environment -AllowFailure:$AllowFailure
}

function Wait-Health {
    param([string]$Label,[string]$Uri,[int]$TimeoutSeconds)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $attempt = 0
    while ([DateTime]::UtcNow -lt $deadline) {
        $attempt++
        $probe = Invoke-Step -Step "$Label health probe $attempt" -Executable 'curl.exe' -Arguments @('--fail','--silent','--show-error',$Uri) -AllowFailure
        if ($probe.exitCode -eq 0) { return }
        Invoke-Step -Step "$Label health retry delay $attempt" -Executable (Get-Command pwsh).Source -Arguments @('-NoProfile','-Command','Start-Sleep -Seconds 3') | Out-Null
    }
    throw "$Label did not become healthy at $Uri"
}

function Stop-VariantStack {
    param([string]$Prefix)
    foreach ($suffix in @('customers-service','discovery-server','config-server')) {
        Invoke-Step -Step "Remove $Prefix-$suffix" -Executable 'podman' -Arguments @('rm','-f',"$Prefix-$suffix") -AllowFailure | Out-Null
    }
    Invoke-Step -Step "Remove $Prefix-net" -Executable 'podman' -Arguments @('network','rm',"$Prefix-net") -AllowFailure | Out-Null
}

function Invoke-PetclinicWorkload {
    param([string]$Variant)
    $endpoints = @(
        @{m='GET';p='/actuator/health'}, @{m='GET';p='/actuator/info'}, @{m='GET';p='/owners'},
        @{m='GET';p='/owners/1'}, @{m='GET';p='/owners/2'}, @{m='GET';p='/owners/3'},
        @{m='GET';p='/owners/4'}, @{m='GET';p='/owners/5'}, @{m='GET';p='/owners/6'},
        @{m='GET';p='/owners/7'}, @{m='GET';p='/owners/8'}, @{m='GET';p='/owners/9'},
        @{m='GET';p='/owners/10'}, @{m='GET';p='/petTypes'}, @{m='GET';p='/owners/1/pets/1'},
        @{m='GET';p='/owners/2/pets/2'}, @{m='GET';p='/owners/3/pets/3'}, @{m='GET';p='/owners/4/pets/4'},
        @{m='GET';p='/owners/5/pets/5'}, @{m='GET';p='/owners/6/pets/6'},
        @{m='POST';p='/owners';b='{"firstName":"Ledger","lastName":"One","address":"1 Audit St","city":"Cairo","telephone":"5551234567"}'},
        @{m='POST';p='/owners';b='{"firstName":"Ledger","lastName":"Two","address":"2 Audit St","city":"Cairo","telephone":"5551234568"}'},
        @{m='PUT';p='/owners/1';b='{"id":1,"firstName":"George","lastName":"Franklin","address":"110 W. Liberty St.","city":"Madison","telephone":"6085551023"}'},
        @{m='GET';p='/owners/1'}, @{m='GET';p='/owners/2'}, @{m='GET';p='/owners/3'}, @{m='GET';p='/actuator/health'}
    )
    $request = 0
    for ($round=1; $round -le 3; $round++) {
        foreach ($endpoint in $endpoints) {
            $request++
            $arguments = @('--fail','--silent','--show-error','-X',$endpoint.m)
            if ($endpoint.ContainsKey('b')) { $arguments += @('-H','Content-Type: application/json','--data',$endpoint.b) }
            $arguments += "http://localhost:8081$($endpoint.p)"
            Invoke-Step -Step "$Variant workload request $request/81 round $round $($endpoint.m) $($endpoint.p)" -Executable 'curl.exe' -Arguments $arguments | Out-Null
            Invoke-Step -Step "$Variant request pacing $request" -Executable (Get-Command pwsh).Source -Arguments @('-NoProfile','-Command','Start-Sleep -Milliseconds 200') | Out-Null
        }
    }
}

function Get-Metric {
    param([string]$Text,[string]$Name)
    $match = [regex]::Match($Text,"(?m)^$([regex]::Escape($Name)):\s+(\d+)\s+kB")
    if (-not $match.Success) { throw "Metric $Name missing from smaps_rollup" }
    return [long]$match.Groups[1].Value
}

# Records an image's content-addressable identity (ID + first repo digest) rather than its mutable tag.
function Add-ImageProvenance {
    param([string]$Role,[string]$Reference)
    $probe = Invoke-Step -Step "Record image ID and digest for $Role" -Executable 'podman' -Arguments @('image','inspect','--format','{{.Id}} {{if .RepoDigests}}{{index .RepoDigests 0}}{{else}}<none>{{end}}',$Reference)
    $identity = $probe.stdout.Trim()
    Add-ScenarioNote -Title "Image provenance: $Role" -Text "Reference: $Reference`n`nID + digest: $identity"
    return $identity
}

# Resolves a (possibly :latest) reference to its immutable image ID so podman run pins the exact image.
function Resolve-ImageId {
    param([string]$Reference)
    $probe = Invoke-Step -Step "Resolve image ID for $Reference" -Executable 'podman' -Arguments @('image','inspect','--format','{{.Id}}',$Reference)
    return $probe.stdout.Trim()
}

# Deterministic content hash of a directory tree (path + file bytes), excluding the .git metadata dir.
function Get-DirectoryContentHash {
    param([string]$Root)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $fullRoot = (Resolve-Path -LiteralPath $Root).Path
        $files = Get-ChildItem -LiteralPath $fullRoot -Recurse -File | Where-Object { $_.FullName -notmatch '(\\|/)\.git(\\|/)' } | Sort-Object FullName
        foreach ($file in $files) {
            $rel = ($file.FullName.Substring($fullRoot.Length).TrimStart('\','/')) -replace '\\','/'
            $relBytes = [Text.Encoding]::UTF8.GetBytes($rel)
            [void]$sha.TransformBlock($relBytes,0,$relBytes.Length,$null,0)
            $fileHash = [Convert]::FromHexString((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash)
            [void]$sha.TransformBlock($fileHash,0,$fileHash.Length,$null,0)
        }
        [void]$sha.TransformFinalBlock([byte[]]::new(0),0,0)
        return [BitConverter]::ToString($sha.Hash).Replace('-','')
    } finally { $sha.Dispose() }
}

function Measure-Variant {
    param([string]$Variant,[string]$Image)
    $prefix = "jmoa-ledger-$($Variant.ToLowerInvariant())-$([guid]::NewGuid().ToString('N').Substring(0,8))"
    $network = "$prefix-net"
    $container = "$prefix-customers-service"
    Stop-VariantStack -Prefix $prefix
    try {
        Invoke-Step -Step "$Variant reset Podman VM page cache" -Executable 'podman' -Arguments @('machine','ssh',"sudo sh -c 'sync; echo 3 > /proc/sys/vm/drop_caches' && echo DROP_OK") | Out-Null
        Invoke-Step -Step "$Variant create isolated network" -Executable 'podman' -Arguments @('network','create',$network) | Out-Null
        $configFlags='-XX:+UseContainerSupport -XX:+UseSerialGC -Xms24m -Xmx80m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off'
        $discoveryFlags='-XX:+UseContainerSupport -XX:+UseSerialGC -Xms16m -Xmx96m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -Xshare:off'
        $customerFlags='-XX:+UseContainerSupport -XX:+UseSerialGC -Xms32m -Xmx256m -Xss256k -XX:ReservedCodeCacheSize=48m -XX:CICompilerCount=2 -XX:NativeMemoryTracking=summary -Xshare:off'
        Invoke-Step -Step "$Variant start config server" -Executable 'podman' -Arguments @('run','-d','--name',"$prefix-config-server",'--network',$network,'--network-alias','config-server','-p','8888:8888','-v',"${ConfigRepository}:/app/config-repo:ro",'-e','SPRING_PROFILES_ACTIVE=native','-e','GIT_REPO=/app/config-repo','-e','MANAGEMENT_TRACING_ENABLED=false','-e','MANAGEMENT_METRICS_ENABLED=false','-e',"JAVA_TOOL_OPTIONS=$configFlags",$configImageRef) | Out-Null
        Wait-Health -Label "$Variant config server" -Uri 'http://localhost:8888/actuator/health' -TimeoutSeconds 180
        Invoke-Step -Step "$Variant capture config-server in-container JDK" -Executable 'podman' -Arguments @('exec',"$prefix-config-server",'java','-version') | Out-Null
        Invoke-Step -Step "$Variant start discovery server" -Executable 'podman' -Arguments @('run','-d','--name',"$prefix-discovery-server",'--network',$network,'--network-alias','discovery-server','-p','8761:8761','-e','SPRING_PROFILES_ACTIVE=docker','-e','CONFIG_SERVER_URI=http://config-server:8888','-e',"JAVA_TOOL_OPTIONS=$discoveryFlags",$discoveryImageRef) | Out-Null
        Wait-Health -Label "$Variant discovery server" -Uri 'http://localhost:8761/actuator/health' -TimeoutSeconds 180
        Invoke-Step -Step "$Variant capture discovery-server in-container JDK" -Executable 'podman' -Arguments @('exec',"$prefix-discovery-server",'java','-version') | Out-Null
        Invoke-Step -Step "$Variant start customers service" -Executable 'podman' -Arguments @('run','-d','--name',$container,'--network',$network,'--network-alias','customers-service','-p','8081:8081','-e','SPRING_PROFILES_ACTIVE=docker','-e','CONFIG_SERVER_URI=http://config-server:8888','-e',"JAVA_TOOL_OPTIONS=$customerFlags",'-e','MALLOC_ARENA_MAX=1',$Image) | Out-Null
        Wait-Health -Label "$Variant customers service" -Uri 'http://localhost:8081/actuator/health' -TimeoutSeconds 900
        Invoke-Step -Step "$Variant capture customers-service in-container JDK" -Executable 'podman' -Arguments @('exec',$container,'java','-version') | Out-Null
        Invoke-Step -Step "$Variant warmup $WarmupSeconds seconds" -Executable (Get-Command pwsh).Source -Arguments @('-NoProfile','-Command',"Start-Sleep -Seconds $WarmupSeconds") | Out-Null
        Invoke-PetclinicWorkload -Variant $Variant
        Invoke-Step -Step "$Variant post-workload settle $SettleSeconds seconds" -Executable (Get-Command pwsh).Source -Arguments @('-NoProfile','-Command',"Start-Sleep -Seconds $SettleSeconds") | Out-Null
        $smapsRollup = Invoke-Step -Step "$Variant capture /proc/1/smaps_rollup" -Executable 'podman' -Arguments @('exec',$container,'cat','/proc/1/smaps_rollup')
        Invoke-Step -Step "$Variant capture full /proc/1/smaps" -Executable 'podman' -Arguments @('exec',$container,'cat','/proc/1/smaps') | Out-Null
        $memoryCurrent = Invoke-Step -Step "$Variant capture cgroup memory.current" -Executable 'podman' -Arguments @('exec',$container,'cat','/sys/fs/cgroup/memory.current')
        Invoke-Step -Step "$Variant capture JVM command line" -Executable 'podman' -Arguments @('exec',$container,'jcmd','1','VM.command_line') | Out-Null
        Invoke-Step -Step "$Variant capture JVM flags" -Executable 'podman' -Arguments @('exec',$container,'jcmd','1','VM.flags') | Out-Null
        Invoke-Step -Step "$Variant capture NMT summary" -Executable 'podman' -Arguments @('exec',$container,'jcmd','1','VM.native_memory','summary') | Out-Null
        Invoke-Step -Step "$Variant capture heap info" -Executable 'podman' -Arguments @('exec',$container,'jcmd','1','GC.heap_info') | Out-Null
        Invoke-Step -Step "$Variant capture class histogram" -Executable 'podman' -Arguments @('exec',$container,'jcmd','1','GC.class_histogram') | Out-Null
        Invoke-Step -Step "$Variant capture metaspace" -Executable 'podman' -Arguments @('exec',$container,'jcmd','1','VM.metaspace') | Out-Null
        Invoke-Step -Step "$Variant capture classloader stats" -Executable 'podman' -Arguments @('exec',$container,'jcmd','1','VM.classloader_stats') | Out-Null
        Invoke-Step -Step "$Variant inspect runtime container" -Executable 'podman' -Arguments @('inspect',$container) | Out-Null
        Invoke-Step -Step "$Variant collect service logs" -Executable 'podman' -Arguments @('logs',$container) | Out-Null
        return [ordered]@{
            pssKb = Get-Metric -Text $smapsRollup.stdout -Name 'Pss'
            privateDirtyKb = Get-Metric -Text $smapsRollup.stdout -Name 'Private_Dirty'
            memoryCurrentBytes = [long]$memoryCurrent.stdout.Trim()
            image = $Image
        }
    } finally {
        Stop-VariantStack -Prefix $prefix
    }
}

function Write-ExplodedDockerfile {
    param([string]$Root)
    $dockerfile = @'
FROM eclipse-temurin:17
WORKDIR application
ENV SPRING_PROFILES_ACTIVE=docker
COPY dependencies/ ./
RUN true
COPY spring-boot-loader/ ./
RUN true
COPY snapshot-dependencies/ ./
RUN true
COPY application/ ./
EXPOSE 8081
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
'@
    [IO.File]::WriteAllText((Join-Path $Root 'Dockerfile'),$dockerfile,[Text.UTF8Encoding]::new($false))
    $dockerfileHash = (Get-FileHash -LiteralPath (Join-Path $Root 'Dockerfile') -Algorithm SHA256).Hash
    Add-ScenarioNote -Title "Generated Dockerfile for $Root" -Text "Generated in this scenario. SHA-256: $dockerfileHash."
}

try {
    foreach ($required in @($PetclinicSource,$ConfigRepository,$ProfilePath,$AdmissionPath,$SafeSamsPath,$BuildJavaHome,$RuntimeJavaHome,$Maven)) {
        if (-not (Test-Path -LiteralPath $required)) { throw "Required input missing: $required" }
    }
    Add-ScenarioAsset -Role 'Frozen PetClinic runtime profile' -Path $ProfilePath -Provenance REUSED_FROZEN_INPUT -Note 'The original profile-generation command chain is incomplete; this scenario does not claim fresh profile training.' | Out-Null
    Add-ScenarioAsset -Role 'Frozen observed-site admission set' -Path $AdmissionPath -Provenance REUSED_FROZEN_INPUT -Note 'Accepted public-safe admission input.' | Out-Null
    Add-ScenarioAsset -Role 'Frozen additional safe SAM allowlist' -Path $SafeSamsPath -Provenance REUSED_FROZEN_INPUT -Note 'Accepted public-safe allowlist.' | Out-Null
    Invoke-Step -Step 'Inspect reused config support image' -Executable 'podman' -Arguments @('image','inspect',$configImage) | Out-Null
    Invoke-Step -Step 'Inspect reused discovery support image' -Executable 'podman' -Arguments @('image','inspect',$discoveryImage) | Out-Null
    # Pin the :latest support images to immutable IDs and record ID + digest provenance.
    $configImageRef = Resolve-ImageId -Reference $configImage
    $discoveryImageRef = Resolve-ImageId -Reference $discoveryImage
    Add-ImageProvenance -Role 'config support image (pinned)' -Reference $configImageRef | Out-Null
    Add-ImageProvenance -Role 'discovery support image (pinned)' -Reference $discoveryImageRef | Out-Null
    # Config-repo provenance: Git revision plus a deterministic content hash of the mounted tree.
    $configRepoHead = (Invoke-Step -Step 'Record mounted config-repo git revision' -Executable 'git' -Arguments @('-C',$ConfigRepository,'rev-parse','HEAD') -AllowFailure).stdout.Trim()
    $configRepoContentHash = Get-DirectoryContentHash -Root $ConfigRepository
    Add-ScenarioNote -Title 'Config-repo provenance' -Text "Path: $ConfigRepository`n`nGit HEAD: $configRepoHead`n`nContent hash (excluding .git): $configRepoContentHash"
    Invoke-Step -Step 'Record Podman state before scenario' -Executable 'podman' -Arguments @('ps','-a') | Out-Null
    Invoke-Step -Step 'Record build JDK' -Executable (Join-Path $BuildJavaHome 'bin\java.exe') -Arguments @('-version') | Out-Null
    Invoke-Step -Step 'Record runtime JDK used for extraction' -Executable $java17 -Arguments @('-version') | Out-Null
    Invoke-Step -Step 'Build and install current JMOA runtime/plugin' -Executable $Maven -Arguments @('-q','-pl','jmoa-runtime-lib,jmoa-maven-plugin','clean','install') -WorkingDirectory $repositoryRoot -Environment $buildEnvironment | Out-Null
    Add-ScenarioAsset -Role 'Freshly built JMOA runtime library' -Path $runtimeJar -Provenance BUILT_IN_SCENARIO -Note 'Built before the target-service baseline; it is not present in B0.' | Out-Null
    Invoke-Step -Step 'Clone frozen PetClinic source into isolated work directory' -Executable 'git' -Arguments @('-c','core.longpaths=true','clone','--no-hardlinks',$PetclinicSource,$sourceRoot) -WorkingDirectory $workRoot | Out-Null
    Invoke-Step -Step 'Checkout frozen PetClinic revision' -Executable 'git' -Arguments @('checkout','--detach',$Revision) -WorkingDirectory $sourceRoot | Out-Null
    Invoke-Step -Step 'Record frozen PetClinic source revision' -Executable 'git' -Arguments @('rev-parse','HEAD') -WorkingDirectory $sourceRoot | Out-Null

    $mvnw = Join-Path $sourceRoot 'mvnw.cmd'
    Invoke-Step -Step 'Build strict B0 customers-service before any JMOA target mutation' -Executable $mvnw -Arguments @('-pl','spring-petclinic-customers-service','-am','-DskipTests','package') -WorkingDirectory $sourceRoot -Environment $buildEnvironment | Out-Null
    $builtBaseline = Get-ChildItem (Join-Path $serviceRoot 'target') -Filter '*.jar' -File | Where-Object Name -NotMatch 'sources|javadoc|original' | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (-not $builtBaseline) { throw 'Baseline JAR was not produced.' }
    $baselineJar = Join-Path $artifactRoot 'petclinic-customers-b0.jar'
    Copy-Item -LiteralPath $builtBaseline.FullName -Destination $baselineJar -Force
    Add-ScenarioAsset -Role 'Strict B0 artifact' -Path $baselineJar -Provenance BUILT_IN_SCENARIO -Note 'Copied immediately after the baseline package, before target-service JMOA mutation.' | Out-Null
    New-Item -ItemType Directory -Force -Path $baselineExploded | Out-Null
    Invoke-Step -Step 'Extract strict B0 as exploded Boot application' -Executable $java17 -Arguments @('-Djarmode=tools','-jar',$baselineJar,'extract','--launcher','--layers','--destination',$baselineExploded) -WorkingDirectory $runRoot | Out-Null
    Write-ExplodedDockerfile -Root $baselineExploded
    Invoke-Step -Step 'Build strict B0 runtime image' -Executable 'podman' -Arguments @('build','-t',$baselineImage,$baselineExploded) | Out-Null
    Invoke-Step -Step 'Inspect strict B0 runtime image' -Executable 'podman' -Arguments @('image','inspect',$baselineImage) | Out-Null
    Add-ImageProvenance -Role 'strict B0 application image' -Reference $baselineImage | Out-Null

    Add-ScenarioNote -Title 'Baseline runtime starts before JMOA target transformation' -Text 'The following commands launch the auxiliary services and strict B0, execute the full 81-request workload, capture memory/JVM evidence, collect logs, and tear the stack down.'
    $runtimeResult.baseline = Measure-Variant -Variant 'B0' -Image $baselineImage

    Add-ScenarioNote -Title 'Fresh JMOA transformation stage' -Text 'The strict B0 runtime is complete. JMOA now compiles the same checkout, applies MODE_C/full-P2 using the explicitly reused frozen profile/admission inputs, packages optimized dependencies, runs the raw LVT/LVTT reducer, and materializes V2.'
    Invoke-Step -Step 'Compile same source checkout for JMOA transformation' -Executable $mvnw -Arguments @('-q','-pl','spring-petclinic-customers-service','-am','-DskipTests','clean','compile') -WorkingDirectory $sourceRoot -Environment $buildEnvironment | Out-Null
    $safeSams = (Get-Content -LiteralPath $SafeSamsPath -Raw).Trim()
    $pluginArguments = @('-q',"${pluginCoordinates}:deduplicate-lambdas",'-Djmoa.mode=MODE_C',"-Djmoa.profilePath=$([IO.Path]::GetFullPath($ProfilePath))",'-Djmoa.enableObservedSiteAdmission=true',"-Djmoa.observedAdmissionSitesFile=$([IO.Path]::GetFullPath($AdmissionPath))","-Djmoa.additionalSafeSamInterfaces=$safeSams",'-Djmoa.generateTier1Runtime=true','-Djmoa.failOnMissingRuntimeLibrary=true',"-Djmoa.additionalClasspathJars=$runtimeJar",'-Djmoa.expandDependencies=true','-Djmoa.expandIncludes=spring','-Djmoa.frameworkFiltering=true','-Djmoa.allowSpringAotFrameworkSites=true','-Djmoa.allowExpandedDependencySites=true','-Djmoa.tier2AdapterConsolidation=PACKAGE_SAM','-Djmoa.hybridOverlayCoordinates=org.springframework:spring-core:7.0.2','-Djmoa.maxExpandedClasses=100000','-Djmoa.packageOptimizedDependencies=true','-Djmoa.reportOnly=false')
    Invoke-Step -Step 'Apply fresh JMOA full-P2 transformation' -Executable $mvnw -Arguments $pluginArguments -WorkingDirectory $serviceRoot -Environment $buildEnvironment | Out-Null
    Invoke-Step -Step 'Package transformed customers-service' -Executable $mvnw -Arguments @('-q','jar:jar','org.springframework.boot:spring-boot-maven-plugin:4.0.1:repackage','-DskipTests') -WorkingDirectory $serviceRoot -Environment $buildEnvironment | Out-Null
    $optimizedLibs = Join-Path $serviceRoot 'target\jmoa-optimized-libs'
    $reducedLibs = Join-Path $serviceRoot 'target\jmoa-reduced-libs'
    Invoke-Step -Step 'Apply fresh raw LVT/LVTT reducer' -Executable $mvnw -Arguments @('-q',"${pluginCoordinates}:reduce-bytecode",'-Djmoa.reducer.enabled=true','-Djmoa.reducer.reportOnly=false','-Djmoa.reducer.optimize=true','-Djmoa.reducer.profile=release-low-footprint','-Djmoa.reducer.engine=raw','-Djmoa.reducer.stripLocalVariableTable=true','-Djmoa.reducer.stripLocalVariableTypeTable=true',"-Djmoa.reducer.inputDir=$optimizedLibs","-Djmoa.reducer.outputDir=$reducedLibs") -WorkingDirectory $serviceRoot -Environment $buildEnvironment | Out-Null
    $builtV2 = Get-ChildItem (Join-Path $serviceRoot 'target') -Filter '*.jar' -File | Where-Object Name -NotMatch 'sources|javadoc|original' | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $v2Jar = Join-Path $artifactRoot 'petclinic-customers-v2.jar'
    Copy-Item -LiteralPath $builtV2.FullName -Destination $v2Jar -Force
    Add-ScenarioAsset -Role 'Fresh V2 application artifact before reduced-layer materialization' -Path $v2Jar -Provenance GENERATED_IN_SCENARIO -Note 'Generated from the same checkout after baseline measurement.' | Out-Null
    Invoke-Step -Step 'Materialize V2 exploded Boot application and reduced dependency layer' -Executable (Get-Command pwsh).Source -Arguments @('-NoProfile','-File',(Join-Path $repositoryRoot 'examples\spring-petclinic-customers-nocds\scripts\04-materialize-exploded-boot.ps1'),'-CustomersServiceDir',$serviceRoot,'-ReducedLibDir',$reducedLibs,'-RuntimeJar',$runtimeJar,'-OutputDir',$v2Exploded,'-Java',$java17) -WorkingDirectory $repositoryRoot | Out-Null
    Add-ScenarioAsset -Role 'V2 materialization manifest' -Path (Join-Path $v2Exploded 'jmoa-materialization-manifest.json') -Provenance GENERATED_IN_SCENARIO -Note 'Records every replaced dependency and runtime-library hash.' | Out-Null
    Write-ExplodedDockerfile -Root $v2Exploded
    Invoke-Step -Step 'Build V2 runtime image' -Executable 'podman' -Arguments @('build','-t',$v2Image,$v2Exploded) | Out-Null
    Invoke-Step -Step 'Inspect V2 runtime image' -Executable 'podman' -Arguments @('image','inspect',$v2Image) | Out-Null
    Add-ImageProvenance -Role 'transformed V2 application image' -Reference $v2Image | Out-Null

    $runtimeResult.v2 = Measure-Variant -Variant 'V2' -Image $v2Image
    $runtimeResult.delta = [ordered]@{
        pssKb = $runtimeResult.v2.pssKb - $runtimeResult.baseline.pssKb
        privateDirtyKb = $runtimeResult.v2.privateDirtyKb - $runtimeResult.baseline.privateDirtyKb
        memoryCurrentBytes = $runtimeResult.v2.memoryCurrentBytes - $runtimeResult.baseline.memoryCurrentBytes
    }
    # Emit the artifact-lineage.json the campaign gate requires (review Issue #4): source revision,
    # artifact SHAs, transform/reducer/materialization report SHAs, and preservation failures. It is
    # derived from this run's own ledger + reports so provenance is never reconstructed from the image.
    $lineagePath = Join-Path $runRoot 'artifact-lineage.json'
    Invoke-Step -Step 'Build artifact lineage document' -Executable (Get-Command pwsh).Source -Arguments @('-NoProfile','-File',(Join-Path $PSScriptRoot 'build-artifact-lineage.ps1'),'-RunRoot',$runRoot,'-SourceRevision',$Revision,'-OutputPath',$lineagePath) | Out-Null
    $lineageAsset = Add-ScenarioAsset -Role 'Artifact lineage' -Path $lineagePath -Provenance GENERATED_IN_SCENARIO -Note 'B0/V2 build lineage consumed by the campaign artifact-lineage gate.'
    $runtimeResult.artifactLineage = [ordered]@{ path = $lineagePath; sha256 = $lineageAsset.sha256 }
    Complete-ScenarioLedger -Status 'COMPLETE_SINGLE_SCREEN' -Result $runtimeResult | Out-Null
    $completed = $true
    $runtimeResult | ConvertTo-Json -Depth 8
} catch {
    Add-ScenarioNote -Title 'Scenario Failure' -Text $_.Exception.ToString()
    Complete-ScenarioLedger -Status 'FAILED' -Result @{error=$_.Exception.Message} | Out-Null
    throw
} finally {
    if (-not $completed) { Write-Warning "Scenario failed. Ledger preserved under $runRoot" }
}
