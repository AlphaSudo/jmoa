# PetClinic Linux Environment

Status: **BLOCKED_PENDING_FIXED_HYPER-V_MEMORY**

The provided Debian VM is reachable and its SSH ED25519 host key was pinned
before key-based campaign access was installed.

## Initial Audit

```text
OS: Debian GNU/Linux 13 (trixie)
kernel: 6.12.74+deb13+1-amd64
virtualization: microsoft
clocksource: tsc
cgroup: v2
Podman: 5.4.2, native linux/amd64
Java: OpenJDK 21.0.10
Maven: 3.9.9
PowerShell: missing
guest memory: approximately 390 MiB
swap: active, approximately 16 MiB used
memory PSI avg10: 0.00 during the audit
```

The VM is not yet benchmark-admissible. Hyper-V must be changed to:

```text
fixed RAM: at least 8 GiB
vCPU: 4
Dynamic Memory: off
automatic checkpoints: off
```

The current Windows account can inspect the guest but is not authorized to
change Hyper-V settings. This is a host administration gate, not a JMOA or
Linux campaign failure.

After the fixed-memory change, Debian setup must install PowerShell and JDK 26,
disable swap for the campaign, remove stale stopped development containers,
and rerun the complete fingerprint, preflight, and calibration. No runtime arm
may start before those gates pass.
