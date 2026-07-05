# V2-H PetClinic Hardened Final Verdict

V2-H does not confirm the V2-F-hardened productized reducer as a runtime memory improvement.

## Verdict

```text
status: SCREEN_FAILED_NO_CONFIRMATION
runtime claim: false
artifact claim: true
```

The hardened reducer remains useful as an artifact-footprint reducer:

```text
materialized dependency jar byte delta: -3,855,370
BOOT-INF/lib entries replaced: 162/162
```

But the screen blocks runtime promotion:

```text
PSS delta: +7,804 KB
Private_Dirty delta: +7,824 KB
memory.current delta: -7,364,608 bytes
```

## Claim Integrity Outcome

The previous V2-E runtime claim remains valid only for the earlier `v0.7.0` reducer policy:

```text
full P2 vs full P2 + V2-E reducer
median PSS delta: -1,624 KB
paired wins: 2/3
```

That claim is not transferred to the V2-F-hardened/productized reducer.

## Next Recommended Action

Keep V2-F/G as artifact/productization milestones and do not tag `v0.8.0-v2h-productized-reducer-confirmed`.

Before another runtime attempt, investigate why the hardened policy changes runtime page behavior:

```text
compare old V2-E reduced jar set vs V2-F hardened jar set
identify jars skipped by hardening that were reduced in V2-E
run a policy-diff artifact and V2-D attribution analysis
consider a reviewed allowlist mode instead of broad hardening transfer
```

