# Patient No-CDS V2-C Validation

Status: `CONFIRMED_WIN`

V2-C analyzed the six raw Patient no-CDS runs after the pair folders were
normalized into the standard evidence layout. Raw evidence remains private
under `target`.

| V2-C field | Result |
| --- | --- |
| Expected policy | `NO_CDS_LOW_DIRTY` |
| Valid runs | 6/6 |
| Invalid runs | 0 |
| Pairs | 3 |
| Paired wins | 2/3 |
| Median PSS delta | -8,903 KB |
| Median Private_Dirty delta | -8,636 KB |
| Median memory.current delta | -9,707,520 B |
| Verdict | `CONFIRMED_WIN` |
| Perturbing diagnostics | none |
| Workload errors | 0 |

The first pair regressed, while pairs 2 and 3 passed all three primary metric
thresholds. V2-C therefore accepts the result under the existing three-pair
confirmation rule; no fifth pair was needed.

The engine emitted a non-failing warning that dynamic runtime-origin proof was
not present. This is expected for a clean memory campaign because class-load
logging was disabled. The campaign still captured the static materialization
proof, artifact hashes, and live no-CDS off-state proof.

V2-C validates the evidence and pairing. It does not by itself claim that the
reducer is universally beneficial.
