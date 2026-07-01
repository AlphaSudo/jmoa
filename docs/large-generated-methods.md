# Large Generated Methods

V2-B highlights large methods and connects them to V2-A generated-family labels.

This helps answer:

- Which generated/AOT/proxy/helper classes are bytecode-heavy?
- Which classes are numerous but individually small?
- Which classes contain near-64KB methods?
- Are large generated classes loaded at runtime?
- Is the large footprint in method code, constant pool, annotations, or debug
  metadata?

Current V2-B output is report-only. Future reducers may include:

```text
DEBUG_ATTRIBUTE_STRIP_REPORT
LARGE_METHOD_SPLIT_REPORT
GENERATED_HELPER_DUPLICATE_SHAPE_REPORT
BOOTSTRAP_METHOD_DENSITY_REPORT
```

Automated method splitting is not admitted yet. It is high risk because control
flow, exception tables, stack maps, and verifier behavior must be preserved.
