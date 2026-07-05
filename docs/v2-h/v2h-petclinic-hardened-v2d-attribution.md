# V2-H PetClinic Hardened V2-D Attribution

V2-D-style attribution was applied to the valid V2-H screen evidence.

## Primary Movement

The hardened reducer made the artifact smaller but increased PSS and Private_Dirty in the screen:

```text
PSS delta: +7,804 KB
Private_Dirty delta: +7,824 KB
memory.current delta: -7,364,608 bytes
```

The main observed runtime regression is heap page movement:

```text
heap PSS delta: +6,756 KB
heap used delta: +24 KB
class histogram bytes delta: +45,256 bytes
```

That pattern is not retained-object growth. Heap used and histogram bytes were effectively flat while heap PSS moved.

## Supporting Movement

```text
anonymous_rw PSS delta: +940 KB
NMT total committed delta: -1,797 KB
loaded classes delta: +1
startup delta: +4.088 seconds
```

NMT total committed moved down while PSS moved up, so NMT does not explain the PSS regression. This is consistent with page-touch / dirty-page behavior rather than a clean class metadata reduction story.

## Causal Hypothesis

```text
primary: HEAP_PAGE_TOUCH_GROWTH
secondary: ANONYMOUS_RW_SMALL_GROWTH
not supported: RETAINED_OBJECT_GROWTH
not supported: CLASS_COUNT_SAVINGS
not supported: NMT_VISIBLE_MEMORY_WIN
```

## Boundary

This screen attribution blocks productized runtime confirmation. It does not invalidate the earlier V2-E `v0.7.0` runtime-confirmed reducer result, because that result used a different reducer policy.

