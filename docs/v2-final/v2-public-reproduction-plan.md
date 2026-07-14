# V2 Public Reproduction Plan

Status: `PUBLIC_GOLDEN_PATH_QUALIFIED`

The public example under `examples/spring-petclinic-customers-nocds` is the
qualified clean-clone golden path. It builds the pinned public customers
service, applies full P2 and the raw reducer, materializes an exploded-Boot
artifact with a runtime-library hash proof, then runs Java 17 semantic smoke.

## Golden Path Target

Preferred public quickstart:

```text
Spring PetClinic customers-service
full P2
plus productized raw LVT/LVTT reducer
EXPLODED_BOOT_APP
NO_CDS_LOW_DIRTY
config/discovery support services
```

The default quickstart proves build, packaging, and semantic viability. It does
not replace the frozen V2-C/V2-D paired memory protocol.

## Required Quickstart Gates

The qualified quickstart produces:

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
