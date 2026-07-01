# Phase V2-A2 Runtime Attribution

Status: implemented as a report layer.

The plugin can consume:

- `-Xlog:class+load=info` logs,
- `jcmd GC.class_histogram` output.

It writes:

```text
generated-class-runtime-attribution.json
generated-class-runtime-attribution.md
generated-class-origin-map.json
generated-class-survival-report.md
```

The report attributes, per generated-class family:

- static generated class count,
- runtime loaded count,
- runtime-only loaded count,
- unloaded count,
- histogram class count,
- histogram instances,
- histogram bytes,
- survival category,
- optimization priority.

The attributor does not admit transformations. It feeds the V2-A safety and ROI
models.
