# V2-Q Application Materialization

The application reducer writes a class-root tree, so materialization overlays
that tree into `BOOT-INF/classes`, never into the exploded application-layer
root. The public visits materialization copied fourteen overlay files with zero
hash mismatches.

The helper rejects an output path that aliases either input directory.
