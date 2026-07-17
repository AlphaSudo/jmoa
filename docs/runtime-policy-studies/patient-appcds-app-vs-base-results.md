# Patient Dynamic AppCDS Economics

Primary comparison: `APP - BASE` on the fixed V1 artifact under `SINGLE_REPLICA`.

| Profile/archive | Order | PSS | Private_Dirty | memory.current | Verdict |
| --- | --- | ---: | ---: | ---: | --- |
| STARTUP A | BASE->APP | +24,049 KB | +4,204 KB | +119,775,232 B | regression |
| STARTUP B | APP->BASE | +24,901 KB | +5,096 KB | +120,520,704 B | regression |
| STARTUP median | balanced | +24,475 KB | +4,650 KB | +120,147,968 B | rejected |
| REPRESENTATIVE A | BASE->APP | +40,207 KB | +20,376 KB | +136,208,384 B | regression |
| REPRESENTATIVE B | APP->BASE | +32,655 KB | +12,744 KB | +128,376,832 B | regression |
| REPRESENTATIVE median | balanced | +36,431 KB | +16,560 KB | +132,292,608 B | rejected |

Both profiles were structurally stable and completed 600 requests per arm with zero errors. The explanatory V1 `BASE - OFF` pair was approximately PSS-neutral (+256 KB), changed Private_Dirty by -2,864 KB, and changed `memory.current` by +4,886,528 bytes. The large marginal penalty therefore belongs to the Patient application archive under this topology, not to CDS as a universal mechanism.
