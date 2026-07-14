param(
    [string]$ExplodedDir = "./petclinic-work/exploded-customers-v2",
    [string]$OutputDir = "./petclinic-work/semantic-smoke",
    [string]$Image = "localhost/jmoa-v2-customers-quickstart:latest",
    [int]$Port = 8081
)

$ErrorActionPreference = "Stop"
$prefix = "jmoa-v2-quickstart"
$network = "$prefix-net"
$config = "$prefix-config"
$discovery = "$prefix-discovery"
$customers = "$prefix-customers"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

function Cleanup {
    foreach ($name in @($customers, $discovery, $config)) { & podman rm -f $name *> $null }
    & podman network rm $network *> $null
}
function Wait-Up([string]$Uri, [int]$Seconds = 300, [string]$Container = "") {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if ($Container) {
            $state = (& podman inspect $Container --format '{{.State.Status}}' 2>$null | Out-String).Trim()
            if ($state -eq "exited" -or $state -eq "dead") {
                $exitCode = (& podman inspect $Container --format '{{.State.ExitCode}}' 2>$null | Out-String).Trim()
                throw "Container $Container exited with code $exitCode before becoming ready."
            }
        }
        try { $r = Invoke-RestMethod -Uri $Uri -TimeoutSec 5; if ($r.status -eq "UP" -or $null -eq $r.status) { return } } catch {}
        Start-Sleep -Seconds 3
    }
    throw "Service did not become ready: $Uri"
}

$context = Join-Path $OutputDir "image"
if (Test-Path $context) { Remove-Item $context -Recurse -Force }
New-Item -ItemType Directory -Force -Path $context | Out-Null
foreach ($layer in @("dependencies", "spring-boot-loader", "snapshot-dependencies", "application")) {
    $source = Join-Path $ExplodedDir $layer
    if (Test-Path $source) { Copy-Item $source (Join-Path $context $layer) -Recurse -Force } else { New-Item -ItemType Directory -Force (Join-Path $context $layer) | Out-Null }
}
@'
FROM eclipse-temurin:17
WORKDIR /application
COPY dependencies/ ./
COPY spring-boot-loader/ ./
COPY snapshot-dependencies/ ./
COPY application/ ./
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
'@ | Set-Content (Join-Path $context "Dockerfile") -Encoding ASCII

Cleanup
$passed = $false
$failure = $null
try {
    & podman build -q -t $Image $context
    if ($LASTEXITCODE -ne 0) { throw "Customers image build failed." }
    & podman network create $network | Out-Null
    & podman run -d --name $config --network $network --network-alias config-server -p 8888:8888 springcommunity/spring-petclinic-config-server | Out-Null
    Wait-Up "http://localhost:8888/actuator/health" 300 $config
    & podman run -d --name $discovery --network $network --network-alias discovery-server -p 8761:8761 springcommunity/spring-petclinic-discovery-server | Out-Null
    Wait-Up "http://localhost:8761/actuator/health" 300 $discovery
    & podman run -d --name $customers --network $network --network-alias customers-service -p "${Port}:8081" `
        -e SPRING_PROFILES_ACTIVE=docker -e CONFIG_SERVER_URL=http://config-server:8888 `
        -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/ `
        -e MALLOC_ARENA_MAX=1 -e JAVA_TOOL_OPTIONS=-Xshare:off $Image | Out-Null
    Wait-Up "http://localhost:$Port/actuator/health" 600 $customers
    $checks = @("/actuator/health", "/owners", "/petTypes")
    $errors = 0
    foreach ($path in $checks) { try { Invoke-WebRequest "http://localhost:$Port$path" -TimeoutSec 15 | Out-Null } catch { $errors++ } }
    $logs = & podman logs $customers 2>&1 | Out-String
    $linkage = @("VerifyError", "ClassFormatError", "NoSuchMethodError", "NoClassDefFoundError", "ExceptionInInitializerError") | Where-Object { $logs -match $_ }
    $result = [ordered]@{ metadataVersion = "v2-public-semantic-smoke-v1"; health = "UP"; requests = $checks.Count; errors = $errors; linkageErrors = @($linkage); passed = ($errors -eq 0 -and @($linkage).Count -eq 0) }
    $result | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $OutputDir "semantic-smoke.json") -Encoding UTF8
    if (-not $result.passed) { throw "Semantic smoke failed." }
    $passed = $true
} catch {
    $failure = $_
    $containerLogs = [ordered]@{}
    foreach ($name in @($config, $discovery, $customers)) {
        $containerLogs[$name] = (& podman logs --tail 400 $name 2>&1 | Out-String)
    }
    $failureReport = [ordered]@{
        metadataVersion = "v2-public-semantic-smoke-failure-v1"
        passed = $false
        error = $_.Exception.Message
        containers = $containerLogs
    }
    $failureReport | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $OutputDir "semantic-smoke-failure.json") -Encoding UTF8
} finally { Cleanup }
if (-not $passed) { throw $failure }
Write-Host "Public semantic smoke passed."
