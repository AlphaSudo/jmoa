# V1 Runtime-Cost Census

All values are direct within-block `V1 - B0` medians from the sealed six-order campaigns.

| Service | PSS KB | Heap PSS KB | Anonymous RW PSS KB | Loaded classes | Class loaders | NMT Class KB | NMT metadata KB | NMT Code KB | JMOA objects / bytes | MethodHandle bytes | LambdaForm bytes |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| doctor-service | 654 | -574 | 522 | -112.5 | 17 | -264.5 | -96 | -88.5 | 393 / 6288 | 4416 | 2744 |
| patient-service | 910 | 104 | 94 | -8 | -2 | -27 | 0 | 48.5 | 6 / 96 | -1088 | -708 |
| spring-petclinic-customers-service | 2516 | 1194 | 242 | -153 | 25 | -111 | 64 | 214 | 297 / 4752 | -24560 | 2088 |

Code-cache **used** bytes and exact class-load names were not captured in the claim runs; NMT Code committed and live histogram classes are reported instead.
