# Historical Baseline Recovery

This directory reconciles historical B0/V1 evidence with the current sealed
six-order B0/V1/V2 campaigns.

## Result

- Historical absolute B0 and V1 run vectors were recovered for all three services.
- Doctor's historical report contains a median-index bug: source-stated
  `-6,048 KB`; corrected independent-median delta `-2,728 KB`; paired-delta
  median `-2,036 KB`.
- Doctor's current B0 is not the historical comparator: 13 normalized JAR
  entries differ, including generated application classes.
- Patient's exact historical B0 artifact was not recovered, and its historical
  CDS/workload contract differs materially from the current campaign.
- PetClinic's current B0 is not the historical comparator: 10 normalized JAR
  entries differ. Its historical/current V1 JAR is exact.

The current balanced matrix remains authoritative for the artifacts it froze.
This audit does not rewrite those outcomes.

## Final Reconstruction Decisions

- Doctor: exact historical B0/V1 artifacts were run under reconstructed
  effective base CDS. V1 measured `+2,661 KB` PSS versus B0, so the historical
  direction did not reproduce and no six-order campaign is authorized.
- Patient: the historical B0 artifact, source revision, and support/config
  identity were not recovered. No performance run is authorized.
- PetClinic: historical B0 contains JMOA output and semantic application drift.
  It is not a clean baseline and no performance run is authorized.

The current sealed matrix remains authoritative for its own artifacts and
protocol. Historical directional budgets remain engineering diagnostics only.

## Read Next

1. [Absolute B0 comparison](historical-vs-current-b0-absolute.md)
2. [V1 identity comparison](historical-vs-current-v1-identity.md)
3. [Engineering budget](historical-expected-engineering-budget.md)
4. [Baseline acceptance decision](baseline-acceptance-decision.md)
5. [Mechanism activation contract](mechanism-activation-study-contract.md)
6. [Final comparator reconstruction closure](../comparator-reconstruction/comparator-reconstruction-closure.md)
