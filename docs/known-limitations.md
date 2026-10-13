# Known limitations

## Stable distribution

JMOA **2.1.0** is the stable plugin/runtime release on GitHub Releases, using
`com.yourorg.jmoa` coordinates. It is not on Maven Central. The plugin targets
Java 22; the runtime support library targets Java 17. CI uses Java 26 and tests
the public modules, documentation and publication safety; it does not reproduce
container memory campaigns.

The public PetClinic quickstart needs explicit frozen profiling/admission/SAM
inputs. It demonstrates build, reduction, materialization and semantic smoke;
it is not a zero-input reproduction of the 81-session accepted RAM campaign.

The accepted 14.88 MiB PSS / 16.24 MiB cgroup PetClinic result is packaging-inclusive
and tied to exact R41F/B0 deployments. Lifecycle CPU increased 14.71% and startup
increased 1.652 s within the frozen gates. It is not a universal or free saving.

## Provisional Patient evidence

JMOA **2.15** publishes no new Maven binaries. The Java 26/Leyden Patient result
is 8.38 MiB lower median stopped-process PSS with a conditional 8.19 MiB lower
saving bound. Original calibration failed and used an approved post-observation
exception. Performance bounds remain unqualified or failed. Startup/primary
warmth, cold-start capacity, whole-stack RAM, production parity and uploaded
stable-binary parity are not established.

The workload uses a finite synthetic stroke fixture, not live ML equivalence;
ConfigClient is absent and dependency line-number observability is reduced.
Private service source, raw requests, runtime configuration and captures are
excluded. Published aggregates support inspection, not independent reproduction.

## Mutation boundary

The stable raw reducer is opt-in LVT/LVTT only and preserves line numbers,
verification frames, annotations, signatures and other non-target structures.
Signed, sealed and multi-release JARs are skipped. Generated/proxy/AOT mutation,
method splitting and broad constant-pool surgery are not shipped capabilities.
Patient's experimental metadata treatment does not broaden that stable policy.

See [current release boundaries](releases/current-state.md),
[compatibility](reference/compatibility-matrix.md) and
[the Patient benchmark](product-evidence/patient-scoped-ram-benchmark.md).
