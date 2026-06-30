package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Tier2PackageAdapterGenerator {

    private final boolean compact;

    public Tier2PackageAdapterGenerator() {
        this(false);
    }

    public Tier2PackageAdapterGenerator(boolean compact) {
        this.compact = compact;
    }

    public Tier2AdapterArtifact generate(LambdaFilterDecision decision) {
        return generate(decision, Opcodes.V22);
    }

    public Tier2AdapterArtifact generate(LambdaFilterDecision decision, int classFileVersion) {
        if (!decision.eligible() || decision.tier() != LambdaTier.TIER2) {
            throw new IllegalArgumentException("Tier 2 adapter generation requires a Tier 2 eligible decision");
        }
        String internalName = Tier2AdapterNamingStrategy.internalName(decision.meta());
        byte[] classBytes = generateClassBytes(internalName, decision, classFileVersion);
        return new Tier2AdapterArtifact(internalName, classBytes);
    }

    /**
     * PACKAGE mode: one adapter class per package, implementing all distinct SAM
     * interfaces needed by Tier 2 sites in that package.  Each SAM method gets its
     * own siteId-dispatch body.  If two SAM interfaces share the same method name
     * AND descriptor they are naturally merged (one method body satisfies both).
     * If they share the method name but have different descriptors, they coexist as
     * overloaded methods.  A conflict arises only if two SAM interfaces define the
     * same method name with the same erased descriptor but incompatible return types;
     * in that case the conflicting SAMs fall back to per-SAM grouping for that
     * package.
     */
    public List<Tier2AdapterArtifact> generatePackageAdapters(
        List<LambdaFilterDecision> decisions,
        int classFileVersion
    ) {
        // Group by package
        Map<String, List<LambdaFilterDecision>> byPackage = new LinkedHashMap<>();
        decisions.stream()
            .sorted(Comparator.comparing(d -> d.meta().siteKey()))
            .forEach(decision -> {
                if (!decision.eligible() || decision.tier() != LambdaTier.TIER2) {
                    throw new IllegalArgumentException("Tier 2 adapter generation requires Tier 2 eligible decisions");
                }
                String pkgName = Tier2AdapterNamingStrategy.packageOnlyInternalName(decision.meta());
                byPackage.computeIfAbsent(pkgName, k -> new ArrayList<>()).add(decision);
            });

        List<Tier2AdapterArtifact> artifacts = new ArrayList<>();
        for (Map.Entry<String, List<LambdaFilterDecision>> entry : byPackage.entrySet()) {
            String internalName = entry.getKey();
            List<LambdaFilterDecision> pkgDecisions = entry.getValue();

            // Check for method signature conflicts between SAM interfaces
            Map<String, Set<String>> methodSigToSamInterfaces = new LinkedHashMap<>();
            for (LambdaFilterDecision d : pkgDecisions) {
                String samIface = d.meta().samInterfaceInternalName();
                String sig = d.meta().indyName() + d.meta().samMethodTypeDesc();
                methodSigToSamInterfaces.computeIfAbsent(sig, k -> new LinkedHashSet<>()).add(samIface);
            }

            // Detect conflicts: same method name+desc but different SAM interfaces
            // with incompatible return types is very rare; for safety, we fall back
            // to per-SAM grouping if any method signature maps to >1 SAM interface
            boolean hasConflict = false;
            for (Map.Entry<String, Set<String>> sigEntry : methodSigToSamInterfaces.entrySet()) {
                if (sigEntry.getValue().size() > 1) {
                    // Multiple SAM interfaces share this method signature.
                    // Check if they have compatible method descriptors (they should
                    // since the sig key already includes the descriptor).
                    // If the descriptors match, the JVM merges them — no conflict.
                    // But if two SAMs use the same name with different descriptors,
                    // they'd be in different sig entries and wouldn't trigger this.
                    hasConflict = true;
                    break;
                }
            }

            if (hasConflict) {
                // Fall back to PACKAGE_SAM for this package
                Map<String, List<LambdaFilterDecision>> samGroups = new LinkedHashMap<>();
                for (LambdaFilterDecision d : pkgDecisions) {
                    String samName = Tier2AdapterNamingStrategy.packageSamInternalName(d.meta());
                    samGroups.computeIfAbsent(samName, k -> new ArrayList<>()).add(d);
                }
                for (Map.Entry<String, List<LambdaFilterDecision>> samEntry : samGroups.entrySet()) {
                    artifacts.add(new Tier2AdapterArtifact(
                        samEntry.getKey(),
                        generatePackageSamClassBytes(samEntry.getKey(), samEntry.getValue(), classFileVersion)
                    ));
                }
            } else {
                // Generate one class implementing all SAM interfaces
                artifacts.add(new Tier2AdapterArtifact(
                    internalName,
                    generatePackageClassBytes(internalName, pkgDecisions, classFileVersion)
                ));
            }
        }
        return List.copyOf(artifacts);
    }

    private byte[] generatePackageClassBytes(
        String internalName,
        List<LambdaFilterDecision> decisions,
        int classFileVersion
    ) {
        if (decisions.isEmpty()) {
            throw new IllegalArgumentException("At least one Tier 2 decision is required");
        }

        // Collect distinct SAM interfaces
        Set<String> samInterfaces = new LinkedHashSet<>();
        for (LambdaFilterDecision d : decisions) {
            samInterfaces.add(d.meta().samInterfaceInternalName());
        }

        // Group decisions by SAM method signature for method generation,
        // tracking the global site index so dispatch matches the clinit siteId.
        Map<String, List<LambdaFilterDecision>> bySamMethod = new LinkedHashMap<>();
        Map<LambdaFilterDecision, Integer> globalSiteIndex = new java.util.IdentityHashMap<>();
        for (int i = 0; i < decisions.size(); i++) {
            LambdaFilterDecision d = decisions.get(i);
            globalSiteIndex.put(d, i);
            String methodKey = d.meta().indyName() + "|" + d.meta().samMethodTypeDesc();
            bySamMethod.computeIfAbsent(methodKey, k -> new ArrayList<>()).add(d);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(
            classFileVersion,
            Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
            internalName,
            null,
            "java/lang/Object",
            samInterfaces.toArray(new String[0])
        );
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, compact ? "s" : "siteId", "I", null, null).visitEnd();

        // One field per site, typed by its SAM interface
        for (LambdaFilterDecision decision : decisions) {
            String samIface = decision.meta().samInterfaceInternalName();
            String fName = compact
                ? Tier2AdapterNamingStrategy.compactFieldName(decision.meta())
                : Tier2AdapterNamingStrategy.fieldName(decision.meta());
            cw.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fName,
                "L" + samIface + ";",
                null,
                null
            ).visitEnd();
        }

        generateIndexedConstructor(cw, internalName);
        generatePackageClinit(cw, internalName, decisions);

        // Generate one dispatch method per SAM method signature
        for (Map.Entry<String, List<LambdaFilterDecision>> entry : bySamMethod.entrySet()) {
            List<LambdaFilterDecision> methodDecisions = entry.getValue();
            LambdaFilterDecision first = methodDecisions.getFirst();
            String samName = first.meta().indyName();
            String samDesc = first.meta().samMethodTypeDesc();
            generatePackageMethod(cw, internalName, methodDecisions, globalSiteIndex, samName, samDesc);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generatePackageClinit(
        ClassWriter cw,
        String internalName,
        List<LambdaFilterDecision> decisions
    ) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_STATIC, "<clinit>", "()V");
        ga.visitCode();
        Type classType = Type.getObjectType(internalName);
        for (int i = 0; i < decisions.size(); i++) {
            ga.newInstance(classType);
            ga.dup();
            ga.push(i);
            ga.invokeConstructor(classType, new Method("<init>", "(I)V"));
            String samIface = decisions.get(i).meta().samInterfaceInternalName();
            String fName = compact
                ? Tier2AdapterNamingStrategy.compactFieldName(decisions.get(i).meta())
                : Tier2AdapterNamingStrategy.fieldName(decisions.get(i).meta());
            ga.putStatic(classType, fName, Type.getType("L" + samIface + ";"));
        }
        ga.returnValue();
        ga.endMethod();
    }

    private void generatePackageMethod(
        ClassWriter cw,
        String internalName,
        List<LambdaFilterDecision> decisions,
        Map<LambdaFilterDecision, Integer> globalSiteIndex,
        String samName,
        String samDesc
    ) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, samName, samDesc, null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, samName, samDesc);
        ga.visitCode();
        for (LambdaFilterDecision decision : decisions) {
            int siteId = globalSiteIndex.get(decision);
            org.objectweb.asm.Label next = ga.newLabel();
            ga.loadThis();
            ga.getField(Type.getObjectType(internalName), compact ? "s" : "siteId", Type.INT_TYPE);
            ga.push(siteId);
            ga.ifICmp(GeneratorAdapter.NE, next);
            emitSamBody(ga, decision, samDesc);
            ga.mark(next);
        }
        ga.throwException(Type.getType(IllegalStateException.class), "Unknown JMOA adapter site id");
        ga.endMethod();
    }

    public List<Tier2AdapterArtifact> generatePackageSamAdapters(
        List<LambdaFilterDecision> decisions,
        int classFileVersion
    ) {
        Map<String, List<LambdaFilterDecision>> grouped = new LinkedHashMap<>();
        decisions.stream()
            .sorted(Comparator.comparing(decision -> decision.meta().siteKey()))
            .forEach(decision -> {
                if (!decision.eligible() || decision.tier() != LambdaTier.TIER2) {
                    throw new IllegalArgumentException("Tier 2 adapter generation requires Tier 2 eligible decisions");
                }
                String internalName = compact
                    ? Tier2AdapterNamingStrategy.compactPackageSamInternalName(decision.meta())
                    : Tier2AdapterNamingStrategy.packageSamInternalName(decision.meta());
                grouped.computeIfAbsent(internalName, ignored -> new ArrayList<>()).add(decision);
            });

        List<Tier2AdapterArtifact> artifacts = new ArrayList<>();
        for (Map.Entry<String, List<LambdaFilterDecision>> entry : grouped.entrySet()) {
            artifacts.add(new Tier2AdapterArtifact(
                entry.getKey(),
                generatePackageSamClassBytes(entry.getKey(), entry.getValue(), classFileVersion)
            ));
        }
        return List.copyOf(artifacts);
    }

    /**
     * PACKAGE_SIGNATURE mode: group by package + erased SAM method descriptor.
     * Merges different SAM interfaces that have the same erased method shape
     * (e.g. Function and UnaryOperator both have erased "(Ljava/lang/Object;)Ljava/lang/Object;").
     * Each group implements all the distinct SAM interfaces in it and has one
     * dispatch method per unique erased method descriptor.
     */
    public List<Tier2AdapterArtifact> generatePackageSignatureAdapters(
        List<LambdaFilterDecision> decisions,
        int classFileVersion
    ) {
        Map<String, List<LambdaFilterDecision>> grouped = new LinkedHashMap<>();
        decisions.stream()
            .sorted(Comparator.comparing(decision -> decision.meta().siteKey()))
            .forEach(decision -> {
                if (!decision.eligible() || decision.tier() != LambdaTier.TIER2) {
                    throw new IllegalArgumentException("Tier 2 adapter generation requires Tier 2 eligible decisions");
                }
                String internalName = compact
                    ? Tier2AdapterNamingStrategy.compactPackageSignatureInternalName(decision.meta())
                    : Tier2AdapterNamingStrategy.packageSignatureInternalName(decision.meta());
                grouped.computeIfAbsent(internalName, ignored -> new ArrayList<>()).add(decision);
            });

        List<Tier2AdapterArtifact> artifacts = new ArrayList<>();
        for (Map.Entry<String, List<LambdaFilterDecision>> entry : grouped.entrySet()) {
            artifacts.add(new Tier2AdapterArtifact(
                entry.getKey(),
                generatePackageSignatureClassBytes(entry.getKey(), entry.getValue(), classFileVersion)
            ));
        }
        return List.copyOf(artifacts);
    }

    private byte[] generateClassBytes(String internalName, LambdaFilterDecision decision, int classFileVersion) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        String samInterface = decision.meta().samInterfaceInternalName();
        String samDesc = decision.meta().samMethodTypeDesc();

        cw.visit(
            classFileVersion,
            Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
            internalName,
            null,
            "java/lang/Object",
            new String[]{samInterface}
        );

        String fieldDesc = "L" + samInterface + ";";
        cw.visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
            "INSTANCE",
            fieldDesc,
            null,
            null
        ).visitEnd();

        generateConstructor(cw);
        generateClinit(cw, internalName, samInterface);
        generateSamMethod(cw, decision, samDesc);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] generatePackageSignatureClassBytes(
        String internalName,
        List<LambdaFilterDecision> decisions,
        int classFileVersion
    ) {
        if (decisions.isEmpty()) {
            throw new IllegalArgumentException("At least one Tier 2 decision is required");
        }

        // Collect distinct SAM interfaces (may be multiple within same erased signature group)
        Set<String> samInterfaces = new LinkedHashSet<>();
        for (LambdaFilterDecision d : decisions) {
            samInterfaces.add(d.meta().samInterfaceInternalName());
        }

        // Group decisions by SAM method name + descriptor for method generation
        Map<String, List<LambdaFilterDecision>> bySamMethod = new LinkedHashMap<>();
        Map<LambdaFilterDecision, Integer> globalSiteIndex = new java.util.IdentityHashMap<>();
        for (int i = 0; i < decisions.size(); i++) {
            LambdaFilterDecision d = decisions.get(i);
            globalSiteIndex.put(d, i);
            String methodKey = d.meta().indyName() + "|" + d.meta().samMethodTypeDesc();
            bySamMethod.computeIfAbsent(methodKey, k -> new ArrayList<>()).add(d);
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(
            classFileVersion,
            Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
            internalName,
            null,
            "java/lang/Object",
            samInterfaces.toArray(new String[0])
        );
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, compact ? "s" : "siteId", "I", null, null).visitEnd();

        for (LambdaFilterDecision decision : decisions) {
            String samIface = decision.meta().samInterfaceInternalName();
            String fName = compact
                ? Tier2AdapterNamingStrategy.compactFieldName(decision.meta())
                : Tier2AdapterNamingStrategy.fieldName(decision.meta());
            cw.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fName,
                "L" + samIface + ";",
                null,
                null
            ).visitEnd();
        }

        generateSignatureIndexedConstructor(cw, internalName);
        generateSignatureClinit(cw, internalName, decisions);

        for (Map.Entry<String, List<LambdaFilterDecision>> entry : bySamMethod.entrySet()) {
            List<LambdaFilterDecision> methodDecisions = entry.getValue();
            LambdaFilterDecision first = methodDecisions.getFirst();
            String samName = first.meta().indyName();
            String samDesc = first.meta().samMethodTypeDesc();
            generateSignatureMethod(cw, internalName, methodDecisions, globalSiteIndex, samName, samDesc);
        }

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateSignatureIndexedConstructor(ClassWriter cw, String internalName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(I)V", null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PRIVATE, "<init>", "(I)V");
        ga.visitCode();
        ga.loadThis();
        ga.invokeConstructor(Type.getType(Object.class), new Method("<init>", "()V"));
        ga.loadThis();
        ga.loadArg(0);
        ga.putField(Type.getObjectType(internalName), compact ? "s" : "siteId", Type.INT_TYPE);
        ga.returnValue();
        ga.endMethod();
    }

    private void generateSignatureClinit(
        ClassWriter cw,
        String internalName,
        List<LambdaFilterDecision> decisions
    ) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_STATIC, "<clinit>", "()V");
        ga.visitCode();
        Type classType = Type.getObjectType(internalName);
        for (int i = 0; i < decisions.size(); i++) {
            ga.newInstance(classType);
            ga.dup();
            ga.push(i);
            ga.invokeConstructor(classType, new Method("<init>", "(I)V"));
            String samIface = decisions.get(i).meta().samInterfaceInternalName();
            String fName = compact
                ? Tier2AdapterNamingStrategy.compactFieldName(decisions.get(i).meta())
                : Tier2AdapterNamingStrategy.fieldName(decisions.get(i).meta());
            ga.putStatic(classType, fName, Type.getType("L" + samIface + ";"));
        }
        ga.returnValue();
        ga.endMethod();
    }

    private void generateSignatureMethod(
        ClassWriter cw,
        String internalName,
        List<LambdaFilterDecision> decisions,
        Map<LambdaFilterDecision, Integer> globalSiteIndex,
        String samName,
        String samDesc
    ) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, samName, samDesc, null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, samName, samDesc);
        ga.visitCode();
        for (LambdaFilterDecision decision : decisions) {
            int siteId = globalSiteIndex.get(decision);
            org.objectweb.asm.Label next = ga.newLabel();
            ga.loadThis();
            ga.getField(Type.getObjectType(internalName), compact ? "s" : "siteId", Type.INT_TYPE);
            ga.push(siteId);
            ga.ifICmp(GeneratorAdapter.NE, next);
            emitSamBody(ga, decision, samDesc);
            ga.mark(next);
        }
        ga.throwException(Type.getType(IllegalStateException.class), "Unknown JMOA adapter site id");
        ga.endMethod();
    }

    private byte[] generatePackageSamClassBytes(
        String internalName,
        List<LambdaFilterDecision> decisions,
        int classFileVersion
    ) {
        if (decisions.isEmpty()) {
            throw new IllegalArgumentException("At least one Tier 2 decision is required");
        }
        LambdaFilterDecision first = decisions.getFirst();
        String samInterface = first.meta().samInterfaceInternalName();
        String samDesc = first.meta().samMethodTypeDesc();
        String samName = first.meta().indyName();
        for (LambdaFilterDecision decision : decisions) {
            if (!samInterface.equals(decision.meta().samInterfaceInternalName())
                || !samDesc.equals(decision.meta().samMethodTypeDesc())
                || !samName.equals(decision.meta().indyName())) {
                throw new IllegalArgumentException("Package+SAM consolidation requires matching SAM shape");
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(
            classFileVersion,
            Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
            internalName,
            null,
            "java/lang/Object",
            new String[]{samInterface}
        );
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, compact ? "s" : "siteId", "I", null, null).visitEnd();
        for (LambdaFilterDecision decision : decisions) {
            String fName = compact
                ? Tier2AdapterNamingStrategy.compactFieldName(decision.meta())
                : Tier2AdapterNamingStrategy.fieldName(decision.meta());
            cw.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fName,
                "L" + samInterface + ";",
                null,
                null
            ).visitEnd();
        }

        generateIndexedConstructor(cw, internalName);
        generatePackageSamClinit(cw, internalName, samInterface, decisions);
        generatePackageSamMethod(cw, internalName, decisions, samName, samDesc);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateIndexedConstructor(ClassWriter cw, String internalName) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(I)V", null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PRIVATE, "<init>", "(I)V");
        ga.visitCode();
        ga.loadThis();
        ga.invokeConstructor(Type.getType(Object.class), new Method("<init>", "()V"));
        ga.loadThis();
        ga.loadArg(0);
        ga.putField(Type.getObjectType(internalName), compact ? "s" : "siteId", Type.INT_TYPE);
        ga.returnValue();
        ga.endMethod();
    }

    private void generatePackageSamClinit(
        ClassWriter cw,
        String internalName,
        String samInterface,
        List<LambdaFilterDecision> decisions
    ) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_STATIC, "<clinit>", "()V");
        ga.visitCode();
        Type classType = Type.getObjectType(internalName);
        for (int i = 0; i < decisions.size(); i++) {
            ga.newInstance(classType);
            ga.dup();
            ga.push(i);
            ga.invokeConstructor(classType, new Method("<init>", "(I)V"));
            String fName = compact
                ? Tier2AdapterNamingStrategy.compactFieldName(decisions.get(i).meta())
                : Tier2AdapterNamingStrategy.fieldName(decisions.get(i).meta());
            ga.putStatic(classType, fName, Type.getType("L" + samInterface + ";"));
        }
        ga.returnValue();
        ga.endMethod();
    }

    private void generatePackageSamMethod(
        ClassWriter cw,
        String internalName,
        List<LambdaFilterDecision> decisions,
        String samName,
        String samDesc
    ) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, samName, samDesc, null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, samName, samDesc);
        ga.visitCode();
        org.objectweb.asm.Label fallback = ga.newLabel();
        for (int i = 0; i < decisions.size(); i++) {
            org.objectweb.asm.Label next = ga.newLabel();
            ga.loadThis();
            ga.getField(Type.getObjectType(internalName), compact ? "s" : "siteId", Type.INT_TYPE);
            ga.push(i);
            ga.ifICmp(GeneratorAdapter.NE, next);
            emitSamBody(ga, decisions.get(i), samDesc);
            ga.mark(next);
        }
        ga.mark(fallback);
        ga.throwException(Type.getType(IllegalStateException.class), "Unknown JMOA adapter site id");
        ga.endMethod();
    }

    private void generateConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PRIVATE, "<init>", "()V");
        ga.visitCode();
        ga.loadThis();
        ga.invokeConstructor(Type.getType(Object.class), new Method("<init>", "()V"));
        ga.returnValue();
        ga.endMethod();
    }

    private void generateClinit(ClassWriter cw, String internalName, String samInterface) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_STATIC, "<clinit>", "()V");
        ga.visitCode();
        Type classType = Type.getObjectType(internalName);
        ga.newInstance(classType);
        ga.dup();
        ga.invokeConstructor(classType, new Method("<init>", "()V"));
        ga.putStatic(classType, "INSTANCE", Type.getType("L" + samInterface + ";"));
        ga.returnValue();
        ga.endMethod();
    }

    private void generateSamMethod(ClassWriter cw, LambdaFilterDecision decision, String samDesc) {
        String samName = decision.meta().indyName();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, samName, samDesc, null, null);
        GeneratorAdapter ga = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, samName, samDesc);
        ga.visitCode();
        emitSamBody(ga, decision, samDesc);
        ga.endMethod();
    }

    private void emitSamBody(GeneratorAdapter ga, LambdaFilterDecision decision, String samDesc) {
        Type[] samArgs = Type.getArgumentTypes(samDesc);
        Type[] instArgs = Type.getArgumentTypes(decision.meta().instantiatedMethodTypeDesc());
        Type[] implArgs = Type.getArgumentTypes(decision.meta().implDesc());
        int tag = decision.meta().implTag();

        if (tag == Opcodes.H_NEWINVOKESPECIAL) {
            Type objType = Type.getObjectType(decision.meta().implOwner());
            ga.newInstance(objType);
            ga.dup();
            for (int i = 0; i < implArgs.length; i++) {
                ga.loadArg(i);
                convertType(ga, samArgs[i], instArgs[i]);
                convertType(ga, instArgs[i], implArgs[i]);
            }
            ga.invokeConstructor(objType, new Method("<init>", decision.meta().implDesc()));
        } else {
            boolean isStatic = tag == Opcodes.H_INVOKESTATIC;
            if (!isStatic) {
                ga.loadArg(0);
                convertType(ga, samArgs[0], instArgs[0]);
                convertType(ga, instArgs[0], Type.getObjectType(decision.meta().implOwner()));
                for (int i = 0; i < implArgs.length; i++) {
                    ga.loadArg(i + 1);
                    convertType(ga, samArgs[i + 1], instArgs[i + 1]);
                    convertType(ga, instArgs[i + 1], implArgs[i]);
                }
            } else {
                for (int i = 0; i < implArgs.length; i++) {
                    ga.loadArg(i);
                    convertType(ga, samArgs[i], instArgs[i]);
                    convertType(ga, instArgs[i], implArgs[i]);
                }
            }

            int opcode = switch (tag) {
                case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
                case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
                case Opcodes.H_INVOKESPECIAL -> Opcodes.INVOKESPECIAL;
                case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
                default -> throw new IllegalArgumentException("Unsupported Tier 2 impl tag: " + tag);
            };
            ga.visitMethodInsn(
                opcode,
                decision.meta().implOwner(),
                decision.meta().implName(),
                decision.meta().implDesc(),
                decision.meta().implOwnerInterface()
            );
        }

        Type implRetType = switch (tag) {
            case Opcodes.H_NEWINVOKESPECIAL -> Type.getObjectType(decision.meta().implOwner());
            default -> Type.getReturnType(decision.meta().implDesc());
        };
        Type samRetType = Type.getReturnType(samDesc);

        if (implRetType.getSort() == Type.VOID) {
            if (samRetType.getSort() != Type.VOID) {
                ga.push((String) null);
            }
        } else {
            Type instRetType = Type.getReturnType(decision.meta().instantiatedMethodTypeDesc());
            convertType(ga, implRetType, instRetType);
            convertType(ga, instRetType, samRetType);
        }

        ga.returnValue();
    }

    private static void convertType(GeneratorAdapter ga, Type from, Type to) {
        if (from.equals(to)) {
            return;
        }
        if (isPrimitive(from) && !isPrimitive(to)) {
            ga.box(from);
            if (!to.equals(boxedType(from))) {
                ga.checkCast(to);
            }
        } else if (!isPrimitive(from) && isPrimitive(to)) {
            ga.checkCast(boxedType(to));
            ga.unbox(to);
        } else if (isPrimitive(from) && isPrimitive(to)) {
            ga.cast(from, to);
        } else {
            ga.checkCast(to);
        }
    }

    private static boolean isPrimitive(Type type) {
        int sort = type.getSort();
        return sort >= Type.BOOLEAN && sort <= Type.DOUBLE;
    }

    private static Type boxedType(Type primitiveType) {
        return switch (primitiveType.getSort()) {
            case Type.BOOLEAN -> Type.getType(Boolean.class);
            case Type.BYTE -> Type.getType(Byte.class);
            case Type.CHAR -> Type.getType(Character.class);
            case Type.SHORT -> Type.getType(Short.class);
            case Type.INT -> Type.getType(Integer.class);
            case Type.LONG -> Type.getType(Long.class);
            case Type.FLOAT -> Type.getType(Float.class);
            case Type.DOUBLE -> Type.getType(Double.class);
            default -> throw new IllegalArgumentException("Not a primitive: " + primitiveType);
        };
    }
}
