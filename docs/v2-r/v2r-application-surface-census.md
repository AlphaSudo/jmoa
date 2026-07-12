# V2-R Application Surface Census

V2-R separates three universes that are easy to confuse:

```text
application packaged classes
generated/synthetic-like class families
dependency classfile surface
```

## Census

| Target | Evidence | Classes / Records | Byte Signal | Status |
| --- | --- | ---: | ---: | --- |
| PetClinic visits application layer | V2-Q application reducer | 7 scanned, 4 reduced | 480 bytes removed | Low ROI |
| PetClinic customers dependency/generated families | V2-B analysis | 54,326 static classes | 200,152,982 classfile bytes | Dependency/global context only |
| Doctor D2 dependency/generated families | V2-B analysis | 59,424 static classes | 219,457,652 classfile bytes | Private dependency/global context only |

The only committed public application-layer mutation evidence is the V2-Q
visits-service application result:

```text
ordinary application classes reduced: 4
raw byte-preservation failures: 0
removed bytes: 480
semantic smoke: passed
3-pair confirmation: failed
```

Generated-family static context remains useful for discovery. For example,
V2-B shows Spring Data generated classes around `1.38 MB` in both PetClinic and
Doctor dependency surfaces. That is a discovery signal, not a mutation
admission.
