# V2-N Runtime Protocol Registry

This registry contains known runtime-policy scopes, not generic deployment
advice. A match requires the exact service, launch mode, runtime policy, and
reducer engine.

| Protocol | Scope | Launch mode | Runtime policy | CDS rule |
| --- | --- | --- | --- | --- |
| PetClinic customers full P2 + raw | Public | `EXPLODED_BOOT_APP` | `NO_CDS_LOW_DIRTY` | CDS/AppCDS/Leyden off; no javaagent |
| PetClinic visits baseline + raw | Public | `EXPLODED_BOOT_APP` | `NO_CDS_LOW_DIRTY` | CDS/AppCDS/Leyden off; no javaagent |
| Doctor corrected D2R + raw | Private | `SPRING_BOOT_FAT_JAR` | `CDS` | Fresh D2R-specific archive with measured mapping proof |

The Doctor archive and artifact SHA-256 values are the audited D2R pair from
the private confirmation. They are fingerprints, not a reusable archive.

V2-H's hardened ASM reducer is intentionally absent: its screen failed, so it
is a blocked runtime-promotion case rather than a registry recommendation.
