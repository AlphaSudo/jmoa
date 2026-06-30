package com.yourorg.jmoa.plugin.filter;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.JmoaExecutionMode;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyConfig;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LambdaFilterFrameworkSafetyTest {

    @Test
    void modeAIgnoresFrameworkClassifier() {
        LambdaMeta meta = meta("org/springframework/context/SafeThing");
        LambdaProfileIndex profileIndex = new LambdaProfileIndex(Map.of(meta.siteKey(), 1L), Set.of(), Set.of());

        LambdaFilterResult result = new LambdaFilter(
            LambdaFilterConfig.defaults()
                .withExecutionMode(JmoaExecutionMode.MODE_A)
                .withFrameworkSafetyConfig(new FrameworkSafetyConfig(
                    true, true, true, false,
                    List.of("org.springframework.context"),
                    List.of("com.fasterxml.jackson"),
                    10_000,
                    FrameworkSafetyConfig.defaults().safeSamInterfaces(),
                    Set.of()
                )),
            ignored -> AccessResolver.Visibility.PUBLIC,
            LambdaSourceIndex.empty()
        ).filter(List.of(meta), profileIndex);

        assertEquals(1, result.tier1Eligible().size());
    }

    @Test
    void modeCDeniesUnsafeExpandedFrameworkSite() {
        LambdaMeta meta = meta("org/acme/framework/GeneratedThing");
        LambdaProfileIndex profileIndex = new LambdaProfileIndex(Map.of(meta.siteKey(), 1L), Set.of(), Set.of());
        LambdaSourceIndex sourceIndex = LambdaSourceIndex.of(Map.of(
            meta.ownerInternalName(),
            new ClassRootDescriptor(new File("."), false, ClassRootKind.EXPANDED_DEPENDENCY)
        ));

        LambdaFilterResult result = new LambdaFilter(
            LambdaFilterConfig.defaults()
                .withExecutionMode(JmoaExecutionMode.MODE_C)
                .withFrameworkSafetyConfig(new FrameworkSafetyConfig(
                    true, true, true, false,
                    List.of("org.springframework.context"),
                    List.of("com.fasterxml.jackson"),
                    10_000,
                    FrameworkSafetyConfig.defaults().safeSamInterfaces(),
                    Set.of()
                )),
            ignored -> AccessResolver.Visibility.PUBLIC,
            sourceIndex
        ).filter(List.of(meta), profileIndex);

        assertEquals(ExclusionReason.FRAMEWORK_SAFETY_DENIED, result.excluded().getFirst().exclusionReason());
    }

    private static LambdaMeta meta(String ownerInternalName) {
        return new LambdaMeta(
            ownerInternalName + "::method()V#0|get|()Ljava/util/function/Supplier;|8|java/util/LinkedHashMap::<init>()V",
            ownerInternalName,
            ownerInternalName.substring(0, ownerInternalName.lastIndexOf('/')),
            "method",
            "()V",
            0,
            "get",
            "()Ljava/util/function/Supplier;",
            "()Ljava/lang/Object;",
            false,
            false,
            Opcodes.H_NEWINVOKESPECIAL,
            "java/util/LinkedHashMap",
            "<init>",
            "()V",
            "()Ljava/lang/Object;",
            0L
        );
    }
}
