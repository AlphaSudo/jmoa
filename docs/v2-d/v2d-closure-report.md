# V2-D Closure Report

Status: closed as report-only memory attribution infrastructure.

V2-D is the explanation layer for JMOA evidence. It does not enable V2-A or
V2-B mutation and does not create a new memory claim by itself.

## Implemented

```text
jmoa:attribution Maven goal
category-level memory delta calculation
smaps/NMT reconciliation
heap PSS vs heap used and class-histogram comparison
object-family histogram grouping
class/metaspace attribution
optional V2-A generated-family enrichment
optional V2-B bytecode/runtime-correlation enrichment
causal hypothesis classification
JSON and Markdown reports
historical attribution replay on recovered Phase 33 evidence
```

## Historical Replay Gate

```text
33M PetClinic exploded Boot full P2:
  CONFIRMED_WIN
  primary attribution: HEAP_PAGE_TOUCH_REDUCTION
  supporting attribution: CLASS_COUNT_SAVINGS

33K.7b PetClinic fat-JAR full P2:
  CONFIRMED_REGRESSION
  primary attribution: HEAP_PAGE_TOUCH_GROWTH
  supporting attribution: CLASS_COUNT_SAVINGS, but not enough to prevent regression
```

The replay intentionally does not treat V2-B near-64KB static method risk as a
runtime memory cause. V2-B correlation showed three near-limit methods and zero
loaded near-limit methods in the available PetClinic evidence.

## What V2-D Adds

V2-C could say:

```text
33M is a valid win.
33K.7b is a valid regression.
```

V2-D now says:

```text
33M won mainly because heap PSS/private dirty fell while retained heap stayed flat.
33K.7b regressed mainly because heap PSS/private dirty rose while retained heap stayed flat.
```

That distinction matters because it prevents false explanations such as:

```text
fewer classes automatically means lower memory
heap PSS movement always means retained object movement
NMT alone explains every PSS delta
static near-64KB method risk is automatically runtime memory cost
```

## Remaining V2-A Work

```text
generated-class mutation still disabled
Spring AOT BeanDefinition helper rewrite not implemented
CGLIB/JDK proxy/ByteBuddy/Hibernate proxy rewriting not implemented
no generated-class memory win claim
```

## Remaining V2-B Work

```text
bytecode mutation still disabled
debug attribute stripping not implemented
LocalVariableTable reducer not implemented
large-method splitting not implemented
constant-pool reducer not implemented
BootstrapMethods reducer not implemented
no bytecode-size memory or startup win claim
```

## Future Gate

Future mutation work should follow this order:

```text
semantic safety gate
V2-C confirmation
V2-D attribution
claim reconciliation
```

V2-D is now ready to explain future V2-A and V2-B mutation experiments, but it
does not make those mutations safe on its own.
