# V2-M V2-L Recommendation Proof

The analyze mode was run against the actual local V2-L raw reducer, safety,
semantic smoke, runtime screen, V2-C, and V2-D reports.

Result:

```text
decision: RECOMMEND_CONFIRMED
confirmation scope: PUBLIC
protocol match: true
runtime promotion allowed: true
```

Normalized identity:

```text
requested service: Spring PetClinic visits-service
confirmed service: spring-petclinic-visits-service
launch mode: EXPLODED_BOOT_APP
runtime policy: NO_CDS_LOW_DIRTY
```

The service labels match after punctuation/spacing normalization. Launch mode
and runtime policy match exactly.

Evidence gates:

```text
artifact bytes removed: 3,532,027
classes reduced and audited: 29,701
failed audits: 0
semantic smoke: passed
V2-C: CONFIRMED_WIN
V2-D: present
```

This recommendation does not transfer to a different service or runtime
protocol.
