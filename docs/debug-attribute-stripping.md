# Debug attribute stripping

Stable JMOA 2.1 ships an **opt-in raw dependency reducer** for
`LocalVariableTable` and `LocalVariableTypeTable`. The separate bytecode-size
profiler remains report-only. Provisional 2.15 adds Patient benchmark evidence
and no new Maven reducer binary.

## Stable mutation contract

Use the `release-low-footprint` raw profile with reduction enabled,
`reportOnly=false`, `optimize=true` and both LVT/LVTT switches explicitly set.
See [exact configuration](reference/reducer-configuration.md).

`RawReducerBytePreservationAuditor` verifies non-target classfile structures.
Line numbers, source information, stack maps, annotations, signatures,
BootstrapMethods, nest/record/module metadata and method semantics are preserved.
Signed, sealed and multi-release JARs are skipped; failures leave no promotable
partial output. Removing local-variable metadata reduces debugger visibility.

## Size reports and runtime benefit

The size profiler reports LineNumberTable, LVT/LVTT, SourceFile and other
attribute bytes. Reporting a surface does not authorize stripping it.
Removed classfile bytes are artifact savings. PSS benefit requires exact
materialization, origin proof, semantic workload and paired runtime evidence.

## Patient experimental scope

The provisional 2.15 composed Patient candidate reduced dependency line-number
observability in addition to its other admitted transformations. This is a cost
of that exact experimental artifact, not an advertised stable reducer option.
Its 8.38 MiB stopped-PSS observation has a conditional 8.19 MiB lower bound;
original calibration failed and used an approved exception, and performance
bounds remain unqualified or failed.

See [release boundaries](releases/current-state.md) and
[bytecode transformation](architecture/bytecode-transformation.md).
