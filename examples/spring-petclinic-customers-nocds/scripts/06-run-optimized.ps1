param(
    [string]$ExplodedDir = "./petclinic-work/exploded-customers-optimized",
    [string]$Java = "java"
)

$ErrorActionPreference = "Stop"
$env:MALLOC_ARENA_MAX = "1"
& $Java -Xshare:off org.springframework.boot.loader.launch.JarLauncher
