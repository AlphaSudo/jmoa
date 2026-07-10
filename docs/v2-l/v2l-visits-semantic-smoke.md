# V2-L Visits Semantic Smoke

Status:

```text
PASSED
```

The baseline and raw-reduced exploded Boot images were started independently
under the same standalone runtime configuration.

Both variants passed:

```text
health: UP
visit reads: HTTP 200
aggregate pet-visit read: HTTP 200
visit creation: HTTP 201
post-write read: HTTP 200
VerifyError: 0
ClassFormatError: 0
NoSuchMethodError: 0
NoClassDefFoundError: 0
ExceptionInInitializerError: 0
```

The confirmation workload repeated six representative requests for three
rounds. All six measured runs completed 18 requests with zero errors.

`/actuator/info` returned 404 in the self-contained default profile. It was not
part of the visits business/health gate and was excluded from the measured
workload rather than hidden as an error.

No startup claim is made from the smoke or later measurement.
