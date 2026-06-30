package com.yourorg.jmoa.plugin.weave;

import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class LambdaWeavingClassVisitor extends ClassVisitor {

    private static final String LAMBDA_METAFACTORY = "java/lang/invoke/LambdaMetafactory";
    private static final String JMOA_FACTORY = "jmoa/runtime/JmoaFactory";

    private final String ownerInternalName;
    private final LambdaWeavingPlan weavingPlan;

    public LambdaWeavingClassVisitor(
        int api,
        ClassVisitor delegate,
        String ownerInternalName,
        LambdaWeavingPlan weavingPlan
    ) {
        super(api, delegate);
        this.ownerInternalName = ownerInternalName;
        this.weavingPlan = weavingPlan;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new MethodVisitor(api, delegate) {
            private int lambdaOrdinalInMethod = 0;

            @Override
            public void visitInvokeDynamicInsn(String indyName, String indyDesc, Handle bsm, Object... bsmArgs) {
                if (!isStatelessLambdaMetafactory(indyDesc, bsm, bsmArgs)) {
                    super.visitInvokeDynamicInsn(indyName, indyDesc, bsm, bsmArgs);
                    return;
                }

                Handle implHandle = (Handle) bsmArgs[1];
                String siteKey = LambdaMeta.buildSiteKey(
                    ownerInternalName,
                    name,
                    descriptor,
                    lambdaOrdinalInMethod++,
                    indyName,
                    indyDesc,
                    implHandle.getTag(),
                    implHandle.getOwner(),
                    implHandle.getName(),
                    implHandle.getDesc()
                );

                LambdaWeaveTarget target = weavingPlan.targetFor(siteKey);
                if (target == null) {
                    super.visitInvokeDynamicInsn(indyName, indyDesc, bsm, bsmArgs);
                    return;
                }

                if (target.kind() == LambdaWeaveTarget.WeaveKind.TIER1_FACTORY) {
                    super.visitLdcInsn(target.slotId());
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        JMOA_FACTORY,
                        target.factoryMethodName(),
                        target.factoryMethodDescriptor(),
                        false
                    );
                    return;
                }

                super.visitFieldInsn(
                    Opcodes.GETSTATIC,
                    target.tier2AdapterInternalName(),
                    target.tier2AdapterFieldName(),
                    Type.getReturnType(indyDesc).getDescriptor()
                );
            }
        };
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
}
