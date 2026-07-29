# PetClinic Runtime Reconciliation

| Dataset | Environment | Median PSS | Qualification |
| --- | --- | ---: | --- |
| Phase 33M | historical exploded Boot | -4,758 KB | retained historical confirmation |
| historical replay | Windows/WSL/Podman | +8,647 KB | runtime drift |
| fresh adoption screen | Windows/WSL/Podman | -3,615 KB | single screen only |
| audited Windows campaign | Windows/WSL/Podman | n/a | B0 noise failed twice; product pairs not admitted |
| dedicated Linux campaign | Debian Hyper-V | pending | authoritative only if controls, V2-C, and V2-D pass |

Older evidence is not erased or arithmetically combined. The dedicated Linux
number becomes authoritative only if B0 and V2 controls pass, all six product
arms are valid, V2-C confirms, and V2-D completes.
