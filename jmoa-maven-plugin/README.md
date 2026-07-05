# JMOA Maven Plugin

The Maven plugin is the build-time optimizer entry point.

Current responsibilities:

- scan class roots and expanded dependency jars for lambda sites
- apply profile and framework admission rules
- generate Tier 1 runtime plans and Tier 2 package/SAM adapters
- rewrite eligible `invokedynamic` call sites
- package optimized dependency jars
- validate adapter references
- write measurement and class-load evidence summaries
- write V2-A generated/synthetic/proxy/AOT inventory reports when explicitly
  enabled
- write V2-B bytecode/classfile size reports when explicitly enabled
- write V2-C evidence validation reports for already-captured measurements
- write V2-D memory attribution reports for V2-C-valid evidence

The plugin is intentionally not a runtime javaagent. Optimized artifacts are
produced at build time and then verified in the target deployment shape.

## Important Goals

- `jmoa:optimize`
- `jmoa:coverage-report`
- `jmoa:check-coverage`
- `jmoa:measure-impact`
- `jmoa:evidence`
- `jmoa:attribution`
- `jmoa:reduce-bytecode`

## V2-A Generated-Class Flags

Generated-class inventory is disabled by default.

```text
jmoa.synthetic.enabled=false
jmoa.synthetic.inventoryOnly=true
jmoa.synthetic.optimizeFamily=none
jmoa.synthetic.failOnUnsafe=true
```

When enabled, the plugin writes inventory, safety taxonomy, prototype selection,
and ROI V2 feature reports under `target/`. Runtime attribution reports are also
written when `jmoa.synthetic.classLoadLog` or `jmoa.synthetic.classHistogram`
points to existing evidence files.

Generated-class bytecode mutation remains disabled. Optimization requests still
fail fast unless the mode is inventory/report-only.

## V2-B Bytecode Size Flags

Bytecode-size profiling is disabled by default.

```text
jmoa.size.enabled=false
jmoa.size.reportOnly=true
jmoa.size.optimize=false
jmoa.size.failOnNear64k=false
jmoa.size.warnMethodBytes=32768
jmoa.size.dangerMethodBytes=49152
jmoa.size.failMethodBytes=65535
```

When enabled, the plugin writes classfile, method, constant-pool, attribute, and
bytecode ROI reports under `target/`. Mutation and strip flags fail fast in this
release.

## V2-C Evidence Flags

Evidence analysis is disabled by default and operates on already-captured run
folders.

```text
jmoa.evidence.enabled=false
jmoa.evidence.mode=analyze
jmoa.evidence.inputDir=<evidence-dir>
jmoa.evidence.outputDir=<output-dir>
jmoa.evidence.expectedPolicy=UNKNOWN
```

The evidence goal validates runs, analyzes paired confirmations, detects
diagnostic perturbation, and emits JSON/Markdown reports. It does not start
containers or change optimizer behavior.

## V2-D Attribution Flags

Memory attribution is disabled by default and requires V2-C-valid evidence by
default.

```text
jmoa.attribution.enabled=false
jmoa.attribution.mode=analyze
jmoa.attribution.inputDir=<v2-c-evidence-dir>
jmoa.attribution.outputDir=<output-dir>
jmoa.attribution.requireV2CValid=true
jmoa.attribution.generatedClassReport=<optional-v2a-report>
jmoa.attribution.bytecodeRuntimeCorrelationReport=<optional-v2b-report>
```

The attribution goal explains memory movement across smaps, NMT, heap/object
histograms, class/metaspace signals, optional V2-A generated-family context, and
optional V2-B bytecode runtime correlation. It is report-only and does not make
V2-A or V2-B mutation safe by itself.

## V2-E Reducer Flags

The bytecode reducer is disabled by default and report-only by default.

```text
jmoa.reducer.enabled=false
jmoa.reducer.reportOnly=true
jmoa.reducer.optimize=false
jmoa.reducer.profile=none
jmoa.reducer.inputDir=<optimized-lib-dir>
jmoa.reducer.outputDir=${project.build.directory}/jmoa-reduced-libs
jmoa.reducer.stripLocalVariableTable=false
jmoa.reducer.stripLocalVariableTypeTable=false
```

Unsafe strip flags fail fast:

```text
jmoa.reducer.stripLineNumberTable=false
jmoa.reducer.stripSourceFile=false
jmoa.reducer.stripStackMapTable=false
jmoa.reducer.stripAnnotations=false
jmoa.reducer.stripSignature=false
jmoa.reducer.stripBootstrapMethods=false
```

Mutation is allowed only when `reportOnly=false`, `optimize=true`,
`profile=release-low-footprint`, and both local-variable strip flags are true.
V2-E preserves line numbers, stack-map frames, annotations, signatures, and
BootstrapMethods. Classes that carry `BootstrapMethods` are skipped in mutation
mode instead of being rewritten.

V2-F hardens this reducer for product use. Signed, multi-release, and sealed
JARs are skipped by default; `module-info.class` is preserved; and the reducer
emits `jmoa-reducer-manifest.json` with input/output hashes and timestamp
policy.

See the repository-level docs for deployment materialization and measurement
boundaries.
