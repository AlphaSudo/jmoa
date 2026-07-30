# Direct Result Reconciliation

JMOA now publishes two different evidence matrices because they answer two
different questions.

## Buyer Comparison

The direct matrix compares a clean no-JMOA `B0` artifact with final JMOA V2.
The current state is `DIRECT_PRODUCT_MATRIX_INCOMPLETE_ENVIRONMENT_BLOCKED`.
Doctor passed confirmation. PetClinic's latest campaign did not admit a product
comparison because the same B0 artifact failed the noise gate twice. Patient's
accepted-artifact comparison remains open.

## Engineering Evolution

The V1-to-V2 matrix shows that the evidence, reducer, materialization, and
runtime-policy work added in V2 improved all three accepted V1 artifacts. Those
medians are not added to older baseline-to-V1 measurements.

## PetClinic Baseline Correction

The historical PetClinic direct-replication baseline image was inspected during
this campaign and contained `JmoaRuntime.class`. It was therefore not a clean
no-JMOA baseline. A source-frozen B0 JAR was rebuilt with zero JMOA entries and
used for the published direct screen.

This correction invalidates the historical artifact as a clean B0 comparator.
The historical optimized artifact and incremental reducer experiments remain
useful only within their documented scopes; the Phase 33M B0-to-full-P2 delta
is not a clean no-JMOA product comparison. The current unified six-order
campaign is the PetClinic buyer-comparison authority.

## Claim Rule

Never arithmetically combine `B0 -> V1` and `V1 -> V2` medians. Never use a
screen as a confirmed win. Never turn a failed same-artifact noise gate into a
product delta. Cite the direct matrix for adoption claims and the evolution
matrix for engineering progress.
