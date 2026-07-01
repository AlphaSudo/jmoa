# V2-B1.5 Doctor Bytecode Size Analysis

Status: analysis completed from existing V2-B report-only smoke.

This is a sanitized private-service analysis. It does not publish private source
code and does not claim a V2-B memory win.

## Scope

```text
service: doctor-service, private Spring Boot service
deployment shape: corrected Spring Boot fat JAR
mutation enabled: false
report-only profiler: true
```

## Summary

```text
classes scanned:           59,424
classfile bytes scanned:   219,457,652
method bytecode bytes:      17,850,196
largest method code bytes:      57,198
near-64KB danger methods:            3
```

The largest method is the same dependency method found in PetClinic:

```text
org.bouncycastle.pqc.crypto.hqc.ReedSolomon::<clinit>()V
artifact: bcprov-jdk18on-1.81.jar
code length: 57,198 bytes
threshold: DANGER
generated family: PLAIN
runtime loaded from existing Doctor class-load evidence: unavailable
```

The second danger-region method is also shared:

```text
com.google.common.net.TldPatterns::<clinit>()V
artifact: guava-14.0.1.jar
code length: 49,478 bytes
threshold: DANGER
generated family: PLAIN
runtime loaded from existing Doctor class-load evidence: unavailable
```

## Family Footprint

| Family | Classes | Classfile bytes | Method bytes | Debug bytes | Largest method |
| --- | ---: | ---: | ---: | ---: | ---: |
| PLAIN | 44,955 | 122,932,001 | 10,871,966 | 13,385,686 | 57,198 |
| SYNTHETIC_BRIDGE_METHODS | 9,816 | 49,514,163 | 3,689,293 | 6,574,311 | 13,313 |
| LAMBDA_METAFATORY_SITE | 4,001 | 44,126,922 | 3,142,094 | 5,385,568 | 6,057 |
| SPRING_DATA_GENERATED | 293 | 1,387,124 | 69,362 | 129,194 | 754 |
| SPRING_AOT_BEAN_DEFINITIONS | 322 | 1,057,731 | 39,941 | 34,048 | 240 |
| BYTEBUDDY | 21 | 222,724 | 14,303 | 15,322 | 415 |
| SPRING_AOT_REGISTRATION | 2 | 122,767 | 6,135 | 2,576 | 5,960 |
| SPRING_CGLIB | 12 | 90,084 | 16,979 | 96 | 1,768 |
| HIBERNATE_PROXY | 2 | 4,136 | 123 | 360 | 32 |

Doctor has extra Kotlin, Spring Security, PostgreSQL, and AOT footprint compared
with PetClinic. The AOT footprint is visible but is not the near-64KB risk.

## Top Artifacts By Classfile Bytes

| Artifact | Classes | Classfile bytes | Method bytes | Largest method |
| --- | ---: | ---: | ---: | ---: |
| hibernate-core-7.2.7.Final.jar | 8,010 | 36,568,676 | 2,203,240 | 2,692 |
| byte-buddy-1.17.8.jar | 5,966 | 21,533,021 | 1,193,241 | 5,208 |
| bcprov-jdk18on-1.81.jar | 5,994 | 12,853,894 | 2,749,794 | 57,198 |
| prometheus-metrics-exposition-formats-1.4.3.jar | 809 | 6,674,366 | 495,101 | 2,892 |
| tomcat-embed-core-11.0.20.jar | 1,467 | 6,266,205 | 779,161 | 13,313 |
| spring-data-jpa-4.0.4.jar | 1,064 | 6,110,063 | 356,811 | 3,408 |
| spring-web-7.0.6.jar | 1,328 | 4,926,594 | 269,186 | 1,539 |
| jackson-databind-3.1.0.jar | 905 | 4,814,805 | 336,836 | 1,020 |
| guava-14.0.1.jar | 1,594 | 4,802,058 | 316,738 | 49,478 |
| protobuf-java-4.32.0.jar | 756 | 4,778,210 | 450,018 | 2,892 |

## Top Largest Classes

| Class | Artifact | Bytes | Method bytes | Largest method | Family |
| --- | --- | ---: | ---: | ---: | --- |
| kotlin.collections.ArraysKt___ArraysKt | kotlin-stdlib-2.2.21.jar | 678,485 | 121,147 | 191 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder | hibernate-core-7.2.7.Final.jar | 370,052 | 24,985 | 458 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter | hibernate-core-7.2.7.Final.jar | 362,444 | 41,219 | 2,073 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.hql.internal.SemanticQueryBuilder | hibernate-core-7.2.7.Final.jar | 327,155 | 31,728 | 792 | LAMBDA_METAFATORY_SITE |
| kotlin.collections.unsigned.UArraysKt___UArraysKt | kotlin-stdlib-2.2.21.jar | 315,464 | 48,322 | 171 | LAMBDA_METAFATORY_SITE |
| org.springframework.data.jpa.repository.query.HqlParser | spring-data-jpa-4.0.4.jar | 301,379 | 70,312 | 1,865 | PLAIN |
| org.hibernate.grammars.hql.HqlParser | hibernate-core-7.2.7.Final.jar | 281,870 | 70,560 | 2,380 | PLAIN |
| org.hibernate.sql.ast.spi.AbstractSqlAstTranslator | hibernate-core-7.2.7.Final.jar | 238,963 | 42,425 | 1,394 | LAMBDA_METAFATORY_SITE |
| org.hibernate.persister.entity.AbstractEntityPersister | hibernate-core-7.2.7.Final.jar | 219,409 | 21,442 | 2,196 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.criteria.spi.HibernateCriteriaBuilderDelegate | hibernate-core-7.2.7.Final.jar | 219,339 | 9,109 | 19 | SYNTHETIC_BRIDGE_METHODS |

## Top Constant-Pool Risks

| Class | Artifact | CP entries | UTF8 entries | Invokedynamic constants | Family |
| --- | --- | ---: | ---: | ---: | --- |
| com.google.common.net.TldPatterns | guava-14.0.1.jar | 12,539 | 6,274 | 0 | PLAIN |
| org.bouncycastle.pqc.legacy.crypto.qtesla.QTesla3p$QTesla3PPolynomial | bcprov-jdk18on-1.81.jar | 8,289 | 49 | 0 | PLAIN |
| org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter | hibernate-core-7.2.7.Final.jar | 7,826 | 3,981 | 170 | LAMBDA_METAFATORY_SITE |
| org.hibernate.query.hql.internal.SemanticQueryBuilder | hibernate-core-7.2.7.Final.jar | 7,376 | 3,968 | 86 | LAMBDA_METAFATORY_SITE |
| org.hibernate.persister.entity.AbstractEntityPersister | hibernate-core-7.2.7.Final.jar | 5,715 | 2,874 | 48 | LAMBDA_METAFATORY_SITE |

## Top Attribute Risks

| Class | Artifact | Attribute bytes | Debug bytes | StackMap bytes | LocalVariable bytes | Family |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| kotlin.collections.ArraysKt___ArraysKt | kotlin-stdlib-2.2.21.jar | 398,297 | 152,054 | 36,948 | 90,690 | LAMBDA_METAFATORY_SITE |
| kotlin.collections.unsigned.UArraysKt___UArraysKt | kotlin-stdlib-2.2.21.jar | 163,815 | 59,405 | 15,592 | 34,034 | LAMBDA_METAFATORY_SITE |
| org.springframework.data.jpa.repository.query.HqlParser | spring-data-jpa-4.0.4.jar | 148,289 | 46,958 | 8,725 | 11,330 | PLAIN |
| org.hibernate.query.sqm.internal.SqmCriteriaNodeBuilder | hibernate-core-7.2.7.Final.jar | 147,664 | 78,080 | 2,326 | 57,128 | LAMBDA_METAFATORY_SITE |
| org.hibernate.grammars.hql.HqlParser | hibernate-core-7.2.7.Final.jar | 146,104 | 45,920 | 8,454 | 11,212 | PLAIN |

## Findings

1. Doctor shares the same near-64KB dependency risks as PetClinic.
2. Doctor adds a large Kotlin standard-library footprint and visible AOT helper
   footprint.
3. The private service AOT registration class is large enough to inspect in
   future V2-A/V2-B correlation, but it is not near the JVM method-code limit.
4. Existing Doctor evidence for this analysis is static profiler output. Runtime
   class-load correlation should be collected before any causality claim.
5. The right next step is cross-service runtime correlation, not mutation.

