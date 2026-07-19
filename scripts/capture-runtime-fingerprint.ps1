param(
    [string]$OutputDirectory = 'target/runtime-equivalence/fingerprint',
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$ContainerCli = 'podman',
    [string[]]$ContainerName = @(),
    [string[]]$SupportContainerName = @(),
    [string]$WorkloadScript = '',
    [string]$EffectivePom = '',
    [string]$DependencyTree = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime-automation-common.ps1')
New-JmoaDirectory -Path $OutputDirectory

function Run([string]$Executable, [string[]]$Arguments) {
    $result = Invoke-JmoaExternal -Executable $Executable -Arguments $Arguments
    [ordered]@{ exitCode = $result.exitCode; output = $result.output }
}
function Inspect-Container([string]$Name) {
    $inspect = Run $ContainerCli @('inspect', $Name)
    if ($inspect.exitCode -ne 0) { return [ordered]@{ name=$Name; available=$false; error=$inspect.output } }
    $data = $inspect.output | ConvertFrom-Json
    $hostPid = [string]$data.State.Pid
    $pid = ''
    $runtime = [ordered]@{}
    if ($hostPid -and $hostPid -ne '0') {
        $jcmdList = Run $ContainerCli @('exec',$Name,'sh','-lc','jcmd -l 2>/dev/null || true')
        $runtime.jcmdList = $jcmdList.output
        $javaLine = @($jcmdList.output -split "`r?`n" | Where-Object { $_ -match '^\s*\d+\s+' -and $_ -notmatch '\sJdkJcmd\b' } | Select-Object -First 1)
        if ($javaLine.Count) { $pid = ([regex]::Match($javaLine[0], '^\s*(\d+)')).Groups[1].Value }
        if (-not $pid) {
            $pid = ((Run $ContainerCli @('exec',$Name,'sh','-lc',"ps -eo pid,comm,args | awk '/[j]ava/{print `$1; exit}'")).output).Trim()
        }
    }
    if ($pid) {
        foreach ($capture in @(
            @{name='javaVersion'; command='java -version 2>&1'},
            @{name='javaHome'; command="java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.home =/{print `$2; exit}'"},
            @{name='defaultCdsArchive'; command='h=$(java -XshowSettings:properties -version 2>&1 | grep ''java.home ='' | head -1 | cut -d= -f2- | xargs); f=$(find "$h" -type f -name classes.jsa 2>/dev/null | head -1); if [ -n "$f" ]; then printf ''%s|'' "$f"; sha256sum "$f" | cut -d'' '' -f1; fi'},
            @{name='procCmdline'; command="tr '\0' ' ' < /proc/$pid/cmdline"},
            @{name='procEnviron'; command="tr '\0' '\n' < /proc/$pid/environ | grep -E '^(JAVA|JDK|MALLOC|SPRING|JMOA)'"},
            @{name='vmVersion'; command="jcmd $pid VM.version"},
            @{name='vmCommandLine'; command="jcmd $pid VM.command_line"},
            @{name='vmFlags'; command="jcmd $pid VM.flags"},
            @{name='heapInfo'; command="jcmd $pid GC.heap_info"},
            @{name='metaspace'; command="jcmd $pid VM.metaspace"},
            @{name='classloaders'; command="jcmd $pid VM.classloader_stats"},
            @{name='nmt'; command="jcmd $pid VM.native_memory summary"},
            @{name='maps'; command="cat /proc/$pid/maps"}
        )) { $runtime[$capture.name] = (Run $ContainerCli @('exec',$Name,'sh','-lc',$capture.command)).output }
    }
    [ordered]@{
        name = $Name; available = $true; id = $data.Id; imageId = $data.Image; imageName = $data.Config.Image
        command = @($data.Config.Cmd); entrypoint = @($data.Config.Entrypoint); environment = @($data.Config.Env | Where-Object { $_ -notmatch '(?i)(token|password|secret|credential|key)=' })
        labels = $data.Config.Labels; state = $data.State.Status; hostPid = $hostPid; containerJvmPid = $pid; runtime = $runtime
    }
}

$hostInfo = Get-ComputerInfo -Property WindowsProductName,WindowsVersion,OsBuildNumber,CsProcessors,CsNumberOfLogicalProcessors,CsTotalPhysicalMemory,HyperVisorPresent
$fingerprint = [ordered]@{
    schemaVersion = 'jmoa-runtime-fingerprint-v1'
    capturedUtc = [DateTime]::UtcNow.ToString('o')
    host = [ordered]@{
        os = $hostInfo.WindowsProductName; version=$hostInfo.WindowsVersion; build=$hostInfo.OsBuildNumber
        processors=@($hostInfo.CsProcessors | ForEach-Object Name); logicalProcessors=$hostInfo.CsNumberOfLogicalProcessors
        totalPhysicalMemory=$hostInfo.CsTotalPhysicalMemory; hypervisor=$hostInfo.HyperVisorPresent
        podman=(Run $ContainerCli @('version','--format','json')); podmanInfo=(Run $ContainerCli @('info','--format','json'))
    }
    buildToolchain = [ordered]@{
        repositoryRoot=(Resolve-Path $RepositoryRoot).Path; gitRevision=(Run 'git' @('-C',$RepositoryRoot,'rev-parse','HEAD')).output
        gitStatus=(Run 'git' @('-C',$RepositoryRoot,'status','--short')).output; javaHome=$env:JAVA_HOME
        java=(Run 'java' @('-version')); javac=(Run 'javac' @('-version')); maven=(Run 'mvn' @('-version'))
        effectivePom=if ($EffectivePom) { Get-JmoaSha256 $EffectivePom } else { '' }
        dependencyTree=if ($DependencyTree) { Get-JmoaSha256 $DependencyTree } else { '' }
    }
    runtimeContainers=@($ContainerName | ForEach-Object { Inspect-Container $_ })
    supportContainers=@($SupportContainerName | ForEach-Object { Inspect-Container $_ })
    workload=[ordered]@{ path=$WorkloadScript; sha256=if ($WorkloadScript) { Get-JmoaSha256 $WorkloadScript } else { '' } }
}
$normalized = [ordered]@{
    host=$fingerprint.host; buildToolchain=$fingerprint.buildToolchain
    runtimeContainers=@($fingerprint.runtimeContainers | ForEach-Object { [ordered]@{ imageId=$_.imageId; imageName=$_.imageName; command=$_.command; entrypoint=$_.entrypoint; environment=$_.environment; runtime=$_.runtime } })
    supportContainers=@($fingerprint.supportContainers | ForEach-Object { [ordered]@{ imageId=$_.imageId; imageName=$_.imageName; command=$_.command; entrypoint=$_.entrypoint; environment=$_.environment } })
    workload=$fingerprint.workload
}
$fingerprint.runtimeFingerprintSha256 = Get-JmoaTextSha256 (($normalized | ConvertTo-Json -Depth 20 -Compress))
Write-JmoaJson $fingerprint (Join-Path $OutputDirectory 'runtime-fingerprint.json')
Write-JmoaJson $normalized (Join-Path $OutputDirectory 'runtime-fingerprint.normalized.json')
$fingerprint | ConvertTo-Json -Depth 20
