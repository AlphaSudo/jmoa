# 12 Troubleshooting

## Campaign stops after a workspace edit

Expected behavior. The runner freezes implementation hashes. Preserve the directory and start a new output directory after completing and testing edits.

## Podman machine restart leaves Compose unavailable

Use the frozen `PODMAN_MACHINE_RESTART` reset strategy. It waits for the Podman machine and verifies the Docker-compatible Compose API before launch.

## A run fails during teardown

The primary launch/workload/capture error remains primary. Teardown writes its own failure report and still invalidates the observation.

## B0 varies by several MiB

Do not add an arbitrary same-artifact gate. Preserve valid variation and use all six balanced permutations.

## V2 historical ID differs from the concrete directory hash

Record both with distinct names. A historical logical product ID is provenance; the campaign freeze must hash the actual file or directory bytes it launches.

## One valid block loses

Keep it. Final gates use all six direct block deltas. Never replace a valid loss.

## Raw evidence contains private paths or credentials

Keep it outside Git. Publish only sanitized hashes, metrics, terminal verdicts, and claim boundaries.
