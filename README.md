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
- `docs`: architecture, runtime-origin verification, materialization, and
  measurement methodology

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

## Safety

Before publishing or tagging a release, run:

```powershell
./scripts/check-publication-safety.ps1
```

This checks for common local paths, secret-like strings, committed binaries,
generated artifacts, and private service markers.

## License

See [LICENSE-PENDING.md](LICENSE-PENDING.md). No open-source license has been
selected yet.
