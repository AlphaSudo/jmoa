# V2-H PetClinic Hardened Reducer Screen

V2-H screened the productized V2-F-hardened reducer against the already confirmed full-P2 PetClinic artifact.

## Protocol

```text
comparison: full P2 vs full P2 + V2-F-hardened reducer
service: Spring PetClinic customers-service
launch mode: EXPLODED_BOOT_APP / JarLauncher
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
CDS/AppCDS/Leyden: disabled
runtime javaagent: absent
workload: corrected 27 endpoints x 3 rounds
class-load logging during memory run: disabled
```

## Artifact Gate

```text
dependency jar byte delta: -3,855,370
BOOT-INF/lib entries replaced: 162/162
same jar count: true
hash proof: passed
```

## Screen Result

| Metric | Full P2 | Full P2 + hardened reducer | Delta |
| --- | ---: | ---: | ---: |
| RSS | 350,008 KB | 357,904 KB | +7,896 KB |
| PSS | 336,638 KB | 344,442 KB | +7,804 KB |
| Private_Dirty | 327,336 KB | 335,160 KB | +7,824 KB |
| memory.current | 416,817,152 bytes | 409,452,544 bytes | -7,364,608 bytes |
| heap PSS | 89,100 KB | 95,856 KB | +6,756 KB |
| anonymous_rw PSS | 216,552 KB | 217,492 KB | +940 KB |
| NMT total committed | 303,677 KB | 301,880 KB | -1,797 KB |
| heap used | 55,239 KB | 55,263 KB | +24 KB |
| class histogram bytes | 55,901,288 bytes | 55,946,544 bytes | +45,256 bytes |
| loaded classes | 24,250 | 24,251 | +1 |
| startup seconds | 29.632 | 33.720 | +4.088 |

## Gate Decision

The screen did not pass the V2-H promotion gate:

```text
artifact bytes lower: true
workload errors: 0
PSS not worse by > 1 MB: false
Private_Dirty not worse by > 1 MB: false
memory.current not worse by > 1 MB: true
```

Because PSS and Private_Dirty regressed by more than 1 MB, V2-H does not proceed to 3-pair confirmation.

## Verdict

```text
V2-H hardened/productized reducer runtime confirmation: not promoted.
V2-F/G remain artifact/productization claims.
V2-E v0.7.0 runtime claim remains valid only for the earlier confirmed reducer policy.
```

