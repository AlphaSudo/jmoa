# V2-I Policy Diff

V2-I compares three reducer surfaces:

| Surface | Engine | Jar policy | Reduced classes | BootstrapMethods skipped | Report removed bytes | Materialized jar delta | Runtime status |
| --- | --- | --- | ---: | ---: | ---: | ---: | --- |
| V2-E v0.7.0 | `asm pre-engine-field` | no signed/MR/sealed skip policy | 31,910 | 0 | 5,417,754 | -5,395,897 | CONFIRMED_WIN |
| V2-F/V2-H hardened | `asm` | skip signed/MR/sealed jars and BootstrapMethods classes | 23,680 | 6,029 | 3,870,720 | -3,855,370 | SCREEN_FAILED |
| V2-I raw recovery | `raw` | skip signed/MR/sealed jars; preserve BootstrapMethods while reducing LVT/LVTT | 29,709 | 0 | 3,684,536 | -3,668,109 | CONFIRMED_WIN |

## Interpretation

V2-H failed because the safer productized artifact surface was not equivalent to
the earlier V2-E runtime-confirmed artifact. The hardened `asm` policy skipped
signed jars, multi-release jars, sealed jars, and every BootstrapMethods-bearing
class.

V2-I keeps the jar-level safety policy from V2-F, but adds an explicit `raw`
engine that removes only nested LocalVariableTable and LocalVariableTypeTable
entries inside Code attributes. BootstrapMethods are preserved rather than
rewritten or stripped.

The raw engine recovered the 6,029 BootstrapMethods-bearing classes skipped by
V2-H, but it did not reproduce the old V2-E artifact byte delta. That is
expected: the raw engine avoids ASM classfile round-tripping and is more
byte-preserving. The runtime result therefore has to stand on its own V2-C/V2-D
evidence, not on artifact-byte similarity.
