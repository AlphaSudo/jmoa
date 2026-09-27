# Changelog

## 2.1.0

- Publishes the first claimable direct PetClinic whole-deployment result for
  exact R41F versus the documented strict no-JMOA B0 exploded deployment:
  median process PSS `-15,241.5 KiB` and target-cgroup `memory.current`
  `-17,033,216 B`, with 12/12 favorable held-out blocks and exact
  `p=0.00048828125`.
- Records the complete 81-session four-arm campaign, including same-artifact
  controls, screening, held-out factorial reduction, product costs, and sealed
  evidence hashes. No predecessor observation was reused.
- Advances the accepted PetClinic evidence pointer to revision 3 while keeping
  the exact R41F artifact and image identities unchanged.
- Adds the JMOA 2.1 technical paper, a concise product-oriented README, a
  sanitized machine-readable result, and explicit packaging/content
  attribution boundaries.
- Promotes release coordinates from `2.0.0-rc2` to `2.1.0` and ships GitHub
  Release JARs, source JARs, POMs, manifest, and SHA-256 checksums.

## 2.0.0-rc2

- Publishes a sanitized public evidence archive with run manifests, smaps,
  cgroup, NMT, heap, histogram, workload, and runtime-verification evidence.
- Reconciles historical B0-to-V2 reporting with a fresh exact-image five-pair
  replication: the direct comparison is mixed and is no longer release-claimed.
- Retains the reproducible V1-to-V2 incremental confirmation: median PSS
  `-6,012 KB`, Private_Dirty `-5,708 KB`, and memory.current `-8,081,408 B`
  across 2/3 paired wins in the frozen public customers-service protocol.
- Adds isolated-local-repository support to release-artifact installation and
  records a successful downloaded-asset consumer quickstart with semantic smoke.

## 2.0.0-rc1

- Ships the V1 full-P2 optimizer and byte-preserving raw dependency LVT/LVTT
  reducer behind explicit opt-in controls.
- Adds classfile profiling, V2-C evidence validation, V2-D attribution,
  recommendation, preflight, materialization, smoke, and confirmation tooling.
- Recorded the then-available public customers B0-to-V2 and V1-to-V2 memory
  acceptance records; RC2 supersedes the direct B0-to-V2 release claim.
- Retains generated-family analysis as diagnostic infrastructure; no proxy or
  generated-class mutation is enabled.
- Selects GitHub Release artifacts plus SHA-256 checksums as distribution.

Historical `v0.x` milestone notes remain under `docs/releases`.
