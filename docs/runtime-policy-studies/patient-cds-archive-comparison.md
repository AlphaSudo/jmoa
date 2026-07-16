# Patient CDS Archive Comparison

The current V1 and V2 training class-load sets are nearly identical after normalizing hidden/generated suffixes:

- V1: `18,944` classes
- V2: `18,942` classes
- shared: `18,942`
- Jaccard similarity: `0.999894`
- V1-only: two ordinary JDK/Jackson classes
- V2-only: none

The V2 archive is about `2.72 MB` smaller, but class-load coverage drift is not a persuasive explanation for the policy regression. Runtime mapping and shared/private memory economics are the stronger observed difference.

Training class-load logs prove observed loads, not guaranteed archive inclusion. The generic comparison tool preserves that boundary.

