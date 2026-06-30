package com.yourorg.jmoa.plugin.weave;

import com.example.tier2.Tier2FixtureHost;
import com.example.tier2.Tier2PrivateSyntheticFixture;
import com.example.tier2.Tier2PrivateSyntheticInterfaceFixture;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.ExclusionReason;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.runtime.RuntimeAdapterKind;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlan;
import com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlanResult;
import com.yourorg.jmoa.plugin.runtime.Tier2AdapterArtifact;
import com.yourorg.jmoa.plugin.runtime.Tier2PackageAdapterGenerator;
import com.yourorg.jmoa.plugin.scanner.LambdaScanner;
import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import jmoa.runtime.JmoaRuntimeSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaClassWeaverTest {

    @AfterEach
    void resetRuntime() {
        JmoaRuntimeSupport.reset();
    }

    @Test
    void rewritesOnlyEligibleTier1AndTier2Sites() throws Exception {
        File classFile = copyFixtureClass("com/example/tier2/Tier2WeavingFixture.class", "Tier2WeavingFixture.class");
        List<LambdaSite> sites = LambdaScanner.scanClassFile(classFile);

        LambdaSite tier1Site = findSiteByImplName(sites, "length");
        LambdaSite tier2Site = findSiteByImplName(sites, "decorate");
        LambdaSite untouchedSite = findSiteByImplName(sites, "trim");

        LambdaFilterResult filterResult = new LambdaFilterResult(List.of(
            LambdaFilterDecision.eligible(tier1Site.toMeta(), true, false, 5L, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC),
            LambdaFilterDecision.eligible(tier2Site.toMeta(), true, false, 3L, LambdaTier.TIER2, AccessResolver.Visibility.PACKAGE_PRIVATE),
            LambdaFilterDecision.excluded(untouchedSite.toMeta(), false, false, 0L, ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC)
        ));
        Tier1RuntimePlanResult runtimePlanResult = new Tier1RuntimePlanResult(
            List.of(new Tier1RuntimePlan(0, RuntimeAdapterKind.FUNCTION, filterResult.tier1Eligible().getFirst())),
            List.of()
        );
        LambdaWeavingPlan weavingPlan = new LambdaWeavingPlanBuilder().build(filterResult, runtimePlanResult);
        Tier2AdapterArtifact tier2Artifact = new Tier2PackageAdapterGenerator().generate(filterResult.tier2Eligible().getFirst(), Opcodes.V22);

        byte[] patchedBytes = new LambdaClassWeaver().weaveClass(classFile, weavingPlan, getClass().getClassLoader());
        CheckClassAdapter.verify(new ClassReader(patchedBytes), getClass().getClassLoader(), false, new PrintWriter(System.out));

        assertInvokeStaticFactoryRewrite(patchedBytes);
        assertTier2GetStaticRewrite(patchedBytes, tier2Artifact.internalName());
        assertEquals(1, countRemainingInvokeDynamicSites(patchedBytes), "only the untouched lambda should remain as invokedynamic");
        LambdaWeaveSanityClassResult sanity = new LambdaWeaveSanityChecker().verifyClass(
            Files.readAllBytes(classFile.toPath()),
            patchedBytes,
            weavingPlan
        );
        assertEquals(2, sanity.expectedEligibleSites());
        assertEquals(2, sanity.rewrittenEligibleSites());
        assertEquals(0, sanity.remainingEligibleSites());
        assertEquals(0, sanity.unexpectedRemovedSites());
    }

    @Test
    void patchedClassStillRunsWithTier1FactoryAndTier2Adapter() throws Throwable {
        File classFile = copyFixtureClass("com/example/tier2/Tier2WeavingFixture.class", "Tier2WeavingFixture.class");
        List<LambdaSite> sites = LambdaScanner.scanClassFile(classFile);

        LambdaSite tier1Site = findSiteByImplName(sites, "length");
        LambdaSite tier2Site = findSiteByImplName(sites, "decorate");
        LambdaSite untouchedSite = findSiteByImplName(sites, "trim");

        LambdaFilterResult filterResult = new LambdaFilterResult(List.of(
            LambdaFilterDecision.eligible(tier1Site.toMeta(), true, false, 5L, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC),
            LambdaFilterDecision.eligible(tier2Site.toMeta(), true, false, 3L, LambdaTier.TIER2, AccessResolver.Visibility.PACKAGE_PRIVATE),
            LambdaFilterDecision.excluded(untouchedSite.toMeta(), false, false, 0L, ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC)
        ));
        Tier1RuntimePlanResult runtimePlanResult = new Tier1RuntimePlanResult(
            List.of(new Tier1RuntimePlan(0, RuntimeAdapterKind.FUNCTION, filterResult.tier1Eligible().getFirst())),
            List.of()
        );
        LambdaWeavingPlan weavingPlan = new LambdaWeavingPlanBuilder().build(filterResult, runtimePlanResult);
        Tier2AdapterArtifact tier2Artifact = new Tier2PackageAdapterGenerator().generate(filterResult.tier2Eligible().getFirst(), Opcodes.V22);

        MethodHandle lengthHandle = MethodHandles.publicLookup().findVirtual(
            String.class,
            "length",
            MethodType.methodType(int.class)
        );
        JmoaRuntimeSupport.installFunctionTargets(new MethodHandle[]{lengthHandle});

        byte[] patchedBytes = new LambdaClassWeaver().weaveClass(classFile, weavingPlan, getClass().getClassLoader());

        ByteArrayClassLoader loader = new ByteArrayClassLoader(getClass().getClassLoader());
        loader.define("com.example.tier2.Tier2FixtureHost", loadClassBytes("com/example/tier2/Tier2FixtureHost.class"));
        loader.define("com.example.tier2.Tier2WeavingFixture", patchedBytes);
        Class<?> adapterClass = loader.define(
            tier2Artifact.internalName().replace('/', '.'),
            tier2Artifact.classBytes()
        );
        assertNotNull(adapterClass);

        Class<?> fixtureType = loader.loadClass("com.example.tier2.Tier2WeavingFixture");
        Object fixture = fixtureType.getDeclaredConstructor().newInstance();

        @SuppressWarnings("unchecked")
        Function<String, Integer> tier1Function = (Function<String, Integer>) invokeNoArg(fixtureType, fixture, "tier1Function");
        @SuppressWarnings("unchecked")
        Function<String, String> tier2Function = (Function<String, String>) invokeNoArg(fixtureType, fixture, "tier2Function");
        @SuppressWarnings("unchecked")
        Function<String, String> untouchedFunction = (Function<String, String>) invokeNoArg(fixtureType, fixture, "untouchedFunction");

        assertEquals(5, tier1Function.apply("abcde"));
        assertEquals("tier2-PATIENT", tier2Function.apply(" patient "));
        assertEquals("safe", untouchedFunction.apply(" safe "));
    }

    @Test
    void widensPrivateSyntheticTier2TargetsBeforeInvokingGeneratedAdapter() throws Throwable {
        File classFile = copyFixtureClass(
            "com/example/tier2/Tier2PrivateSyntheticFixture.class",
            "Tier2PrivateSyntheticFixture.class"
        );
        List<LambdaSite> sites = LambdaScanner.scanClassFile(classFile);
        LambdaSite tier2Site = findSiteByImplNamePrefix(sites, "lambda$");

        LambdaFilterResult filterResult = new LambdaFilterResult(List.of(
            LambdaFilterDecision.eligible(
                tier2Site.toMeta(),
                true,
                false,
                7L,
                LambdaTier.TIER2,
                AccessResolver.Visibility.PRIVATE
            )
        ));
        LambdaWeavingPlan weavingPlan = new LambdaWeavingPlanBuilder().build(
            filterResult,
            new Tier1RuntimePlanResult(List.of(), List.of())
        );
        Tier2AdapterArtifact tier2Artifact = new Tier2PackageAdapterGenerator().generate(
            filterResult.tier2Eligible().getFirst(),
            Opcodes.V22
        );

        byte[] patchedBytes = new LambdaClassWeaver().weaveClass(classFile, weavingPlan, getClass().getClassLoader());
        assertMethodAccess(
            patchedBytes,
            tier2Site.implHandle().getName(),
            tier2Site.implHandle().getDesc(),
            Opcodes.ACC_PRIVATE,
            false
        );

        ByteArrayClassLoader loader = new ByteArrayClassLoader(getClass().getClassLoader());
        Class<?> fixtureType = loader.define(
            Tier2PrivateSyntheticFixture.class.getName(),
            patchedBytes
        );
        Class<?> adapterClass = loader.define(
            tier2Artifact.internalName().replace('/', '.'),
            tier2Artifact.classBytes()
        );
        assertNotNull(adapterClass);

        Object fixture = fixtureType.getDeclaredConstructor().newInstance();
        @SuppressWarnings("unchecked")
        Predicate<String> predicate = (Predicate<String>) invokeNoArg(fixtureType, fixture, "tier2Predicate");

        assertTrue(predicate.test("patient"));
        assertFalse(predicate.test(" "));
    }

    @Test
    void sanityCheckerDoesNotFlagLaterUntouchedLambdaWhenEarlierSiteIsRewrittenInSameMethod() throws Exception {
        File classFile = copyFixtureClass(
            "com/example/tier2/Tier2OrdinalShiftFixture.class",
            "Tier2OrdinalShiftFixture.class"
        );
        List<LambdaSite> sites = LambdaScanner.scanClassFile(classFile);
        LambdaSite rewrittenSite = findSiteByImplName(sites, "decorate");
        LambdaSite untouchedSite = findSiteByImplName(sites, "trim");

        LambdaFilterResult filterResult = new LambdaFilterResult(List.of(
            LambdaFilterDecision.eligible(
                rewrittenSite.toMeta(),
                true,
                false,
                5L,
                LambdaTier.TIER2,
                AccessResolver.Visibility.PACKAGE_PRIVATE
            ),
            LambdaFilterDecision.excluded(
                untouchedSite.toMeta(),
                false,
                false,
                0L,
                ExclusionReason.NOT_OBSERVED,
                AccessResolver.Visibility.PUBLIC
            )
        ));
        LambdaWeavingPlan weavingPlan = new LambdaWeavingPlanBuilder().build(
            filterResult,
            new Tier1RuntimePlanResult(List.of(), List.of())
        );

        byte[] patchedBytes = new LambdaClassWeaver().weaveClass(classFile, weavingPlan, getClass().getClassLoader());
        LambdaWeaveSanityClassResult sanity = new LambdaWeaveSanityChecker().verifyClass(
            Files.readAllBytes(classFile.toPath()),
            patchedBytes,
            weavingPlan
        );

        assertEquals(1, sanity.expectedEligibleSites());
        assertEquals(1, sanity.rewrittenEligibleSites());
        assertEquals(0, sanity.remainingEligibleSites());
        assertEquals(0, sanity.unexpectedRemovedSites());
    }

    @Test
    void widensPrivateSyntheticTier2TargetsOnInterfacesToLegalPublicMethods() throws Throwable {
        File classFile = copyFixtureClass(
            "com/example/tier2/Tier2PrivateSyntheticInterfaceFixture.class",
            "Tier2PrivateSyntheticInterfaceFixture.class"
        );
        List<LambdaSite> sites = LambdaScanner.scanClassFile(classFile);
        LambdaSite tier2Site = findSiteByImplNamePrefix(sites, "lambda$");

        LambdaFilterResult filterResult = new LambdaFilterResult(List.of(
            LambdaFilterDecision.eligible(
                tier2Site.toMeta(),
                true,
                false,
                4L,
                LambdaTier.TIER2,
                AccessResolver.Visibility.PRIVATE
            )
        ));
        LambdaWeavingPlan weavingPlan = new LambdaWeavingPlanBuilder().build(
            filterResult,
            new Tier1RuntimePlanResult(List.of(), List.of())
        );
        Tier2AdapterArtifact tier2Artifact = new Tier2PackageAdapterGenerator().generate(
            filterResult.tier2Eligible().getFirst(),
            Opcodes.V22
        );

        byte[] patchedBytes = new LambdaClassWeaver().weaveClass(classFile, weavingPlan, getClass().getClassLoader());
        assertMethodAccess(
            patchedBytes,
            tier2Site.implHandle().getName(),
            tier2Site.implHandle().getDesc(),
            Opcodes.ACC_PUBLIC,
            true
        );
        assertMethodAccess(
            patchedBytes,
            tier2Site.implHandle().getName(),
            tier2Site.implHandle().getDesc(),
            Opcodes.ACC_PRIVATE,
            false
        );

        ByteArrayClassLoader loader = new ByteArrayClassLoader(getClass().getClassLoader());
        Class<?> fixtureType = loader.define(
            Tier2PrivateSyntheticInterfaceFixture.class.getName(),
            patchedBytes
        );
        Class<?> adapterClass = loader.define(
            tier2Artifact.internalName().replace('/', '.'),
            tier2Artifact.classBytes()
        );
        assertNotNull(adapterClass);

        @SuppressWarnings("unchecked")
        Predicate<String> predicate = (Predicate<String>) fixtureType.getMethod("tier2Predicate").invoke(null);
        assertTrue(predicate.test("patient"));
        assertFalse(predicate.test(" "));
    }

    private static File copyFixtureClass(String resourceName, String fileName) throws Exception {
        File tempDir = Files.createTempDirectory("jmoa-weaver").toFile();
        File classFile = new File(tempDir, fileName);
        Files.write(classFile.toPath(), loadClassBytes(resourceName));
        return classFile;
    }

    private static byte[] loadClassBytes(String resourceName) throws Exception {
        ClassLoader loader = LambdaClassWeaverTest.class.getClassLoader();
        try (InputStream is = loader.getResourceAsStream(resourceName)) {
            assertNotNull(is, resourceName);
            return is.readAllBytes();
        }
    }

    private static LambdaSite findSiteByImplName(List<LambdaSite> sites, String implName) {
        return sites.stream()
            .filter(site -> implName.equals(site.implHandle().getName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No lambda site found for impl " + implName));
    }

    private static LambdaSite findSiteByImplNamePrefix(List<LambdaSite> sites, String implNamePrefix) {
        return sites.stream()
            .filter(site -> site.implHandle().getName().startsWith(implNamePrefix))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No lambda site found for impl prefix " + implNamePrefix));
    }

    private static void assertInvokeStaticFactoryRewrite(byte[] classBytes) {
        AtomicInteger hits = new AtomicInteger();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                            && owner.equals("jmoa/runtime/JmoaFactory")
                            && methodName.equals("createFunction")
                            && methodDescriptor.equals("(I)Ljava/util/function/Function;")) {
                            hits.incrementAndGet();
                        }
                    }
                };
            }
        }, 0);
        assertEquals(1, hits.get());
    }

    private static void assertTier2GetStaticRewrite(byte[] classBytes, String adapterInternalName) {
        AtomicInteger hits = new AtomicInteger();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        if (opcode == Opcodes.GETSTATIC
                            && owner.equals(adapterInternalName)
                            && name.equals("INSTANCE")
                            && descriptor.equals("Ljava/util/function/Function;")) {
                            hits.incrementAndGet();
                        }
                    }
                };
            }
        }, 0);
        assertEquals(1, hits.get());
    }

    private static int countRemainingInvokeDynamicSites(byte[] classBytes) {
        AtomicInteger count = new AtomicInteger();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        if ("java/lang/invoke/LambdaMetafactory".equals(bootstrapMethodHandle.getOwner())) {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, 0);
        return count.get();
    }

    private static void assertMethodAccess(byte[] classBytes, String methodName, String methodDesc, int flag, boolean expected) {
        AtomicInteger access = new AtomicInteger(Integer.MIN_VALUE);
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int methodAccess, String name, String descriptor, String signature, String[] exceptions) {
                if (methodName.equals(name) && methodDesc.equals(descriptor)) {
                    access.set(methodAccess);
                }
                return super.visitMethod(methodAccess, name, descriptor, signature, exceptions);
            }
        }, 0);
        assertEquals(expected, (access.get() & flag) != 0);
    }

    private static Object invokeNoArg(Class<?> owner, Object target, String methodName) throws Exception {
        Method method = owner.getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        private ByteArrayClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Class<?> define(String binaryName, byte[] classBytes) {
            return defineClass(binaryName, classBytes, 0, classBytes.length);
        }
    }
}
