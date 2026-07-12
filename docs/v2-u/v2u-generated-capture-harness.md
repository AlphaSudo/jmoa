# V2-U Generated Lifecycle Capture Harness

V2-U adds:

```text
scripts/capture-generated-lifecycle.ps1
```

The helper captures startup, warmup, and workload diagnostic folders for a
single already-launched or launchable JVM. It records a
`generated-lifecycle-manifest.json` with the V2-U identity tuple and stage paths.

## Required Inputs

```text
Service
ArtifactPath
OutputDir
Pid or PidFile
LaunchMode
RuntimePolicy
ReducerEngine
FamilyRegistryVersion
ScannerVersion
```

Optional inputs:

```text
LaunchCommand
WarmupCommand
WorkloadCommand
ClassLoadLogPath
CgroupMemoryCurrentPath
ImageId
ContainerId
```

## Captured Per Stage

```text
class-load.log
class-histogram.txt
nmt-summary.txt
classloader-stats.txt
vm-command-line.txt
vm-flags.txt
vm-metaspace.txt
memory.current
timestamp.txt
```

The manifest also reserves this path per stage:

```text
generated-class-runtime-attribution.json
```

That file is produced by the existing generated-class runtime attribution step
from the raw class-load log and histogram. `jmoa:analyze-generated-evidence`
uses the manifest paths only when those attribution files exist.

## Boundary

This helper intentionally records diagnostic evidence. It is not a V2-C memory
pair capture and it should not be mixed into claimable memory measurements.
