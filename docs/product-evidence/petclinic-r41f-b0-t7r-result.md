# PetClinic direct R41F versus clean B0 result

## Decision

- Direct RAM claimable: **False**
- Product promotion authorized: **False**
- Terminal decision: `T7R_DIRECT_MEMORY_NOT_CLAIMABLE`
- Accepted deployment after this result: **V2E**

## Exact comparison

| Arm | Meaning | Artifact SHA-256 | Image ID |
|---|---|---|---|
| B0 | strict no-JMOA PetClinic baseline | `2F4A63A803B2A05050ACF020AD382ABE589C52EE50253A1F5DC85F6D62584ACF` | `EA33EE1387AC4E4DC6C16DF8606EF5342499809892EE0A6C9291891B35BA6268` |
| R41F | complete JMOA candidate | `415217CE9926ECA2BC0052800DD456291D438A9C63589CBFE5A55238BE71EB9F` | `7CC8F1766B9AC45983DFD634846060A6C62209259CCB40596972A3168A5CABEB` |

Both arms used symmetric Spring Boot fat-JAR images. The result belongs to the
complete R41F deployment and does not isolate metadata removal from packaging.

## Primary RAM result

- Favorable PSS blocks: **8/12**
- Median R41F-minus-B0 PSS: **-644.5 KiB**
- Exact two-sided sign-test: **0.3876953125**
- Bootstrap 95% interval: **[-1336.5, 231] KiB**
- Conservative debited median PSS: **-644.5 KiB**
- Median `memory.current`: **-2840576 bytes**
- Median Private Dirty: **-518 KiB**
- Median cgroup anon/file: **-528384 / -2027520 bytes**

## Important component numbers

| Metric | Median R41F−B0 |
|---|---:|
| Java-heap mapping PSS | -570 KiB |
| Native process `[heap]` PSS | 2912 KiB |
| Combined Java/native heap PSS | 2298 KiB |
| Anonymous RW PSS | -4088 KiB |
| NMT total committed | -2791.5 KiB |
| Metaspace used / committed | -3169 / -3174.5 KiB |
| Compressed class space used / committed | -93 / -103 KiB |
| Loaded classes | -156 |

## Tradeoffs

- Lifecycle CPU median: **1.08724181494658%**, **551050.5 usec**
- Startup median: **0.797088296647472%**
- Latency median/p95: **0% / -5.25265957446809%**

Failed direct-RAM predicates: **PSS_FAVORABLE_BLOCKS, PSS_EXACT_SIGN_P, PSS_MEDIAN_MATERIAL, PSS_BOOTSTRAP_UPPER_BELOW_ZERO, PSS_ONE_SIDED_UPPER_BELOW_ZERO, DEBITED_PSS_FAVORABLE_BLOCKS, DEBITED_PSS_EXACT_SIGN_P, DEBITED_PSS_MEDIAN_MATERIAL, DEBITED_PSS_BOOTSTRAP_UPPER_BELOW_ZERO, MEMORY_CURRENT_MEDIAN_MATERIAL, PRIVATE_DIRTY_BOOTSTRAP_UPPER_BELOW_ZERO, CGROUP_ANON_BOOTSTRAP_UPPER_BELOW_ZERO, COMBINED_JAVA_NATIVE_HEAP, ANONYMOUS_RW**.

Failed promotion predicates: **DIRECT_RAM_CLAIMABLE**.

No R4.87 or historical B0-versus-V2E observations were reused, and no
cross-campaign medians were added. Scope is PetClinic customers-service only.