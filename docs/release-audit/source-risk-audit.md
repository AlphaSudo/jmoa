# Source publication audit boundary

The stable public modules are the plugin and runtime support library. Current
2.15 publication adds evidence and documentation, with no new executable Maven
artifacts. See [inventory](publishable-source-inventory.md).

Publication checks reject private machine paths, credential material and raw
binary/campaign artifacts. Documentation checks validate local links, JSON,
current claim qualifiers and the stable accepted evidence hash. Default CI
tests source modules; it does not run private Patient or PetClinic measurements.

Internal implementation plans and personal drafts are excluded from the current
tree. Historic public commits/tags remain immutable in this documentation update.
The public benchmark's calibration, cost, warmth and binary-lineage limitations
remain disclosed; publication safety is not scientific acceptance.
