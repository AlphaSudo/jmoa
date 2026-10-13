package com.yourorg.jmoa.plugin.siteeconomics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Emits the deterministic admission manifest for a family ranking: the exact
 * site keys of admitted families, held out from any ranking signal, plus the
 * identity bindings a downstream v2 admission document must carry. Ordering
 * is by familyKey then siteKey, so identical rankings produce identical
 * manifests.
 */
public final class AdmissionManifestGenerator {

    public record ManifestEntry(String familyKey, String siteKey, String tier) {
    }

    public record AdmissionManifest(
            String rankingPolicyVersion,
            String trainingEvidenceSha256,
            long selectionCutoffBytes,
            boolean heldOutExcluded,
            List<ManifestEntry> entries,
            Map<String, String> familyDecisions) {

        public AdmissionManifest {
            entries = List.copyOf(entries);
            familyDecisions = Map.copyOf(familyDecisions);
        }

        public int siteCount() {
            return entries.size();
        }
    }

    public AdmissionManifest generate(
            MonotoneFamilyFrontierRanker.FamilyRanking ranking,
            String trainingEvidenceSha256,
            long selectionCutoffBytes) {
        if (trainingEvidenceSha256 == null || trainingEvidenceSha256.isBlank()) {
            throw new IllegalArgumentException("trainingEvidenceSha256 is required");
        }
        if (!trainingEvidenceSha256.equalsIgnoreCase(ranking.modelTrainingEvidenceSha256())) {
            throw new IllegalArgumentException("Admission training evidence must match the frozen model evidence");
        }
        List<ManifestEntry> entries = new ArrayList<>();
        Map<String, String> decisions = new TreeMap<>();
        for (MonotoneFamilyFrontierRanker.RankedFamily ranked : ranking.rankedFamilies()) {
            decisions.put(ranked.family().familyKey(), ranked.decision());
            if (!ranked.admitted()) {
                continue;
            }
            ranked.family().sites().stream()
                    .map(row -> new ManifestEntry(row.familyKey(), row.siteKey(), row.tier()))
                    .sorted((a, b) -> a.siteKey().compareTo(b.siteKey()))
                    .forEach(entries::add);
        }
        return new AdmissionManifest(
                ranking.rankingPolicyVersion(),
                trainingEvidenceSha256,
                selectionCutoffBytes,
                true,
                entries,
                decisions);
    }
}
