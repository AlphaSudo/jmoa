# Bytecode Size Profiler

JMOA V2-B adds report-only classfile and method-size profiling.

It is disabled by default:

```text
-Djmoa.size.enabled=false
-Djmoa.size.reportOnly=true
-Djmoa.size.optimize=false
```

To enable reports:

```powershell
mvn process-classes `
  -Djmoa.size.enabled=true `
  -Djmoa.size.reportOnly=true
```

Optional scanner inputs:

```text
-Djmoa.size.scanClasspathJars=true
-Djmoa.size.jarPaths=<semicolon-or-newline-separated-jars>
```

Outputs under `target/`:

```text
classfile-size-profile.json
classfile-size-profile.md
classfile-size-profile.csv
classfile-size-family-breakdown.json
method-code-size-report.json
method-code-size-report.md
near-64kb-methods.json
constant-pool-bloat-report.json
attribute-size-report.json
bytecode-roi-v2-report.json
```

When runtime evidence is supplied, V2-B also emits:

```text
bytecode-runtime-correlation.json
bytecode-runtime-correlation.md
bytecode-runtime-correlation-top-loaded.json
bytecode-runtime-correlation-near64k.json
```

Runtime evidence inputs:

```text
-Djmoa.size.classLoadLog=/path/to/classload.log
-Djmoa.size.classHistogram=/path/to/GC.class_histogram.txt
```

If those are not set, V2-B reuses the V2-A synthetic runtime inputs when they
exist.

## What Is Measured

Per class:

- classfile bytes,
- constant-pool count,
- field and method count,
- interface and attribute counts,
- total method bytecode bytes,
- largest method `Code` length,
- bootstrap method count,
- invokedynamic count,
- synthetic and bridge method count,
- debug, annotation, StackMapTable, LineNumberTable, LocalVariableTable, and
  SourceFile attribute bytes,
- V2-A generated-family classification.

Per method:

- method name and descriptor,
- `Code` length,
- threshold bucket,
- synthetic/bridge/static-initializer flags,
- instruction, invoke, branch, switch, invokedynamic, and ldc counts.

## Safety Boundary

V2-B does not mutate bytecode. Any optimization or stripping flag fails fast in
this release. The profiler is intended to answer which classes and methods are
large before JMOA admits any reducer. Runtime correlation reports are evidence
for prioritization only; they do not prove PSS, Private_Dirty, or startup
causality without paired runtime experiments.
