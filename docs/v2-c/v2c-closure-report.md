# V2-C Closure Report

Status: closed as the JMOA evidence engine foundation.

V2-C is the measurement truth layer for future optimizer work. It does not
change optimizer behavior and does not enable V2-A or V2-B mutation.

## Implemented

```text
evidence schema and models
smaps_rollup parser
full smaps parser
memory.current parser
workload JSON parser
GC.heap_info parser
GC.class_histogram parser
NMT summary parser
evidence validator
paired confirmation analyzer
variance classifier
perturbation detector
historical replay mode
jmoa:evidence Maven goal
JSON and Markdown reports
```

## Replay Gate

Historical replay passed with one documented optional skip:

```text
33M PetClinic exploded Boot full P2: CONFIRMED_WIN
33K.7b PetClinic fat-JAR full P2: CONFIRMED_REGRESSION, HEAP_PAGE_TOUCH
32I invalid Doctor fixture: INVALID_EVIDENCE
32L Doctor corrected win: skipped because raw run-level evidence is unavailable
```

The replay gate deliberately did not use sanitized summary Markdown as raw
evidence. This keeps V2-C honest: it can replay evidence folders, not prose.

## Closure Decision

V2-C is closed because:

```text
real recovered PetClinic raw evidence replayed the audited win
real recovered PetClinic raw evidence replayed the audited fat-JAR failure
invalid evidence was rejected
Doctor raw replay absence is explicit and documented
tests and publication safety checks pass
```

## Remaining V2-A Work

```text
generated-class mutation still disabled
Spring AOT BeanDefinition helper rewrite not implemented
CGLIB/JDK proxy/ByteBuddy/Hibernate proxy rewriting not implemented
no generated-class memory win claim
```

## Remaining V2-B Work

```text
bytecode mutation still disabled
debug attribute stripping not implemented
LocalVariableTable reducer not implemented
large-method splitting not implemented
constant-pool reducer not implemented
BootstrapMethods reducer not implemented
no bytecode-size memory or startup win claim
```

## Future Gate

Any future V2-A generated-class mutation or V2-B bytecode reducer must pass V2-C
validation before a performance claim can be made.
