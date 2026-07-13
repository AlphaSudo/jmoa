# V2-W Target Artifacts

| Target | Artifact SHA-256 | Launch mode | Policy | Engine |
| --- | --- | --- | --- | --- |
| customers | `007F1796B83FCC2217A57A6975EF5CAFD7494A5EF050A7F5261C088B63C6CC2F` | `EXPLODED_BOOT_APP` | `NO_CDS_LOW_DIRTY` | `raw` |
| visits | `5BCEF666130DDE532F76E017F504E2ED3758DBB2454AC74E41C3C2EC740CA4FF` | `EXPLODED_BOOT_APP` | `NO_CDS_LOW_DIRTY` | `raw` |
| doctor-d2r | `9D00877C0AF90E02B0C8D812F8BC659297CA67D6A939016CAF96D3D5CED79742` | `SPRING_BOOT_FAT_JAR` | `CDS` | `raw` |

Customers and Visits inventories were generated from canonical JARs materialized
from the exact exploded image layers. Doctor used the exact D2R fat JAR and the
D2R-specific CDS archive, whose mapping was verified during capture.
