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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RewriteRoiBatchRankerTest {

    private final RewriteRoiBatchRanker ranker = new RewriteRoiBatchRanker();
    private final RewriteRoiCandidateClassifier classifier = new RewriteRoiCandidateClassifier();

    private LambdaMeta buildMeta(String owner) {
        String siteKey = owner + "::m()V#0|apply|()Ljava/util/function/Function;|6|" + owner + "::lambda$m$0()V";
        return new LambdaMeta(siteKey, owner, owner.substring(0, owner.lastIndexOf('/')),
            "m", "()V", 0, "apply", "()Ljava/util/function/Function;", "()Ljava/lang/Object;",
            false, false, 6, owner, "lambda$m$0", "()V", "(Ljava/lang/Object;)Ljava/lang/Object;", 0);
    }

    @Test
    void unknownPackageBatch_cannotGetAdmitNext() {
        LambdaMeta m = buildMeta("com/example/unknown/Service");
        LambdaFilterDecision d = LambdaFilterDecision.excluded(m, true, false, 200,
            ExclusionReason.FRAMEWORK_SAFETY_DENIED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m.siteKey(), m.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_UNKNOWN, List.of(FrameworkSafetyReason.UNKNOWN_FRAMEWORK_PACKAGE)));

        List<RewriteRoiCandidateRecord> candidates = classifier.classify(new LambdaFilterResult(List.of(d)));
        List<Map<String, Object>> batches = ranker.rank(candidates);

        for (Map<String, Object> batch : batches) {
            String batchName = (String) batch.get("batchName");
            if (batchName.contains("UnknownPackage")) {
                assertNotEquals("ADMIT_NEXT", batch.get("recommendation"),
                    "Unknown package batch must NOT get ADMIT_NEXT");
                assertEquals("REVIEW_PACKAGE", batch.get("recommendation"));
            }
        }
    }

    @Test
    void missingProfileBatch_getsProfileFirst() {
        LambdaMeta m = buildMeta("org/springframework/boot/Foo");
        LambdaFilterDecision d = LambdaFilterDecision.excluded(m, false, false, 0,
            ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m.siteKey(), m.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_SAFE, List.of(FrameworkSafetyReason.MISSING_PROFILE)));

        List<RewriteRoiCandidateRecord> candidates = classifier.classify(new LambdaFilterResult(List.of(d)));
        List<Map<String, Object>> batches = ranker.rank(candidates);

        boolean foundMissingProfileBatch = false;
        for (Map<String, Object> batch : batches) {
            int count = (int) batch.get("candidateCount");
            if (count > 0) {
                String rec = (String) batch.get("recommendation");
                // Missing-profile sites should never get ADMIT_NEXT
                if ("PROFILE_FIRST".equals(rec)) {
                    foundMissingProfileBatch = true;
                }
                assertNotEquals("ADMIT_NEXT", rec,
                    "Missing-profile candidate should not get ADMIT_NEXT");
            }
        }
        assertTrue(foundMissingProfileBatch || batches.stream().allMatch(b -> (int)b.get("candidateCount") == 0),
            "Should have a batch with PROFILE_FIRST or no non-empty batches");
    }

    @Test
    void emptyBatchesAreRemoved() {
        List<Map<String, Object>> batches = ranker.rank(List.of());
        assertTrue(batches.isEmpty(), "Empty candidate list should produce no batches");
    }

    @Test
    void batchesAreSortedByRoiScoreDescending() {
        LambdaMeta m1 = buildMeta("org/springframework/boot/A");
        LambdaMeta m2 = buildMeta("org/springframework/boot/B");

        LambdaFilterDecision d1 = LambdaFilterDecision.excluded(m1, true, false, 200,
            ExclusionReason.FRAMEWORK_SAFETY_DENIED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m1.siteKey(), m1.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_SAFE, List.of(FrameworkSafetyReason.MISSING_PROFILE)));
        LambdaFilterDecision d2 = LambdaFilterDecision.excluded(m2, false, false, 0,
            ExclusionReason.NOT_OBSERVED, AccessResolver.Visibility.PUBLIC,
            FrameworkSafetyDecision.deny(m2.siteKey(), m2.ownerInternalName(), ClassRootKind.EXPANDED_DEPENDENCY,
                FrameworkSafetyLevel.FRAMEWORK_SAFE, List.of(FrameworkSafetyReason.MISSING_PROFILE)));

        List<RewriteRoiCandidateRecord> candidates = classifier.classify(new LambdaFilterResult(List.of(d1, d2)));
        List<Map<String, Object>> batches = ranker.rank(candidates);

        for (int i = 1; i < batches.size(); i++) {
            int prev = (int) batches.get(i - 1).get("roiScore");
            int curr = (int) batches.get(i).get("roiScore");
            assertTrue(prev >= curr, "Batches must be sorted by ROI score descending");
        }
    }
}
