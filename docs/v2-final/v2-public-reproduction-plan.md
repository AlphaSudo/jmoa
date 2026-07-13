# V2 Public Reproduction Plan

Status: P0 blocker remains.

The current public example under `examples/spring-petclinic-customers-nocds` is
a scaffold. It explains the workflow but does not yet provide a clean-clone,
single-command public golden path.

## Golden Path Target

Preferred public quickstart:

```text
Spring PetClinic visits-service
baseline
vs
baseline + productized raw LVT/LVTT reducer
EXPLODED_BOOT_APP
NO_CDS_LOW_DIRTY
embedded HSQLDB
```

This path is simpler than customers because it does not require config/discovery
support services for the minimal public demo.

## Required Quickstart Gates

The quickstart must run from a clean clone and produce:

- baseline artifact
- raw-reduced artifact
- reducer manifest v2
- raw byte-preservation audit
- materialization proof
- semantic smoke result
- reducer recommendation
- runtime policy recommendation
- optional screen/confirmation command

## Release Rule

The default quickstart may stop at artifact/audit/smoke. A runtime memory claim
still requires V2-C and V2-D evidence.

