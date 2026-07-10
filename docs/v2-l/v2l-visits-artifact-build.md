# V2-L Visits Artifact Build

The public source revision was built with:

```powershell
./mvnw.cmd -q -pl spring-petclinic-visits-service -am -DskipTests package
```

Baseline output:

```text
Spring Boot fat JAR bytes: 92,543,671
Spring Boot fat JAR SHA-256: 80E92024541A3F681732E3EC8E1A0D825514DFA89AF69121A0028A1BCC8126F5
BOOT-INF/lib JARs: 161
BOOT-INF/lib compressed bytes: 92,299,443
```

The executable JAR was extracted into Spring Boot layers. V2-L retained the same
application classes and loader layer for both variants; only the candidate
dependency layer was replaced with raw-reduced outputs.

Comparison:

```text
baseline: public visits-service build
candidate: the same build with raw-reduced dependency JARs
```

This is not a visits full-P2 build and is not documented as one.
