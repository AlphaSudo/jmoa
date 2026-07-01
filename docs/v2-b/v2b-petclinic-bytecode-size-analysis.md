# V2-B1.5 PetClinic Bytecode Size Analysis

Status: analysis completed from existing V2-B report-only smoke.

This is not a bytecode mutation result and not a new memory-win claim. It turns
the PetClinic V2-B profiler output into a reviewable bytecode-footprint finding.

## Scope

```text
service: Spring PetClinic customers-service
deployment shape: EXPLODED_BOOT_APP / JarLauncher / extracted layers
mutation enabled: false
report-only profiler: true
```

## Summary

```text
classes scanned:           54,326
classfile bytes scanned:   200,152,982
method bytecode bytes:      16,988,241
largest method code bytes:      57,198
near-64KB danger methods:            3
```

The largest method found is not a Spring or JMOA-generated method. It is:

```text
org.bouncycastle.pqc.crypto.hqc.ReedSolomon::<clinit>()V
artifact: bcprov-jdk18on-1.81.jar
code length: 57,198 bytes
threshold: DANGER
generated family: PLAIN
runtime loaded in available class-load proof: not observed
```

The second danger-region method is:

```text
com.google.common.net.TldPatterns::<clinit>()V
artifact: guava-14.0.1.jar
code length: 49,478 bytes
threshold: DANGER
generated family: PLAIN
runtime loaded in available class-load proof: not observed
```

The Bouncy Castle entry appears twice in the scan output because the same class
is encountered through duplicate artifact roots in the smoke input. Treat it as
one dependency-family risk unless a future classpath-origin pass proves two
runtime-visible copies.

## Family Footprint

| Family | Classes | Classfile bytes | Method bytes | Debug bytes | Largest method |
| --- | ---: | ---: | ---: | ---: | ---: |
| PLAIN | 42,174 | 118,527,605 | 11,064,871 | 12,477,940 | 57,198 |
| SYNTHETIC_BRIDGE_METHODS | 8,393 | 40,931,868 | 2,972,070 | 5,307,517 | 13,313 |
| LAMBDA_METAFATORY_SITE | 3,446 | 39,086,800 | 2,867,692 | 4,843,374 | 6,057 |
| SPRING_DATA_GENERATED | 290 | 1,379,849 | 69,182 | 128,718 | 754 |
| BYTEBUDDY | 21 | 222,724 | 14,303 | 15,322 | 415 |
| HIBERNATE_PROXY | 2 | 4,136 | 123 | 360 | 32 |

The generated-like footprint is meaningful, especially lambda-site and bridge
families, but the near-64KB methods are plain dependency static initializers.
That makes them profiler findings, not V2-A generated-class mutation targets.

## Top Artifacts By Classfile Bytes

| Artifact | Classes | Classfile bytes | Method bytes | Largest method |
| --- | ---: | ---: | ---: | ---: |
| hibernate-core-7.2.0.Final.jar | 8,009 | 36,550,865 | 2,201,522 | 2,692 |
| byte-buddy-1.17.8.jar | 5,966 | 21,533,021 | 1,193,241 | 5,208 |
| bcprov-jdk18on-1.81.jar | 5,994 | 12,853,894 | 2,749,794 | 57,198 |
| prometheus-metrics-exposition-formats-1.4.3.jar | 809 | 6,674,366 | 495,101 | 2,892 |
| tomcat-embed-core-11.0.15.jar | 1,464 | 6,244,902 | 775,875 | 13,313 |
| mysql-connector-j-9.5.0.jar | 1,066 | 6,232,375 | 652,955 | 10,737 |
| spring-data-jpa-4.0.1.jar | 1,064 | 6,093,380 | 356,349 | 3,408 |
| spring-web-7.0.2.jar | 1,318 | 4,876,190 | 265,781 | 1,539 |
| guava-14.0.1.jar | 1,594 | 4,802,058 | 316,738 | 49,478 |
| jackson-databind-3.0.3.jar | 879 | 4,620,675 | 321,971 | 1,020 |

## Top Largest Classes

| Class | Artifact | Bytes | Method bytes | Largest method | Family |
| --- | --- | ---: | ---: | ---: | --- |
| org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder | hibernate-core-7.2.0.Final.jar | 370,052 | 24,985 | 458 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter | hibernate-core-7.2.0.Final.jar | 361,670 | 41,018 | 2,073 | LAMBDA_METAFATORY_SITE |
| com.mysql.cj.exceptions.MysqlErrorNumbers | mysql-connector-j-9.5.0.jar | 338,077 | 10,782 | 10,737 | PLAIN |
| org.hibernate.query.hql.internal.SemanticQueryBuilder | hibernate-core-7.2.0.Final.jar | 327,188 | 31,723 | 792 | LAMBDA_METAFATORY_SITE |
| org.springframework.data.jpa.repository.query.HqlParser | spring-data-jpa-4.0.1.jar | 301,379 | 70,312 | 1,865 | PLAIN |
| org.hibernate.grammars.hql.HqlParser | hibernate-core-7.2.0.Final.jar | 281,870 | 70,560 | 2,380 | PLAIN |
| org.hibernate.sql.ast.spi.AbstractSqlAstTranslator | hibernate-core-7.2.0.Final.jar | 238,592 | 42,416 | 1,394 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.criteria.spi.HibernateCriteriaBuilderDelegate | hibernate-core-7.2.0.Final.jar | 219,339 | 9,109 | 19 | SYNTHETIC_BRIDGE_METHODS |
| org.hibernate.persister.entity.AbstractEntityPersister | hibernate-core-7.2.0.Final.jar | 219,215 | 21,425 | 2,196 | LAMBDA_METAFATORY_SITE |
| org.springframework.data.jpa.repository.query.EqlParser | spring-data-jpa-4.0.1.jar | 173,394 | 41,681 | 3,408 | PLAIN |

## Top Constant-Pool Risks

| Class | Artifact | CP entries | UTF8 entries | Invokedynamic constants | Family |
| --- | --- | ---: | ---: | ---: | --- |
| com.mysql.cj.exceptions.MysqlErrorNumbers | mysql-connector-j-9.5.0.jar | 12,625 | 6,456 | 0 | PLAIN |
| com.google.common.net.TldPatterns | guava-14.0.1.jar | 12,539 | 6,274 | 0 | PLAIN |
| org.bouncycastle.pqc.legacy.crypto.qtesla.QTesla3p$QTesla3PPolynomial | bcprov-jdk18on-1.81.jar | 8,289 | 49 | 0 | PLAIN |
| org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter | hibernate-core-7.2.0.Final.jar | 7,818 | 3,976 | 170 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.hql.internal.SemanticQueryBuilder | hibernate-core-7.2.0.Final.jar | 7,375 | 3,968 | 86 | LAMBDA_METAFATORY_SITE |

The constant-pool risks split into two shapes:

- static tables/constants in MySQL, Guava, and Bouncy Castle,
- lambda/bootstrap dense Hibernate translator and query classes.

## Top Attribute Risks

| Class | Artifact | Attribute bytes | Debug bytes | StackMap bytes | LocalVariable bytes | Family |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| org.springframework.data.jpa.repository.query.HqlParser | spring-data-jpa-4.0.1.jar | 148,289 | 46,958 | 8,725 | 11,330 | PLAIN |
| org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder | hibernate-core-7.2.0.Final.jar | 147,664 | 78,080 | 2,326 | 57,128 | LAMBDA_METAFATORY_SITE |
| org.hibernate.grammars.hql.HqlParser | hibernate-core-7.2.0.Final.jar | 146,104 | 45,920 | 8,454 | 11,212 | PLAIN |
| org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter | hibernate-core-7.2.0.Final.jar | 132,837 | 63,378 | 13,716 | 41,570 | LAMBDA_METAFATORY_SITE |
| org.hibernate.sql.ast.spi.AbstractSqlAstTranslator | hibernate-core-7.2.0.Final.jar | 117,712 | 55,154 | 10,068 | 31,986 | LAMBDA_METAFATORY_SITE |

This supports the planned V2-B reducer order: first produce debug-attribute
savings estimates, then consider an explicit opt-in LocalVariableTable reducer.
Do not strip LineNumberTable or StackMapTable by default.

## Findings

1. The largest near-64KB method is a dependency static initializer, not a
   generated Spring/JMOA class.
2. The available PetClinic class-load proof did not observe the near-64KB
   Bouncy Castle or Guava classes loading. V2-B should not claim runtime memory
   causality from this report alone.
3. Hibernate and Spring Data JPA dominate the largest class and attribute lists.
4. V2-A family labels are useful: large Hibernate classes often carry
   `LAMBDA_METAFATORY_SITE`, while bridge-heavy classes are isolated from plain
   parser/static-table shapes.
5. The next safe work is runtime correlation and debug-strip savings analysis,
   not bytecode mutation.

