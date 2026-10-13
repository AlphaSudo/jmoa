# JMOA 2.15: scoped Patient RAM benchmark

Published 2026-10-13. This is a **provisional benchmark evidence release**, with no new executable Maven artifacts. The existing stable JMOA 2.1 release and accepted PetClinic deployment remain separate records.

## Result and qualification

In the exact tested Java 26/Leyden Patient configuration, 12 paired blocks showed **8.38 MiB lower median stopped-process PSS after the fixed workload**. The one-sided lower saving bound is **8.19 MiB under the declared model**. **The original null calibration failed and used a user-approved post-observation exception. Performance bounds remain unqualified or failed.** These qualifications belong beside the RAM number wherever this result is quoted.

| Endpoint | Result |
| --- | ---: |
| Median process-PSS saving | 8,586 KiB / 8.384765625 MiB |
| Conditional lower saving bound | 8,384 KiB / 8.1875 MiB |
| Fresh confirmation | 24 units / 12 paired blocks; all valid |
| Pairs strictly exceeding 8 MiB | 11/12 |
| Formal RAM floor | 8,192 KiB / 8 MiB |
| Original null maximum difference | 540 KiB; original limit 512 KiB, failed |
| Original null absolute median bias | 176 KiB; original limit 128 KiB, failed |
| Qualified performance bounds | No |

This is a direct B0-versus-composed comparison. Historical 5.71 MiB and incremental 2.41 MiB medians were never added to establish it. All 96 campaign attempts, including failures, remain accounted for; prior incomplete confirmation epochs were excluded in full.

## All fresh pairs

Positive savings mean the candidate used less PSS. File charges are reported separately in the JSON and never added to process PSS.

| Pair | Order | B0 PSS KiB | Candidate PSS KiB | Saving KiB |
| --- | --- | ---: | ---: | ---: |
| 1 | BA | 334808 | 326360 | 8448 |
| 2 | BA | 334672 | 326288 | 8384 |
| 3 | BA | 334460 | 326064 | 8396 |
| 4 | AB | 335252 | 325544 | 9708 |
| 5 | BA | 334348 | 326252 | 8096 |
| 6 | AB | 334708 | 325784 | 8924 |
| 7 | BA | 334944 | 325840 | 9104 |
| 8 | AB | 334344 | 325916 | 8428 |
| 9 | AB | 334660 | 326288 | 8372 |
| 10 | BA | 334548 | 325720 | 8828 |
| 11 | AB | 334836 | 325860 | 8976 |
| 12 | AB | 334508 | 325784 | 8724 |

The lower bound uses the third-smallest saving among 12 pairs. Its nominal one-sided coverage is 98.0713% under independent, exchangeable, stable pairs. Those assumptions are explicit and unproved. The one-sided sign result for exceeding 8 MiB is p=0.003173828125, conditional on that model. Observed baseline drift was -86 KiB, AB/BA median difference -402 KiB, and first/last difference -354 KiB, within the frozen 1,024 KiB drift bounds.

The eight B0 controls originally failed precision admission. Their observed maximum difference was 540 KiB and median bias -176 KiB. The human approved that exact observed block before the untouched fresh confirmation; this is an exception, not a passed calibration. Subtracting 176 KiB from the lower bound gives 8,208 KiB, while subtracting 540 KiB gives 7,844 KiB. These are sensitivity illustrations, **not established bias corrections or confidence bounds**.

## Costs: all 82 metrics

The five failed confirmation metrics were startup, early read-user, early actuator-prometheus, early stroke-low, and repeated delete. Null cost calibration also failed, and confirmation cost stationarity/assessability did not qualify. A row marked Passed below describes its numerical margin test only; it does not override the failed overall performance qualification.

For each paired metric, acceptance required at least ten of twelve margins strictly favorable and its tenth ordered margin no greater than zero. Repeated routes used max(20% relative allowance, 2 ms), early routes max(25%, 5 ms), and startup/lifecycle CPU 20%. Early singleton observations are not latency-tail estimates. Old-rule violations and complete paired margins remain in the JSON.

| Metric | Median ratio | Median delta ms | Upper paired margin ms | Favorable pairs | Margin test |
| --- | ---: | ---: | ---: | ---: | --- |
| startupNs | 1.0512 | 276.4238 | 483.4002 | 8/12 | Failed |
| lifecycleCpuNs | 0.9479 | -1353.8730 | -2370.5470 | 11/12 | Passed |
| early:create | 1.0357 | 0.6094 | -2.3291 | 12/12 | Passed |
| early:read-user | 1.1062 | 1.8498 | 0.5976 | 9/12 | Failed |
| early:details | 1.0284 | 0.3093 | -2.3660 | 12/12 | Passed |
| early:history | 1.0330 | 0.3860 | -3.8547 | 12/12 | Passed |
| early:list | 1.0883 | 1.6902 | -0.4495 | 10/12 | Passed |
| early:status | 1.0983 | 1.1842 | -2.3253 | 12/12 | Passed |
| early:doctor | 1.0224 | 0.2183 | -2.4715 | 12/12 | Passed |
| early:blood-group | 1.0012 | -0.0164 | -3.2442 | 12/12 | Passed |
| early:update | 0.9944 | -0.0955 | -3.6484 | 11/12 | Passed |
| early:read-id | 1.1279 | 1.5422 | -2.2647 | 12/12 | Passed |
| early:delete | 1.0537 | 1.0356 | -0.3620 | 10/12 | Passed |
| early:missing-user | 1.0688 | 0.6406 | -3.2503 | 12/12 | Passed |
| early:duplicate-create | 1.0624 | 0.4528 | -3.5116 | 12/12 | Passed |
| early:patient-self | 1.0563 | 0.5222 | -2.0963 | 11/12 | Passed |
| early:patient-update | 0.9537 | -0.5482 | -2.8329 | 12/12 | Passed |
| early:mobile-conflict | 1.0094 | 0.0622 | -4.2503 | 12/12 | Passed |
| early:cross-user-denied | 0.9709 | -0.2892 | -3.5774 | 12/12 | Passed |
| early:no-auth | 0.8801 | -0.5813 | -4.6369 | 12/12 | Passed |
| early:invalid-auth | 0.9956 | -0.0169 | -4.7532 | 12/12 | Passed |
| early:staff-list-denied | 0.9475 | -0.2022 | -4.7050 | 12/12 | Passed |
| early:doctor-list | 1.0474 | 0.4610 | -3.2013 | 12/12 | Passed |
| early:staff-status | 1.0421 | 0.2846 | -3.8045 | 12/12 | Passed |
| early:doctor-own-filter | 1.0108 | 0.0723 | -4.5636 | 12/12 | Passed |
| early:doctor-cross-filter-denied | 1.0264 | 0.0792 | -4.6279 | 12/12 | Passed |
| early:staff-history | 0.9220 | -0.4694 | -4.7312 | 12/12 | Passed |
| early:staff-blood-group | 0.9637 | -0.1628 | -4.7621 | 12/12 | Passed |
| early:missing-id | 0.9930 | -0.0304 | -4.5038 | 12/12 | Passed |
| early:missing-update | 1.0401 | 0.1968 | -4.2810 | 12/12 | Passed |
| early:malformed-create | 1.0789 | 0.6916 | -1.9028 | 12/12 | Passed |
| early:empty-status | 0.9808 | -0.0756 | -4.4720 | 12/12 | Passed |
| early:actuator-info-disabled | 1.0055 | 0.0189 | -4.0290 | 12/12 | Passed |
| early:actuator-prometheus | 1.1912 | 3.7223 | 0.3365 | 9/12 | Failed |
| early:stroke-health | 0.9754 | -0.1853 | -3.0598 | 11/12 | Passed |
| early:stroke-low | 1.1849 | 6.3523 | 5.0512 | 7/12 | Failed |
| early:stroke-high | 0.9732 | -0.3084 | -2.9038 | 11/12 | Passed |
| early:stroke-staff | 0.9049 | -0.9537 | -3.0725 | 12/12 | Passed |
| early:stroke-upstream-error | 0.9376 | -0.9553 | -4.8613 | 11/12 | Passed |
| early:stroke-empty-upstream | 0.9546 | -0.4971 | -2.7804 | 11/12 | Passed |
| early:stroke-malformed | 0.8807 | -0.5805 | -4.4818 | 11/12 | Passed |
| early:missing-supplement | 1.0665 | 0.3140 | -3.6972 | 12/12 | Passed |
| repeated:create | 0.9970 | -0.0328 | -1.4839 | 12/12 | Passed |
| repeated:read-user | 0.9499 | -0.4384 | -1.6868 | 12/12 | Passed |
| repeated:details | 0.9588 | -0.2745 | -1.9939 | 12/12 | Passed |
| repeated:history | 0.9398 | -0.4281 | -1.9808 | 12/12 | Passed |
| repeated:list | 0.9839 | -0.1810 | -1.9672 | 12/12 | Passed |
| repeated:status | 0.9639 | -0.2575 | -1.4164 | 12/12 | Passed |
| repeated:doctor | 0.9768 | -0.1602 | -2.0123 | 12/12 | Passed |
| repeated:blood-group | 0.9826 | -0.0792 | -1.9606 | 12/12 | Passed |
| repeated:update | 0.9792 | -0.2530 | -1.7217 | 12/12 | Passed |
| repeated:read-id | 0.9719 | -0.1824 | -2.0595 | 12/12 | Passed |
| repeated:delete | 1.0615 | 0.8945 | 2.3480 | 8/12 | Failed |
| repeated:missing-user | 1.0326 | 0.1398 | -1.7372 | 12/12 | Passed |
| repeated:duplicate-create | 1.0228 | 0.1397 | -1.1808 | 12/12 | Passed |
| repeated:patient-self | 0.9906 | -0.0801 | -1.2208 | 12/12 | Passed |
| repeated:patient-update | 0.9971 | -0.0361 | -2.0858 | 12/12 | Passed |
| repeated:mobile-conflict | 1.0191 | 0.1237 | -1.6279 | 12/12 | Passed |
| repeated:cross-user-denied | 0.9976 | -0.0088 | -1.6177 | 12/12 | Passed |
| repeated:no-auth | 1.0113 | 0.0292 | -1.7026 | 12/12 | Passed |
| repeated:invalid-auth | 0.9738 | -0.0729 | -1.8318 | 12/12 | Passed |
| repeated:staff-list-denied | 1.0106 | 0.0358 | -1.6104 | 12/12 | Passed |
| repeated:doctor-list | 1.0300 | 0.3214 | -1.0927 | 12/12 | Passed |
| repeated:staff-status | 1.0914 | 0.5463 | -1.2262 | 12/12 | Passed |
| repeated:doctor-own-filter | 1.0415 | 0.2801 | -1.3929 | 12/12 | Passed |
| repeated:doctor-cross-filter-denied | 1.0689 | 0.2590 | -1.5970 | 12/12 | Passed |
| repeated:staff-history | 1.0055 | 0.0343 | -1.0750 | 12/12 | Passed |
| repeated:staff-blood-group | 1.0861 | 0.3580 | -1.2284 | 12/12 | Passed |
| repeated:missing-id | 1.0516 | 0.2425 | -1.2994 | 12/12 | Passed |
| repeated:missing-update | 1.0181 | 0.1001 | -1.5409 | 12/12 | Passed |
| repeated:malformed-create | 1.0368 | 0.1711 | -1.0236 | 12/12 | Passed |
| repeated:empty-status | 1.0471 | 0.1908 | -1.5689 | 12/12 | Passed |
| repeated:actuator-info-disabled | 1.0293 | 0.1221 | -1.6471 | 12/12 | Passed |
| repeated:actuator-prometheus | 1.1149 | 0.7607 | -0.5532 | 11/12 | Passed |
| repeated:stroke-health | 1.0173 | 0.0579 | -1.8609 | 12/12 | Passed |
| repeated:stroke-low | 0.9819 | -0.2022 | -1.4860 | 11/12 | Passed |
| repeated:stroke-high | 0.9547 | -0.6827 | -0.5600 | 10/12 | Passed |
| repeated:stroke-staff | 0.9610 | -0.4565 | -2.2294 | 12/12 | Passed |
| repeated:stroke-upstream-error | 0.9614 | -0.5872 | -1.2857 | 11/12 | Passed |
| repeated:stroke-empty-upstream | 1.0051 | 0.0679 | -0.7141 | 10/12 | Passed |
| repeated:stroke-malformed | 0.9597 | -0.2353 | -1.5336 | 12/12 | Passed |
| repeated:missing-supplement | 1.0393 | 0.1823 | -1.4640 | 12/12 | Passed |

## Exact tested configuration

- Temurin 26.0.2.1+1, artifact-specific Leyden AOT caches, five pinned JRE component hashes in the JSON.
- Patient cgroup 576 MiB, zero swap; Xms32m/Xmx256m, Serial GC, compact headers, Xss256k, 48 MiB code cache, two compiler threads, NMT summary.
- **Both arms** used TrimNativeHeapInterval=5000 and MALLOC_ARENA_MAX=1.
- Full 788 rewritten sites: 455 Tier 1 and 333 Tier 2; 498 owners, 192 logical/195 physical helpers.
- Fixed 632-request primary workload before stopped-process PSS measurement. Cost workloads followed the RAM claim; 50 warmup and 100 measured cycles, 44 requests per cycle.
- Ambient cache on a constrained Windows/WSL host; startup and primary-workload warmth unqualified. Actual paging, pressure and Windows interference were retained. File-charge change was bounded at 16 MiB; no absolute 16 MiB file-residency guarantee.
- Bounded post-cost settling was part of the protocol and did not retake RAM measurements. Support services and original infrastructure were restored and independently checked.
- Finite 384 MiB synthetic stroke fixture, no live ML; ConfigClient absent. Dependency line-number observability reduced.

| Identity | SHA-256 |
| --- | --- |
| B0 application | `AB51B5D2015A76922CF8DC4BB4B49A541D7FC673A029D65BB7FF2A95391EF7FA` |
| Composed application | `4A240E3091282F5D6DA34D42AFFFA7B64AE60EC29A97FDB09BE496FAB7177C5A` |
| B0 own AOT cache | `D8DA947E4C4F28C3EBE056AF40A1984B1746AFD6D286789B1ABD387956C79ABA` |
| Composed own AOT cache | `925274D15ED32DC765FD4E8793CE75A514DCAF4F64D870148BC5F5C447CF460F` |
| Shared tested core | `C6CB1CEB1B1A17EED1D08E63C5A637B6723AA8A6FEDBFBB28D5B5C9A64AD0E6E` |

These hashes identify the tested material; this release does not distribute the private Patient application or caches. The relationship between the tested core and the previously uploaded JMOA 2.1 binaries remains unresolved. The 2.15 tag names this evidence publication; Maven artifact coordinates are unchanged.

## Evidence and publication boundary

Saved raw evidence was independently reanalysed: all 24 readiness proofs and 76 successful historical/fresh units passed raw checks. This is an independent reader/reanalysis, **not external replication or a new 76-run experiment**. Public aggregates do not replace private raw requests, sealed source snapshots, host logs or controlled independent reproduction.

This report supports a scoped observed RAM reduction. Cold-start capacity, whole-stack RAM, live-ML equivalence, production parity, released-binary parity and performance non-regression are not established. The completed Plan 1.33 checklist records execution and dispositions; it does not mean every scientific or performance gate passed.

Read the [machine-readable record](patient-plan133-scoped-ram-result.json), [release notes](../releases/jmoa-2.15.md), and [existing PetClinic result](petclinic-r41f-b0-t7r23-result.md). Raw private evidence remains outside the public repository. Plan 1.34 will address the unresolved qualifications prospectively; no future result is promised by this release.
