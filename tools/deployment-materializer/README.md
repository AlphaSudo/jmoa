# Deployment Materializer

This directory tracks the product boundary for materializing JMOA output into
runtime artifacts.

Current public v0.1 status:

- plugin code can package optimized dependency jars,
- docs define fat-JAR, expanded-classpath, and exploded-Boot requirements,
- PetClinic scripts scaffold exploded Boot extraction,
- standalone reusable materializer CLI is still TODO.

Do not treat a build-time rewrite as publishable until materialization and
runtime-origin proof both pass.
