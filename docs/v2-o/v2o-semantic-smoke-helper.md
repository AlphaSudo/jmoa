# V2-O Semantic Smoke Helper

`scripts/runtime-semantic-smoke.ps1` polls health, executes endpoint cases from
a JSON file, and scans optional container logs for verifier, class-format, and
linkage failures. It writes the V2-C-recognized `jmoa-semantic-smoke.json`.

Endpoint input can be an array or an object with an `endpoints` array:

```json
{
  "endpoints": [
    {"path": "/actuator/health", "method": "GET", "expectedStatus": 200}
  ]
}
```

Shared headers may be supplied as a separate JSON object. Do not commit bearer
tokens or private endpoint files; keep them in ignored local inputs.

A passed smoke is required before a screen. It does not produce a memory claim.
