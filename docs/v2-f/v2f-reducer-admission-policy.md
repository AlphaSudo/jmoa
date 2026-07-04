# V2-F Reducer Admission Policy

V2-F does not make the reducer universal. It defines when JMOA may recommend the existing V2-E reducer.

## Recommend V2-E When

```text
target is a release artifact
user explicitly enables reducer mutation
profile is release-low-footprint
only LocalVariableTable / LocalVariableTypeTable stripping is requested
dry-run or V2-B reports show meaningful removable local-variable metadata
signed, multi-release, and sealed jars are skipped or explicitly reviewed
semantic smoke passes for the deployment shape
runtime claims pass V2-C validation and V2-D attribution
```

## Do Not Recommend V2-E When

```text
the artifact is intended for debugging local variable values
LineNumberTable stripping is requested
StackMapTable stripping is requested
annotation stripping is requested
Signature stripping is requested
BootstrapMethods stripping or rewriting is requested
signed / multi-release / sealed jars must be rewritten without review
semantic smoke is unavailable
a runtime claim is desired but V2-C confirmation is unavailable
```

## Current Claim Scope

The only confirmed runtime claim remains PetClinic customers-service under:

```text
EXPLODED_BOOT_APP
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
```

Doctor is currently artifact-smoke only.

