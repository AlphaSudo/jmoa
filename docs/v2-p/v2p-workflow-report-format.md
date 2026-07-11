# V2-P Workflow Report Format

`jmoa-runtime-workflow-report.json` records the service, artifact, reducer
engine, launch mode, runtime policy, scope, every gate state, and the current
workflow state. It also records `claimAllowed` and always sets
`claimDeclared` to `false`.

The report is an orchestration record. It is not a replacement for V2-C
confirmation or V2-D attribution reports.
