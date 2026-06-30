package com.yourorg.jmoa.plugin.dedup;

import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DeduplicationEngineTest {

    static class PackagePrivateHolder {
        static String packageHelper(String value) {
            return value.trim();
        }
    }

    static class PrivateSyntheticHolder {
        static java.util.function.Function<String, String> lambdaFactory() {
            return value -> value.trim();
        }
    }

    @Test
    public void testGroupingPublicMethods() {
        ClassNode cn = new ClassNode();
        cn.name = "com/example/MyClass";
        MethodNode mn = new MethodNode();
        InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
            "apply",
            "()Ljava/util/function/Function;",
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false),
            Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            new Handle(Opcodes.H_INVOKEVIRTUAL, "java/lang/String", "length", "()I", false),
            Type.getMethodType("(Ljava/lang/String;)Ljava/lang/Integer;")
        );

        LambdaSite site1 = new LambdaSite(
            new File("MyClass.class"), cn, mn, indy, 0,
            "apply", "()Ljava/util/function/Function;",
            Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            new Handle(Opcodes.H_INVOKEVIRTUAL, "java/lang/String", "length", "()I", false),
            Type.getMethodType("(Ljava/lang/String;)Ljava/lang/Integer;")
        );

        LambdaSite site2 = new LambdaSite(
            new File("OtherClass.class"), cn, mn, indy, 0,
            "apply", "()Ljava/util/function/Function;",
            Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            new Handle(Opcodes.H_INVOKEVIRTUAL, "java/lang/String", "length", "()I", false),
            Type.getMethodType("(Ljava/lang/String;)Ljava/lang/Integer;")
        );

        List<LambdaSite> sites = List.of(site1, site2);
        ClassLoader cl = getClass().getClassLoader();

        List<SharedGroup> groups = DeduplicationEngine.groupSites(sites, cl, true);
        assertEquals(1, groups.size(), "Should form exactly 1 group");
        SharedGroup group = groups.get(0);
        assertEquals(AccessTier.TIER1_PUBLIC_LOOKUP, group.accessTier(), "Public target should stay Tier 1");
        assertNull(group.targetPackageInternal(), "Public target should be global (null package)");
        assertFalse(group.needsAccessWidening(), "Public target does not need access widening");
        assertEquals(2, group.sites().size());
        assertTrue(group.synthClassName().contains("LF_String_length_"), "Name should match naming strategy");
    }

    @Test
    public void testGroupingPackagePrivateMethodsIntoTier2() {
        String owner = PackagePrivateHolder.class.getName().replace('.', '/');
        ClassNode cn1 = new ClassNode();
        cn1.name = owner;
        MethodNode mn1 = new MethodNode();
        InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
            "apply",
            "()Ljava/util/function/Function;",
            new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false),
            Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            new Handle(Opcodes.H_INVOKESTATIC, owner, "packageHelper", "(Ljava/lang/String;)Ljava/lang/String;", false),
            Type.getMethodType("(Ljava/lang/String;)Ljava/lang/String;")
        );

        LambdaSite site1 = new LambdaSite(
            new File("PackagePrivateA.class"), cn1, mn1, indy, 0,
            "apply", "()Ljava/util/function/Function;",
            Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            new Handle(Opcodes.H_INVOKESTATIC, owner, "packageHelper", "(Ljava/lang/String;)Ljava/lang/String;", false),
            Type.getMethodType("(Ljava/lang/String;)Ljava/lang/String;")
        );

        ClassNode cn2 = new ClassNode();
        cn2.name = owner;
        MethodNode mn2 = new MethodNode();
        LambdaSite site2 = new LambdaSite(
            new File("PackagePrivateB.class"), cn2, mn2, indy, 0,
            "apply", "()Ljava/util/function/Function;",
            Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
            new Handle(Opcodes.H_INVOKESTATIC, owner, "packageHelper", "(Ljava/lang/String;)Ljava/lang/String;", false),
            Type.getMethodType("(Ljava/lang/String;)Ljava/lang/String;")
        );

        List<SharedGroup> groups = DeduplicationEngine.groupSites(List.of(site1, site2), getClass().getClassLoader(), true);
        assertEquals(1, groups.size());
        SharedGroup group = groups.getFirst();
        assertEquals(AccessTier.TIER2_PACKAGE_DIRECT, group.accessTier());
        assertEquals(owner.substring(0, owner.lastIndexOf('/')), group.targetPackageInternal());
        assertFalse(group.needsAccessWidening());
    }

    @Test
    public void testGroupingPrivateSyntheticLambdaIntoTier2WithWidening() throws Exception {
        File classFile = new File(PrivateSyntheticHolder.class
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toURI()
        );
        File compiledClass = new File(classFile, PrivateSyntheticHolder.class.getName().replace('.', File.separatorChar) + ".class");
        List<LambdaSite> sites = com.yourorg.jmoa.plugin.scanner.LambdaScanner.scanClassFile(compiledClass);
        assertFalse(sites.isEmpty());

        Handle implHandle = sites.getFirst().implHandle();
        assertTrue(implHandle.getName().startsWith("lambda$"));

        List<SharedGroup> groups = DeduplicationEngine.groupSites(List.of(sites.getFirst(), sites.getFirst()), getClass().getClassLoader(), true);
        assertEquals(1, groups.size());
        SharedGroup group = groups.getFirst();
        assertEquals(AccessTier.TIER2_PACKAGE_DIRECT, group.accessTier());
        assertTrue(group.needsAccessWidening());
    }
}
