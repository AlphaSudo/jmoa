# Security Policy

Do not publish secrets, private service code, database dumps, JVM heap dumps,
JFR recordings, or raw logs that may contain internal paths or service data.

If you find a sensitive file in a prepared release branch:

1. Stop publication.
2. Remove the file from the release branch.
3. Rotate any exposed credentials if applicable.
4. Re-run `scripts/check-publication-safety.ps1`.

This source release intentionally excludes private HMS, patient-service, and
doctor-service source code. Those services are represented only by sanitized
case-study summaries in the separate portfolio repository.
