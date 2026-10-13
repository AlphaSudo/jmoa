# Deferred To V3

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The stable 2.1 binary distribution does not ship:

- generated/proxy/AOT mutation;
- large-method splitting;
- constant-pool or BootstrapMethods rewriting;
- annotation, signature, line-number, or stack-map stripping;
- automatic production deployment mutation;
- a universal runtime-policy selector without registered service evidence;
- startup claims;
- Maven Central publication or a zero-input public release bundle.

Any future mutation must retain semantic gates, byte-preservation or equivalent
invariants, runtime-origin proof, V2-C confirmation, and V2-D attribution.


The filename preserves a historical decision. It is not a published delivery
schedule. Provisional 2.15 adds evidence only and enables none of these mutation
capabilities in the stable binary distribution.
