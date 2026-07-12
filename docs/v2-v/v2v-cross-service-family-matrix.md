# V2-V Cross-Service Generated-Family Matrix

Families are classified only after matched lifecycle evidence is available.

```text
SINGLE_SERVICE_RUNTIME_RELEVANT
MULTI_SERVICE_RUNTIME_RELEVANT
MULTI_PROTOCOL_RUNTIME_RELEVANT
STATIC_ONLY_IN_MATCHED_CAPTURES
SAFETY_BLOCKED_RUNTIME_RELEVANT
EVIDENCE_INCOMPLETE
```

High relevance does not override safety. CGLIB, JDK proxy, ByteBuddy,
Hibernate proxy, and Spring AOT families remain blocked or report-only until a
separate bounded semantic contract exists.
