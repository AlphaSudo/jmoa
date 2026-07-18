# Reducer Configuration

`reduce-bytecode` is disabled and report-only by default.

## Safe Raw Mutation Profile

```powershell
mvn com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.reportOnly=false `
  -Djmoa.reducer.optimize=true `
  -Djmoa.reducer.profile=release-low-footprint `
  -Djmoa.reducer.engine=raw `
  -Djmoa.reducer.inputDir=<dependency-jars> `
  -Djmoa.reducer.outputDir=<reduced-jars> `
  -Djmoa.reducer.stripLocalVariableTable=true `
  -Djmoa.reducer.stripLocalVariableTypeTable=true
```

## Selection

`jmoa.reducer.artifactIncludes` and `jmoa.reducer.artifactExcludes` accept the
artifact selection patterns used by `ArtifactSelectionPolicy`. Application
classes are excluded unless `jmoa.reducer.includeApplicationClasses=true` and
an application input directory is supplied. Application mutation has no shipped
runtime claim and should remain off.

Generated-family reduction is `report-only`. It is not a mutation switch.

## Preserved And Blocked Attributes

The shipped reducer targets LVT/LVTT only. Attempts to strip line numbers,
source files, stack maps, annotations, signatures, or BootstrapMethods are
rejected by `ReducerSafetyPolicy`. The raw preservation auditor verifies
non-target structures after each class transformation.

## JAR Handling

Signed, multi-release, and sealed JARs are skipped. `module-info.class` is
preserved. Input/output SHA-256, class counts, removed bytes, skip reasons, and
preserved attributes are written to the reducer report and manifest.

An exception writes a failure report and leaves no promotable partial result.
Reduced JARs must still pass materialization, runtime-origin, semantic, V2-C,
and V2-D gates before a runtime claim.
