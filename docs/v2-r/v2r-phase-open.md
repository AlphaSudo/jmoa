# V2-R Phase Open

V2-R opens after V2-Q confirmed that a tiny application-class metadata reducer
can pass semantic smoke, look promising once, and still fail paired
confirmation.

```text
phase: V2-R
name: Application / Generated Surface ROI Discovery
mode: report-only
mutation: disabled
runtime claim: none
```

The goal is not to rerun V2-Q. The goal is to decide where application,
generated, synthetic, or AOT surfaces are large enough, runtime-relevant enough,
and safe enough to justify a future prototype.

Boundaries:

```text
no application-class mutation enabled by V2-R
no generated/proxy mutation enabled by V2-R
no V2-Q runtime claim revived
no dependency-reducer evidence transferred to application classes
```
