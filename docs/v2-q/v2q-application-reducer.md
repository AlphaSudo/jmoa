# V2-Q Application Raw Reducer

V2-Q adds an opt-in application-class directory path to `jmoa:reduce-bytecode`.
The reducer writes a parallel `application-classes` tree under its output
directory; it never rewrites the supplied input tree in place.

Required mutation flags:

```text
jmoa.reducer.enabled=true
jmoa.reducer.optimize=true
jmoa.reducer.reportOnly=false
jmoa.reducer.engine=raw
jmoa.reducer.profile=release-low-footprint
jmoa.reducer.stripLocalVariableTable=true
jmoa.reducer.stripLocalVariableTypeTable=true
jmoa.reducer.includeApplicationClasses=true
jmoa.reducer.applicationInputDir=<packaged classes directory>
jmoa.reducer.generatedFamilies=report-only
```

Outputs include `application-raw-reducer-report`, generated-family inventory
and policy reports, and an application-specific raw byte-preservation audit.
