# Patient Pair Attribution

Status: **COMPLETE**

The original confirmation was pair-variant. Full `smaps`, heap information,
NMT, and class histograms show that retained object growth does not explain the
regressive pairs.

| Capture | PSS KB | Heap PSS KB | Anonymous RW Outside Heap KB | Histogram Bytes | Heap Used KB |
|---|---:|---:|---:|---:|---:|
| Original pair 1 | -2,359 | -1,644 | +2,248 | -54,112 | -8,227 |
| Original pair 2 | +12,127 | +864 | +13,892 | -143,760 | +1,746 |
| Original pair 3 | +1,652 | -272 | +4,788 | -43,712 | -15,198 |
| Controlled screen | +7,579 | +1,572 | +8,932 | -183,536 | -14,936 |

Classification:

```text
full smaps: NON_HEAP_ANONYMOUS_GROWTH
histogram: NO_MEANINGFUL_OBJECT_GROWTH
retained object growth: false
```

This attribution justified the bounded CDS/order/GC/settle-time diagnostics.
It is diagnostic evidence, not a replacement for the corrected final balanced
confirmation.
