# Phase V2-B2 Runtime Correlation

Status: implemented as report-only infrastructure.

V2-B2 correlates the V2-B classfile footprint profile with runtime evidence. It
does not mutate bytecode and does not claim memory causality by itself.

## Inputs

```text
jmoa.size.classLoadLog
jmoa.size.classHistogram
```

If size-specific runtime inputs are not provided, the plugin reuses:

```text
jmoa.synthetic.classLoadLog
jmoa.synthetic.classHistogram
```

This keeps combined V2-A/V2-B diagnostic runs simple.

## Outputs

```text
bytecode-runtime-correlation.json
bytecode-runtime-correlation.md
bytecode-runtime-correlation-top-loaded.json
bytecode-runtime-correlation-near64k.json
```

## Categories

```text
STATIC_ONLY_RISK
RUNTIME_LOADED_COLD
RUNTIME_LOADED_HOT
WORKLOAD_SURVIVOR
MEMORY_CORRELATED
STARTUP_CORRELATED
UNKNOWN_NEEDS_JFR
```

The categories are intentionally conservative:

- static-only classes are not treated as runtime cost,
- class-loaded classes without histogram evidence are loaded/cold,
- histogram-positive classes are workload survivors,
- high histogram-byte classes are memory-correlated candidates,
- startup correlation still requires separate startup timing evidence.

## Boundary

This phase answers:

```text
which bytecode-heavy classes loaded?
which near-64KB methods belong to loaded classes?
which generated-family classes have live histogram evidence?
```

It does not answer:

```text
which classes caused PSS or Private_Dirty deltas?
which classes should be rewritten?
```

Those require paired runtime experiments and reducer safety gates.

