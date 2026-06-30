package com.yourorg.jmoa.plugin.roi;

import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.dedup.AccessResolver;
import com.yourorg.jmoa.plugin.filter.ExclusionReason;
import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import com.yourorg.jmoa.plugin.filter.LambdaFilterResult;
import com.yourorg.jmoa.plugin.filter.LambdaTier;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyDecision;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyLevel;
import com.yourorg.jmoa.plugin.framework.FrameworkSafetyReason;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RewriteRoiCandidateClassifierTest {

    private final RewriteRoiCandidateClassifier classifier = new RewriteRoiCandidateClassifier();

    // --- Helper builders ---

    private LambdaMeta buildMeta(String owner, String implOwner, String implName,
                                  boolean capturing, boolean serializable) {
        String siteKey = owner + "::method()V#0|apply|()Ljava/util/function/Function;|6|" + implOwner + "::" + implName + "()V";
        return new LambdaMeta(
            siteKey, owner, owner.substring(0, owner.lastIndexOf('/')),
            "method", "()V", 0,
            "apply", "()Ljava/util/function/Function;", "()Ljava/lang/Object;",
            capturing, serializable,
            6, implOwner, implName, "()V",
            "(Ljava/lang/Object;)Ljava/lang/Object;", 0
        );
    }

    private FrameworkSafetyDecision allowedFramework(String siteKey, String owner) {
        return FrameworkSafetyDecision.allow(siteKey, owner, ClassRootKind.EXPANDED_DEPENDENCY,
            FrameworkSafetyLevel.FRAMEWORK_SAFE,
            List.of(FrameworkSafetyReason.SAFE_EXPANDED_DEPENDENCY_PREFIX,
                FrameworkSafetyReason.COLD_PROFILED_SITE,
                FrameworkSafetyReason.SAFE_SAM_KIND));
    }

    private FrameworkSafetyDecision deniedMissingProfile(String siteKey, String owner) {
        return FrameworkSafetyDecision.deny(siteKey, owner, ClassRootKind.EXPANDED_DEPENDENCY,
            FrameworkSafetyLevel.FRAMEWORK_SAFE,
            List.of(FrameworkSafetyReason.MISSING_PROFILE));
    }

    private FrameworkSafetyDecision deniedUnknownPackage(String siteKey, String owner) {
        return FrameworkSafetyDecision.deny(siteKey, owner, ClassRootKind.EXPANDED_DEPENDENCY,
            FrameworkSafetyLevel.FRAMEWORK_UNKNOWN,
            List.of(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE));
    }

    private FrameworkSafetyDecision deniedProxy(String siteKey, String owner) {
        return FrameworkSafetyDecision.deny(siteKey, owner, ClassRootKind.EXPANDED_DEPENDENCY,
            FrameworkSafetyLevel.FRAMEWORK_UNKNOWN,
            List.of(FrameworkSafetyReason.PROXY_OR_ENHANCER_PACKAGE));
    }

    // --- Tests ---

    @Test
    void tierS_assignedToAllowedEligibleFrameworkSites() {
        LambdaMeta meta = buildMeta("org/springframework/boot/Foo", "org/springframework/boot/Foo", "lambda$init$0", false, false);
        FrameworkSafetyDecision fsd = allowedFramework(meta.siteKey(), meta.ownerInternalName());
        LambdaFilterDecision decision = LambdaFilterDecision.eligible(
            meta, true, false, 500, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC, fsd);

        List<RewriteRoiCandidateRecord> result = classifier.classify(new LambdaFilterResult(List.of(decision)));

        assertEquals(1, result.size());
        assertEquals(RewriteRoiSafetyTier.TIER_S, result.get(0).safetyTier());
        assertEquals(RewriteRoiAdmissionPrerequisite.NONE, result.get(0).admissionPrerequisite());
        assertEquals("ELIGIBLE", result.get(0).currentDecision());
    }

    @Test
    void tierD_assignedToCapturingSites() {
        LambdaMeta meta = buildMeta("org/springframework/boot/Bar", "org/springframework/boot/Bar", "lambda$run$0", true, false);
        FrameworkSafetyDecision fsd = FrameworkSafetyDecision.deny(meta.siteKey(), meta.ownerInternalName(),
            ClassRootKind.EXPANDED_DEPENDENCY, FrameworkSafetyLevel.FRAMEWORK_SAFE,
            List.of(FrameworkSafetyReason.CAPTURING_LAMBDA));
        LambdaFilterDecision decision = LambdaFilterDecision.excluded(
            meta, true, false, 100, ExclusionReason.CAPTURING, AccessResolver.Visibility.PUBLIC, fsd);

        List<RewriteRoiCandidateRecord> result = classifier.classify(new LambdaFilterResult(List.of(decision)));

        assertEquals(1, result.size());
        assertEquals(RewriteRoiSafetyTier.TIER_D, result.get(0).safetyTier());
        assertEquals(RewriteRoiAdmissionPrerequisite.NEVER, result.get(0).admissionPrerequisite());
    }

    @Test
    void tierD_assignedToProxyPackages() {
        LambdaMeta meta = buildMeta("org/springframework/cglib/proxy/Enhancer", "org/springframework/cglib/proxy/Enhancer", "lambda$create$0", false, false);
        FrameworkSafetyDecision fsd = deniedProxy(meta.siteKey(), meta.ownerInternalName());
        LambdaFilterDecision decision = LambdaFilterDecision.excluded(
            meta, false, false, 0, ExclusionReason.FRAMEWORK_SAFETY_DENIED, AccessResolver.Visibility.PUBLIC, fsd);

        List<RewriteRoiCandidateRecord> result = classifier.classify(new LambdaFilterResult(List.of(decision)));

        assertEquals(RewriteRoiSafetyTier.TIER_D, result.get(0).safetyTier());
    }

    @Test
    void tierB_assignedToMissingProfileSites() {
        LambdaMeta meta = buildMeta("org/springframework/boot/Baz", "org/springframework/boot/Baz", "lambda$exec$0", false, false);
        FrameworkSafetyDecision fsd = deniedMissingProfile(meta.siteKey(), meta.ownerInternalName());
        LambdaFilterDecision decision = LambdaFilterDecision.excluded(
            meta, false, false, 0, ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC, fsd);

        List<RewriteRoiCandidateRecord> result = classifier.classify(new LambdaFilterResult(List.of(decision)));

        assertEquals(RewriteRoiSafetyTier.TIER_B, result.get(0).safetyTier());
        assertEquals(RewriteRoiAdmissionPrerequisite.PROFILE_REQUIRED, result.get(0).admissionPrerequisite());
    }

    @Test
    void tierC_unknownPackage_prerequisiteIsPackageAllowlist() {
        LambdaMeta meta = buildMeta("com/example/custom/Handler", "com/example/custom/Handler", "lambda$handle$0", false, false);
        FrameworkSafetyDecision fsd = deniedUnknownPackage(meta.siteKey(), meta.ownerInternalName());
        LambdaFilterDecision decision = LambdaFilterDecision.excluded(
            meta, true, false, 200, ExclusionReason.FRAMEWORK_SAFETY_DENIED, AccessResolver.Visibility.PUBLIC, fsd);

        List<RewriteRoiCandidateRecord> result = classifier.classify(new LambdaFilterResult(List.of(decision)));

        assertEquals(RewriteRoiSafetyTier.TIER_C, result.get(0).safetyTier());
        assertEquals(RewriteRoiAdmissionPrerequisite.PACKAGE_ALLOWLIST_REQUIRED, result.get(0).admissionPrerequisite());
    }

    @Test
    void wouldBeTierConfidence_mediumForTwoDenialReasons() {
        // NOT_OBSERVED exclusion + MISSING_PROFILE safety = 2 reasons → MEDIUM confidence
        LambdaMeta meta = buildMeta("org/springframework/boot/Qux", "org/springframework/boot/Qux", "lambda$run$0", false, false);
        FrameworkSafetyDecision fsd = deniedMissingProfile(meta.siteKey(), meta.ownerInternalName());
        LambdaFilterDecision decision = LambdaFilterDecision.excluded(
            meta, false, false, 0, ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC, fsd);

        List<RewriteRoiCandidateRecord> result = classifier.classify(new LambdaFilterResult(List.of(decision)));

        assertEquals("MEDIUM", result.get(0).wouldBeTierConfidence());
    }

    @Test
    void applicationSitesAreExcludedFromCandidateInventory() {
        LambdaMeta meta = buildMeta("com/pro/service/Foo", "com/pro/service/Foo", "lambda$run$0", false, false);
        FrameworkSafetyDecision fsd = FrameworkSafetyDecision.application(meta.siteKey(), meta.ownerInternalName());
        LambdaFilterDecision decision = LambdaFilterDecision.eligible(meta, true, false, 500, LambdaTier.TIER1, AccessResolver.Visibility.PUBLIC);

        List<RewriteRoiCandidateRecord> result = classifier.classify(new LambdaFilterResult(List.of(decision)));

        assertTrue(result.isEmpty(), "Application sites should not appear in framework candidate inventory");
    }

    @Test
    void profileHeat_unknown_whenNotObservedAndNotInferredCold() {
        LambdaMeta meta = buildMeta("org/springframework/data/Repo", "org/springframework/data/Repo", "lambda$find$0", false, false);
        FrameworkSafetyDecision fsd = deniedMissingProfile(meta.siteKey(), meta.ownerInternalName());
        LambdaFilterDecision decision = LambdaFilterDecision.excluded(
            meta, false, false, 0, ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC, fsd);

        List<RewriteRoiCandidateRecord> result = classifier.classify(new LambdaFilterResult(List.of(decision)));

        assertEquals("UNKNOWN", result.get(0).profileHeat());
        assertTrue(result.get(0).missingProfile());
    }
}
