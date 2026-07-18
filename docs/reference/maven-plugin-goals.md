# Maven Plugin Goals

Coordinates: `com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2`.

| Goal | Purpose | Mutation |
| --- | --- | --- |
| `deduplicate-lambdas` | Scan, admit, generate adapters, weave lambda sites, and emit the main report | Opt-in/configured |
| `optimize` | Lifecycle-facing wrapper for the lambda optimization path | Opt-in/configured |
| `reduce-bytecode` | Estimate or remove dependency LVT/LVTT metadata | Disabled/report-only by default |
| `measure-impact` | Build or execute measurement plans | No class mutation |
| `coverage-report` | Compare observed profile coverage with current sites | Report only |
| `check-coverage` | Enforce configured coverage expectations | Report/gate only |
| `evidence` | Parse, validate, pair, classify, and report run evidence | No mutation |
| `attribution` | Reconcile V2-C-valid memory evidence and causal hypotheses | No mutation |
| `recommend-reducer` | Evaluate a service/protocol against registered reducer evidence | Recommendation only |
| `recommend-runtime` | Select a registered service-specific runtime policy | Recommendation only |
| `runtime-preflight` | Verify artifact/archive/policy compatibility before runtime | Gate only |
| `analyze-generated-runtime` | Attribute generated-family runtime load/histogram evidence | Report only |
| `analyze-generated-relevance` | Rank generated-family relevance | Report only |
| `analyze-generated-evidence` | Reconcile matched generated-family lifecycle bundles | Report only |

Invoke a goal directly:

```powershell
mvn com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2:<goal> -D<property>=<value>
```

The plugin is compiled for Java 22. Use a Java 22-or-newer Maven runtime; the
current CI and qualification environment uses Java 26.

Goal-specific properties are declared with Maven `@Parameter` annotations in
the corresponding `*Mojo.java` class. Production workflows should use reviewed
configuration and the recommendation/preflight gates rather than infer defaults
from this summary.
