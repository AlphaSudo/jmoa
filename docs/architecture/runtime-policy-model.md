# Runtime policy model

Runtime policy is part of the artifact contract. The current publications have
different service and policy scopes; see [release boundaries](../releases/current-state.md).

| Policy/context | Required identity and live proof | Evidence scope |
| --- | --- | --- |
| Stable 2.1 PetClinic no-CDS | `-Xshare:off`; no JSA or AOT cache; exact Java 17/SerialGC tuple; declared allocator/trim policy and artifact hashes | Accepted R41F fat JAR versus B0 exploded Boot; packaging-inclusive |
| Provisional 2.15 Patient Leyden | Exact Java 26 runtime and shared core; separate SHA-bound cache per application; cache-use and application identity proof | Scoped stopped-PSS result; failed original calibration admitted by exception; performance bounds unqualified or failed |
| Historical stock JDK base CDS | Same stock archive bytes/path/runtime in both arms; no application archive | Earlier Patient V1/V2 studies |
| Historical application CDS | Fresh per-artifact archive; registered artifact/archive pair and mapping proof | Earlier Doctor studies |
| Diagnostic | Declared JFR, class-load tracing, NMT detail, GC or other perturbations | Explanation only unless a protocol explicitly qualifies overhead |

## Common runtime settings

Patient used `MALLOC_ARENA_MAX=1` and native trimming every 5,000 ms in both
arms. The policy must be frozen for both arms; a common setting is not a
treatment contribution. Neither sharing nor native trimming is enabled
implicitly by the JMOA optimizer.

## Admission and compatibility

Train application archives/caches for the exact transformed bytes and JDK.
Prove actual runtime use, rather than infer it from a filename. Stock base CDS,
dynamic application CDS and Leyden AOT caches are distinct experimental states.
A historical rejection of one Patient AppCDS layout does not reject the later
Leyden study, nor does that study qualify every archive or runtime configuration.

The retained [runtime registry](../reference/runtime-protocol-registry.json)
supports historical tooling replay. Its older confirmed entries are not new
2.15 admissions. Recommendations require an exact service/deployment/policy
match; the provisional Patient result is not registered as a fully accepted
performance configuration.
