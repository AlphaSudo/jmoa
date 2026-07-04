# V2-F Reducer Productization Status

Status: implemented as reducer product hardening and second-service artifact smoke.

Completed:

```text
V2-E stale docs updated after v0.7.0
signed JARs skipped by default
multi-release JARs skipped by default
sealed JARs skipped by default
module-info.class preserved
BootstrapMethods classes still skipped in mutation mode
LineNumberTable preserved
StackMapTable preserved
annotations preserved
Signature preserved
BootstrapMethods preserved
reducer manifest emitted with input/output hashes
PetClinic hardened artifact smoke passed
Doctor corrected D2 dependency artifact smoke passed
```

Not claimed:

```text
new runtime memory claim
Doctor runtime claim
fat-JAR runtime claim
startup claim
new reducer type
```

The most important V2-F change is conservative product behavior: the reducer now skips JAR surfaces where mutation could invalidate signatures, version-specific class behavior, or package sealing.

