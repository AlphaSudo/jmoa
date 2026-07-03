# V2-E Failure Handling

V2-E must fail loudly. A reducer failure is not a partial success.

## Failure Rules

```text
unsafe flag:
  fail build and emit reducer-failure-report

classfile parse failure:
  fail build and emit reducer-failure-report

jar write failure:
  delete partial reduced jar when possible
  fail build and emit reducer-failure-report

attribute preservation failure:
  fail build and emit reducer-failure-report
```

## Failure Outputs

```text
reducer-failure-report.json
reducer-failure-report.md
```

## Promotion Rule

Reduced artifacts are not promotable until:

```text
class files are readable
LineNumberTable is preserved
StackMapTable is preserved
annotations are preserved
Signature is preserved
BootstrapMethods is preserved
semantic smoke passes
V2-C validates any performance evidence
V2-D explains any memory movement
```

For the first reducer, classes with `BootstrapMethods` are skipped before
rewrite. That avoids accidental invokedynamic metadata changes from classfile
round-tripping.
