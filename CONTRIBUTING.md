# Contributing

This repository is currently a public source-readiness release for JMOA.

Before opening a pull request:

1. Run the safety scanner.
2. Run the Maven build.
3. Do not add private service source, generated evidence, logs, JFR/HProf files,
   CDS archives, or local-machine paths.
4. Keep claims tied to measured evidence. Do not generalize a case-study result
   to every service or every deployment mode.

The project is not yet accepting broad feature requests until the public API and
license are finalized.
