# V2-H Claim Reconciliation

V2-H exists because the confirmed V2-E runtime result and the V2-F productized reducer are not the same artifact policy.

## Reconciled Claims

The `v0.7.0-v2e-runtime-confirmed` PetClinic runtime confirmation used the earlier V2-E LVT/LVTT reducer policy:

```text
full P2 vs full P2 + V2-E reducer
artifact byte delta: -5,395,897
median PSS delta: -1,624 KB
median Private_Dirty delta: -1,636 KB
median memory.current delta: -12,255,232 bytes
paired wins: 2/3
```

V2-F hardened that reducer for product use by skipping safer surfaces:

```text
signed jars
multi-release jars
sealed jars
classes carrying BootstrapMethods
```

The hardened V2-F PetClinic artifact smoke therefore removes fewer bytes:

```text
reducer positive removed bytes: 3,870,720
materialized dependency-jar byte delta: -3,855,370
```

The first number is the reducer report's sum of per-jar positive removals. The second number is the actual total byte delta of the materialized dependency jar set after ZIP rewriting effects.

## V2-H Question

V2-H asks:

```text
Does the productized V2-F-hardened reducer retain the PetClinic runtime win?
```

This must be answered separately from the `v0.7.0` V2-E confirmation.

## Claim Boundary

Confirmed before V2-H:

```text
V2-E earlier reducer policy is runtime-confirmed on PetClinic under the documented exploded Boot no-CDS protocol.
V2-F hardened reducer is artifact-smaller on PetClinic.
V2-G hardened reducer generalizes at artifact level to Doctor corrected D2.
```

Not transferable without V2-H:

```text
The V2-E runtime win cannot be automatically applied to the V2-F hardened/productized reducer.
The V2-G Doctor artifact result is not a Doctor runtime claim.
```

