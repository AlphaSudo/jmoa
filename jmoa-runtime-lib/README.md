# JMOA Runtime Library

This module contains the small runtime surface used by rewritten bytecode.

It provides adapter contracts such as:

- `JmoaFactory`
- `JmoaRuntimeSupport`
- function, predicate, supplier, consumer, and bi-consumer adapters

The final optimized application uses this library as a normal dependency. It is
not a runtime instrumentation javaagent.
