# V2-O Runtime Automation Guide

V2-O makes the runtime evidence workflow repeatable without deciding a policy
or interpreting a memory result on its own.

## Workflow

```text
recommend-runtime -> runtime-preflight -> train/prove -> semantic smoke
-> paired screen capture -> V2-C -> V2-D -> human claim review
```

Use only one runtime policy for both sides of a performance comparison. A CDS
candidate must use a fresh archive trained for the exact candidate artifact.
For `NO_CDS_LOW_DIRTY`, pass `-MallocArenaMax 1`; the screen helper rejects an
empty or different value. No-CDS runs also reject enabled CDS, AppCDS, Leyden,
or a runtime javaagent.

## Launch Script Contract

`runtime-screen-pair.ps1` invokes each user-supplied launch script as:

```powershell
& <launch-script> -RunDirectory <dir> -ContainerName <name> -Variant <BASELINE|CANDIDATE>
```

The launch script must start a fresh container with that name and return only
after the container has been launched. It must not enable class-load logging or
JFR for a clean memory pair.

## Workload Script Contract

The screen helper invokes the workload script as:

```powershell
& <workload-script> -OutputPath <run-dir>/workload-result.json `
  -BaseUrl <health-url-without-trailing-slash> -ContainerName <name> `
  -Variant <BASELINE|CANDIDATE>
```

The workload script must write JSON with `health`, `errors`, and `requests`.
Use `health: "UP"` and `errors: 0` only after the intended workload completes.

## Required Capture Layout

For pair one, the screen helper emits:

```text
<capture-root>/
  b1/
    run-manifest.json
    smaps_rollup.txt
    smaps.txt
    memory.current
    nmt-summary.txt
    heap-info.txt
    class-histogram.txt
    workload-result.json
  c1/
    ...same files...
```

This is V2-C-native evidence. Confirmation uses three or more clean pairs.

## Claim Boundary

A green preflight, smoke, or single screen only admits the next gate. It does
not establish an artifact, startup, or runtime-memory claim.
