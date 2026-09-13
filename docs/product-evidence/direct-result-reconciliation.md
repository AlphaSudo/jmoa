# Direct Result Reconciliation

JMOA publishes distinct evidence tracks because they answer different
questions. Their deltas must never be added or substituted for one another.

## Buyer Comparison

The direct matrix compares a clean no-JMOA `B0` artifact with final JMOA V2.
That matrix is complete: all three services ran 18 valid final observations.
Doctor passed the complete product gate. Patient and PetClinic had favorable
median PSS deltas but missed the required magnitude and fully negative
bootstrap-interval gates. The frozen three-service launch criterion therefore
remains not passed at one complete product win out of three.

## Engineering Evolution

The V1-to-V2 matrix shows that the evidence, reducer, materialization, and
runtime-policy work added in V2 improved all three accepted V1 artifacts. Those
medians are not added to older baseline-to-V1 measurements.

## PetClinic Candidate Confirmation

The later R4.87 campaign asks a third question: whether exact R41F reduces RAM
versus accepted exact V2E under its frozen PetClinic deployment protocol. R41F
combines audited dependency debug-metadata compaction with deterministic
fat-JAR packaging; V2E is the accepted exploded-Boot deployment.

R4.87 is claimable on that scope: 12/12 favorable paired PSS blocks, median
-12,797 KiB, exact `p=0.00048828125`, and bootstrap 95% interval
[-13,299.5, -12,005] KiB. Median `memory.current` was -13,932,544 bytes. The
claim explicitly carries a +19.20% median lifecycle-CPU tradeoff and a +106,496
byte median cgroup-file tradeoff; both passed prospectively frozen limits.

This result does not turn R41F into a clean B0 comparator, does not isolate
metadata compaction from packaging, and does not promote R41F. See the
[R4.87 result](petclinic-r41f-v2e-r487-result.md).

## PetClinic Direct R41F Confirmation

T7R subsequently made the missing direct comparison using exact R41F and exact
strict no-JMOA B0 in symmetric fat-JAR images. It completed four qualification
sessions, eight passing same-artifact controls, and 24 fresh held-out sessions
in 12 alternating paired blocks. All 36 sessions were valid, and no replacement
was used.

The direct result is not claimable: 8/12 blocks favored R41F, median PSS was
-644.5 KiB, exact `p=0.3876953125`, and the bootstrap 95% interval was
[-1,336.5, +231] KiB. `memory.current` improved in 12/12 blocks by -2,840,576
bytes median, but missed the frozen 4 MiB materiality gate. R41F also reduced
cgroup file by -2,027,520 bytes, anonymous-RW PSS by -4,088 KiB, and NMT
committed by -2,791.5 KiB. A consistent +2,912 KiB median native-process-heap
PSS offset cancelled most of those reductions in total PSS.

T7R therefore appended no claim row and did not promote R41F. Exact V2E remains
accepted. This is a valid negative direct result, not a control or protocol
failure. See the [T7R result](petclinic-r41f-b0-t7r-result.md).

## PetClinic Baseline Correction

The historical PetClinic direct-replication baseline image was inspected during
this campaign and contained `JmoaRuntime.class`. It was therefore not a clean
no-JMOA baseline. A source-frozen B0 JAR was rebuilt with zero JMOA entries and
used for the published direct screen.

This correction does not invalidate the historical full-P2 or V1-to-V2
experiments. It narrows what they can answer. The latest clean-B0 campaign is
the current PetClinic buyer-comparison authority, and its terminal result is
`ENVIRONMENT_VARIANCE_TOO_HIGH`, with no product pair admitted.

## Claim Rule

Never arithmetically combine `B0 -> V1`, `V1 -> V2`, `V2E -> R41F`, or direct
`B0 -> R41F` medians.
Never use a screen as a confirmed win. Never turn a failed same-artifact noise
gate into a product delta. Cite the direct matrix for clean buyer-comparison
claims, the evolution matrix for V1-to-V2 engineering progress, R4.87 only for
the exact PetClinic R41F-versus-V2E candidate claim, and T7R for the exact
PetClinic R41F-versus-strict-B0 direct nonclaim.
