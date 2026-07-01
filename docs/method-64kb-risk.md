# Method 64KB Risk

The JVM classfile format stores each method body in a `Code` attribute. The
`code_length` value must stay below `65,536`, so `65,535` bytes is the practical
upper bound.

V2-B reports method-size buckets:

```text
NORMAL   < 8 KB
NOTICE   >= 8 KB
LARGE    >= 16 KB
WARN     >= 32 KB
DANGER   >= 48 KB
CRITICAL >= 60 KB
LIMIT    >= 65,535 bytes
```

Configurable thresholds:

```text
-Djmoa.size.warnMethodBytes=32768
-Djmoa.size.dangerMethodBytes=49152
-Djmoa.size.failMethodBytes=65535
-Djmoa.size.failOnNear64k=false
```

When `failOnNear64k=true`, JMOA fails the build if any method reaches the
configured danger threshold. By default this is report-only.

The report is especially useful for generated code, AOT helpers, large
`<clinit>` methods, switch-heavy methods, and instrumentation output.
