# V2-K Doctor Semantic Smoke

Status:

```text
NOT_ATTEMPTED
```

Doctor semantic smoke is blocked until all pre-smoke gates are clean:

```text
runtime inventory clean
runtime unblock gate clean
images present
private config present
database init present
D2R CDS trained or no-CDS diagnostic explicitly selected
runtime materialization proof passed
```

Required smoke checks:

```text
health UP
config loaded
database reachable
representative endpoints return success
0 workload errors
no VerifyError
no ClassFormatError
no NoSuchMethodError
no NoClassDefFoundError
no ExceptionInInitializerError
```

Because smoke has not run, V2-K cannot proceed to Doctor runtime screening.
