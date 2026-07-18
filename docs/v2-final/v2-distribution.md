# V2 Distribution

V2 includes tooling to build a local release bundle. Maven Central and GitHub
Packages publication are not part of `2.0.0-rc2`, and the current `v2.0.0`
GitHub release has no uploaded binary assets.

The locally generated release bundle contains:

- `jmoa-maven-plugin-2.0.0-rc2.jar`, POM, and sources;
- `jmoa-runtime-lib-2.0.0-rc2.jar`, POM, and sources;
- `SHA256SUMS.txt` and `jmoa-release-manifest.json`;
- `jmoa-v2-public-evidence-2.0.0-rc2.zip` and its SHA-256 entry;
- the sanitized public PetClinic customers profile, observed-site admission
  keys, and additional-safe-SAM allowlist as checksummed reproduction assets.

Run `scripts/build-v2-release-artifacts.ps1` with the required frozen public
inputs. A consumer can install the POM/JAR
pairs into a local Maven repository with `mvn install:install-file`, then invoke
`com.yourorg.jmoa:jmoa-maven-plugin:2.0.0-rc2:<goal>`.

The public quickstart also requires the frozen profile/admission/SAM inputs and
the runtime JAR in the materialized
`BOOT-INF/lib`; its SHA-256 is recorded in the materialization manifest.

Do not describe these files as downloadable GitHub release assets until they
are actually uploaded.
