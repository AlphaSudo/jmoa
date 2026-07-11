# V2-N Doctor Canonical Ingestion Proof

The V2-N analyze goal was run against a temporary bundle of canonical V2-K
final reports, not the normalized V2-N input fixture.

Selected reports:

```text
Doctor confirmation
Doctor V2-C validation
Doctor V2-D attribution
Doctor semantic smoke result
Doctor materialization proof result
```

Result:

```text
decision: RECOMMEND_CONFIRMED_POLICY
scope: PRIVATE
registry match: true
runtime policy promotion allowed: true
matched protocol: doctor-corrected-d2r-raw-cds
```

The loader prefers a `-result` evidence file over the older planning record
when both are present. This prevents a superseded `NOT_ATTEMPTED` Doctor smoke
plan from hiding the final passing semantic smoke.
