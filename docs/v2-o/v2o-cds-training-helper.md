# V2-O CDS Training Helper

`scripts/train-cds-policy.ps1` records an explicitly requested CDS training
command. The caller supplies the artifact, target archive, executable, and
arguments; the helper never invents a command or reuses an earlier archive.

The report records artifact/archive SHA-256 values, command exit status,
optional workload status, optional health status, and archive bytes.

```powershell
./scripts/train-cds-policy.ps1 `
  -ArtifactPath <candidate-artifact> `
  -ArchivePath <candidate.jsa> `
  -TrainerExecutable <explicit-trainer> `
  -TrainerArguments <explicit-training-arguments> `
  -FailOnFailure
```

`TRAINED_NOT_MEASURED` means only that an archive was created. It must still
pass runtime materialization proof and semantic smoke before screening.
