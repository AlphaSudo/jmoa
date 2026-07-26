# PETCLINIC_TARGET_ONLY_V1 Result

## Verdict

The authoritative run ended with:

```text
TARGET_B0_RUNTIME_VARIANCE
```

The 2 GiB Debian VM passed the target capacity qualification. Both B0
same-artifact control pairs then completed with healthy workloads and complete
target captures, but their median drift exceeded every frozen reproducibility
threshold. Under the preregistered stop rule, the V2 controls and B0/V2 product
pairs were not run.

This is a host/runtime reproducibility result, not a JMOA performance result.
The campaign produced no direct B0-versus-V2 comparison and therefore neither
confirms nor rejects a JMOA memory benefit.

## Execution Identity

- Protocol: `PETCLINIC_TARGET_ONLY_V1`
- Run ID: `petclinic-target-only-20260726T035855Z`
- Immutable runner revision:
  `153ec9c42182dff7248775a34ed5f8a418e09fc3`
- Baseline artifact SHA-256:
  `88D4BC9F000A22041F8FD521A0442B85F9FC49966948FA11F642CBBABE0D6AE7`
- Campaign package SHA-256:
  `941C2975316E872B2135706A4AB304FC4E930CFED4F448701ED45E2739117E40`
- Immutable source archive SHA-256:
  `22C23ADD433827D6B482039EA038DCDC5BC8172E2394641E566F3837E55DC966`

The retrieved private evidence archive is not committed. Its SHA-256 is
`E6E27881BCF4E0D8554C70155BE77A691F37A43BDCFACE6BDCA5424A7EDA2AEA`
and its size is 4,796,162 bytes.

## Capacity Qualification

The excluded B0 capacity arm passed:

| Check | Result |
| --- | ---: |
| Requests | 81 |
| Workload errors | 0 |
| Health | `UP` |
| Mutations proven | Yes |
| Pre-arm available memory | 1,192,226,816 bytes |
| Post-arm available memory | 718,135,296 bytes |
| Swap used | 0 bytes |
| OOM / OOM-kill events | 0 / 0 |
| PSI some/full `avg10` | 0 / 0 |
| Complete target captures | Yes |

The capacity arm was diagnostic and excluded from all medians.

## B0 Same-Artifact Controls

Both arms in both pairs used the same frozen baseline artifact. Each arm
completed the corrected 81-request workload with zero semantic errors.

| Pair | Order | `abs(delta PSS)` | `abs(delta Private_Dirty)` | `abs(delta memory.current)` |
| --- | --- | ---: | ---: | ---: |
| B0 pair 1 | A to B | 5,535 KB | 5,612 KB | 5,763,072 bytes |
| B0 pair 2 | B to A | 10,169 KB | 10,200 KB | 10,932,224 bytes |
| Median | reversed controls | 7,852 KB | 7,906 KB | 8,347,648 bytes |
| Frozen maximum | | 1,024 KB | 1,024 KB | 2,097,152 bytes |

The signed PSS delta was positive in both execution orders, indicating a
systematic second-run shift. The report field is named
`systematicSecondRunAdvantage`; this result is treated only as evidence of
order-sensitive runtime variance, not as an advantage for either artifact.

## Stop Rule

The frozen protocol required B0 same-artifact reproducibility before any V2
evidence could be admitted. B0 failed that gate, so these stages were not run:

- V2 same-artifact controls;
- B0-to-V2 product pair 1;
- V2-to-B0 product pair 2;
- B0-to-V2 product pair 3;
- V2-C product confirmation and V2-D attribution.

No thresholds, pair counts, workload settings, runtime policy, or artifact
definitions were changed after target evidence began.

## Command Ledgers

Every executed scenario arm has a Markdown command ledger containing the exact
commands and captured responses, plus an integrity summary.

| Arm | Commands | Markdown ledger SHA-256 |
| --- | ---: | --- |
| Capacity B0 | 150 | `95067C2A182BB797AACC5341B6CBCF5BA4FE2711EEBCFCDF5C0B78A56839E497` |
| B0 pair 1, first arm | 149 | `C7F6DEC878A61E9008C51E87D183EC3BA1A612230B70D2A101C03E35EC237005` |
| B0 pair 1, second arm | 151 | `99F700CF377FAA9A4EE905B5A2B37157C398AA573DF60868EAE49C374307EFCD` |
| B0 pair 2, first arm | 164 | `09842F639BD3A12984464CB44274FDE40AD5362E59E2BBF2ADBB563F4B04B742` |
| B0 pair 2, second arm | 150 | `646A6F788E0719F572A06CC7D80A3A25F7C9D4B5DE11A67A53EA27762744F7F7` |

The root terminal summary contains eight audited closure commands with zero
hard failures. Its Markdown ledger SHA-256 is
`B7B89DC3A40463E3AD0A8F2929701D1E2E02993AF70B5B4C4E76A8DFFD61832B`.

## Closure Note

The original run completed the capacity and B0 evidence correctly, but the root
summary writer rejected the structured terminal result after the stop rule
fired. Commit `4cad69c` generalized that ledger result type. The root summary
was then regenerated from the existing immutable reports and ledgers; no arm
command, response, capture, metric, or verdict was changed.

## Claim Boundary

Allowed:

```text
The 2 GiB target had enough capacity to execute the PetClinic target workload,
but same-artifact B0 controls were not reproducible under the frozen protocol.
The campaign therefore stopped before comparing JMOA V2 with baseline.
```

Not allowed:

```text
JMOA V2 failed on this VM.
JMOA V2 regressed memory.
The VM lacked enough memory to run the target.
The earlier PetClinic runtime wins were disproved.
```
