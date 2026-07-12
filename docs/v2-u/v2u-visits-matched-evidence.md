# V2-U Visits Matched Evidence

Status: `PARTIAL_INFRASTRUCTURE`

Visits-service has strong raw-reducer runtime evidence from V2-L, but V2-U is a
different question: generated-family static inventory must match generated
lifecycle diagnostic captures for the same artifact.

Current state:

```text
raw reducer runtime confirmation: present from V2-L
matching generated-family static inventory: absent
startup generated-family capture: absent
warmup generated-family capture: absent
workload generated-family capture: absent
```

Verdict:

```text
EVIDENCE_INCOMPLETE
prototypeAdmitted=false
```

V2-U must not combine V2-L memory evidence with another artifact's generated
inventory. A fresh visits generated lifecycle bundle is required.
