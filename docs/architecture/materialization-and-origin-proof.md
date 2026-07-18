# Materialization And Origin Proof

An optimized class is irrelevant unless the deployed JVM actually loads it.
JMOA therefore treats packaging and runtime origin as acceptance gates.

## Supported Deployment Shapes

### Exploded Boot

The application JAR is expanded into application, dependency, and loader
layers. Optimized dependency JARs replace the matching `BOOT-INF/lib` entries,
and `JarLauncher` starts the exploded layout. The materialization manifest
records every source and destination hash.

### Spring Boot Fat JAR

The outer JAR is rebuilt with selected `BOOT-INF/lib` entries replaced. Entry
names, replacement counts, dependency counts, runtime-library identity, and
outer artifact SHA-256 are verified. Original JARs may not shadow optimized
ones.

## Static Proof

The materialization scripts record:

- accepted input artifact SHA-256;
- replacement JAR input and output hashes;
- expected and observed `BOOT-INF/lib` entry names;
- runtime-library presence and identity;
- duplicate, missing, and unexpected replacement counts;
- launch mode and image/artifact identity.

`prove-fat-jar-materialization.ps1` and
`prove-runtime-materialization.ps1` implement the reusable proof boundary.

## Live Proof

`runtime-screen-pair.ps1` captures the process command line, mapped JSA files,
container identity, runtime artifact hash, and policy-specific proof. CDS-enabled
runs record archive SHA-256. Stock-base-CDS pairs additionally require identical
default archive path, bytes, and device/inode between arms. No-CDS runs reject
any mapped JSA. Application-CDS runs require the registered artifact/archive
pair.

Class-load logging may be used in a separate diagnostic run to sample loaded
class origins. It is excluded from official memory pairs because log generation
can perturb cgroup and file-backed accounting.

## Failure Semantics

Missing replacements, stale archives, policy mismatch, unexpected JSA mappings,
runtime-library mismatch, or verifier/linkage errors stop the workflow before a
performance claim. A healthy container alone is not origin proof.
