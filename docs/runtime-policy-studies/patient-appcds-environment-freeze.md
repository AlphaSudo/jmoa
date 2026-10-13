# Patient AppCDS Environment Freeze

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

- Status: `FROZEN`
- Release baseline: `v2.0.0`
- Topology: `SINGLE_REPLICA`
- Launch mode: `SPRING_BOOT_FAT_JAR`
- Runtime: `openjdk version "26.0.1" 2026-04-21 | OpenJDK Runtime Environment Temurin-26.0.1+8 (build 26.0.1+8) | OpenJDK 64-Bit Server VM Temurin-26.0.1+8 (build 26.0.1+8, mixed mode, sharing)`
- Podman/cgroup: `5.7.1 / v2`
- Workload: `600 requests`, zero errors required
- Cache policy: `DROP_CACHES_BEFORE_EACH_VARIANT`
- AOT cache and runtime javaagent: forbidden

Raw paths, private configuration, archives, and run captures remain uncommitted.
