# 08 Capture Memory

Capture claim metrics before perturbing diagnostics.

## Required process and cgroup files

```text
/proc/<pid>/smaps_rollup
/proc/<pid>/smaps
memory.current
memory.stat
memory.events
memory.swap.current
memory.pressure
```

## Required JVM diagnostics

```text
VM.command_line
VM.flags
VM.native_memory summary
GC.heap_info
GC.class_histogram
VM.metaspace
VM.classloader_stats
```

Also record image/container identity, runtime JDK fingerprint, startup time, and logs. Class-load logging, JFR, forced GC, and NMT detail are diagnostic and must not run before the official memory capture.

Each scenario writes one chronological `scenario-command-ledger.md` containing every external command, HTTP response body, exit/status, stdout, and stderr.
