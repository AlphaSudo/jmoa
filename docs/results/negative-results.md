# Negative Results Register

JMOA preserves valid failures because they define the product boundary.

## Hardened ASM Metadata Reducer

**Hypothesis:** a productized policy that skipped signed, multi-release, sealed,
and BootstrapMethods-bearing surfaces would retain the earlier metadata-reducer
runtime benefit.

**Artifact result:** 162 JARs were safely materialized and dependency bytes fell
by about 3.86 MB.

**Runtime result:** the PetClinic screen regressed by `+7,804 KB` PSS and
`+7,824 KB` Private_Dirty. The screen stopped before confirmation.

**Disposition:** artifact-safe only; runtime promotion rejected. The older
runtime claim was not transferred to the hardened artifact set.

## Application-Class LVT/LVTT Reduction

**Hypothesis:** extending raw metadata reduction to admitted application classes
would add incremental value.

**Artifact result:** four visits-service classes passed byte-preservation audit;
only 480 bytes were removed. Semantic smoke passed.

**Runtime result:** three-pair confirmation produced `1/3` wins and median PSS
`+5,732 KB`.

**Disposition:** low ROI, artifact/semantic only, runtime promotion blocked. No
V2-C or V2-D claim pipeline was run after the failed gate.

## Generated-Family Mutation

**Hypothesis:** runtime-relevant generated, synthetic, Spring Data, proxy, or AOT
families would expose one bounded safe mutation candidate.

**Evidence result:** matched static/runtime bundles were completed for three
services. Lambda belonged to the existing optimizer domain; Spring Data and
synthetic/bridge surfaces lacked a bounded safe transform; Spring AOT and proxy
families remained safety-blocked.

**Disposition:** discovery complete, no generated-family mutation admitted.

## Patient Dynamic Application CDS

**Hypothesis:** a Patient application archive could add value over the stock JDK
base archive.

**Artifact result:** dynamic and extracted-layout common-class studies produced
structurally inspectable archives.

**Runtime result:** tested single-replica application archives regressed or
failed their frozen admission gates. The final V1-to-V2 application-archive
screen was not authorized after fixed-artifact rejection.

**Disposition:** dynamic Patient application CDS blocked. This does not
contradict the later stock-base-CDS confirmation because the latter maps no
Patient application archive.

## Historical Baseline-To-V2 PetClinic Result

**Hypothesis:** the older direct baseline-to-V2 result would reproduce on the
exact release images.

**Runtime result:** the fresh five-pair replication was valid but mixed: `2/5`
PSS wins and median PSS `+585 KB`.

**Disposition:** retained as provenance, not a current release claim. The
reproducible public claim is finalized V1 to V2.
