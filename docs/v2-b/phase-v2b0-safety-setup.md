# Phase V2-B0 Safety Setup

Status: implemented.

V2-B is disabled by default and report-only when enabled.

Feature flags:

```text
jmoa.size.enabled=false
jmoa.size.reportOnly=true
jmoa.size.optimize=false
jmoa.size.failOnNear64k=false
jmoa.size.warnMethodBytes=32768
jmoa.size.dangerMethodBytes=49152
jmoa.size.failMethodBytes=65535
jmoa.size.stripDebugAttributes=false
jmoa.size.stripLocalVariableTables=false
jmoa.size.stripLineNumberTables=false
jmoa.size.stripSourceFile=false
jmoa.size.optimizeBootstrapMethods=false
jmoa.size.optimizeConstantPool=false
```

Any mutation or strip flag fails fast in this release.
