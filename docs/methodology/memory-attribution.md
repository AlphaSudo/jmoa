# Memory Attribution

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

V2-D explains a V2-C-valid result. It does not manufacture a cause from one
metric and does not turn diagnostic correlation into proof of causality.

## Inputs

`MemoryAttributionEngine` reconciles:

- smaps rollup and full mapping categories;
- heap PSS and heap Private_Dirty;
- anonymous writable and executable mappings;
- mapped files, native libraries, stacks, and JDK image mappings;
- NMT total and category committed values;
- heap occupancy and class histogram bytes/instances;
- class, hidden-class, classloader, and metaspace evidence;
- optional generated-family and bytecode-runtime reports.

It emits category deltas, smaps/NMT reconciliation, object-family summaries,
class/metaspace attribution, and ranked causal hypotheses with supporting and
contradicting evidence.

## Interpretation Patterns

**Heap page touch:** heap PSS moves while heap used, committed heap, and
histogram bytes remain comparatively flat.

**Retained object movement:** heap occupancy and histogram families move with
heap PSS.

**Allocator/native movement:** anonymous writable mappings move, with NMT
malloc/arena categories explaining some or all of the direction.

**Class/metaspace movement:** loaded classes and NMT Class/Metaspace move in the
same direction. A lower class count is not automatically the cause of a PSS
reduction.

## Historical examples

**PetClinic fat-JAR rejection:** PSS and heap PSS increased while retained-object
evidence did not explain the change. V2-D classified heap page-touch growth;
the result remained a valid regression.

**Patient stock-base-CDS confirmation:** median PSS was `-8,279 KB` and heap PSS
was about `-6,188 KB`, while heap-used and histogram deltas were small. The
primary hypothesis was `HEAP_PAGE_TOUCH_REDUCTION`, with anonymous writable
reduction as supporting evidence. JMOA does not claim fewer retained business
objects or class-count savings from that result.

## Limits

NMT does not account for every native or third-party allocation. smaps describes
mapped pages but not allocation call paths. Histograms are snapshots, not
allocation profiles. JFR or async-profiler can support a separate diagnostic
study, but their runs are marked perturbing and excluded from claim medians.


## Current release attribution boundaries

The 2.1 factorial measured packaging as the dominant PetClinic main effect:
-12,715.25 KiB, versus -2,465 KiB for content, with +3,801.5 KiB interaction.
Its direct 14.88 MiB deployment saving cannot be described as metadata alone.
Native process-heap PSS increased 2,972 KiB despite lower total PSS.

Patient's 2.15 direct result combines the frozen candidate changes and its own
Leyden cache. Static rewrite counts, cache-file shrinkage and classfile bytes
do not isolate causal contributions. Shared runtime flags are common policy.
The provisional RAM record does not establish accepted performance economics.
