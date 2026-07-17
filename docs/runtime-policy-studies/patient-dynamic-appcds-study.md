# Patient Dynamic AppCDS Archive Economics Study

Status: `COMPLETE` / `NON_BLOCKING_POST_V2` / `TERMINAL`

The study isolates three runtime arms: OFF (no CDS), BASE (default JDK CDS), and APP (BASE plus a Patient dynamic archive). Its primary admission question is APP versus BASE. It uses exactly STARTUP and REPRESENTATIVE training profiles, independent A/B archives, actual archived-class inspection, balanced run order, cold-cache resets, live archive mapping proof, and fixed artifact hashes.

Final verdict: [`SINGLE_REPLICA_ARCHIVE_REGRESSION`](patient-appcds-terminal-verdict.md).
