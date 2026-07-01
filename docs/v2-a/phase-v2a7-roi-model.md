# Phase V2-A7 ROI Model Integration

Status: implemented as generated-class ROI feature report.

The plugin writes:

```text
jmoa-roi-v2-report.json
jmoa-roi-v2-report.md
```

Per family, the report includes:

- generated class count,
- generated class bytes,
- synthetic method count,
- bridge method count,
- runtime generated loaded count when evidence is supplied,
- class histogram bytes when evidence is supplied,
- safety risk,
- optimization priority.

The report does not yet change lambda candidate admission. It gives JMOA a
stable feature surface for future generated-class-aware ROI decisions.
