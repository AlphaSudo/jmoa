package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import com.yourorg.jmoa.plugin.weave.LambdaWeaveTarget;
import com.yourorg.jmoa.plugin.weave.LambdaWeavingPlan;
import com.yourorg.jmoa.plugin.weave.LambdaWeavingPlanBuilder;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Track T4: ROOT_PACKAGE_SIGNATURE consolidates Tier-2 sites by physical
 * root + package + erased signature. Compactness is retained inside a root,
 * while identical package+signature groups in different roots never share an
 * adapter — so multi-root fan-out and cross-root adapter references are
 * impossible by construction.
 */
class Tier2RootPackageSignatureTest {

    @Test
    void samePackageAndSignatureInDifferentRootsProduceOneAdapterPerRoot() {
        LambdaFilterDecision root0Site = site("owner/SiteA#0", "com/example", 1);
        LambdaFilterDecision root1Site = site("owner2/SiteB#0", "com/example", 2);
        Map<String, Integer> ordinals = Map.of(
                "owner/SiteA#0", 0,
                "owner2/SiteB#0", 1);

        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator(true)
                .generateRootPackageSignatureAdapters(List.of(root0Site, root1Site), ordinals, Opcodes.V22);

        assertEquals(2, artifacts.size(), "two roots must never share a root-aware adapter");
        assertEquals(2, artifacts.stream().map(Tier2AdapterArtifact::internalName).distinct().count());
        assertTrue(artifacts.stream().anyMatch(a -> a.internalName().contains("$R0_")));
        assertTrue(artifacts.stream().anyMatch(a -> a.internalName().contains("$R1_")));
        assertTrue(artifacts.stream().allMatch(a -> a.internalName().startsWith("com/example/J$R")));
    }

    @Test
    void sameRootSitesMergeAndWeaveTargetsUseRootAwareNames() {
        LambdaFilterDecision a = site("owner/SiteA#0", "com/example", 1);
        LambdaFilterDecision b = site("owner/SiteA#1", "com/example", 2);
        Map<String, Integer> ordinals = Map.of("owner/SiteA#0", 0, "owner/SiteA#1", 0);

        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator(true)
                .generateRootPackageSignatureAdapters(List.of(a, b), ordinals, Opcodes.V22);
        assertEquals(1, artifacts.size(), "same root + package + signature must merge");

        LambdaWeavingPlan plan = new LambdaWeavingPlanBuilder(false, false, false, true, false, true, ordinals)
                .build(new LambdaFilterResult(List.of(a, b)), new com.yourorg.jmoa.plugin.runtime.Tier1RuntimePlanResult(List.of(), List.of()));
        for (LambdaFilterDecision decision : List.of(a, b)) {
            LambdaWeaveTarget target = plan.targetFor(decision.meta().siteKey());
            assertNotNull(target);
            assertEquals(LambdaWeaveTarget.WeaveKind.TIER2_ADAPTER, target.kind());
            assertEquals(artifacts.getFirst().internalName(), target.tier2AdapterInternalName(),
                    "weave targets and generated artifacts must agree on the root-aware name");
        }
    }

    @Test
    void packagePrivateTargetsRemainInTheirPackageAndReferenceClosureIsExact() {
        LambdaFilterDecision packagePrivate = site(
                "private/Site#0", "com/example/privatepkg", 1,
                "()Ljava/util/function/Function;", "(Ljava/lang/Object;)Ljava/lang/Object;",
                AccessResolver.Visibility.PACKAGE_PRIVATE);
        Map<String, Integer> ordinals = Map.of(packagePrivate.meta().siteKey(), 2);
        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator(true)
                .generateRootPackageSignatureAdapters(List.of(packagePrivate), ordinals, Opcodes.V22);
        LambdaWeavingPlan plan = new LambdaWeavingPlanBuilder(false, false, false, true, false, true, ordinals)
                .build(new LambdaFilterResult(List.of(packagePrivate)), new Tier1RuntimePlanResult(List.of(), List.of()));

        assertEquals(1, artifacts.size());
        assertTrue(artifacts.getFirst().internalName().startsWith("com/example/privatepkg/J$R2_"),
                "package-private implementation access requires the adapter to stay package-local");
        assertEquals(artifacts.getFirst().internalName(),
                plan.targetFor(packagePrivate.meta().siteKey()).tier2AdapterInternalName(),
                "every generated reference must close over an emitted artifact");
    }

    @Test
    void rootOrdinalKeepsDifferentRootsDistinctAndGenerationIsDeterministic() {
        LambdaFilterDecision a = site("owner/SiteA#0", "com/example", 1);
        Map<String, Integer> ordinals = Map.of("owner/SiteA#0", 3);

        String name = Tier2AdapterNamingStrategy.rootPackageSignatureInternalName(a.meta(), 3);
        String other = Tier2AdapterNamingStrategy.rootPackageSignatureInternalName(a.meta(), 4);
        assertNotEquals(name, other);
        assertTrue(name.contains("$R3_"));

        List<Tier2AdapterArtifact> first = new Tier2PackageAdapterGenerator(true)
                .generateRootPackageSignatureAdapters(List.of(a), ordinals, Opcodes.V22);
        List<Tier2AdapterArtifact> second = new Tier2PackageAdapterGenerator(true)
                .generateRootPackageSignatureAdapters(List.of(a), ordinals, Opcodes.V22);
        assertEquals(first.getFirst().internalName(), second.getFirst().internalName());
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                first.getFirst().classBytes(), second.getFirst().classBytes(),
                "root-aware generation must be byte-deterministic");
    }

    @Test
    void compactRootAwareNamesUseNinetySixBitSignatureTagsAndSeparateSignatures() {
        LambdaFilterDecision objectShape = site("owner/Object#0", "com/example", 1);
        LambdaFilterDecision intShape = site(
                "owner/Int#0", "com/example", 2,
                "()Ljava/util/function/IntUnaryOperator;", "(I)I", AccessResolver.Visibility.PUBLIC);
        Map<String, Integer> ordinals = Map.of(objectShape.meta().siteKey(), 0, intShape.meta().siteKey(), 0);

        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator(true)
                .generateRootPackageSignatureAdapters(List.of(objectShape, intShape), ordinals, Opcodes.V22);

        assertEquals(2, artifacts.size());
        assertEquals(2, artifacts.stream().map(Tier2AdapterArtifact::internalName).distinct().count(),
                "different erased signatures must not collide inside one root/package");
        assertTrue(artifacts.stream().allMatch(a -> a.internalName().matches("com/example/J\\$R0_[0-9a-f]{24}")),
                "new T4 names use a 96-bit signature tag while historical A-D names stay unchanged");
    }

    @Test
    void missingRootIdentityFailsClosedInGenerationAndWeaving() {
        LambdaFilterDecision site = site("owner/Missing#0", "com/example", 1);
        assertThrows(IllegalArgumentException.class, () -> new Tier2PackageAdapterGenerator(true)
                .generateRootPackageSignatureAdapters(List.of(site), Map.of(), Opcodes.V22));
        assertThrows(IllegalArgumentException.class, () -> new LambdaWeavingPlanBuilder(
                false, false, false, true, false, true, Map.of())
                .build(new LambdaFilterResult(List.of(site)),
                        new Tier1RuntimePlanResult(List.of(), List.of())));
    }

    @Test
    void packageAndErasedSignatureRemainPartOfTheRootAwareKey() {
        LambdaFilterDecision packageA = site("owner/A#0", "com/a", 1);
        LambdaFilterDecision packageB = site("owner/B#0", "com/b", 1);
        Map<String, Integer> roots = Map.of("owner/A#0", 0, "owner/B#0", 0);
        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator(false)
                .generateRootPackageSignatureAdapters(List.of(packageA, packageB), roots, Opcodes.V22);
        assertEquals(2, artifacts.size(), "split packages in one root must not collide");
        assertNotEquals(artifacts.get(0).internalName(), artifacts.get(1).internalName());
    }

    @Test
    void noCrossRootTargetsAndEveryTargetHasExactlyOneRootLocalArtifact() {
        LambdaFilterDecision root0a = site("r0/A#0", "com/example", 1);
        LambdaFilterDecision root0b = site("r0/B#0", "com/example", 2);
        LambdaFilterDecision root1a = site("r1/A#0", "com/example", 3);
        Map<String, Integer> ordinals = Map.of(
                root0a.meta().siteKey(), 0, root0b.meta().siteKey(), 0, root1a.meta().siteKey(), 1);
        List<LambdaFilterDecision> decisions = List.of(root0a, root0b, root1a);
        List<Tier2AdapterArtifact> artifacts = new Tier2PackageAdapterGenerator(true)
                .generateRootPackageSignatureAdapters(decisions, ordinals, Opcodes.V22);
        LambdaWeavingPlan plan = new LambdaWeavingPlanBuilder(false, false, false, true, false, true, ordinals)
                .build(new LambdaFilterResult(decisions), new Tier1RuntimePlanResult(List.of(), List.of()));

        Set<String> artifactNames = artifacts.stream().map(Tier2AdapterArtifact::internalName)
                .collect(java.util.stream.Collectors.toSet());
        for (LambdaFilterDecision decision : decisions) {
            String target = plan.targetFor(decision.meta().siteKey()).tier2AdapterInternalName();
            assertTrue(artifactNames.contains(target), "generated-reference closure failed for " + decision.meta().siteKey());
            assertTrue(target.contains("$R" + ordinals.get(decision.meta().siteKey()) + "_"),
                    "target must reference only the adapter assigned to its physical root");
        }
        assertEquals(2, artifacts.size(), "root 0 sites merge locally; identical root 1 package gets its own artifact");
    }

    @Test
    void existingCurrentPackagePackageSamAndPackageSignatureShapesStayPinned() {
        LambdaFilterDecision objectA = site("shape/A#0", "com/example", 1);
        LambdaFilterDecision objectB = site("shape/B#0", "com/example", 2);
        LambdaFilterDecision intA = site(
                "shape/C#0", "com/example", 3,
                "()Ljava/util/function/IntUnaryOperator;", "(I)I", AccessResolver.Visibility.PUBLIC);
        List<LambdaFilterDecision> decisions = List.of(objectA, objectB, intA);
        Tier2PackageAdapterGenerator generator = new Tier2PackageAdapterGenerator(false);

        assertEquals(3, decisions.stream().map(generator::generate).count(), "CURRENT shape changed");
        assertEquals(1, generator.generatePackageAdapters(decisions, Opcodes.V22).size(), "PACKAGE shape changed");
        assertEquals(2, generator.generatePackageSamAdapters(decisions, Opcodes.V22).size(), "PACKAGE_SAM shape changed");
        assertEquals(2, generator.generatePackageSignatureAdapters(decisions, Opcodes.V22).size(),
                "PACKAGE_SIGNATURE shape changed");
    }

    private static LambdaFilterDecision site(String siteKey, String pkg, int ordinal) {
        return site(siteKey, pkg, ordinal,
                "()Ljava/util/function/Function;", "(Ljava/lang/Object;)Ljava/lang/Object;",
                AccessResolver.Visibility.PUBLIC);
    }

    private static LambdaFilterDecision site(
            String siteKey,
            String pkg,
            int ordinal,
            String indyFactoryDesc,
            String samMethodDesc,
            AccessResolver.Visibility visibility) {
        LambdaMeta meta = new LambdaMeta(
                siteKey,
                pkg + "/Owner",
                pkg,
                "run",
                "()V",
                ordinal,
                "apply",
                indyFactoryDesc,
                samMethodDesc,
                false,
                false,
                Opcodes.H_INVOKESTATIC,
                pkg + "/Helper",
                false,
                "helper",
                samMethodDesc,
                samMethodDesc,
                1L);
        return LambdaFilterDecision.eligible(meta, true, false, 1L, LambdaTier.TIER2, visibility);
    }
}
