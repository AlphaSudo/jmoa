# Deferred To V3

V2 deliberately does not ship:

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
