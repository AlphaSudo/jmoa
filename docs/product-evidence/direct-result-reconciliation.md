# Direct Result Reconciliation

JMOA now publishes two different evidence matrices because they answer two
different questions.

## Buyer Comparison

The direct matrix compares a clean no-JMOA `B0` artifact with final JMOA V2.
The result is `ONE_SERVICE_PRODUCT_WIN`: Doctor passed confirmation; PetClinic
and Patient failed their bounded screens.

## Engineering Evolution

The V1-to-V2 matrix shows that the evidence, reducer, materialization, and
runtime-policy work added in V2 improved all three accepted V1 artifacts. Those
medians are not added to older baseline-to-V1 measurements.

## PetClinic Baseline Correction

The historical PetClinic direct-replication baseline image was inspected during
this campaign and contained `JmoaRuntime.class`. It was therefore not a clean
no-JMOA baseline. A source-frozen B0 JAR was rebuilt with zero JMOA entries and
used for the published direct screen.

This correction does not invalidate the historical full-P2 or V1-to-V2
experiments. It narrows what they can answer. The clean B0 screen is the current
source for the buyer comparison.

## Claim Rule

Never arithmetically combine `B0 -> V1` and `V1 -> V2` medians. Never use a
screen as a confirmed win. Cite the direct matrix for adoption claims and the
evolution matrix for engineering progress.
