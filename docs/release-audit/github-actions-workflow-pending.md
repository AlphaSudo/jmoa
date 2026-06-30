# GitHub Actions Workflow Pending

The initial public source push does not include `.github/workflows/build.yml` because the current GitHub OAuth token can create and push the repository but does not have the `workflow` scope required to add or update GitHub Actions workflow files.

The intended CI gate is:

```yaml
name: build

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '26'
          cache: maven
      - name: Build public modules
        run: mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean test
```

The equivalent local validation is available through:

```powershell
./scripts/build-all.ps1
./scripts/check-publication-safety.ps1
```

