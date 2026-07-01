# Phase V2-B1 Classfile Size Profiler

Status: implemented.

The profiler scans:

- target class directories,
- Spring AOT class directories,
- BOOT-INF/classes-style directories,
- explicit jar paths,
- optimized library jars,
- Spring Boot fat jars with nested BOOT-INF/lib jars.

Outputs:

```text
classfile-size-profile.json
classfile-size-profile.md
classfile-size-profile.csv
classfile-size-family-breakdown.json
```

It records classfile bytes, constant-pool count, method count, total method code
bytes, largest method code length, attribute buckets, and V2-A generated-family
classification.
