# V2-S Static Family Census

The scanner reconciled existing local V2-A static inventories without changing their artifacts.

| Service | Largest discovery families | Notes |
|---|---|---|
| PetClinic customers | synthetic/bridge: 40,931,868 B; lambda: 39,086,800 B; Spring Data: 1,379,849 B | Full static inventory plus diagnostic capture available. |
| Doctor corrected D2 | synthetic/bridge: 49,514,163 B; lambda: 44,126,922 B; Spring Data: 1,387,124 B; Spring AOT BeanDefinitions: 1,057,731 B | Static inventory available. |
| PetClinic visits | packaged application inventory only | A matching full generated-family static inventory was not recovered; this is explicitly incomplete. |

Static classfile bytes are not runtime-memory savings predictions.
