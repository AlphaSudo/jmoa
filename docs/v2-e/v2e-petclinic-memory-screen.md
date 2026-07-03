# V2-E PetClinic Memory Screen

Status: passed as a single-screen promotion gate.

This screen compares the already-confirmed PetClinic full P2 artifact against
the same artifact with the V2-E LocalVariableTable / LocalVariableTypeTable
reducer applied to dependency jars.

## Runtime Setup

```text
service: Spring PetClinic customers-service
comparison: full P2 vs full P2 + V2-E reducer
launch mode: EXPLODED_BOOT_APP
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
CDS/AppCDS: off
runtime javaagent: absent
workload: 27 endpoints x 3 rounds
```

## Artifact Delta

```text
full P2 dependency jars: 92,466,274 bytes
full P2 + reducer dependency jars: 87,070,377 bytes
delta: -5,395,897 bytes
```

## Workload Gate

```text
full P2 requests: 81
full P2 errors: 0
full P2 + reducer requests: 81
full P2 + reducer errors: 0
sampled class/linkage errors: false
```

## Memory Screen

| Metric | Full P2 | Full P2 + Reducer | Delta |
| --- | ---: | ---: | ---: |
| PSS KB | 354,002 | 336,122 | -17,880 |
| Private_Dirty KB | 344,956 | 326,888 | -18,068 |
| memory.current bytes | 435,200,000 | 399,994,880 | -35,205,120 |
| RSS KB | 367,200 | 349,224 | -17,976 |
| NMT total committed KB | 303,574 | 301,576 | -1,998 |
| NMT Java heap committed KB | 107,024 | 109,656 | +2,632 |
| NMT Class committed KB | 19,378 | 19,361 | -17 |
| NMT Code committed KB | 23,070 | 23,349 | +279 |
| NMT classes | 24,252 | 24,250 | -2 |
| Startup seconds | 16.614 | 16.162 | -0.452 |

## Promotion Result

The screen passed the promotion gate:

```text
artifact bytes lower: true
workload errors zero: true
PSS not worse by more than 1 MB: true
Private_Dirty not worse by more than 1 MB: true
memory.current not worse by more than 1 MB: true
```

## Claim Boundary

This is a single-screen result. It is enough to promote V2-E to 3-pair
confirmation, but it is not a confirmed runtime memory claim.

The next required step is:

```text
P2-1  -> P2R-1
P2-2  -> P2R-2
P2-3  -> P2R-3
```

with V2-C validation and V2-D attribution.
