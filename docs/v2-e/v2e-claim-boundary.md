# V2-E Claim Boundary

V2-E is confirmed as an incremental runtime reducer for one public service and one documented runtime protocol.

## Confirmed

```text
Spring PetClinic customers-service
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS
no AppCDS
no Leyden
no runtime javaagent
full P2 vs full P2 + V2-E reducer
```

Confirmed median deltas:

```text
PSS: -1,624 KB
Private_Dirty: -1,636 KB
memory.current: -12,255,232 bytes
paired wins: 2/3
workload errors: 0
```

## Not Confirmed

```text
startup win
fat-JAR win
CDS/AppCDS win
cross-service runtime generalization
all-debug-metadata stripping safety
LineNumberTable stripping safety
StackMapTable stripping safety
annotation stripping safety
Signature stripping safety
BootstrapMethods rewriting or stripping
```

## Explanation Boundary

V2-D attribution does not explain the PetClinic V2-E result as retained-object reduction, class-count reduction, or heap-used reduction. The confirmed movement was primarily NMT-visible / anonymous_rw / metaspace related under the documented no-CDS exploded-Boot protocol.

