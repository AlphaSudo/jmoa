# 09 Run Balanced Blocks

First run one independent qualification observation for B0, V1, and V2. Qualification establishes identity, semantics, captures, and capacity; it is not a performance claim.

Then run exactly six blocks:

```text
1: B0,V1,V2
2: B0,V2,V1
3: V1,B0,V2
4: V1,V2,B0
5: V2,B0,V1
6: V2,V1,B0
```

Each artifact appears twice in each execution position. There are 18 independent final observations.

```powershell
./scripts/run-jmoa-evaluation.ps1 `
  -Service PetClinicCustomers `
  -Stages B0,V1,V2,Final,Explain `
  -ConfigPath <private-config.json> `
  -OutputDirectory <new-private-campaign-directory>
```

Valid losses remain in the dataset. Only objectively invalid observations may be replaced, with at most two preserved retries per slot.
