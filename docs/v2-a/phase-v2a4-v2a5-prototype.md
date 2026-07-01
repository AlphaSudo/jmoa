# Phase V2-A4/V2-A5 Prototype

Status: implemented as report-only prototype selection.

Selected first family:

```text
SPRING_AOT_BEAN_DEFINITIONS
```

Why this family:

- build-time generated,
- static artifact exists,
- easier to inventory,
- less runtime identity-sensitive than proxy classes.

Current implementation mode:

```text
REPORT_ONLY_REPACK_CANDIDATE
```

No generated-class bytecode mutation is enabled. The prototype writes:

```text
synthetic-prototype-family-selection.md/json
synthetic-optimizer-prototype-report.md/json
synthetic-affected-classes.json
synthetic-rewritten-classes.json
synthetic-safety-validation.json
```

The empty `synthetic-rewritten-classes.json` is intentional. Mutation remains
blocked until semantic gates are automated and confirmed.
