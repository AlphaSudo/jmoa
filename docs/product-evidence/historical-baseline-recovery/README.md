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

## Decisions

- Doctor: corrected campaign conditionally authorized after a new freeze.
- Patient: no corrected campaign authorized.
- PetClinic: corrected campaign conditionally authorized after a new freeze.
- Mechanism activation counters: blocked pending accepted comparator identity.

No service was launched and no performance campaign was started by this audit.

## Read Next

1. [Absolute B0 comparison](historical-vs-current-b0-absolute.md)
2. [V1 identity comparison](historical-vs-current-v1-identity.md)
3. [Engineering budget](historical-expected-engineering-budget.md)
4. [Baseline acceptance decision](baseline-acceptance-decision.md)
5. [Mechanism activation contract](mechanism-activation-study-contract.md)
