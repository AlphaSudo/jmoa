# V2 Claim Matrix

| Claim | Status | Allowed Wording |
| --- | --- | --- |
| JMOA V2 includes a raw LVT/LVTT reducer | `ALLOWED` | "JMOA V2 adds an opt-in raw dependency reducer for local-variable debug metadata." |
| V2 raw reducer improves customers over full-P2 | `ALLOWED_NARROW` | "Confirmed on customers-service full-P2 vs full-P2+raw under exploded Boot no-CDS." |
| V2 raw reducer improves visits baseline | `ALLOWED_NARROW` | "Confirmed on visits-service baseline vs baseline+raw under exploded Boot no-CDS." |
| V2 raw reducer improves Doctor D2 | `ALLOWED_PRIVATE_NARROW` | "Confirmed on private Doctor D2/D2R under fat-JAR/CDS with fresh variant-specific CDS." |
| Final V2 product substantially beats customers baseline | `BLOCKED` | Not allowed until direct `B0 -> V2` passes. |
| V2 improves all Spring Boot services | `FORBIDDEN` | Universal claims are not supported. |
| V2 optimizes generated/proxy classes | `FORBIDDEN` | V2 only inventories and classifies generated/proxy families. |
| V2 improves startup | `FORBIDDEN` | Startup wins are not confirmed. |

