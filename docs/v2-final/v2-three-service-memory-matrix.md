# V2 Three-Service Memory Matrix

Overall status: **READY_FOR_V2_FINAL**

Comparison: `final V1 -> final V2`

The matrix uses a confirmed runtime policy per service. The policy is held
constant between V1 and V2 within each service; different services may use
different confirmed policies.

| Service | Runtime policy | Status | Valid runs | Paired wins | Median PSS KB | Median Private_Dirty KB | Median memory.current bytes | V2-C | V2-D |
|---|---|---|---:|---:|---:|---:|---:|---|---|
| petclinic-customers | NO_CDS_LOW_DIRTY | PASS | 6/6 | 2/3 | -6012 | -5708 | -8081408 | CONFIRMED_WIN | True |
| doctor | CDS | PASS | 6/6 | 3/3 | -5156 | -5212 | -6975488 | CONFIRMED_WIN | True |
| patient | NO_CDS_LOW_DIRTY | PASS | 6/6 | 2/3 | -8903 | -8636 | -9707520 | CONFIRMED_WIN | True |

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

Patient's corrected CDS policy remains blocked in the separate
`patient-cds-final-verdict` record. The final no-CDS confirmation is the
admitted Patient policy and is recorded in `patient-final-policy-verdict`.

The aggregate status is not a claim that CDS, fat JARs, or any one allocator
policy is universally beneficial. It is a policy-selected three-service gate.
