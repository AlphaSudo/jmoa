# V2-S Generated-Family Safety Matrix

| Family | Default state | Why |
|---|---|---|
| JDK proxy, Spring CGLIB, ByteBuddy, Hibernate proxy | `GENERATED_MUTATION_BLOCKED` | Proxy dispatch, class identity, class-loader, reflection, and framework contracts. |
| Spring AOT BeanDefinitions and registrations | `GENERATED_MUTATION_BLOCKED` | AOT context/registration behavior is framework-semantic. |
| Lambda, Spring Data generated, bridge, compiler helper | `GENERATED_REPORT_ONLY` | Potentially interesting, but no family-specific bounded mutation or semantic proof exists. |

No V2-S report overrides this matrix simply because a family is large or runtime-relevant.
