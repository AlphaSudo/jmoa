# Runtime Origin Verification

Static jar inspection is useful, but it is not enough.

A jar can exist on disk while the JVM loads the original class from a different
runtime origin. JMOA therefore treats dynamic class-origin proof as part of the
claim gate.

## Static Checks

Static checks should verify:

- optimized jars exist in the expected materialized location,
- original jars do not shadow optimized jars,
- `jmoa-runtime-lib` is present when required,
- generated `JmoaPkgAdapters` classes are present in every root that references
  them,
- classfile versions are compatible.

## Dynamic Checks

Dynamic origin proof should capture:

```text
-Xlog:class+load=info
```

The verifier should sample rewritten classes across relevant package families
and confirm they load from the optimized jar or optimized exploded root.

For PetClinic, the accepted dynamic proof sampled Spring Boot, Spring Data,
Spring Beans, Spring Web, Spring WebMVC, Actuator, JMOA adapters, and JMOA
runtime classes.

## Failure Rule

If a rewritten class loads from the original artifact when an optimized artifact
is expected, the measurement is not publishable.
