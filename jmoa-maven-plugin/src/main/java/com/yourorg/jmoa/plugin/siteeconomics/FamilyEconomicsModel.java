package com.yourorg.jmoa.plugin.siteeconomics;

/**
 * Explicit, immutable count-to-cost model. There is intentionally no default:
 * every ranking must name the exact training evidence that produced its unit.
 */
public record FamilyEconomicsModel(
        String schemaVersion,
        String rankingPolicyVersion,
        String trainingEvidenceSha256,
        long avoidedCostUnitBytes,
        boolean frozenBeforeRanking,
        String estimator) {

    public static final String SCHEMA_VERSION = "jmoa-family-economics-model-v1";
    public static final String POLICY_VERSION = "site-economics-v3-trained-family-frontier-1";

    public FamilyEconomicsModel {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported family economics model schemaVersion");
        }
        if (!POLICY_VERSION.equals(rankingPolicyVersion)) {
            throw new IllegalArgumentException("Unsupported family economics ranking policy");
        }
        if (trainingEvidenceSha256 == null || !trainingEvidenceSha256.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalArgumentException("trainingEvidenceSha256 must be SHA-256");
        }
        if (avoidedCostUnitBytes <= 0L) {
            throw new IllegalArgumentException("avoidedCostUnitBytes must be positive");
        }
        if (!frozenBeforeRanking) {
            throw new IllegalArgumentException("Family economics model must be frozen before ranking");
        }
        if (estimator == null || estimator.isBlank()) {
            throw new IllegalArgumentException("estimator is required");
        }
        trainingEvidenceSha256 = trainingEvidenceSha256.toUpperCase();
    }
}
