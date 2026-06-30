package com.yourorg.jmoa.plugin.roi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 20B: Analyzes denied framework sites by multiple dimensions.
 *
 * <p>Aggregates denied sites by denial reason, package, SAM interface, access visibility,
 * profile status, would-be tier, root kind, and admission prerequisite. Tier B is split
 * by prerequisite group to avoid an opaque single bucket.</p>
 */
public final class RewriteRoiDenialAnalyzer {

    /**
     * Analyze the denial breakdown from classified candidates.
     *
     * @param candidates all classified candidates (both eligible and denied)
     * @return denial breakdown maps
     */
    public Map<String, Object> analyze(List<RewriteRoiCandidateRecord> candidates) {
        List<RewriteRoiCandidateRecord> denied = candidates.stream()
            .filter(c -> "EXCLUDED".equals(c.currentDecision()))
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalDenied", denied.size());
        result.put("byDenialReason", countByPrimaryDenialReason(denied));
        result.put("byPackage", topCounts(denied, RewriteRoiCandidateRecord::ownerPackage, 50));
        result.put("byOwnerClass", topCounts(denied, RewriteRoiCandidateRecord::ownerClass, 50));
        result.put("bySamInterface", topCounts(denied, RewriteRoiCandidateRecord::samInterface, 50));
        result.put("byAccessVisibility", countBy(denied, RewriteRoiCandidateRecord::accessVisibility));
        result.put("byProfileHeat", countBy(denied, RewriteRoiCandidateRecord::profileHeat));
        result.put("byWouldBeTier", countBy(denied, RewriteRoiCandidateRecord::wouldBeTier));
        result.put("byRootKind", countBy(denied, RewriteRoiCandidateRecord::rootKind));
        result.put("bySafetyTier", countBySafetyTier(denied));
        result.put("byAdmissionPrerequisite", countByPrerequisite(denied));
        return result;
    }

    /**
     * Compute the denial breakdown map (keyed by primary denial reason).
     */
    public Map<String, Long> computeDenialBreakdown(List<RewriteRoiCandidateRecord> candidates) {
        return candidates.stream()
            .filter(c -> "EXCLUDED".equals(c.currentDecision()))
            .filter(c -> c.deniedReasonPrimary() != null)
            .collect(Collectors.groupingBy(
                RewriteRoiCandidateRecord::deniedReasonPrimary,
                LinkedHashMap::new,
                Collectors.counting()
            ));
    }

    /**
     * Compute Tier B breakdown by admission prerequisite.
     */
    public Map<String, Long> computeTierBByPrerequisite(List<RewriteRoiCandidateRecord> candidates) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (RewriteRoiAdmissionPrerequisite prereq : RewriteRoiAdmissionPrerequisite.values()) {
            long count = candidates.stream()
                .filter(c -> c.safetyTier() == RewriteRoiSafetyTier.TIER_B)
                .filter(c -> c.admissionPrerequisite() == prereq)
                .count();
            if (count > 0) {
                result.put(prereq.name(), count);
            }
        }
        return result;
    }

    /**
     * Compute safety tier summary counts.
     */
    public Map<String, Long> computeSafetyTierSummary(List<RewriteRoiCandidateRecord> candidates) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (RewriteRoiSafetyTier tier : RewriteRoiSafetyTier.values()) {
            result.put(tier.name(), candidates.stream()
                .filter(c -> c.safetyTier() == tier)
                .count());
        }
        return result;
    }

    // --- Private helpers ---

    private Map<String, Long> countByPrimaryDenialReason(List<RewriteRoiCandidateRecord> denied) {
        return denied.stream()
            .filter(c -> c.deniedReasonPrimary() != null)
            .collect(Collectors.groupingBy(
                RewriteRoiCandidateRecord::deniedReasonPrimary,
                LinkedHashMap::new,
                Collectors.counting()
            ));
    }

    private Map<String, Long> countBySafetyTier(List<RewriteRoiCandidateRecord> denied) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (RewriteRoiSafetyTier tier : RewriteRoiSafetyTier.values()) {
            long count = denied.stream().filter(c -> c.safetyTier() == tier).count();
            if (count > 0) {
                result.put(tier.name(), count);
            }
        }
        return result;
    }

    private Map<String, Long> countByPrerequisite(List<RewriteRoiCandidateRecord> denied) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (RewriteRoiAdmissionPrerequisite prereq : RewriteRoiAdmissionPrerequisite.values()) {
            long count = denied.stream().filter(c -> c.admissionPrerequisite() == prereq).count();
            if (count > 0) {
                result.put(prereq.name(), count);
            }
        }
        return result;
    }

    private <T> Map<String, Long> countBy(List<RewriteRoiCandidateRecord> records,
                                           Function<RewriteRoiCandidateRecord, T> extractor) {
        return records.stream()
            .collect(Collectors.groupingBy(
                r -> String.valueOf(extractor.apply(r)),
                LinkedHashMap::new,
                Collectors.counting()
            ));
    }

    private List<Map<String, Object>> topCounts(List<RewriteRoiCandidateRecord> records,
                                                 Function<RewriteRoiCandidateRecord, String> extractor,
                                                 int limit) {
        return records.stream()
            .collect(Collectors.groupingBy(extractor, Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey))
            .limit(limit)
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value", entry.getKey());
                row.put("count", entry.getValue());
                return row;
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
