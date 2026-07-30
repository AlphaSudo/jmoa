# Doctor Reconstructed Order Classification

- Classification: **V1_RUNTIME_COST**
- d1, V1 second - B0 first: **2661 KB PSS**
- d2, V1 first - B0 second: **8141 KB PSS**
- Order-balanced artifact estimate: **5401 KB PSS**
- Order-position estimate: **-2740 KB PSS**
- B0-first pair timing: **TIMING_CONFOUNDED**
- V1-first pair timing: **TIMING_CONFOUNDED**

V1 is more expensive in both observed orders; this supports a reconstructed-runtime V1 cost, not an exact historical replay claim.

This is a two-order reconstructed diagnostic, not a historical replay or a
product performance claim. The exact artifacts ran in a reconstructed support
environment, and the workload primarily exercises Actuator plus one read-only
business endpoint.
