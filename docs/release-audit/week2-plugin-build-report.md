# Week 2 Plugin Build Report

Status: PASS for the default public build target.

The release repo default build includes:

- `jmoa-runtime-lib`
- `jmoa-maven-plugin`

Local verification command:

```text
mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean test
```

Notes:

- JDK 22+ is required because the plugin module uses Java release 22.
- Verified locally with Temurin JDK 26 and Maven 3.9.9.
- The copied mode-c launcher source is intentionally outside the default reactor
  until its launcher/process-exit tests are separated from normal unit tests.
