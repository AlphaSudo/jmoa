#!/usr/bin/env bash
set -euo pipefail

# The frozen campaign asks Podman Desktop to execute host-Linux commands through
# `podman machine ssh`. Native Linux has no Podman machine. Translate only that
# operation to the local host and pass every real Podman operation through.
if [[ "${1:-}" == "machine" && "${2:-}" == "ssh" ]]; then
  shift 2
  if [[ "$#" -ne 1 ]]; then
    printf 'linux-podman-compat: expected one machine ssh command, got %s\n' "$#" >&2
    exit 64
  fi
  exec /bin/bash -lc "$1"
fi

exec /usr/bin/podman "$@"
