# Synthetic Class Safety Model

V2-A treats generated classes as runtime contracts until proven otherwise.

Default rule:

```text
UNKNOWN is the default safety category.
```

The inventory scanner may identify a class as Spring AOT, CGLIB, JDK proxy,
Spring Data generated, ByteBuddy, Hibernate proxy, or lambda-related, but the
initial risk level remains `UNKNOWN`.

## Unsafe Until Proven

The following areas are not optimized in V2-A1:

- CGLIB proxy subclasses,
- JDK dynamic proxies,
- ByteBuddy-generated classes,
- Hibernate proxies,
- bridge methods required by generic dispatch,
- synthetic accessors involved in private/nestmate access,
- Spring AOT classes whose bean registration semantics have not been proven
  stable after transformation.

## Allowed V2-A1 Action

The only allowed action is reporting:

```text
scan -> classify -> report
```

No class files are modified by generated-class inventory mode.

