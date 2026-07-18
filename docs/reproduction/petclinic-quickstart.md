# PetClinic Quickstart

The customers-service quickstart is the public, clean-clone-qualified build and
semantic-smoke path. It is not the accepted memory-confirmation runner.

## Prerequisites

- Java 22 or newer for the Maven plugin build; Java 26 is the qualified build environment;
- Maven 3.9.9;
- PowerShell 7;
- Podman for semantic smoke;
- the frozen public customers profile JSON;
- the frozen observed-site admission file;
- the additional-safe-SAM allowlist.

The last three inputs were used in clean-clone qualification but are not
currently uploaded to the `v2.0.0` GitHub release. Until they are published,
the workflow is executable only for a reviewer who has those public-safe frozen
inputs. This limitation is intentional and visible.

## Build JMOA

```powershell
git clone https://github.com/AlphaSudo/jmoa.git
cd jmoa
mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean install
```

## Run The Qualified Workflow

```powershell
./examples/spring-petclinic-customers-nocds/scripts/00-quickstart.ps1 `
  -ProfilePath <petclinic-customers-profile.json> `
  -AdmissionPath <petclinic-customers-admission.txt> `
  -SafeSamsPath <jmoa-additional-safe-sams.txt> `
  -RuntimeJar ./jmoa-runtime-lib/target/jmoa-runtime-lib-2.0.0-rc2.jar
```

The script pins PetClinic revision
`305a1f13e4f961001d4e6cb50a9db51dc3fc5967`, builds the baseline, applies full
P2, runs the raw dependency reducer, materializes exploded Boot, proves hashes,
and performs Java 17 semantic smoke. It fails on missing optimizer output,
runtime-library mismatch, failed materialization, health failure, or endpoint
errors.

`-SkipSemanticSmoke` is an artifact-building diagnostic and is not runtime
qualification.

## What It Proves

The quickstart proves buildability, reduction, materialization, and semantic
viability. It does not reproduce the accepted PSS numbers by itself. For that,
use the frozen paired protocol described in
[Extended Confirmation](extended-confirmation.md).
