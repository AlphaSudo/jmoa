# V2-M Phase Boundary

V2-M productizes the decision layer around the existing raw LVT/LVTT reducer.

Goal:

```text
decide whether the raw reducer is confirmed for the exact protocol,
requires a new screen, is artifact-only, must be blocked, or is diagnostic-only
```

V2-M does not:

```text
add a reducer
broaden metadata stripping
run containers
mutate bytecode
transfer a confirmation to a different service, launch mode, or runtime policy
```

The Maven goal is disabled by default and report-only:

```text
jmoa:recommend-reducer
```

Inputs can be supplied as a normalized `reducer-admission-input.json` or as a
directory containing the canonical reducer, audit, safety, semantic, V2-C, and
V2-D reports.

Final closure:

```text
CLOSED_CONFIRMED_INFRASTRUCTURE
```
