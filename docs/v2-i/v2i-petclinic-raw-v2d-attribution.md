# V2-I PetClinic Raw V2-D Attribution

V2-D accepted the V2-C evidence:

```text
evidence verdict: CONFIRMED_WIN
v2c valid: true
hypotheses: 2
```

Primary hypotheses:

```text
HEAP_PAGE_TOUCH_REDUCTION: HIGH
ANONYMOUS_RW_ALLOCATOR_REDUCTION: MEDIUM
```

Important attribution details:

```text
heap PSS median delta: -2,332 KB
heap used delta: about +148 KB
class histogram bytes delta: about +151,640 bytes
anonymous_rw PSS median delta: -2,544 KB
loaded classes median delta: -3
```

The result should not be explained as retained-object shrinkage or class-count
savings alone.
