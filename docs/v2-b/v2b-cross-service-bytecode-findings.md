# V2-B1.5 Cross-Service Bytecode Findings

Status: completed from PetClinic and Doctor V2-B report-only smokes.

This report compares the two service scans. It is a bytecode footprint analysis,
not an optimizer result and not a memory-win claim.

## Services Compared

| Service | Deployment shape | Classes | Classfile bytes | Largest method |
| --- | --- | ---: | ---: | ---: |
| Spring PetClinic customers-service | EXPLODED_BOOT_APP | 54,326 | 200,152,982 | 57,198 |
| Doctor-service | corrected Spring Boot fat JAR | 59,424 | 219,457,652 | 57,198 |

## Shared Near-64KB Risks

Both services report the same danger-region dependency methods:

| Class | Method | Artifact | Code bytes | Family | Runtime evidence |
| --- | --- | --- | ---: | --- | --- |
| org.bouncycastle.pqc.crypto.hqc.ReedSolomon | `<clinit>()V` | bcprov-jdk18on-1.81.jar | 57,198 | PLAIN | not observed in available PetClinic class-load log; Doctor runtime evidence unavailable |
| com.google.common.net.TldPatterns | `<clinit>()V` | guava-14.0.1.jar | 49,478 | PLAIN | not observed in available PetClinic class-load log; Doctor runtime evidence unavailable |

The risk is real because these methods are close to the JVM `Code` attribute
limit, but existing evidence does not prove they are runtime-hot for the service
workloads.

## Common Large-Class Surface

The top shared bytecode-heavy classes are mostly Hibernate and Spring Data JPA:

| Class | PetClinic artifact | PetClinic bytes | Family |
| --- | --- | ---: | --- |
| org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder | hibernate-core-7.2.0.Final.jar | 370,052 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter | hibernate-core-7.2.0.Final.jar | 361,670 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.hql.internal.SemanticQueryBuilder | hibernate-core-7.2.0.Final.jar | 327,188 | LAMBDA_METAFATORY_SITE |
| org.springframework.data.jpa.repository.query.HqlParser | spring-data-jpa-4.0.1.jar | 301,379 | PLAIN |
| org.hibernate.grammars.hql.HqlParser | hibernate-core-7.2.0.Final.jar | 281,870 | PLAIN |
| org.hibernate.sql.ast.spi.AbstractSqlAstTranslator | hibernate-core-7.2.0.Final.jar | 238,592 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.criteria.spi.HibernateCriteriaBuilderDelegate | hibernate-core-7.2.0.Final.jar | 219,339 | SYNTHETIC_BRIDGE_METHODS |
| org.hibernate.persister.entity.AbstractEntityPersister | hibernate-core-7.2.0.Final.jar | 219,215 | LAMBDA_METAFATORY_SITE |
| org.springframework.data.jpa.repository.query.EqlParser | spring-data-jpa-4.0.1.jar | 173,394 | PLAIN |
| org.springframework.data.jpa.repository.query.JpqlParser | spring-data-jpa-4.0.1.jar | 173,152 | PLAIN |

The version numbers differ between services, but the shape is the same:
Hibernate query/SQL translation and Spring Data parser classes dominate the
large dependency class surface.

## Service-Specific Surface

PetClinic-specific top entries are mainly database-driver related:

| Class | Artifact | Bytes | Family |
| --- | --- | ---: | --- |
| com.mysql.cj.exceptions.MysqlErrorNumbers | mysql-connector-j-9.5.0.jar | 338,077 | PLAIN |
| com.mysql.cj.jdbc.ConnectionImpl | mysql-connector-j-9.5.0.jar | 83,462 | LAMBDA_METAFATORY_SITE |

Doctor-specific top entries are mainly Kotlin, PostgreSQL, Protobuf, and private
AOT output:

| Class | Artifact | Bytes | Family |
| --- | --- | ---: | --- |
| kotlin.collections.ArraysKt___ArraysKt | kotlin-stdlib-2.2.21.jar | 678,485 | LAMBDA_METAFATORY_SITE |
| kotlin.collections.unsigned.UArraysKt___UArraysKt | kotlin-stdlib-2.2.21.jar | 315,464 | LAMBDA_METAFATORY_SITE |
| private.doctor.Application__BeanFactoryRegistrations | private doctor artifact | 118,515 | SPRING_AOT_REGISTRATION |
| org.postgresql.jdbc.PgResultSet | postgresql-42.7.10.jar | 98,453 | PLAIN |
| com.google.protobuf.MessageSchema | protobuf-java-4.32.0.jar | 88,313 | PLAIN |

The private AOT class is sanitized here. The raw local report remains ignored
under `target/`.

## Cross-Service Conclusions

1. V2-B found a shared near-64KB method-risk family in Bouncy Castle and Guava.
2. Those near-limit methods are plain dependency static initializers, not
   generated Spring/JMOA classes.
3. Existing runtime evidence does not prove those near-limit classes load during
   the service workloads.
4. The largest loaded/likely-runtime surface to correlate next is Hibernate and
   Spring Data JPA, especially classes tagged `LAMBDA_METAFATORY_SITE`.
5. Attribute-heavy classes show a credible future reducer target:
   LocalVariableTable/LocalVariableTypeTable savings estimates. This must remain
   opt-in and report-first.
6. Do not implement large-method splitting yet. The first mutation candidate, if
   any, should be a conservative debug-attribute reducer behind explicit flags.

## Next Step

Open V2-B2 runtime correlation:

```text
inputs:
  class-load logs
  class histogram
  NMT/metaspace evidence
  classfile-size-profile.json
  generated-family labels

questions:
  which largest classes actually load?
  which near-64KB classes load?
  which generated-like large classes survive workload?
  does bytecode-heavy surface correlate with startup or memory?
```

Mutation remains blocked until runtime correlation and reducer safety gates are
complete.

