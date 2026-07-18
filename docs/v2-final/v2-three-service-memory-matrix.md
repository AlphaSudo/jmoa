# V2 Three-Service Memory Matrix

Overall status: **READY_FOR_V2_FINAL**

Comparison: `final V1 -> final V2`

The matrix uses a confirmed runtime policy per service. The policy is held
constant between V1 and V2 within each service; different services may use
different confirmed policies.

| Service | Runtime policy | Status | Valid runs | Paired wins | Median PSS KB | Median Private_Dirty KB | Median memory.current bytes | V2-C | V2-D |
|---|---|---|---:|---:|---:|---:|---:|---|---|
| petclinic-customers | NO_CDS_LOW_DIRTY | PASS | 6/6 | 2/3 | -6012 | -5708 | -8081408 | CONFIRMED_WIN | True |
| doctor | APPLICATION_CDS | PASS | 6/6 | 3/3 | -5156 | -5212 | -6975488 | CONFIRMED_WIN | True |
| patient | JDK_BASE_CDS_LOW_DIRTY | PASS | 6/6 | 3/3 | -8279 | -8444 | -8523776 | CONFIRMED_WIN | True |

## Gate

- 6/6 valid runs per service
- at least 2/3 paired wins
- median PSS <= -4096 KB
- median Private_Dirty <= -1024 KB
- median memory.current <= -1048576 bytes
- zero workload and semantic errors
- V2-C `CONFIRMED_WIN` and V2-D attribution

Raw private evidence is intentionally excluded from this repository. All three
rows now pass under their service-specific confirmed policies.

Patient's stock JDK base-CDS result is the admitted matrix policy. The separate
`NO_CDS_LOW_DIRTY` result is also independently confirmed. Dynamic Patient
application CDS remains blocked in the AppCDS policy records.

The aggregate status is not a claim that CDS, fat JARs, or any one allocator
policy is universally beneficial. It is a policy-selected three-service gate.
