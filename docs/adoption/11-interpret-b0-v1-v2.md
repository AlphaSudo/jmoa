# 11 Interpret B0/V1/V2

Read all three legs together.

## Doctor: complete product path

```text
B0 -> V1:   +654 KB
V1 -> V2: -6,261.5 KB
B0 -> V2: -4,715.5 KB, 5/6 wins, CI below zero
```

Doctor is a `B0 -> V2` positive control. It is not evidence that V1 wins alone.
The V2 reducer overwhelms low-signal V1 overhead.

## Patient: incremental reducer, missing product effect

```text
B0 -> V1:   +910 KB
V1 -> V2: -6,135.5 KB
B0 -> V2: -1,266.5 KB, 4/6 wins, CI crosses zero
```

The one correction authorized by a proven capture-timing defect changed the
measured direction, but V2 still misses the `-4,096 KB` magnitude gate and its
confidence interval crosses zero. The direct product effect is not confirmed.

## PetClinic: observed reduction below gates

```text
B0 -> V1: +2,516 KB
V1 -> V2: -3,736 KB
B0 -> V2: -2,947 KB, 4/6 wins, CI crosses zero
```

Do not call this a complete product win. Magnitude and uncertainty gates fail.

## Public Verdicts

```text
Doctor: COMPLETE_PRODUCT_WIN
Patient: PRODUCT_EFFECT_NOT_CONFIRMED
PetClinic: PRODUCT_EFFECT_NOT_CONFIRMED
```
