# V2-E PetClinic V2-D Attribution

Status: attribution completed.

V2-D used the V2-C-valid confirmation evidence to explain where memory moved.

## Category Deltas

| Category | Median Delta |
| --- | ---: |
| PSS | -1,624 KB |
| Private_Dirty | -1,636 KB |
| memory.current | -12,255,232 bytes |
| heap PSS | +1,572 KB |
| heap used | +145 KB |
| class histogram bytes | +148,296 bytes |
| anonymous_rw PSS | -2,456 KB |
| anonymous executable code PSS | -376 KB |
| mapped file PSS | -59 KB |
| NMT total committed | -2,074 KB |
| NMT Java heap committed | +2,668 KB |
| NMT metaspace committed | -3,271 KB |
| NMT Class committed | -19 KB |
| NMT Code committed | -349 KB |

## Interpretation

V2-D classified smaps/NMT reconciliation as:

```text
NMT_VISIBLE
```

The heap/object attribution classified the median heap side as:

```text
HEAP_PAGE_TOUCH_GROWTH
```

That means the confirmed win should not be explained as retained-object
reduction. Heap used and class histogram bytes were nearly flat, while heap PSS
moved slightly upward at the median.

The favorable movement came mainly from:

```text
anonymous_rw PSS reduction
NMT total committed reduction
metaspace committed reduction
small code committed reduction
```

## What Not To Claim

Do not claim:

```text
class-count savings caused this result
heap used fell materially
object retention fell materially
debug metadata stripping always reduces runtime memory
```

Correct claim:

```text
V2-E produced a confirmed incremental memory win in this PetClinic no-CDS
exploded Boot protocol, with V2-D attribution pointing to NMT-visible and
anonymous_rw movement rather than retained-object or class-count reduction.
```
