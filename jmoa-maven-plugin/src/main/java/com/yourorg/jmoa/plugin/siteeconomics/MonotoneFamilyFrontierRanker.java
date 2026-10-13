package com.yourorg.jmoa.plugin.siteeconomics;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic monotone family frontier. A family is admitted only when its
 * estimated avoided runtime/class cost exceeds its measured generated-layout
 * cost under an explicit evidence-bound unit conversion. Ranking never uses VALIDATION counts,
 * per-site PSS, or any post-observed runtime outcome. Ties and ordering are
 * purely by familyKey, so the ranking is stable under input reordering.
 */
public final class MonotoneFamilyFrontierRanker {

    public record RankedFamily(
            SiteFamilyAggregator.SiteFamily family,
            boolean admitted,
            long estimatedAvoidedCostBytes,
            long measuredGeneratedLayoutCostBytes,
            String decision) {
    }

    public record FamilyRanking(
            String rankingPolicyVersion,
            String modelTrainingEvidenceSha256,
            long avoidedCostUnitBytes,
            List<RankedFamily> rankedFamilies) {

        public FamilyRanking {
            rankedFamilies = List.copyOf(rankedFamilies);
        }

        public List<String> admittedFamilyKeys() {
            return rankedFamilies.stream().filter(RankedFamily::admitted)
                    .map(rf -> rf.family().familyKey()).collect(Collectors.toList());
        }
    }

    public FamilyRanking rank(List<SiteFamilyAggregator.SiteFamily> families, FamilyEconomicsModel model) {
        if (model == null) throw new IllegalArgumentException("An evidence-bound family economics model is required");
        List<RankedFamily> ranked = families.stream()
                .map(family -> {
                    long avoided;
                    try {
                        avoided = Math.multiplyExact(family.estimatedAvoidedCostUnits(), model.avoidedCostUnitBytes());
                    } catch (ArithmeticException overflow) {
                        throw new IllegalArgumentException("Avoided-cost estimate overflow for " + family.familyKey(), overflow);
                    }
                    long layout = family.measuredGeneratedLayoutCostBytes();
                    boolean hasTrainingActivity = family.estimatedAvoidedCostUnits() > 0L;
                    boolean admitted = hasTrainingActivity && family.layoutCostMeasured() && avoided > layout;
                    String decision = !family.layoutCostMeasured()
                            ? "DEFER_LAYOUT_COST_NOT_MEASURED"
                            : !hasTrainingActivity ? "REJECT_NO_TRAINING_ACTIVITY"
                            : admitted ? "ADMIT_MONOTONE_FRONTIER" : "REJECT_LAYOUT_COST_EXCEEDS_ESTIMATE";
                    return new RankedFamily(family, admitted, avoided, layout, decision);
                })
                .sorted(Comparator.comparing((RankedFamily rf) -> rf.family().familyKey()))
                .collect(Collectors.toList());
        return new FamilyRanking(model.rankingPolicyVersion(), model.trainingEvidenceSha256(),
                model.avoidedCostUnitBytes(), ranked);
    }
}
