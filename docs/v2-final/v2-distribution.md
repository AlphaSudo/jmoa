# V2 Distribution

V2 uses GitHub Release assets. Maven Central and GitHub Packages publication
are not part of `2.0.0-rc1`.

The release bundle contains:

- `jmoa-maven-plugin-2.0.0-rc1.jar`, POM, and sources;
- `jmoa-runtime-lib-2.0.0-rc1.jar`, POM, and sources;
- `SHA256SUMS.txt` and `jmoa-release-manifest.json`;
- the sanitized public PetClinic customers profile, observed-site admission
  keys, and additional-safe-SAM allowlist as checksummed reproduction assets.

Run `scripts/build-v2-release-artifacts.ps1`. A consumer can install the POM/JAR
pairs into a local Maven repository with `mvn install:install-file`, then invoke
`com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc1:<goal>`.

The public quickstart also requires the runtime JAR in the materialized
`BOOT-INF/lib`; its SHA-256 is recorded in the materialization manifest.
