# Runtime Origin Verifier

This directory tracks the runtime-origin verification surface.

Current public v0.1 status:

- plugin measurement code can parse class-load evidence,
- docs define static and dynamic origin gates,
- PetClinic workflow requires `-Xlog:class+load=info` proof,
- standalone reusable verifier CLI is still TODO.

The verifier should fail if sampled rewritten classes load from original jars
when optimized jars are expected.
