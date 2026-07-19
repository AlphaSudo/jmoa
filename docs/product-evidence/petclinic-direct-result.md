# PetClinic Direct Product Result

Comparison: clean no-JMOA `B0` to final JMOA V2.

| Protocol | PSS | Private_Dirty | memory.current | Workload errors | Verdict |
| --- | ---: | ---: | ---: | ---: | --- |
| Exploded Boot, `NO_CDS_LOW_DIRTY` | +5,446 KB | +5,580 KB | +3,059,712 B | 0 | Screen failed |

The baseline was rebuilt from the frozen public service source with zero JMOA
entries. Both arms used exploded Boot / `JarLauncher`, `-Xshare:off`, no runtime
javaagent, `MALLOC_ARENA_MAX=1`, the same workload, and controlled page-cache
reset.

The candidate loaded 154 fewer classes and reduced anonymous writable PSS by
1,504 KB, but heap PSS increased by 7,020 KB while heap used and histogram bytes
were nearly flat. The result is a page-touch regression, so the service stopped
at the screen and did not enter V2-C/V2-D confirmation.

This does not erase the historical public full-P2 or final V1-to-V2 wins. It
means those wins do not transfer to the clean no-JMOA-to-final-V2 buyer
comparison measured here.
