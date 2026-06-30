# Known Limitations

This public source release is v0.1 readiness work, not a polished package.

Known limitations:

- the group id still uses `com.yourorg.jmoa`,
- the legal license is pending,
- the PetClinic reproduction scripts are a scaffold and should be tested from a
  fresh clone before public announcement,
- deployment materializer and runtime-origin verifier are documented product
  surfaces but not yet fully extracted into standalone reusable CLIs,
- CI does not run Podman integration measurements,
- private HMS, patient-service, and doctor-service source code are excluded.

The current goal is to publish real source safely while avoiding private code
or overclaimed automation.
