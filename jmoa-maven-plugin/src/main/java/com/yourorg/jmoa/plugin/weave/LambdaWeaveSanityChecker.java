package com.yourorg.jmoa.plugin.weave;

import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LambdaWeaveSanityChecker {

    private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";

    public LambdaWeaveSanityClassResult verifyClass(byte[] originalBytes, byte[] patchedBytes, LambdaWeavingPlan weavingPlan) {
        Map<String, List<EncounteredLambdaSite>> originalSitesByMethod = collectStatelessLambdaSitesByMethod(originalBytes);
        Map<String, List<EncounteredLambdaSite>> patchedSitesByMethod = collectStatelessLambdaSitesByMethod(patchedBytes);

        List<String> remainingEligibleSiteKeys = new ArrayList<>();
        List<String> unexpectedRemovedSiteKeys = new ArrayList<>();
        int expectedEligibleSites = 0;
        int rewrittenEligibleSites = 0;

        for (Map.Entry<String, List<EncounteredLambdaSite>> entry : originalSitesByMethod.entrySet()) {
            List<EncounteredLambdaSite> originalSites = entry.getValue();
            List<EncounteredLambdaSite> patchedSites = patchedSitesByMethod.getOrDefault(entry.getKey(), List.of());
            int patchedIndex = 0;

            for (EncounteredLambdaSite originalSite : originalSites) {
                boolean preserved = patchedIndex < patchedSites.size()
                    && patchedSites.get(patchedIndex).structuralKey().equals(originalSite.structuralKey());
                if (weavingPlan.targetFor(originalSite.siteKey()) != null) {
                    expectedEligibleSites++;
                    if (preserved) {
                        remainingEligibleSiteKeys.add(originalSite.siteKey());
                        patchedIndex++;
                    } else {
                        rewrittenEligibleSites++;
                    }
                } else {
                    if (preserved) {
                        patchedIndex++;
                    } else {
                        unexpectedRemovedSiteKeys.add(originalSite.siteKey());
                    }
                }
            }
        }

        return new LambdaWeaveSanityClassResult(
            expectedEligibleSites,
            rewrittenEligibleSites,
            remainingEligibleSiteKeys.size(),
            unexpectedRemovedSiteKeys.size(),
            remainingEligibleSiteKeys,
            unexpectedRemovedSiteKeys
        );
    }

    private Map<String, List<EncounteredLambdaSite>> collectStatelessLambdaSitesByMethod(byte[] classBytes) {
        Map<String, List<EncounteredLambdaSite>> sitesByMethod = new LinkedHashMap<>();
        ClassReader reader = new ClassReader(classBytes);
        String ownerInternalName = reader.getClassName();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                String methodKey = ownerInternalName + "::" + name + descriptor;
                List<EncounteredLambdaSite> methodSites = sitesByMethod.computeIfAbsent(methodKey, ignored -> new ArrayList<>());
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    private int lambdaOrdinalInMethod = 0;

                    @Override
                    public void visitInvokeDynamicInsn(String indyName, String indyDesc, Handle bsm, Object... bsmArgs) {
                        if (!isStatelessLambdaMetafactory(indyDesc, bsm, bsmArgs)) {
                            super.visitInvokeDynamicInsn(indyName, indyDesc, bsm, bsmArgs);
                            return;
                        }

                        Handle implHandle = (Handle) bsmArgs[1];
                        int ordinal = lambdaOrdinalInMethod++;
                        methodSites.add(new EncounteredLambdaSite(
                            LambdaMeta.buildSiteKey(
                                ownerInternalName,
                                name,
                                descriptor,
                                ordinal,
                                indyName,
                                indyDesc,
                                implHandle.getTag(),
                                implHandle.getOwner(),
                                implHandle.getName(),
                                implHandle.getDesc()
                            ),
                            buildStructuralKey(
                                ownerInternalName,
                                name,
                                descriptor,
                                indyName,
                                indyDesc,
                                implHandle
                            )
                        ));
                        super.visitInvokeDynamicInsn(indyName, indyDesc, bsm, bsmArgs);
                    }
                };
            }
        }, 0);
        return sitesByMethod;
    }

    private static boolean isStatelessLambdaMetafactory(String indyDesc, Handle bsm, Object[] bsmArgs) {
        if (bsm == null || !LAMBDA_METAFACTORY.equals(bsm.getOwner()) || !"metafactory".equals(bsm.getName())) {
            return false;
        }
        if (Type.getArgumentTypes(indyDesc).length != 0) {
            return false;
        }
        return bsmArgs != null
            && bsmArgs.length >= 3
            && bsmArgs[0] instanceof Type
            && bsmArgs[1] instanceof Handle
            && bsmArgs[2] instanceof Type;
    }

    private static String buildStructuralKey(
        String ownerInternalName,
        String methodName,
        String methodDescriptor,
        String indyName,
        String indyDesc,
        Handle implHandle
    ) {
        return ownerInternalName
            + "::"
            + methodName
            + methodDescriptor
            + "|"
            + indyName
            + "|"
            + indyDesc
            + "|"
            + implHandle.getTag()
            + "|"
            + implHandle.getOwner()
            + "::"
            + implHandle.getName()
            + implHandle.getDesc();
    }

    private record EncounteredLambdaSite(String siteKey, String structuralKey) {
    }
}
