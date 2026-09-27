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

This result does not turn R41F into a clean B0 comparator and does not isolate
metadata compaction from packaging. It did not promote R41F at the time of its
decision. See the [R4.87 result](petclinic-r41f-v2e-r487-result.md).

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

T7R therefore appended no claim row and did not promote R41F. This remains a
valid negative result for its frozen 4 MiB process-PSS endpoint, not a control
or protocol failure. See the [T7R result](petclinic-r41f-b0-t7r-result.md).

## PetClinic Direct Target-Cgroup Confirmation

T7R2 then tested the unresolved product-RAM hypothesis in a new prospective
campaign. It reused no T7R observation: four qualification, eight control and
24 held-out sessions all ran fresh under the same exact B0/R41F fat-JAR image
pair. The primary endpoint was the exact target cgroup's `memory.current`, with
a -2 MiB median materiality rule, a wholly favorable bootstrap interval, and a
one-sided upper bound at least one frozen 1 MiB memcg charge-stock floor below
zero. PSS, Private Dirty, cgroup anon/file, native `[heap]`, NMT, semantics and
product costs were separately bounded.

T7R2 passed all 25 gates. Exact R41F minus strict B0 reduced `memory.current`
in 12/12 blocks by **-3,317,760 bytes median**, exact
`p=0.00048828125`, bootstrap 95% **[-3,715,072,-2,932,736] bytes**, and
one-sided upper **-2,953,216 bytes**. Process PSS was also favorable in 12/12
blocks at **-1,407.5 KiB median**, with bootstrap 95%
**[-1,725.5,-638.5] KiB**. Cgroup file and anon were both favorable.

Verdict: `T7R2_CGROUP_RAM_WIN_PROMOTED`. A claimable whole-product registry row
was appended and accepted PetClinic advanced from exact V2E to exact R41F.
This is a direct target-cgroup total-RAM claim; it does not retroactively alter
T7R's failed 4 MiB PSS endpoint. See the
[T7R2 result](petclinic-r41f-b0-t7r2-cgroup-result.md).

## PetClinic Baseline Correction

The historical PetClinic direct-replication baseline image was inspected during
this campaign and contained `JmoaRuntime.class`. It was therefore not a clean
no-JMOA baseline. A source-frozen B0 JAR was rebuilt with zero JMOA entries and
used for the published direct screen.

This correction does not invalidate the historical full-P2 or V1-to-V2
experiments. It narrows what they can answer. The latest clean-B0 campaign is
the current PetClinic buyer-comparison authority, and its terminal result is
`ENVIRONMENT_VARIANCE_TOO_HIGH`, with no product pair admitted.

## PetClinic Four-Arm Direct Product Confirmation

T7R2.3/T7R2.36, published in JMOA 2.1 as T7R23, resolved the remaining role
and packaging ambiguity with a four-arm factorial. It compared strict B0 and
exact R41 content in both exploded and fat-JAR layouts. Five qualifications,
20 same-artifact controls, eight direct-screen sessions and 48 held-out
sessions completed: 81/81 attempts, with no predecessor observation reuse and
no replacement observation.

The primary direct product contrast is exact R41F minus documented strict B0E.
It reduced process PSS in 12/12 held-out blocks by **-15,241.5 KiB median**,
exact `p=0.00048828125`, bootstrap 95% **[-16,109.5, -15,052.5] KiB**.
Target-cgroup `memory.current` was **-17,033,216 bytes median**, also favorable
in 12/12 blocks. Private Dirty, cgroup anonymous, file and kernel memory all
corroborated the direction.

The factorial estimated a **-12,715.25 KiB packaging main effect**,
**-2,465 KiB content main effect**, and **+3,801.5 KiB interaction**. Therefore
the accepted wording is packaging-inclusive. Median lifecycle CPU increased
14.71% and startup increased 1.652 seconds; all frozen product-cost gates
passed.

Verdict: `T7R23_R487_SCALE_DIRECT_RAM_WIN`. The experiment registry contains a
claimable direct-product row. The exact accepted artifact remains R41F; only
its evidence pointer advances to revision 3. See the
[JMOA 2.1 result](petclinic-r41f-b0-t7r23-result.md).

## Claim Rule

Never arithmetically combine `B0 -> V1`, `V1 -> V2`, `V2E -> R41F`, or direct
`B0 -> R41F` medians.
Never use a screen as a confirmed win. Never turn a failed same-artifact noise
gate into a product delta. Cite the direct matrix for the original B0-to-V2
buyer comparison, the evolution matrix for V1-to-V2 engineering progress,
R4.87 only for exact R41F versus V2E, T7R for the failed direct 4 MiB PSS
hypothesis, T7R2 for the successful independent target-cgroup RAM claim and
R41F promotion, and T7R23 for the current four-arm packaging-inclusive direct
PSS and cgroup-RAM claim.
