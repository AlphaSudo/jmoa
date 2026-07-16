# Patient Root-Cause Investigation

Status: **COMPLETE_STOPPED_AT_BOUNDARY**

The bounded investigation completed the comparator audit, pair attribution, no-CDS test, deterministic separate-CDS test, normal/post-GC test, T+20/T+60/T+120 snapshots, reversed-order test, and one final balanced confirmation.

## Findings

- Patient C2 is equivalent to the finalized V1 optimizer policy after normalizing timestamp and JSON map order.
- The initial V2 incorrectly reduced the JMOA runtime support JAR. The product now supports artifact include/exclude globs, and Patient preserves that JAR byte-for-byte.
- Application classes and Spring Boot loader content are unchanged; raw reducer audits preserve every non-LVT/LVTT structure.
- Outer fat-JAR ZIP metadata differs after repacking, while non-library entry content remains equal.
- The original anonymous writable regression was primarily outside the Java heap, with no meaningful retained-object growth.
- No-CDS strongly favored corrected V2, but CDS diagnostics varied in sign.
- Forced GC did not remove a regressive pair, and T+20 through T+120 did not change sign.
- The final balanced CDS confirmation produced 1/3 PSS wins and a +668 KB median PSS delta.

## Decision

Root cause: **CDS_INTERACTION**, with **HEAP_PAGE_TOUCH_VARIANCE** as the secondary mechanism.

The verified artifact defect was fixed, but corrected Patient still fails the frozen runtime gate. Recommendation is `BLOCK_RUNTIME_PROMOTION`; aggregate status remains `BLOCKED_FINAL_ACCEPTANCE`. No new optimizer or further Patient V2 phase is authorized.
