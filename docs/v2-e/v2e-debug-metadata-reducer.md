# V2-E Debug Metadata Reducer

Status: implemented as the first opt-in bytecode reducer prototype.

V2-E introduces a release-low-footprint reducer for local-variable debug
metadata. It is disabled by default and report-only by default.

## Scope

Allowed first reducer:

```text
LocalVariableTable
LocalVariableTypeTable
```

Not stripped:

```text
LineNumberTable
SourceFile
StackMapTable
RuntimeVisibleAnnotations
RuntimeInvisibleAnnotations
Signature
BootstrapMethods
InnerClasses
NestHost / NestMembers
Record
PermittedSubclasses
```

The default `asm` mutation engine skips classes that contain `BootstrapMethods`.
This keeps the first reducer away from invokedynamic metadata while still
allowing safe local-variable metadata reduction in ordinary classes.

V2-I adds an explicit `raw` engine for the same LVT/LVTT reducer. The raw engine
preserves BootstrapMethods and rewrites only nested `Code` local-variable debug
tables. It is not the default.

## Maven Goal

```text
jmoa:reduce-bytecode
```

## Report-Only Estimate

```powershell
mvn jmoa:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.inputDir=<optimized-lib-dir>
```

## Mutation Mode

Mutation requires every one of these flags:

```powershell
mvn jmoa:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.reportOnly=false `
  -Djmoa.reducer.optimize=true `
  -Djmoa.reducer.profile=release-low-footprint `
  -Djmoa.reducer.engine=asm `
  -Djmoa.reducer.stripLocalVariableTable=true `
  -Djmoa.reducer.stripLocalVariableTypeTable=true `
  -Djmoa.reducer.inputDir=<optimized-lib-dir>
```

## V2-I Raw Engine

```powershell
mvn jmoa:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.reportOnly=false `
  -Djmoa.reducer.optimize=true `
  -Djmoa.reducer.profile=release-low-footprint `
  -Djmoa.reducer.engine=raw `
  -Djmoa.reducer.stripLocalVariableTable=true `
  -Djmoa.reducer.stripLocalVariableTypeTable=true `
  -Djmoa.reducer.inputDir=<optimized-lib-dir>
```

The raw engine keeps signed, multi-release, and sealed JAR skips from V2-F. It
does not strip or rewrite BootstrapMethods; it only allows BootstrapMethods-
bearing classes to keep that attribute while losing local-variable debug tables.

## Outputs

```text
target/jmoa-reduced-libs/reducer-build-report.json
target/jmoa-reduced-libs/reducer-build-report.md
target/jmoa-reduced-libs/debug-metadata-savings-estimate.json
target/jmoa-reduced-libs/debug-metadata-savings-estimate.md
target/jmoa-reduced-libs/bytecode-reducer-safety-taxonomy.json
target/jmoa-reduced-libs/bytecode-reducer-safety-taxonomy.md
target/jmoa-reduced-libs/*.jar
```

## Claim Boundary

V2-E can claim artifact/classfile-footprint reduction only after reduced
artifacts are verified. It cannot claim runtime memory improvement unless a
3-pair V2-C confirmation passes and V2-D explains the result.
