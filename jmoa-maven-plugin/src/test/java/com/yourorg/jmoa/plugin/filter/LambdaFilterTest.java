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

class LambdaFilterTest {

    @Test
    void excludesCapturingSites() {
        LambdaMeta meta = meta(
            "com/example/AppService",
            "capturing-site",
            true,
            false,
            "java/lang/String",
            "length",
            "()I"
        );

        LambdaFilterResult result = filter(meta, LambdaProfileIndex.empty(), AccessResolver.Visibility.PUBLIC);

        assertEquals(1, result.excluded().size());
        assertEquals(ExclusionReason.CAPTURING, result.excluded().getFirst().exclusionReason());
    }

    @Test
    void excludesHotObservedSites() {
        LambdaMeta meta = meta(
            "com/example/AppService",
            "hot-site",
            false,
            false,
            "java/lang/String",
            "length",
            "()I"
        );
        LambdaProfileIndex profileIndex = new LambdaProfileIndex(
            Map.of(meta.siteKey(), 25L),
            Set.of(),
            Set.of()
        );

        LambdaFilterResult result = filter(meta, profileIndex, AccessResolver.Visibility.PUBLIC);

        assertEquals(ExclusionReason.HOT_SITE, result.excluded().getFirst().exclusionReason());
    }

    @Test
    void classifiesObservedPublicSitesAsTier1() {
        LambdaMeta meta = meta(
            "com/example/AppService",
            "tier1-site",
            false,
            false,
            "java/lang/String",
            "length",
            "()I"
        );
        LambdaProfileIndex profileIndex = new LambdaProfileIndex(
            Map.of(meta.siteKey(), 2L),
            Set.of(),
            Set.of()
        );

        LambdaFilterResult result = filter(meta, profileIndex, AccessResolver.Visibility.PUBLIC);

        assertEquals(1, result.tier1Eligible().size());
        assertEquals(0, result.tier2Eligible().size());
    }

    @Test
    void classifiesPackagePrivateSitesAsTier2() {
        String owner = "com/yourorg/jmoa/plugin/filter/LambdaFilterTest";
        LambdaMeta meta = meta(
            "com/example/AppService",
            "tier2-site",
            false,
            false,
            owner,
            "packageHelper",
            "()Ljava/lang/String;"
        );
        LambdaFilterResult result = filter(meta, observed(meta), AccessResolver.Visibility.PACKAGE_PRIVATE);

        assertEquals(1, result.tier2Eligible().size());
        assertEquals(AccessResolver.Visibility.PACKAGE_PRIVATE, result.tier2Eligible().getFirst().accessVisibility());
    }

    @Test
    void excludesPrivateNonSyntheticTargetsFromTier2() {
        LambdaMeta meta = meta(
            "com/example/AppService",
            "private-site",
            false,
            false,
            "com/yourorg/jmoa/plugin/filter/LambdaFilterTest",
            "privateHelper",
            "()Ljava/lang/String;"
        );

        LambdaFilterResult result = filter(meta, observed(meta), AccessResolver.Visibility.PRIVATE);

        assertEquals(1, result.excluded().size());
        assertEquals(ExclusionReason.ACCESS_DENIED, result.excluded().getFirst().exclusionReason());
    }

    @Test
    void excludesFrameworkOwnedSitesBeforeAccessResolution() {
        LambdaMeta meta = meta(
            "org/springframework/example/BeanFactory",
            "framework-site",
            false,
            false,
            "org/springframework/example/BeanFactory",
            "frameworkHelper",
            "()Ljava/lang/Object;"
        );

        LambdaFilterResult result = new LambdaFilter(
            LambdaFilterConfig.defaults().withFrameworkPackageExclusions(List.of("org.springframework")).withHotInvocationThreshold(10L),
            ignored -> AccessResolver.Visibility.PUBLIC
        ).filter(List.of(meta), LambdaProfileIndex.empty());

        assertEquals(ExclusionReason.FRAMEWORK_EXCLUDED, result.excluded().getFirst().exclusionReason());
    }

    @Test
    void infersColdUnobservedSitesWhenClassIsCold() {
        LambdaMeta meta = meta(
            "com/example/ColdService",
            "cold-site",
            false,
            false,
            "java/lang/String",
            "trim",
            "()Ljava/lang/String;"
        );
        LambdaProfileIndex profileIndex = new LambdaProfileIndex(
            Map.of(),
            Set.of(),
            Set.of("com/example/ColdService")
        );

        LambdaFilterResult result = filter(meta, profileIndex, AccessResolver.Visibility.PUBLIC);

        assertEquals(1, result.tier1Eligible().size());
        assertEquals(1, result.inferredColdSiteCount());
    }

    @Test
    void tracksRulePercentages() {
        LambdaMeta capturing = meta("com/example/A", "capturing", true, false, "java/lang/String", "length", "()I");
        LambdaMeta serializable = meta("com/example/A", "serializable", false, true, "java/lang/String", "length", "()I");

        LambdaFilterResult result = new LambdaFilter(
            new LambdaFilterConfig(10_000, true, true, List.of(), JmoaExecutionMode.MODE_A, FrameworkSafetyConfig.defaults(), false, false),
            ignored -> AccessResolver.Visibility.PUBLIC
        ).filter(List.of(capturing, serializable), LambdaProfileIndex.empty());

        assertEquals(50.0d, result.exclusionPercentages().get(ExclusionReason.CAPTURING));
        assertEquals(50.0d, result.exclusionPercentages().get(ExclusionReason.SERIALIZABLE));
    }

    @Test
    void excludesTier1FrameworkSitesWhenDiagnosticFlagIsEnabled() {
        LambdaMeta meta = meta(
            "org/springframework/context/SafeFrameworkFixture",
            "framework-tier1-site",
            false,
            false,
            "java/lang/String",
            "trim",
            "()Ljava/lang/String;"
        );
        LambdaFilterConfig config = LambdaFilterConfig.defaults()
            .withExecutionMode(JmoaExecutionMode.MODE_C)
            .withFrameworkSafetyConfig(new FrameworkSafetyConfig(
                true,
                true,
                true,
                false,
                List.of("org.springframework.context"),
                List.of("org.hibernate"),
                10_000L,
                FrameworkSafetyConfig.defaults().safeSamInterfaces(),
                Set.of()
            ))
            .withDiagnosticTierDisables(true, false);
        LambdaSourceIndex sourceIndex = LambdaSourceIndex.of(Map.of(
            meta.ownerInternalName(),
            new ClassRootDescriptor(new File("."), false, ClassRootKind.EXPANDED_DEPENDENCY)
        ));

        LambdaFilterResult result = new LambdaFilter(config, ignored -> AccessResolver.Visibility.PUBLIC, sourceIndex)
            .filter(List.of(meta), observed(meta));

        assertEquals(1, result.excluded().size());
        assertEquals(ExclusionReason.FRAMEWORK_TIER_DIAGNOSTIC_DISABLED, result.excluded().getFirst().exclusionReason());
    }

    @Test
    void excludesTier2FrameworkSitesWhenDiagnosticFlagIsEnabled() {
        String owner = "org/springframework/context/SafeFrameworkFixture";
        LambdaMeta meta = meta(
            owner,
            "framework-tier2-site",
            false,
            false,
            owner,
            "packageHelper",
            "()Ljava/lang/String;"
        );
        LambdaFilterConfig config = LambdaFilterConfig.defaults()
            .withExecutionMode(JmoaExecutionMode.MODE_C)
            .withFrameworkSafetyConfig(new FrameworkSafetyConfig(
                true,
                true,
                true,
                false,
                List.of("org.springframework.context"),
                List.of("org.hibernate"),
                10_000L,
                FrameworkSafetyConfig.defaults().safeSamInterfaces(),
                Set.of()
            ))
            .withDiagnosticTierDisables(false, true);
        LambdaSourceIndex sourceIndex = LambdaSourceIndex.of(Map.of(
            meta.ownerInternalName(),
            new ClassRootDescriptor(new File("."), false, ClassRootKind.EXPANDED_DEPENDENCY)
        ));

        LambdaFilterResult result = new LambdaFilter(config, ignored -> AccessResolver.Visibility.PACKAGE_PRIVATE, sourceIndex)
            .filter(List.of(meta), observed(meta));

        assertEquals(1, result.excluded().size());
        assertEquals(ExclusionReason.FRAMEWORK_TIER_DIAGNOSTIC_DISABLED, result.excluded().getFirst().exclusionReason());
    }

    private static LambdaFilterResult filter(
        LambdaMeta meta,
        LambdaProfileIndex profileIndex,
        AccessResolver.Visibility visibility
    ) {
        return new LambdaFilter(LambdaFilterConfig.defaults().withHotInvocationThreshold(10L), ignored -> visibility)
            .filter(List.of(meta), profileIndex);
    }

    private static LambdaProfileIndex observed(LambdaMeta meta) {
        return new LambdaProfileIndex(
            Map.of(meta.siteKey(), 1L),
            Set.of(),
            Set.of()
        );
    }

    private static LambdaMeta meta(
        String ownerInternalName,
        String marker,
        boolean capturing,
        boolean serializable,
        String implOwner,
        String implName,
        String implDesc
    ) {
        String enclosingMethodName = "method$" + marker;
        String enclosingMethodDesc = "()V";
        String indyName = "apply";
        String indyFactoryDesc = capturing
            ? "(Ljava/lang/Object;)Ljava/util/function/Function;"
            : "()Ljava/util/function/Function;";
        String siteKey = LambdaMeta.buildSiteKey(
            ownerInternalName,
            enclosingMethodName,
            enclosingMethodDesc,
            0,
            indyName,
            indyFactoryDesc,
            Opcodes.H_INVOKEVIRTUAL,
            implOwner,
            implName,
            implDesc
        );
        return new LambdaMeta(
            siteKey,
            ownerInternalName,
            ownerInternalName.contains("/") ? ownerInternalName.substring(0, ownerInternalName.lastIndexOf('/')) : "",
            enclosingMethodName,
            enclosingMethodDesc,
            0,
            indyName,
            indyFactoryDesc,
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            capturing,
            serializable,
            Opcodes.H_INVOKEVIRTUAL,
            implOwner,
            implName,
            implDesc,
            "(Ljava/lang/String;)Ljava/lang/Object;",
            0L
        );
    }

    @SuppressWarnings("unused")
    private static String privateHelper() {
        return "ok";
    }

    @SuppressWarnings("unused")
    static String packageHelper() {
        return "ok";
    }
}
