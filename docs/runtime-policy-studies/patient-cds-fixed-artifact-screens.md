# Patient CDS Fixed-Artifact Screens

Both screens hold the artifact fixed and change only runtime policy from `NO_CDS_LOW_DIRTY` to `CDS`. All four runs completed 600 requests with zero workload errors and passed live runtime-policy proof.

| Artifact | PSS KB | Private_Dirty KB | memory.current bytes | Heap PSS KB | Archive mapped PSS KB | Verdict |
|---|---:|---:|---:|---:|---:|---|
| Patient V1 final | +20,797 | -2,228 | +120,639,488 | -88,184 | 113,880 | regression |
| Patient V2 final | +40,888 | +17,848 | +138,641,408 | -81,840 | 111,340 | regression |

NMT explains the main trade: CDS reduces ordinary Class and Metaspace commitment, but adds `126–129 MB` of Shared class space. The mapped archive PSS and remaining runtime costs exceed the heap/private-metadata reductions.

These are diagnostic single pairs, not confirmed policy claims. The V1 regression activates the predeclared stop condition: do not tune V2 CDS toward the historical number.

