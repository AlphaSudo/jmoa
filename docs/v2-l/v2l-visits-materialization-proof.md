# V2-L Visits Materialization Proof

Status:

```text
PASSED
```

Both variants were materialized as exploded Spring Boot applications and
launched with `JarLauncher`. The application and loader layers were identical;
the candidate dependency layer contained the 161 outputs from the reducer
manifest.

Layer proof:

```text
baseline dependency JARs: 161
candidate dependency JARs: 161
reducer-manifest outputs: 161
missing candidate JARs: 0
candidate hash mismatches: 0
original JAR shadowing: false
```

Digests and runtime images:

```text
baseline dependency-layer manifest SHA-256:
  75BDF1F90B852E05FAF43D88A73855700E01BFEA8CEB403043284630E7D05375

candidate dependency-layer manifest SHA-256:
  32E934BABA3B92BF7A264E709679A415DCAFF53AD82824B07FEB55E3102AE43E

baseline image ID:
  4f48fece2bc63790418500e5a13f9c07e4c85de049f5512ec511ab9f6aee8a6a

candidate image ID:
  db16e304947b2efcf6001f2c79e74e88efdce48f556086ea6de98229ffb9abe3
```

Every candidate run used the candidate layer digest as both its observed and
expected artifact hash. Runtime verification recorded dynamic origins as
verified, optimized origins as verified, and original shadowing as absent.

Class-load logging was deliberately disabled during memory pairs. V2-L uses the
complete image-layer hash manifest as its non-perturbing runtime materialization
proof.
