package com.yourorg.jmoa.plugin.siteeconomics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Freezes a conservative family-level model from exact training signals. The
 * minimum observed exact family allocation bytes per STARTUP+TRAINING factory
 * invocation is used so a favorable family cannot inflate another family's
 * estimate. VALIDATION and held-out recurrence are deliberately unread.
 */
public final class FamilyEconomicsModelTrainer {
    public enum Status { FROZEN, REJECTED }

    public record TrainingResult(
            Status status,
            String reason,
            FamilyEconomicsModel model,
            int inputFamilyCount,
            int usableFamilyCount,
            List<String> rejectedFamilies) {
        public TrainingResult {
            rejectedFamilies = List.copyOf(rejectedFamilies);
            if ((status == Status.FROZEN) != (model != null)) {
                throw new IllegalArgumentException("Only a FROZEN training result may carry a model");
            }
        }
    }

    public TrainingResult train(List<SiteFeatureRow> rows, String trainingEvidenceSha256) {
        if (trainingEvidenceSha256 == null || !trainingEvidenceSha256.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalArgumentException("trainingEvidenceSha256 must be SHA-256");
        }
        Map<String, List<SiteFeatureRow>> byFamily = new TreeMap<>();
        for (SiteFeatureRow row : rows) byFamily.computeIfAbsent(row.familyKey(), ignored -> new ArrayList<>()).add(row);
        long conservativeUnit = Long.MAX_VALUE;
        int usable = 0;
        List<String> rejected = new ArrayList<>();
        for (Map.Entry<String, List<SiteFeatureRow>> entry : byFamily.entrySet()) {
            long selectionCount = 0L;
            Long exactAllocation = null;
            boolean inconsistent = false;
            try {
                for (SiteFeatureRow row : entry.getValue()) {
                    selectionCount = Math.addExact(selectionCount, row.selectionInvocationCount());
                    if (row.measuredFamilyAllocationBytes() != null) {
                        if (row.measuredFamilyAllocationBytes() < 0L) inconsistent = true;
                        if (exactAllocation == null) exactAllocation = row.measuredFamilyAllocationBytes();
                        else if (!exactAllocation.equals(row.measuredFamilyAllocationBytes())) inconsistent = true;
                    }
                }
            } catch (ArithmeticException overflow) {
                inconsistent = true;
            }
            if (inconsistent || selectionCount == 0L || exactAllocation == null) {
                rejected.add(entry.getKey());
                continue;
            }
            long unit = exactAllocation / selectionCount;
            if (unit <= 0L) {
                rejected.add(entry.getKey());
                continue;
            }
            conservativeUnit = Math.min(conservativeUnit, unit);
            usable++;
        }
        if (usable == 0) {
            return new TrainingResult(Status.REJECTED, "NO_EXACT_FAMILY_TRAINING_SIGNAL", null,
                    byFamily.size(), 0, rejected);
        }
        FamilyEconomicsModel model = new FamilyEconomicsModel(
                FamilyEconomicsModel.SCHEMA_VERSION,
                FamilyEconomicsModel.POLICY_VERSION,
                trainingEvidenceSha256,
                conservativeUnit,
                true,
                "MIN_EXACT_FAMILY_ALLOCATION_BYTES_PER_STARTUP_PLUS_TRAINING_FACTORY_INVOCATION");
        return new TrainingResult(Status.FROZEN, "FROZEN_FROM_EXACT_FAMILY_TRAINING_SIGNAL", model,
                byFamily.size(), usable, rejected);
    }
}
