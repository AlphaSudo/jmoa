package com.yourorg.jmoa.plugin.evidence;

import com.yourorg.jmoa.plugin.evidence.EvidenceModels.ConfirmationReport;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.PairResult;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.VarianceCategory;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.VarianceClassification;

import java.util.ArrayList;
import java.util.List;

public final class EvidenceVarianceClassifier {

    public VarianceClassification classify(ConfirmationReport confirmation) {
        List<VarianceCategory> categories = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        if (confirmation.pairResults().isEmpty()) {
            categories.add(VarianceCategory.UNKNOWN);
            reasons.add("no paired evidence available");
            return new VarianceClassification(categories, reasons);
        }

        long medianPss = confirmation.medianPssDeltaKb();
        long medianPrivateDirty = confirmation.medianPrivateDirtyDeltaKb();
        long medianHeap = confirmation.medianHeapPssDeltaKb();
        long medianAnon = confirmation.medianAnonymousRwDeltaKb();
        long medianExec = confirmation.medianAnonymousExecutableDeltaKb();

        if (medianPss > 1024 && medianPrivateDirty > 1024 && medianAnon > 1024) {
            categories.add(VarianceCategory.ANONYMOUS_RW_ALLOCATOR);
            reasons.add("PSS, Private_Dirty, and anonymous_rw all increased");
        }
        if (medianPss > 1024 && medianPrivateDirty > 1024 && medianHeap > 1024) {
            boolean heapUsedFlat = confirmation.pairResults().stream()
                .mapToLong(PairResult::deltaHeapUsedKb)
                .map(Math::abs)
                .max()
                .orElse(0) < 1024;
            if (heapUsedFlat) {
                categories.add(VarianceCategory.HEAP_PAGE_TOUCH);
                reasons.add("heap PSS increased while heap used stayed near flat");
            }
        }
        if (medianExec > 512) {
            categories.add(VarianceCategory.CODECACHE_JIT);
            reasons.add("anonymous executable/code mapping increased");
        }
        long pssBytes = medianPss * 1024L;
        long currentDelta = confirmation.medianMemoryCurrentDeltaBytes();
        if (Math.abs(currentDelta - pssBytes) > 4L * 1024L * 1024L) {
            categories.add(VarianceCategory.CGROUP_MEMORY_CURRENT_DIVERGENCE);
            reasons.add("memory.current delta diverges from PSS delta by more than 4 MB");
        }
        if (categories.isEmpty()) {
            categories.add(VarianceCategory.UNKNOWN);
            reasons.add("no known variance pattern matched");
        }
        return new VarianceClassification(categories, reasons);
    }
}
