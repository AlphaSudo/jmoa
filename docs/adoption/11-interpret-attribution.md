# 11 Interpret Attribution

V2-D explains a valid result; it does not manufacture one.

Review:

- heap PSS versus heap used and histogram bytes;
- anonymous writable mappings;
- mapped-file PSS;
- NMT total and category deltas;
- Metaspace and class-space deltas;
- loaded class and class-loader deltas;
- V2-A generated-family signals;
- V2-B loaded bytecode-size signals.

Typical interpretations:

```text
heap PSS moves, heap used flat       -> page-touch effect
anonymous_rw moves, NMT partly flat -> allocator/native mapping effect
classes and metaspace both fall     -> class metadata contribution
artifact shrinks, runtime flat      -> artifact-only benefit
launch mode reverses direction      -> materialization sensitivity
```

State counter-evidence and confidence. Do not attribute all memory movement to class count or bytecode bytes merely because those metrics changed.
