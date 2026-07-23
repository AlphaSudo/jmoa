# PetClinic Balanced Campaign Readiness

Status: **READY**, dry-run only. No balanced memory pairs were launched by this
readiness pass.

## Frozen Inputs

- Source revision: `305a1f13e4f961001d4e6cb50a9db51dc3fc5967`
- Signed campaign SHA-256:
  `90CBB67E866F251164BA052C20EFC9F15DB0BC4A594D370DF32240836C5CA6C9`
- B0 artifact SHA-256:
  `88D4BC9F000A22041F8FD521A0442B85F9FC49966948FA11F642CBBABE0D6AE7`
- V2 artifact SHA-256:
  `338F5D44431E66B3EEC9B2CFAD6D9769D70D08E3C351B6FA221AE883D8E5A34B`
- Materialization manifest SHA-256:
  `38CD11C52E996ABE4845E5078626432FDDE889252CB2D6AC897331B2A0E0B4DC`
- Artifact-lineage SHA-256:
  `4EDB212001FD4869F3F0981E8440A358712282421113A7DC5ED847AE4D6173E7`

The manifest pins immutable B0, V2, config-server, and discovery-server image
IDs. The public summary omits local paths; the private command ledger retains
the exact paths and command responses.

## Passed Gates

- Gate A fixtures: `35/35`
- Gate C campaign-manifest integrity: passed
- B0 no-JMOA cleanliness: passed
- V2 replacement hashes: `24/24`
- V2 runtime-library hash: passed
- Non-replaced dependency identity: passed
- Spring Boot loader identity: passed
- Artifact lineage and source revision: passed
- Config Git revision and clean worktree: passed
- Config content SHA-256:
  `80EEA8F0321E72DD7AD87E4F94817A217C89DF5A66D4FFEFFACE50507BF89840`
- Campaign-owned config snapshot hash equals source hash: passed
- Preflight child-ledger integrity: passed
- Windows/WSL/Podman host preflight: passed
- Required ports free and no running Podman containers: passed
- Podman VM admission: at least `1 GiB` available, zero swap used, memory PSI
  `some avg10 <= 1.0` and `full avg10 <= 0.1`

## Authorized Runtime Order

1. Run two reversed B0-to-B0 same-artifact pairs.
2. Stop immediately with `STOPPED_B0_RUNTIME_VARIANCE` if B0 exceeds the
   frozen noise or environment thresholds.
3. Only after B0 qualifies, run two reversed V2-to-V2 same-artifact pairs.
4. Stop with `STOPPED_V2_RUNTIME_VARIANCE` if V2 exceeds the frozen thresholds.
5. Run balanced pairs in order B0/V2, V2/B0, B0/V2.
6. Require canonical response equality, identical initial/final data state,
   proven mutations, zero workload errors, and stable JDK fingerprints.
7. Require every arm's pre/post Podman VM pressure record to pass.
8. Run V2-C validation and non-diagnostic V2-D attribution.
9. Emit one of `TRUSTED_PRODUCT_WIN`, `CONFIRMED_PRODUCT_WIN` with
   `SUBSTANTIAL_4MIB_GATE_NOT_MET`, `PRODUCT_EFFECT_NOT_CONFIRMED`, or
   `ENVIRONMENT_VARIANCE_TOO_HIGH`.

Each arm receives one complete command ledger with responses inline. The
readiness result does not itself make a new memory claim.
