# JMOA B0/V1/V2 Evaluation Guide

This guide measures three artifacts directly:

- `B0`: the same service built without JMOA.
- `V1`: the accepted JMOA transform without the V2 reducer.
- `V2`: V1 plus the accepted V2 reducer.

The headline result is always a direct B0-to-V2 measurement. Historical medians are context, never arithmetic inputs.

Start with [environment setup](01-environment.md), then follow the numbered files. The executable entry point is:

```powershell
./scripts/run-jmoa-evaluation.ps1 `
  -Service PetClinicCustomers `
  -Stages B0,V1,V2,Final,Explain `
  -ConfigPath <private-campaign-config.json> `
  -OutputDirectory <private-output-directory>
```

Raw command responses, runtime captures, credentials, local paths, and private service configuration stay outside Git. Only sanitized summaries belong in `docs/product-evidence`.

The completed reference campaign and its claim boundary are published in the
[three-service direct matrix](../product-evidence/b0-v1-v2-three-service-matrix.md).
It contains one complete product win out of three services; the guide therefore
teaches evaluation and evidence handling, not a universal memory-win promise.
