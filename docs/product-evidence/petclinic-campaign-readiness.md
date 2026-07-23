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

- Gate A fixtures: `31/31`
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

## Authorized Runtime Order

1. Run two reversed B0-to-B0 same-artifact pairs.
2. Run two reversed V2-to-V2 same-artifact pairs.
3. Stop if either control exceeds the frozen noise thresholds.
4. Run balanced pairs in order B0/V2, V2/B0, B0/V2.
5. Require canonical response equality, identical initial/final data state,
   proven mutations, zero workload errors, and stable JDK fingerprints.
6. Run V2-C validation and non-diagnostic V2-D attribution.
7. Claim a product win only if every frozen gate passes.

Each arm receives one complete command ledger with responses inline. The
readiness result does not itself make a new memory claim.
