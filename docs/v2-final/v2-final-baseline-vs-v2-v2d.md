# Historical B0 vs V2 V2-D Attribution

The earlier B0-to-V2 attribution is retained only with its historical record.
The authoritative RC2 exact-image replication instead attributes the mixed
result primarily to `HEAP_PAGE_TOUCH_GROWTH`: heap PSS increased `2,756 KB`
while heap-used and histogram bytes stayed effectively flat. Fewer loaded
classes and lower anonymous-RW PSS were supporting effects, but did not produce
a stable direct B0-to-V2 memory win.
