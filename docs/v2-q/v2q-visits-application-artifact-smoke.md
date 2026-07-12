# V2-Q Visits Application Artifact Smoke

The first runtime target is public PetClinic visits-service. The required
comparison is dependency raw only versus dependency raw plus admitted packaged
application classes. This repository does not treat an artifact report as a
runtime result.

Status: `PASSED`.

The public visits artifact contained seven packaged application classes. Four
ordinary application classes were raw-reduced and byte-preservation audited;
two `JAVAC_SYNTHETIC` classes remained report-only and one ordinary interface
had no removable local-variable metadata. The application class delta was
`-480` bytes with four successful raw audits and zero failed audits.

The corrected application-layer materialization overlaid fourteen files into
`BOOT-INF/classes` with zero hash mismatches. It deliberately does not place
classes at the application-layer root, because that would let the system class
loader shadow Spring Boot's nested-library launcher path.
