# V2-M Recommendation Rules

Rules are evaluated in safety-first order:

1. Failed raw audits or non-LVT/LVTT stripping produce `BLOCK_UNSAFE`.
2. Failed semantic smoke produces `BLOCK_SEMANTIC_FAILURE`.
3. A failed runtime screen produces `BLOCK_RUNTIME_PROMOTION`.
4. Explicit diagnostic evidence produces `DIAGNOSTIC_ONLY`.
5. Non-raw reducer evidence remains `UNKNOWN` unless an earlier failure rule applies.
6. Missing positive artifact reduction, reduced classes, or raw audit proof produces `BLOCK_NO_EVIDENCE`.
7. Low-ROI application-class evidence without application runtime evidence produces `ALLOW_ARTIFACT_ONLY`.
8. A non-winning V2-C verdict produces `BLOCK_RUNTIME_PROMOTION`.
9. A confirmed win without passing semantic smoke or V2-D attribution produces `BLOCK_NO_EVIDENCE`.
10. A complete, exact-protocol confirmation produces `RECOMMEND_CONFIRMED`.
11. A historical confirmation with a protocol mismatch produces `RECOMMEND_SCREEN_REQUIRED`.
12. Artifact plus semantic evidence without V2-C produces `RECOMMEND_SCREEN_REQUIRED`.
13. Artifact plus raw audit evidence without semantic smoke produces `ALLOW_ARTIFACT_ONLY`.

Unsafe attributes include any requested or stripped attribute other than:

```text
LocalVariableTable
LocalVariableTypeTable
```

This means line numbers, source files, stack maps, annotations, signatures,
BootstrapMethods, nest metadata, record metadata, and similar structures cannot
be admitted by wording around a historical win.

Application-class raw reduction has an additional ROI rule. It remains
artifact/semantic-only unless removable application metadata is at least
`32 KB`, at least `50` application classes are reduced, or an application-scope
runtime screen passes. The public V2-Q visits result (`480` bytes, four classes)
is therefore not equivalent to dependency-jar raw reducer evidence.
