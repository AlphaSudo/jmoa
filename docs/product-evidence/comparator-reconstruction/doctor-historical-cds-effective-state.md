# Doctor Historical CDS Effective State

- Historical intent: **artifact-specific application CDS**
- Observed effective policy: **BASE_CDS_AFTER_APPLICATION_ARCHIVE_FALLBACK**
- B0 archive-rejection evidence files: **6**
- V1 archive-rejection evidence files: **6**
- B0 default-CDS mapping evidence files: **6**
- V1 default-CDS mapping evidence files: **6**
- Application-CDS claim supported: **false**
- Corrected diagnostic policy: **EXPLICIT_BASE_CDS**

The historical Doctor process requested application archives, but the recovered runtime diagnostics show that HotSpot rejected the top archive for both variants and continued with its default CDS archive. The historical memory comparison is therefore a base-CDS fallback comparison, not an application-CDS comparison.
