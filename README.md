# JMOA

Build-time JVM/Spring Boot memory optimization toolkit.

JMOA rewrites selected Java lambda and adapter call sites at build time,
materializes the optimized output into the runtime deployment shape, verifies
that optimized classes are actually loaded, and measures JVM/container memory
with PSS-oriented evidence.

The case-study portfolio is published separately:

- Portfolio: https://github.com/AlphaSudo/jmoa-jvm-optimization-portfolio

## What This Is

JMOA is a source release for the tooling behind the published portfolio:

- `jmoa-maven-plugin`: build-time scanner, admission, rewrite, packaging, and
  measurement support
- `jmoa-runtime-lib`: runtime adapter support used by rewritten artifacts
- `tools/mode-c-launcher`: deterministic classpath launch helper, kept outside
  the default CI reactor until its process-exit integration tests are split out
- `examples/spring-petclinic-customers-nocds`: public PetClinic reproduction
  workflow scaffold
- `docs`: architecture, runtime-origin verification, materialization,
  generated-class inventory, bytecode-size profiling, and measurement
  methodology

## What This Is Not

- Not a runtime javaagent for the final optimized service
- Not a universal memory-win guarantee
- Not a replacement for measuring PSS, Private_Dirty, and cgroup memory
- Not tied to one Spring Boot deployment shape
- Not a publication of private HMS, patient-service, or doctor-service source

## Confirmed Case Studies

The source here supports the evidence portfolio, which currently summarizes:

| Case | Runtime shape | CDS mode | Confirmed result |
| --- | --- | --- | --- |
| Patient-service | expanded classpath | CDS/AppCDS-style | ~4.2-4.4 MB median memory reduction |
| Doctor-service | corrected Spring Boot fat JAR | CDS | ~2.7 MB median PSS reduction |
| Spring PetClinic customers-service | exploded Boot / JarLauncher | no CDS | ~4.6 MB median PSS reduction |

The PetClinic result is the public reproducibility bridge. It is scoped to the
project's exploded Boot launch shape and the `NO_CDS_LOW_DIRTY` runtime policy.

## Build

Prerequisites:

- JDK 22 for the Maven plugin module
- JDK 17+ for the runtime library
- Maven 3.9+

From the repository root:

```powershell
./scripts/build-all.ps1
```

or:

```bash
./scripts/build-all.sh
```

The default Maven reactor builds the plugin and runtime library. It does not run
Podman, full PetClinic integration measurements, or the launcher process-exit
integration tests by default.

## PetClinic Reproduction

See [examples/spring-petclinic-customers-nocds](examples/spring-petclinic-customers-nocds/README.md).

The example is intentionally explicit about the claim boundary:

- no CDS
- no AppCDS
- no Leyden
- no runtime javaagent
- `MALLOC_ARENA_MAX=1`
- exploded Boot / `JarLauncher`
- dynamic runtime-origin verification

## V2-A Generated Class Optimizer

V2-A starts the generated/synthetic/proxy/AOT class expansion behind an explicit
feature flag. The current implementation inventories generated class shapes,
can attribute runtime generated-class cost from class-load logs and class
histograms, writes a safety taxonomy, selects the first prototype family, and
feeds generated-class features into a V2 ROI report.

Generated-class bytecode mutation remains disabled.

```powershell
mvn process-classes `
  -Djmoa.synthetic.enabled=true `
  -Djmoa.synthetic.inventoryOnly=true `
  -Djmoa.synthetic.optimizeFamily=none
```

See:

- [Generated Class Optimizer](docs/generated-class-optimizer.md)
- [Synthetic Class Safety Model](docs/synthetic-class-safety-model.md)
- [Spring AOT Generated-Class Optimization](docs/spring-aot-generated-class-optimization.md)
- [Proxy Optimization Non-Goals](docs/proxy-optimization-non-goals.md)

## V2-B Bytecode Size Profiler

V2-B adds report-only classfile and method-size profiling. It reports large
classes, large methods, near-64KB method risk, constant-pool footprint,
attribute footprint, and bytecode-size ROI features.

Generated-class labels from V2-A are included where available. Bytecode-size
mutation and debug stripping remain disabled.

```powershell
mvn process-classes `
  -Djmoa.size.enabled=true `
  -Djmoa.size.reportOnly=true
```

See:

- [Bytecode Size Profiler](docs/bytecode-size-profiler.md)
- [Method 64KB Risk](docs/method-64kb-risk.md)
- [Classfile Footprint ROI](docs/classfile-footprint-roi.md)
- [Debug Attribute Stripping](docs/debug-attribute-stripping.md)
- [Large Generated Methods](docs/large-generated-methods.md)

## V2-C Evidence Engine

V2-C adds a report-only measurement stability engine. It parses already-captured
run folders, validates evidence, detects perturbing diagnostics, analyzes
baseline/candidate pairs, classifies variance patterns, and writes confirmation
reports. It does not run containers and does not change optimizer behavior.

```powershell
mvn jmoa:evidence `
  -Djmoa.evidence.enabled=true `
  -Djmoa.evidence.inputDir=<evidence-dir> `
  -Djmoa.evidence.expectedPolicy=NO_CDS_LOW_DIRTY
```

Historical replay mode can enforce audited outcomes when local archived evidence
folders are available:

```powershell
mvn jmoa:evidence `
  -Djmoa.evidence.enabled=true `
  -Djmoa.evidence.mode=replay `
  -Djmoa.evidence.inputDir=<historical-evidence-root> `
  -Djmoa.evidence.replaySuite=docs/v2-c/historical-replay-suite.example.json
```

See:

- [Evidence Engine](docs/evidence-engine.md)
- [V2-C Evidence Schema](docs/v2-c/jmoa-evidence-schema.md)

## V2-D Memory Attribution Engine

V2-D adds a report-only memory attribution layer. It consumes V2-C-valid
evidence and explains why memory moved: heap page touch, retained objects,
anonymous mappings, class/metaspace movement, generated-family context, and
bytecode/runtime correlation. It does not mutate bytecode and does not claim a
new optimizer win by itself.

```powershell
mvn jmoa:attribution `
  -Djmoa.attribution.enabled=true `
  -Djmoa.attribution.inputDir=<v2-c-evidence-dir> `
  -Djmoa.evidence.expectedPolicy=NO_CDS_LOW_DIRTY
```

Optional V2-A and V2-B reports can be supplied to enrich generated-family and
bytecode/runtime attribution:

```powershell
mvn jmoa:attribution `
  -Djmoa.attribution.enabled=true `
  -Djmoa.attribution.inputDir=<v2-c-evidence-dir> `
  -Djmoa.attribution.generatedClassReport=<generated-class-inventory.json> `
  -Djmoa.attribution.bytecodeRuntimeCorrelationReport=<bytecode-runtime-correlation.json>
```

See:

- [V2-D Scope](docs/v2-d/phase-v2d0-scope.md)
- [Memory Category Model](docs/v2-d/memory-category-model.md)
- [Historical Attribution Replay](docs/v2-d/v2d-historical-attribution-replay.md)
- [V2-D Closure Report](docs/v2-d/v2d-closure-report.md)

## V2-E Debug Metadata Reducer

V2-E adds the first controlled post-v1 reducer prototype: an opt-in
release-low-footprint reducer for `LocalVariableTable` and
`LocalVariableTypeTable` in dependency jars. It is disabled by default and
report-only by default.

It does not strip line numbers, source files, stack-map frames, annotations,
signatures, BootstrapMethods, or framework-sensitive metadata. In mutation mode,
classes that carry `BootstrapMethods` are skipped rather than rewritten.

```powershell
mvn jmoa:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.inputDir=<optimized-lib-dir>
```

Mutation requires the explicit release-low-footprint gate:

```powershell
mvn jmoa:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.reportOnly=false `
  -Djmoa.reducer.optimize=true `
  -Djmoa.reducer.profile=release-low-footprint `
  -Djmoa.reducer.stripLocalVariableTable=true `
  -Djmoa.reducer.stripLocalVariableTypeTable=true `
  -Djmoa.reducer.inputDir=<optimized-lib-dir>
```

See:

- [V2-E Debug Metadata Reducer](docs/v2-e/v2e-debug-metadata-reducer.md)
- [V2-E Reducer Safety Taxonomy](docs/v2-e/v2e-reducer-safety-taxonomy.md)
- [V2-E Failure Handling](docs/v2-e/v2e-failure-handling.md)
- [V2-E Implementation Status](docs/v2-e/v2e-implementation-status.md)
- [V2-E PetClinic Artifact Smoke](docs/v2-e/v2e-petclinic-artifact-smoke.md)
- [V2-E PetClinic Service Smoke](docs/v2-e/v2e-petclinic-service-smoke.md)

## Safety

Before publishing or tagging a release, run:

```powershell
./scripts/check-publication-safety.ps1
```

This checks for common local paths, secret-like strings, committed binaries,
generated artifacts, and private service markers.

## License

JMOA is released under the [Apache License 2.0](LICENSE).
