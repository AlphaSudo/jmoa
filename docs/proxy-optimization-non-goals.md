# Proxy Optimization Non-Goals

JMOA V2-A does not rewrite runtime proxy classes.

The following families are inventory and attribution targets only:

```text
SPRING_CGLIB
JDK_PROXY
BYTEBUDDY
HIBERNATE_PROXY
```

These classes can participate in:

- method interception,
- transaction and security behavior,
- framework caches,
- proxy identity checks,
- reflection-visible class names,
- serialization contracts,
- classloader-specific lookup behavior.

The safety taxonomy marks these families as `UNSAFE_RUNTIME_SEMANTIC` and
allows only:

```text
REPORT_ONLY
RUNTIME_ORIGIN_VERIFY
```

Forbidden transforms:

```text
DELETE
CONSOLIDATE
REPLACE_WITH_SHARED_ADAPTER
MOVE_TO_AOT
```

This is deliberate. V2-A first makes generated runtime shape visible and
attributable; it does not assume that generated/proxy classes are disposable.
