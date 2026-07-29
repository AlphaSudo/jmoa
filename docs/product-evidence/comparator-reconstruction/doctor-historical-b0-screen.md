# Doctor Historical B0 Screen

- Decision: **AUTHORIZE_NON_CLAIM_DIRECTIONAL_PAIR**
- Exact historical B0 artifact: **True**
- Workload: **80 requests, 0 errors**
- Effective runtime policy: **explicit base CDS**
- Capture: **20 seconds after workload**
- Current PSS: **300139 KB**
- Historical median PSS: **336484 KB**
- Current anonymous PSS: **290716 KB**
- Historical median anonymous PSS: **290140 KB**
- Current Private_Dirty: **290716 KB**
- Historical median Private_Dirty: **290148 KB**
- PSS difference after accounting for file mappings: **808 KB**
- memory.current historically comparable: **False**

Anonymous/private-dirty memory is inside the recovered historical B0 distribution. The lower total
PSS is explained by a clean/file-page accounting shift, not by a comparable reduction in anonymous
memory. memory.current is outside the historical range and remains environment-sensitive.

This is a qualified authorization for exactly one **non-claim** B0/V1 directional pair. It is not an
exact historical-runtime claim, and it does not authorize the six-order campaign.
