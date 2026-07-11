# V2-O Automation Compatibility Replay

V2-O is replayed at the contract level because public documentation cannot
re-run private containers or commit raw evidence. The helper inputs and output
names were checked against the audited runtime shapes below.

| Scenario | Policy | Required helpers | Compatibility | Claim |
| --- | --- | --- | --- | --- |
| PetClinic customers V2-I | `NO_CDS_LOW_DIRTY` | preflight, materialization proof, smoke, pair capture, V2-C/V2-D wrapper | compatible | no new claim |
| Doctor D2/D2R V2-K | `CDS` | preflight, fresh training record, mapped-archive proof, smoke, pair capture, wrapper | compatible only with variant-specific archives | no new claim |
| PetClinic visits V2-L | `NO_CDS_LOW_DIRTY` | preflight, materialization proof, smoke, pair capture, wrapper | compatible | no new claim |

The replay confirms that the scripts enforce the earlier lessons: no CDS reuse
across variants, no CDS/no-CDS mixed comparison, and no screen before smoke.
