# Doctor Response Semantic Equivalence

- Classification: **SEMANTICALLY_EQUIVALENT_VOLATILE_FIELDS**
- Business responses per arm: **20 / 20**
- Records per response: **6 / 6**
- Schema equal: **True**
- Raw canonical equal: **False**
- Canonical equal after volatile fields: **True**
- Volatile fields: **createdAt, updatedAt**

The B0 and V1 response bodies differ only in the explicitly listed creation and
update timestamps. Record count, field set, ordering-insensitive business
content, and all nonvolatile field values are equal. This pair passes the
semantic response gate; HTTP 200 alone was not used as proof.
