package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.synth.ProjectAwareClassWriter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Tier1RuntimeClassGenerator {

    public static final String GENERATED_INTERNAL_NAME = "jmoa/runtime/JmoaRuntime";
    public static final int DEFAULT_CLASSFILE_VERSION = Opcodes.V22;

    public byte[] generate(Tier1RuntimePlanResult planResult, ClassLoader projectClassLoader) {
        return generate(planResult, projectClassLoader, DEFAULT_CLASSFILE_VERSION);
    }

    public byte[] generate(
        Tier1RuntimePlanResult planResult,
        ClassLoader projectClassLoader,
        int classFileVersion
    ) {
        Map<RuntimeAdapterKind, List<Tier1RuntimePlan>> plansByKind = groupByKind(planResult.supportedPlans());

        ProjectAwareClassWriter cw = new ProjectAwareClassWriter(
            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
            projectClassLoader
        );
        cw.visit(
            classFileVersion,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
            GENERATED_INTERNAL_NAME,
            null,
            "java/lang/Object",
            null
        );

        for (RuntimeAdapterKind kind : RuntimeAdapterKind.values()) {
            cw.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldName(kind),
                "[Ljava/lang/invoke/MethodHandle;",
                null,
                null
            ).visitEnd();
            cw.visitField(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                cacheFieldName(kind),
                cacheDescriptor(kind),
                null,
                null
            ).visitEnd();
        }

        generateConstructor(cw);
        generateClinit(cw, plansByKind);
        generateCountMethod(cw, RuntimeAdapterKind.FUNCTION, "functionCount");
        generateCountMethod(cw, RuntimeAdapterKind.PREDICATE, "predicateCount");
        generateCountMethod(cw, RuntimeAdapterKind.SUPPLIER, "supplierCount");
        generateCountMethod(cw, RuntimeAdapterKind.CONSUMER, "consumerCount");
        generateCountMethod(cw, RuntimeAdapterKind.BI_CONSUMER, "biConsumerCount");
        generateInvokeFunction(cw);
        generateInvokePredicate(cw);
        generateInvokeSupplier(cw);
        generateInvokeConsumer(cw);
        generateInvokeBiConsumer(cw);
        generateCreateMethod(cw, RuntimeAdapterKind.FUNCTION, "createFunction");
        generateCreateMethod(cw, RuntimeAdapterKind.PREDICATE, "createPredicate");
        generateCreateMethod(cw, RuntimeAdapterKind.SUPPLIER, "createSupplier");
        generateCreateMethod(cw, RuntimeAdapterKind.CONSUMER, "createConsumer");
        generateCreateMethod(cw, RuntimeAdapterKind.BI_CONSUMER, "createBiConsumer");

        cw.visitEnd();
        return cw.toByteArray();
    }

    public void writeTo(File outputDirectory, byte[] classBytes) throws IOException {
        File target = new File(outputDirectory, GENERATED_INTERNAL_NAME + ".class");
        if (target.getParentFile() != null) {
            target.getParentFile().mkdirs();
        }
        Files.write(target.toPath(), classBytes);
    }

    private void generateConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateClinit(ClassWriter cw, Map<RuntimeAdapterKind, List<Tier1RuntimePlan>> plansByKind) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/invoke/MethodHandles",
            "publicLookup",
            "()Ljava/lang/invoke/MethodHandles$Lookup;",
            false
        );
        mv.visitVarInsn(Opcodes.ASTORE, 0);

        for (RuntimeAdapterKind kind : RuntimeAdapterKind.values()) {
            List<Tier1RuntimePlan> plans = plansByKind.getOrDefault(kind, List.of());
            pushInt(mv, plans.size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/invoke/MethodHandle");
            mv.visitFieldInsn(Opcodes.PUTSTATIC, GENERATED_INTERNAL_NAME, fieldName(kind), "[Ljava/lang/invoke/MethodHandle;");

            for (Tier1RuntimePlan plan : plans) {
                emitTargetHandleStore(mv, plan);
            }

            mv.visitFieldInsn(Opcodes.GETSTATIC, GENERATED_INTERNAL_NAME, fieldName(kind), "[Ljava/lang/invoke/MethodHandle;");
            mv.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "jmoa/runtime/JmoaRuntimeSupport",
                installMethodName(kind),
                "([Ljava/lang/invoke/MethodHandle;)V",
                false
            );

            pushInt(mv, plans.size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, cacheComponentInternalName(kind));
            mv.visitFieldInsn(Opcodes.PUTSTATIC, GENERATED_INTERNAL_NAME, cacheFieldName(kind), cacheDescriptor(kind));

            for (Tier1RuntimePlan plan : plans) {
                mv.visitFieldInsn(Opcodes.GETSTATIC, GENERATED_INTERNAL_NAME, cacheFieldName(kind), cacheDescriptor(kind));
                pushInt(mv, plan.slotId());
                mv.visitTypeInsn(Opcodes.NEW, adapterInternalName(kind));
                mv.visitInsn(Opcodes.DUP);
                pushInt(mv, plan.slotId());
                mv.visitMethodInsn(
                    Opcodes.INVOKESPECIAL,
                    adapterInternalName(kind),
                    "<init>",
                    "(I)V",
                    false
                );
                mv.visitInsn(Opcodes.AASTORE);
            }
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void emitTargetHandleStore(MethodVisitor mv, Tier1RuntimePlan plan) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, GENERATED_INTERNAL_NAME, fieldName(plan.adapterKind()), "[Ljava/lang/invoke/MethodHandle;");
        pushInt(mv, plan.slotId());
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        int tag = plan.decision().meta().implTag();
        if (tag == Opcodes.H_NEWINVOKESPECIAL) {
            mv.visitLdcInsn(Type.getObjectType(plan.decision().meta().implOwner()));
            emitMethodTypeFactory(mv, plan);
            mv.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodHandles$Lookup",
                "findConstructor",
                "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                false
            );
        } else {
            mv.visitLdcInsn(Type.getObjectType(plan.decision().meta().implOwner()));
            mv.visitLdcInsn(plan.decision().meta().implName());
            emitMethodTypeFactory(mv, plan);
            switch (tag) {
                case Opcodes.H_INVOKESTATIC -> mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandles$Lookup",
                    "findStatic",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                    false
                );
                case Opcodes.H_INVOKEVIRTUAL -> mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandles$Lookup",
                    "findVirtual",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                    false
                );
                case Opcodes.H_INVOKEINTERFACE -> mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandles$Lookup",
                    "findVirtual",
                    "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;",
                    false
                );
                default -> throw new IllegalArgumentException("Unsupported Tier 1 impl tag: " + tag);
            }
        }

        // Normalise varargs targets like Arrays.asList to fixed arity before we
        // expose them through erased SAM adapters. Without this, Object-typed
        // adapter calls can treat an incoming array as a single varargs element.
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/invoke/MethodHandle",
            "asFixedArity",
            "()Ljava/lang/invoke/MethodHandle;",
            false
        );

        mv.visitInsn(Opcodes.AASTORE);
    }

    private void emitMethodTypeFactory(MethodVisitor mv, Tier1RuntimePlan plan) {
        int tag = plan.decision().meta().implTag();
        String descriptor = tag == Opcodes.H_NEWINVOKESPECIAL
            ? Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(plan.decision().meta().implDesc()))
            : plan.decision().meta().implDesc();
        mv.visitLdcInsn(descriptor);
        mv.visitLdcInsn(Type.getObjectType(plan.decision().meta().implOwner()));
        mv.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "java/lang/Class",
            "getClassLoader",
            "()Ljava/lang/ClassLoader;",
            false
        );
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "java/lang/invoke/MethodType",
            "fromMethodDescriptorString",
            "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;",
            false
        );
    }

    private void generateCountMethod(ClassWriter cw, RuntimeAdapterKind kind, String methodName) {
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            methodName,
            "()I",
            null,
            null
        );
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, GENERATED_INTERNAL_NAME, fieldName(kind), "[Ljava/lang/invoke/MethodHandle;");
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateInvokeFunction(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "invokeFunction",
            "(ILjava/lang/Object;)Ljava/lang/Object;",
            null,
            null
        );
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "jmoa/runtime/JmoaRuntimeSupport",
            "invokeFunction",
            "(ILjava/lang/Object;)Ljava/lang/Object;",
            false
        );
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateInvokePredicate(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "invokePredicate",
            "(ILjava/lang/Object;)Z",
            null,
            null
        );
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "jmoa/runtime/JmoaRuntimeSupport",
            "invokePredicate",
            "(ILjava/lang/Object;)Z",
            false
        );
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateInvokeSupplier(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "invokeSupplier",
            "(I)Ljava/lang/Object;",
            null,
            null
        );
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "jmoa/runtime/JmoaRuntimeSupport",
            "invokeSupplier",
            "(I)Ljava/lang/Object;",
            false
        );
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateInvokeConsumer(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "invokeConsumer",
            "(ILjava/lang/Object;)V",
            null,
            null
        );
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "jmoa/runtime/JmoaRuntimeSupport",
            "invokeConsumer",
            "(ILjava/lang/Object;)V",
            false
        );
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateInvokeBiConsumer(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "invokeBiConsumer",
            "(ILjava/lang/Object;Ljava/lang/Object;)V",
            null,
            null
        );
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "jmoa/runtime/JmoaRuntimeSupport",
            "invokeBiConsumer",
            "(ILjava/lang/Object;Ljava/lang/Object;)V",
            false
        );
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateCreateMethod(ClassWriter cw, RuntimeAdapterKind kind, String methodName) {
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            methodName,
            "(I)" + cacheElementDescriptor(kind),
            null,
            null
        );
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, GENERATED_INTERNAL_NAME, cacheFieldName(kind), cacheDescriptor(kind));
        mv.visitVarInsn(Opcodes.ILOAD, 0);
        mv.visitInsn(Opcodes.AALOAD);
        mv.visitTypeInsn(Opcodes.CHECKCAST, cacheComponentInternalName(kind));
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private Map<RuntimeAdapterKind, List<Tier1RuntimePlan>> groupByKind(List<Tier1RuntimePlan> plans) {
        Map<RuntimeAdapterKind, List<Tier1RuntimePlan>> grouped = new EnumMap<>(RuntimeAdapterKind.class);
        for (Tier1RuntimePlan plan : plans) {
            grouped.computeIfAbsent(plan.adapterKind(), ignored -> new ArrayList<>()).add(plan);
        }
        return grouped;
    }

    private static String fieldName(RuntimeAdapterKind kind) {
        return kind.name() + "_TARGETS";
    }

    private static String cacheFieldName(RuntimeAdapterKind kind) {
        return kind.name() + "_CACHE";
    }

    private static String cacheDescriptor(RuntimeAdapterKind kind) {
        return "[" + cacheElementDescriptor(kind);
    }

    private static String cacheElementDescriptor(RuntimeAdapterKind kind) {
        return switch (kind) {
            case FUNCTION -> "Ljava/util/function/Function;";
            case PREDICATE -> "Ljava/util/function/Predicate;";
            case SUPPLIER -> "Ljava/util/function/Supplier;";
            case CONSUMER -> "Ljava/util/function/Consumer;";
            case BI_CONSUMER -> "Ljava/util/function/BiConsumer;";
        };
    }

    private static String cacheComponentInternalName(RuntimeAdapterKind kind) {
        return switch (kind) {
            case FUNCTION -> "java/util/function/Function";
            case PREDICATE -> "java/util/function/Predicate";
            case SUPPLIER -> "java/util/function/Supplier";
            case CONSUMER -> "java/util/function/Consumer";
            case BI_CONSUMER -> "java/util/function/BiConsumer";
        };
    }

    private static String adapterInternalName(RuntimeAdapterKind kind) {
        return switch (kind) {
            case FUNCTION -> "jmoa/runtime/JmoaFunctionAdapter";
            case PREDICATE -> "jmoa/runtime/JmoaPredicateAdapter";
            case SUPPLIER -> "jmoa/runtime/JmoaSupplierAdapter";
            case CONSUMER -> "jmoa/runtime/JmoaConsumerAdapter";
            case BI_CONSUMER -> "jmoa/runtime/JmoaBiConsumerAdapter";
        };
    }

    private static String installMethodName(RuntimeAdapterKind kind) {
        return switch (kind) {
            case FUNCTION -> "installFunctionTargets";
            case PREDICATE -> "installPredicateTargets";
            case SUPPLIER -> "installSupplierTargets";
            case CONSUMER -> "installConsumerTargets";
            case BI_CONSUMER -> "installBiConsumerTargets";
        };
    }

    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(switch (value) {
                case -1 -> Opcodes.ICONST_M1;
                case 0 -> Opcodes.ICONST_0;
                case 1 -> Opcodes.ICONST_1;
                case 2 -> Opcodes.ICONST_2;
                case 3 -> Opcodes.ICONST_3;
                case 4 -> Opcodes.ICONST_4;
                default -> Opcodes.ICONST_5;
            });
        } else if (value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }
}
