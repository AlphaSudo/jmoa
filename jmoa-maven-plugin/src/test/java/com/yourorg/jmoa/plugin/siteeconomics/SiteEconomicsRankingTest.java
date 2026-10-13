package com.yourorg.jmoa.plugin.siteeconomics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteEconomicsRankingTest {

    private final SiteFamilyAggregator aggregator = new SiteFamilyAggregator();
    private final MonotoneFamilyFrontierRanker ranker = new MonotoneFamilyFrontierRanker();
    private final AdmissionManifestGenerator manifestGenerator = new AdmissionManifestGenerator();
    private final FamilyEconomicsModel model = model(512L);

    @Test
    void rankingIsDeterministicUnderReorderedInput() {
        List<SiteFeatureRow> rows = List.of(
                row("owner/Two#0", "familyB", 10, 5, 1_000L),
                row("owner/One#0", "familyA", 20, 5, 1_000L),
                row("owner/Three#0", "familyC", 1, 0, 1_000L));
        MonotoneFamilyFrontierRanker.FamilyRanking first = ranker.rank(aggregator.aggregate(rows),model);
        List<SiteFeatureRow> shuffled = new ArrayList<>(rows);
        java.util.Collections.reverse(shuffled);
        MonotoneFamilyFrontierRanker.FamilyRanking second = ranker.rank(aggregator.aggregate(shuffled),model);
        assertEquals(
                first.rankedFamilies().stream().map(rf -> rf.family().familyKey()).toList(),
                second.rankedFamilies().stream().map(rf -> rf.family().familyKey()).toList());
        assertEquals(List.of("familyA", "familyB", "familyC"),
                first.rankedFamilies().stream().map(rf -> rf.family().familyKey()).toList());
    }

    @Test
    void validationCountsNeverInfluenceSelectionOrAdmission() {
        // Huge VALIDATION-only activity must not admit a family.
        SiteFeatureRow validationHeavy = new SiteFeatureRow.Builder()
                .siteKey("owner/Heavy#0").familyKey("familyV").tier("TIER2")
                .validationInvocationCount(1_000_000)
                .generatedClassBytes(1_000L)
                .build();
        MonotoneFamilyFrontierRanker.FamilyRanking ranking =
                ranker.rank(aggregator.aggregate(List.of(validationHeavy)),model);
        assertFalse(ranking.admittedFamilyKeys().contains("familyV"));
        assertEquals("REJECT_NO_TRAINING_ACTIVITY",
                ranking.rankedFamilies().getFirst().decision());
    }

    @Test
    void monotoneFrontierAdmitsOnlyWhenAvoidedEstimateExceedsLayoutCost() {
        MonotoneFamilyFrontierRanker.FamilyRanking ranking = ranker.rank(aggregator.aggregate(List.of(
                row("owner/Rich#0", "rich", 100, 100, 10_000L),   // 200*512 = 102,400 > 10,000
                row("owner/Poor#0", "poor", 1, 1, 100_000L))),model);   // 2*512   = 1,024   < 100,000
        assertEquals(List.of("rich"), ranking.admittedFamilyKeys());
        assertEquals("ADMIT_MONOTONE_FRONTIER",
                ranking.rankedFamilies().stream().filter(rf -> rf.family().familyKey().equals("rich")).findFirst().orElseThrow().decision());
    }

    @Test
    void unmeasuredLayoutCostDefersRatherThanAdmits() {
        SiteFeatureRow unmeasured = new SiteFeatureRow.Builder()
                .siteKey("owner/U#0").familyKey("unmeasured").tier("TIER1")
                .startupInvocationCount(1000)
                .build();
        MonotoneFamilyFrontierRanker.FamilyRanking ranking =
                ranker.rank(aggregator.aggregate(List.of(unmeasured)),model);
        assertFalse(ranking.admittedFamilyKeys().contains("unmeasured"));
        assertEquals("DEFER_LAYOUT_COST_NOT_MEASURED",
                ranking.rankedFamilies().getFirst().decision());
    }

    @Test
    void manifestCarriesExactSiteKeysHeldOutExcludedAndDeterministicOrder() {
        AdmissionManifestGenerator.AdmissionManifest manifest = manifestGenerator.generate(
                ranker.rank(aggregator.aggregate(List.of(
                        row("owner/Rich#1", "rich", 100, 100, 10_000L),
                        row("owner/Rich#0", "rich", 100, 100, 10_000L),
                        row("owner/Poor#0", "poor", 1, 1, 100_000L))),model),
                "A".repeat(64),
                512L);
        assertTrue(manifest.heldOutExcluded());
        assertEquals(2, manifest.siteCount());
        assertEquals(List.of("owner/Rich#0", "owner/Rich#1"),
                manifest.entries().stream().map(AdmissionManifestGenerator.ManifestEntry::siteKey).toList());
        assertEquals(FamilyEconomicsModel.POLICY_VERSION, manifest.rankingPolicyVersion());
        assertTrue(manifest.familyDecisions().containsKey("poor"));
        assertThrows(IllegalArgumentException.class,
                () -> manifestGenerator.generate(ranker.rank(List.of(),model), "  ", 1L),
                "training evidence hash is mandatory");
    }

    @Test
    void nullCountsAndMissingSignalsStayNullSafe() {
        SiteFeatureRow sparse = new SiteFeatureRow.Builder()
                .siteKey("owner/Sparse#0").familyKey("sparse").tier("TIER2")
                .build();
        SiteFamilyAggregator.SiteFamily family = aggregator.aggregate(List.of(sparse)).getFirst();
        assertEquals(0L, family.estimatedAvoidedCostUnits());
        assertTrue(family.estimateBasis().startsWith("NO_COUNTS_NULL_SAFE"));
        assertFalse(family.layoutCostMeasured());
        assertThrows(IllegalArgumentException.class, () -> new SiteFeatureRow.Builder().siteKey("x").build());
        assertThrows(IllegalArgumentException.class,()->ranker.rank(List.of(),null));
    }

    @Test
    void modelTrainerUsesOnlyExactFamilyTrainingSignalsAndHasNoDefault() {
        SiteFeatureRow usable=new SiteFeatureRow.Builder().siteKey("a#0").familyKey("a")
                .startupInvocationCount(2).trainingInvocationCount(2).validationInvocationCount(1_000_000)
                .heldOutRecurrenceCount(1_000_000).measuredFamilyAllocationBytes(4_000L).build();
        SiteFeatureRow missing=new SiteFeatureRow.Builder().siteKey("b#0").familyKey("b")
                .startupInvocationCount(100).build();
        FamilyEconomicsModelTrainer.TrainingResult result=new FamilyEconomicsModelTrainer()
                .train(List.of(usable,missing),"A".repeat(64));
        assertEquals(FamilyEconomicsModelTrainer.Status.FROZEN,result.status());
        assertEquals(1_000L,result.model().avoidedCostUnitBytes());
        assertEquals(List.of("b"),result.rejectedFamilies());

        FamilyEconomicsModelTrainer.TrainingResult rejected=new FamilyEconomicsModelTrainer()
                .train(List.of(missing),"B".repeat(64));
        assertEquals(FamilyEconomicsModelTrainer.Status.REJECTED,rejected.status());
        assertEquals("NO_EXACT_FAMILY_TRAINING_SIGNAL",rejected.reason());
    }

    private static SiteFeatureRow row(String siteKey, String familyKey, int startup, int training, long layoutBytes) {
        return new SiteFeatureRow.Builder()
                .siteKey(siteKey).familyKey(familyKey).tier("TIER2")
                .startupInvocationCount(startup).trainingInvocationCount(training)
                .generatedClassBytes(layoutBytes)
                .build();
    }

    private static FamilyEconomicsModel model(long unit) {
        return new FamilyEconomicsModel(FamilyEconomicsModel.SCHEMA_VERSION,
                FamilyEconomicsModel.POLICY_VERSION,"A".repeat(64),unit,true,"test-evidence-model");
    }
}
