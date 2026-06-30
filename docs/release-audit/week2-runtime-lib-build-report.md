# Week 2 Runtime Library Build Report

Status: PASS as part of the default public build target.

`jmoa-runtime-lib` is built and tested with the default Week 2 command:

```text
mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean test
```

The runtime library is a normal application dependency used by rewritten
bytecode. It is not a runtime javaagent.

Verified locally with Temurin JDK 26 and Maven 3.9.9.
