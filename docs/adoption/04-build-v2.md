# 04 Build V2

V2 starts from the accepted V1 application and applies only the admitted V2 reducer.

The current productized reducer strips:

```text
LocalVariableTable
LocalVariableTypeTable
```

It preserves line numbers, stack maps, annotations, signatures, bootstrap methods, module metadata, and other runtime-sensitive attributes. Signed, multi-release, sealed, and otherwise excluded JARs follow the reducer safety policy.

Record:

- reducer engine and profile;
- input/output hashes;
- changed and skipped classes;
- preservation failures;
- dependency bytes before and after;
- materialized dependency manifest.

Zero preservation failures are required. V1 and V2 application and Spring Boot loader fingerprints must match when V2 is a dependency-only reduction.
