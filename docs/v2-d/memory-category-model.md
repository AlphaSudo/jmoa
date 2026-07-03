# V2-D Memory Category Model

V2-D normalizes memory evidence into categories that can be compared pair by
pair. The goal is not only to report that PSS changed, but to identify whether
the movement came from heap pages, anonymous mappings, class metadata, mapped
files, NMT-visible JVM areas, or object families.

## Canonical Categories

```text
TOTAL_PSS
PRIVATE_DIRTY
CGROUP_MEMORY_CURRENT
HEAP_PSS
HEAP_PRIVATE_DIRTY
HEAP_USED
HEAP_COMMITTED
CLASS_HISTOGRAM_BYTES
ANONYMOUS_RW_PSS
ANONYMOUS_EXECUTABLE_CODE_PSS
NATIVE_LIBRARY_PSS
MAPPED_FILE_PSS
STACK_PSS
NMT_TOTAL_COMMITTED
NMT_JAVA_HEAP_COMMITTED
NMT_METASPACE_COMMITTED
NMT_CLASS_COMMITTED
NMT_CODE_COMMITTED
NMT_ARENA_CHUNK_COMMITTED
NMT_MALLOC
```

## Reconciliation Rules

```text
NMT_VISIBLE:
  NMT total committed explains at least 60 percent of PSS movement.

NMT_INVISIBLE_OR_PARTIAL:
  PSS/Private_Dirty movement is larger than NMT total committed movement.

HEAP_PAGE_TOUCH_GROWTH:
  heap PSS grows while heap used, class histogram bytes, and heap committed stay near flat.

HEAP_PAGE_TOUCH_REDUCTION:
  heap PSS shrinks while heap used and class histogram bytes stay near flat.

RETAINED_OBJECT_GROWTH:
  heap PSS grows with heap used or class histogram bytes.

RETAINED_OBJECT_REDUCTION:
  heap PSS shrinks with heap used or class histogram bytes.
```

## Object Families

```text
byte_arrays
char_arrays
arrays
strings
maps
collections
spring
jackson
hibernate
tomcat
netty
lambda_objects
jmoa_runtime
reflection
method_handles
classloader
proxy_objects
unknown
```

The object-family layer is intentionally coarse. It is designed to distinguish
retained object movement from heap page-touch effects, not to replace a JFR or
async-profiler allocation profile.
