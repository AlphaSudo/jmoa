package com.yourorg.jmoa.plugin.runtime;

import com.example.tier2.Tier2FixtureHost;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Tier2PackageAdapterGeneratorTest {

    @Test
    void generatesSamePackageStaticAdapterWithDirectInvoke() throws Throwable {
        LambdaFilterDecision decision = decision(
            "static-tier2",
            "apply",
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            Opcodes.H_INVOKESTATIC,
            "com/example/tier2/Tier2FixtureHost",
            "decorate",
            "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );

        Tier2AdapterArtifact artifact = new Tier2PackageAdapterGenerator().generate(decision);
        assertSamMethodDescriptor(artifact.classBytes(), "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        assertDirectInvoke(artifact.classBytes(), Opcodes.INVOKESTATIC, "decorate");
        CheckClassAdapter.verify(new ClassReader(artifact.classBytes()), getClass().getClassLoader(), false, new PrintWriter(System.out));

        Class<?> adapterClass = MethodHandles.privateLookupIn(Tier2FixtureHost.class, MethodHandles.lookup()).defineClass(artifact.classBytes());
        @SuppressWarnings("unchecked")
        Function<String, String> function = (Function<String, String>) instanceField(adapterClass, "INSTANCE").get(null);

        assertEquals("tier2-PATIENT", function.apply(" patient "));
    }

    @Test
    void generatesSharedPackageSamAdapterWithOneFieldPerSite() throws Throwable {
        LambdaFilterDecision first = decision(
            "static-tier2-a",
            "apply",
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            Opcodes.H_INVOKESTATIC,
            "com/example/tier2/Tier2FixtureHost",
            "decorate",
            "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );
        LambdaFilterDecision second = decisionWithOrdinal(
            "static-tier2-b",
            2,
            "apply",
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            Opcodes.H_INVOKESTATIC,
            "com/example/tier2/Tier2FixtureHost",
            "decorate",
            "(Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/lang/String;)Ljava/lang/String;"
        );

        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator()
            .generatePackageSamAdapters(List.of(first, second), Opcodes.V22);

        assertEquals(1, artifacts.size());
        Tier2AdapterArtifact artifact = artifacts.get(0);
        assertEquals("com/example/tier2/JmoaPkgAdapters$Function", artifact.internalName());
        assertSamMethodDescriptor(artifact.classBytes(), "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        assertDirectInvoke(artifact.classBytes(), Opcodes.INVOKESTATIC, "decorate");
        CheckClassAdapter.verify(new ClassReader(artifact.classBytes()), getClass().getClassLoader(), false, new PrintWriter(System.out));

        Class<?> adapterClass = MethodHandles.privateLookupIn(Tier2FixtureHost.class, MethodHandles.lookup()).defineClass(artifact.classBytes());
        @SuppressWarnings("unchecked")
        Function<String, String> firstFunction =
            (Function<String, String>) instanceField(adapterClass, Tier2AdapterNamingStrategy.fieldName(first.meta())).get(null);
        @SuppressWarnings("unchecked")
        Function<String, String> secondFunction =
            (Function<String, String>) instanceField(adapterClass, Tier2AdapterNamingStrategy.fieldName(second.meta())).get(null);

        assertEquals("tier2-FIRST", firstFunction.apply(" first "));
        assertEquals("tier2-SECOND", secondFunction.apply(" second "));
    }

    @Test
    void generatesSamePackageVirtualAdapterWithDirectInvoke() throws Throwable {
        LambdaFilterDecision decision = decision(
            "virtual-tier2",
            "apply",
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            Opcodes.H_INVOKEVIRTUAL,
            "com/example/tier2/Tier2FixtureHost",
            "label",
            "()Ljava/lang/String;",
            "(Lcom/example/tier2/Tier2FixtureHost;)Ljava/lang/String;"
        );

        Tier2AdapterArtifact artifact = new Tier2PackageAdapterGenerator().generate(decision);
        assertSamMethodDescriptor(artifact.classBytes(), "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        assertDirectInvoke(artifact.classBytes(), Opcodes.INVOKEVIRTUAL, "label");

        Class<?> adapterClass = MethodHandles.privateLookupIn(Tier2FixtureHost.class, MethodHandles.lookup()).defineClass(artifact.classBytes());
        @SuppressWarnings("unchecked")
        Function<Tier2FixtureHost, String> function =
            (Function<Tier2FixtureHost, String>) instanceField(adapterClass, "INSTANCE").get(null);

        assertEquals("patient-tier2", function.apply(new Tier2FixtureHost()));
    }

    @Test
    void preservesErasedConsumerSignatureWhileUnboxingPrimitiveArguments() throws Throwable {
        recordedIds().clear();
        LambdaFilterDecision decision = decision(
            "consumer-tier2",
            "accept",
            "()Ljava/util/function/Consumer;",
            "(Ljava/lang/Object;)V",
            Opcodes.H_INVOKESTATIC,
            "com/example/tier2/Tier2FixtureHost",
            "recordId",
            "(I)V",
            "(Ljava/lang/Integer;)V"
        );

        Tier2AdapterArtifact artifact = new Tier2PackageAdapterGenerator().generate(decision);
        assertSamMethodDescriptor(artifact.classBytes(), "accept", "(Ljava/lang/Object;)V");
        assertDirectInvoke(artifact.classBytes(), Opcodes.INVOKESTATIC, "recordId");

        Class<?> adapterClass = MethodHandles.privateLookupIn(Tier2FixtureHost.class, MethodHandles.lookup()).defineClass(artifact.classBytes());
        @SuppressWarnings("unchecked")
        Consumer<Integer> consumer = (Consumer<Integer>) instanceField(adapterClass, "INSTANCE").get(null);

        consumer.accept(42);
        assertEquals(List.of(42), recordedIds());
    }

    @Test
    void generatesSamePackageInterfaceAdapterWithDirectInvoke() throws Throwable {
        LambdaFilterDecision decision = decision(
            "interface-tier2",
            "apply",
            "()Ljava/util/function/Function;",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            Opcodes.H_INVOKEINTERFACE,
            "com/example/tier2/Tier2FixtureHost$Tier2View",
            "externalId",
            "()Ljava/lang/String;",
            "(Lcom/example/tier2/Tier2FixtureHost$Tier2View;)Ljava/lang/String;"
        );

        Tier2AdapterArtifact artifact = new Tier2PackageAdapterGenerator().generate(decision);
        assertSamMethodDescriptor(artifact.classBytes(), "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
        assertDirectInvoke(artifact.classBytes(), Opcodes.INVOKEINTERFACE, "externalId");

        Class<?> adapterClass = MethodHandles.privateLookupIn(Tier2FixtureHost.class, MethodHandles.lookup()).defineClass(artifact.classBytes());
        @SuppressWarnings("unchecked")
        Function<Object, String> function = (Function<Object, String>) instanceField(adapterClass, "INSTANCE").get(null);

        assertEquals("patient-44", function.apply(newTier2View("patient-44")));
    }

    @Test
    void generatesConstructorBackedSupplierWithErasedReturnSignature() throws Throwable {
        LambdaFilterDecision decision = decision(
            "constructor-tier2",
            "get",
            "()Ljava/util/function/Supplier;",
            "()Ljava/lang/Object;",
            Opcodes.H_NEWINVOKESPECIAL,
            "com/example/tier2/Tier2FixtureHost$Tier2Record",
            "<init>",
            "()V",
            "()Lcom/example/tier2/Tier2FixtureHost$Tier2Record;"
        );

        Tier2AdapterArtifact artifact = new Tier2PackageAdapterGenerator().generate(decision);
        assertSamMethodDescriptor(artifact.classBytes(), "get", "()Ljava/lang/Object;");
        assertDirectInvoke(artifact.classBytes(), Opcodes.INVOKESPECIAL, "<init>");

        Class<?> adapterClass = MethodHandles.privateLookupIn(Tier2FixtureHost.class, MethodHandles.lookup()).defineClass(artifact.classBytes());
        @SuppressWarnings("unchecked")
        Supplier<Object> supplier = (Supplier<Object>) instanceField(adapterClass, "INSTANCE").get(null);

        Object record = supplier.get();
        assertEquals("constructed-tier2", invokeNoArgString(record, "marker"));
    }

    private static LambdaFilterDecision decision(
        String siteKey,
        String indyName,
        String indyFactoryDesc,
        String samMethodTypeDesc,
        int implTag,
        String implOwner,
        String implName,
        String implDesc,
        String instantiatedMethodTypeDesc
    ) {
        return decisionWithOrdinal(
            siteKey,
            1,
            indyName,
            indyFactoryDesc,
            samMethodTypeDesc,
            implTag,
            implOwner,
            implName,
            implDesc,
            instantiatedMethodTypeDesc
        );
    }

    private static LambdaFilterDecision decisionWithOrdinal(
        String siteKey,
        int siteOrdinal,
        String indyName,
        String indyFactoryDesc,
        String samMethodTypeDesc,
        int implTag,
        String implOwner,
        String implName,
        String implDesc,
        String instantiatedMethodTypeDesc
    ) {
        LambdaMeta meta = new LambdaMeta(
            siteKey,
            "com/example/tier2/Tier2FixtureHost",
            "com/example/tier2",
            "buildTier2",
            "()V",
            siteOrdinal,
            indyName,
            indyFactoryDesc,
            samMethodTypeDesc,
            false,
            false,
            implTag,
            implOwner,
            implTag == Opcodes.H_INVOKEINTERFACE,
            implName,
            implDesc,
            instantiatedMethodTypeDesc,
            2L
        );
        return LambdaFilterDecision.eligible(meta, true, false, 2L, LambdaTier.TIER2, AccessResolver.Visibility.PACKAGE_PRIVATE);
    }

    private static void assertDirectInvoke(byte[] classBytes, int opcode, String methodName) {
        AtomicBoolean matched = new AtomicBoolean(false);
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int seenOpcode, String owner, String seenMethodName, String desc, boolean isInterface) {
                        if (seenOpcode == opcode && seenMethodName.equals(methodName)) {
                            matched.set(true);
                        }
                    }
                };
            }
        }, 0);
        assertTrue(matched.get(), "expected direct invoke opcode " + opcode + " for " + methodName);
    }

    private static void assertSamMethodDescriptor(byte[] classBytes, String methodName, String expectedDescriptor) {
        AtomicBoolean matched = new AtomicBoolean(false);
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals(methodName) && descriptor.equals(expectedDescriptor)) {
                    matched.set(true);
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, 0);
        assertTrue(matched.get(), "expected generated SAM method " + methodName + expectedDescriptor);
    }

    private static Field instanceField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> recordedIds() throws ReflectiveOperationException {
        Field field = Tier2FixtureHost.class.getDeclaredField("RECORDED_IDS");
        field.setAccessible(true);
        return (List<Integer>) field.get(null);
    }

    private static Object newTier2View(String externalId) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.example.tier2.Tier2FixtureHost$Tier2ViewImpl");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(externalId);
    }

    private static String invokeNoArgString(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (String) method.invoke(target);
    }
}
