package com.yourorg.jmoa.plugin.synth;

import com.yourorg.jmoa.plugin.dedup.AccessPlan;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.dedup.AccessTier;
import com.yourorg.jmoa.plugin.dedup.DeduplicationKey;
import com.yourorg.jmoa.plugin.dedup.SharedGroup;
import com.yourorg.jmoa.plugin.scanner.LambdaScanner;
import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class ClassPatcherTest {

    public static int runLambda(String input) {
        Function<String, Integer> f = String::length;
        return f.apply(input);
    }

    @Test
    public void testPatchClass() throws Exception {
        String classResourceName = ClassPatcherTest.class.getName().replace('.', '/') + ".class";
        ClassLoader loader = ClassPatcherTest.class.getClassLoader();
        
        File tempDir = Files.createTempDirectory("jmoa-test").toFile();
        File classFile = new File(tempDir, "ClassPatcherTest.class");
        
        try (InputStream is = loader.getResourceAsStream(classResourceName)) {
            assertNotNull(is);
            Files.copy(is, classFile.toPath());
        }

        List<LambdaSite> sites = LambdaScanner.scanClassFile(classFile);
        assertFalse(sites.isEmpty());

        LambdaSite lengthSite = null;
        for (LambdaSite site : sites) {
            if ("length".equals(site.implHandle().getName())) {
                lengthSite = site;
                break;
            }
        }
        assertNotNull(lengthSite);

        DeduplicationKey key = new DeduplicationKey(
                "java/util/function/Function",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL,
                "java/lang/String",
                "length",
                "()I",
                "(Ljava/lang/String;)Ljava/lang/Integer;"
        );

        SharedGroup group = new SharedGroup(
                key,
                "LF_String_length_synth",
                List.of(lengthSite),
                new AccessPlan(
                    AccessTier.TIER1_PUBLIC_LOOKUP,
                    AccessResolver.Visibility.PUBLIC,
                    false,
                    null,
                    "test"
                )
        );

        Map<LambdaSite, SharedGroup> siteToGroupMap = Map.of(lengthSite, group);

        byte[] patchedBytes = ClassPatcher.patchClass(
                classFile,
                List.of(lengthSite),
                siteToGroupMap,
                loader
        );

        assertNotNull(patchedBytes);

        ClassReader cr = new ClassReader(patchedBytes);
        CheckClassAdapter.verify(cr, loader, false, new java.io.PrintWriter(System.out));
    }
}
