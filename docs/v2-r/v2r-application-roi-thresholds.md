# V2-R Application ROI Thresholds

V2-R formalizes the V2-Q lesson:

```text
480 bytes / 4 classes is too small to justify application-class runtime promotion.
```

## Buckets

| Bucket | Threshold | Decision |
| --- | --- | --- |
| `LOW_ROI` | `< 32 KB` and `< 50` reduced application classes | `APPLICATION_LOW_ROI_ARTIFACT_ONLY` |
| `MEDIUM_ROI` | `>= 32 KB` or `>= 50` reduced application classes | `APPLICATION_SCREEN_REQUIRED` after semantic smoke |
| `HIGH_ROI` | `>= 256 KB`, `>= 500` generated/application classes, or runtime-relevant generated family | `CANDIDATE_FOR_PROTOTYPE` planning only |

These thresholds are admission gates, not win claims. A candidate that crosses a
threshold still needs the normal V2-C and V2-D path.
