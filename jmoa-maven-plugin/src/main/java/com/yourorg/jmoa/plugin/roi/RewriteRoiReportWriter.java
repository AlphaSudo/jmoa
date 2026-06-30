package com.yourorg.jmoa.plugin.roi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 20H: Writes the ROI report in two formats.
 *
 * <ol>
 *   <li>{@code target/jmoa-rewrite-roi-report.json} — Full structured JSON</li>
 *   <li>{@code docs/phase20-rewrite-roi.md} — Human-readable markdown summary</li>
 * </ol>
 *
 * <p>Candidate inventory inclusion is controlled by the {@code includeCandidates}
 * flag. When false, only aggregate summaries are written (not per-site records),
 * keeping the report small.</p>
 */
public final class RewriteRoiReportWriter {

    private final boolean includeCandidates;

    public RewriteRoiReportWriter(boolean includeCandidates) {
        this.includeCandidates = includeCandidates;
    }

    /**
     * Write both report files.
     *
     * @param jsonFile the JSON report output file
     * @param markdownFile the markdown summary output file
     * @param report the complete ROI report
     * @throws IOException if writing fails
     */
    public void write(File jsonFile, File markdownFile, RewriteRoiReport report) throws IOException {
        writeJson(jsonFile, report);
        writeMarkdown(markdownFile, report);
    }

    private void writeJson(File jsonFile, RewriteRoiReport report) throws IOException {
        if (jsonFile.getParentFile() != null) {
            jsonFile.getParentFile().mkdirs();
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("timestamp", Instant.now().toString());
        root.put("metadataVersion", "v3.3-phase20");
        root.put("observedCalibration", report.observedCalibration());
        root.put("currentBaseline", report.currentBaseline());

        if (includeCandidates) {
            root.put("candidateInventory", report.candidateInventory());
            root.put("candidateInventoryCount", report.candidateInventory().size());
        } else {
            root.put("candidateInventoryCount", report.candidateInventory().size());
            root.put("candidateInventoryOmitted", true);
            root.put("candidateInventoryNote",
                "Set -Djmoa.rewriteRoiIncludeCandidates=true to include full candidate inventory");
        }

        root.put("denialBreakdown", report.denialBreakdown());
        root.put("denialAggregations", report.denialAggregations());
        root.put("tierBByPrerequisite", report.tierBByPrerequisite());
        root.put("safetyTierSummary", report.safetyTierSummary());
        root.put("costModel", report.costModel());
        root.put("savingsProjections", report.savingsProjections());
        root.put("roiRankings", report.roiRankings());
        root.put("scaleAssessment", report.scaleAssessment());
        root.put("profileProvenance", report.profileProvenance());
        root.put("recommendedAdmissionPlans", report.recommendedAdmissionPlans());
        root.put("reconciliation", report.reconciliation());
        root.put("recommendedDecision", report.recommendedDecision());

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(jsonFile, root);
    }

    private void writeMarkdown(File markdownFile, RewriteRoiReport report) throws IOException {
        if (markdownFile.getParentFile() != null) {
            markdownFile.getParentFile().mkdirs();
        }

        try (PrintWriter out = new PrintWriter(new FileWriter(markdownFile))) {
            out.println("# Phase 20 — Rewrite ROI and Admission Scaling Report");
            out.println();
            out.println("> **Generated**: " + Instant.now().toString());
            out.println("> **Metadata Version**: v3.3-phase20");
            out.println("> **All projections are SPECULATIVE**");
            out.println();

            // Profile Provenance
            writeProfileProvenanceSection(out, report);

            // Reconciliation
            writeReconciliationSection(out, report);

            // Current baseline
            writeBaselineSection(out, report);

            // Safety tier summary
            writeSafetyTierSection(out, report);

            // Tier B prerequisite breakdown
            writeTierBPrerequisiteSection(out, report);

            // Scale assessment
            writeScaleAssessmentSection(out, report);

            // Denial breakdown
            writeDenialSection(out, report);

            // Savings projections
            writeSavingsSection(out, report);

            // ROI rankings
            writeRoiRankingsSection(out, report);

            // Admission plans
            writeAdmissionPlansSection(out, report);

            // Final recommendation
            writeFinalRecommendation(out, report);
        }
    }

    private void writeProfileProvenanceSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Profile Provenance");
        out.println();
        Map<String, Object> prov = report.profileProvenance();
        if (prov != null) {
            out.printf("- **Profile loaded**: %s%n", prov.get("profileLoaded"));
            out.printf("- **Profile path**: %s%n", prov.get("profilePath"));
            out.printf("- **Profile site count**: %s%n", prov.get("profileSiteCount"));
            out.printf("- **Matched profile sites**: %s%n", prov.get("matchedProfileSiteCount"));
            out.printf("- **Unmatched profile sites**: %s%n", prov.get("unmatchedProfileSiteCount"));
            out.printf("- **Observed framework sites**: %s%n", prov.get("observedFrameworkSiteCount"));
        } else {
            out.println("_No profile provenance data available._");
        }
        out.println();
    }

    private void writeReconciliationSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Reconciliation");
        out.println();
        Map<String, Object> recon = report.reconciliation();
        boolean reconciled = Boolean.TRUE.equals(recon.get("reconciled"));
        if (reconciled) {
            out.println("> ✅ **PASSED** — Candidate inventory matches framework safety summary.");
        } else {
            out.println("> ❌ **FAILED** — Candidate inventory does NOT match framework safety summary.");
            out.println("> ROI rankings should be treated as UNRELIABLE until reconciliation passes.");
        }
        out.println();
        out.println("| Metric | Count | Match |");
        out.println("|--------|-------|-------|");
        out.printf("| Total framework sites | %s | %s |%n",
            recon.get("candidateCount"),
            Boolean.TRUE.equals(recon.get("inventoryMatch")) ? "✅" : "❌");
        out.printf("| Framework safety allowed | %s | %s |%n",
            recon.get("candidateAllowedFramework"),
            Boolean.TRUE.equals(recon.get("allowedMatch")) ? "✅" : "❌");
        out.printf("| Execution eligible Tier S | %s | — |%n",
            recon.get("executionEligibleSites"));
        out.printf("| Safety allowed but access denied | %s | — |%n",
            recon.get("safetyAllowedButAccessDeniedSites"));
        out.printf("| Framework safety denied | %s | — |%n",
            recon.get("frameworkDenied"));
        out.println();
    }

    private void writeBaselineSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Current Baseline (Phase 19)");
        out.println();
        Map<String, Object> baseline = report.currentBaseline();
        out.printf("- **Planned sites**: %s%n", baseline.get("plannedSites"));
        out.printf("- **Lambda reduction**: %s%n", baseline.get("lambdaReduction"));
        out.printf("- **Metaspace used delta (forced GC)**: %s KB%n", baseline.get("metaspaceUsedDeltaKb"));
        out.printf("- **Metaspace committed delta**: %s KB%n", baseline.get("metaspaceCommittedDeltaKb"));
        out.printf("- **Replacement cost**: %s bytes (runtime) + %s bytes (adapters) + %s bytes (class growth)%n",
            baseline.get("generatedRuntimeClassBytes"),
            baseline.get("generatedPackageAdapterClassBytes"),
            baseline.get("rewrittenClassByteDelta"));
        out.println();
    }

    private void writeSafetyTierSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Safety Tier Summary");
        out.println();
        out.println("| Tier | Count | Description |");
        out.println("|------|-------|-------------|");
        Map<String, Long> tiers = report.safetyTierSummary();
        out.printf("| S | %d | Already allowed |%n", tiers.getOrDefault("TIER_S", 0L));
        out.printf("| A | %d | Low-risk expansion candidate |%n", tiers.getOrDefault("TIER_A", 0L));
        out.printf("| B | %d | Medium-risk (see prerequisite breakdown) |%n", tiers.getOrDefault("TIER_B", 0L));
        out.printf("| C | %d | High-risk (separate design review) |%n", tiers.getOrDefault("TIER_C", 0L));
        out.printf("| D | %d | Never admit |%n", tiers.getOrDefault("TIER_D", 0L));
        out.println();
    }

    private void writeTierBPrerequisiteSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Tier B Prerequisite Breakdown");
        out.println();
        Map<String, Long> prereqs = report.tierBByPrerequisite();
        if (prereqs.isEmpty()) {
            out.println("_No Tier B candidates._");
        } else {
            out.println("| Prerequisite | Count |");
            out.println("|-------------|-------|");
            for (var entry : prereqs.entrySet()) {
                out.printf("| %s | %d |%n", entry.getKey(), entry.getValue());
            }
        }
        out.println();
    }

    private void writeScaleAssessmentSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Scale Assessment");
        out.println();
        var sa = report.scaleAssessment();
        out.printf("- **Current planned sites**: %d%n", sa.currentPlannedSites());
        out.printf("- **Tier A candidates**: %d%n", sa.tierACandidateCount());
        out.printf("- **Tier B candidates**: %d%n", sa.tierBTotalCount());
        out.printf("- **Max safe near-term rewrites**: %d%n", sa.maxSafeNearTermRewrites());
        out.println();
        out.println("| Target | Profile-unlocked potential | Notes |");
        out.println("|--------|----------------------------|-------|");
        out.printf("| 300 rewrites | %s | %s |%n",
            sa.canReach300() ? "✅ Yes" : "❌ No",
            sa.canReach300() ? "With Tier A + selected Tier B" : "Insufficient safe candidates");
        out.printf("| 500 rewrites | %s | %s |%n",
            sa.canReach500() ? "✅ Yes" : "❌ No",
            sa.canReach500() ? "With full Tier A + B" : "Would require Tier C or more candidates");
        out.printf("| 800 rewrites | %s | %s |%n",
            sa.canReach800() ? "✅ Yes" : "❌ No",
            sa.canReach800() ? "With full Tier A + B" : "Not achievable without Tier C");
        out.println();
        out.println("**Current safe reach**: 134 rewrites only");
        out.printf("**Limiting factor**: `%s`%n", sa.limitingFactor());
        out.printf("**Recommended decision**: `%s`%n", sa.recommendedDecision());
        out.println();
    }

    private void writeDenialSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Denial Breakdown");
        out.println();
        Map<String, Long> breakdown = report.denialBreakdown();
        if (breakdown.isEmpty()) {
            out.println("_No denied framework sites._");
        } else {
            out.println("| Denial Reason | Count |");
            out.println("|--------------|-------|");
            for (var entry : breakdown.entrySet()) {
                out.printf("| %s | %d |%n", entry.getKey(), entry.getValue());
            }
        }
        out.println();
    }

    private void writeSavingsSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Savings Projections (SPECULATIVE)");
        out.println();
        out.println("> ⚠️ All projections are speculative. Phase 19 observed 132 removed lambdas");
        out.println("> produced basically neutral metaspace delta (-11 KB used). Even 0.5 KB/lambda");
        out.println("> may be optimistic at current scale.");
        out.println();
        out.println("| Rewrites | Net 0.25 KB | Net 0.5 KB | Net 1.0 KB | Net 2.0 KB |");
        out.println("|----------|-----------|----------|----------|----------|");
        for (Map<String, Object> proj : report.savingsProjections()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> net = (Map<String, Object>) proj.get("netSavingsKb");
            out.printf("| %s | %s KB | %s KB | %s KB | %s KB |%n",
                proj.get("rewriteCount"),
                net.get("veryLowKb"), net.get("lowKb"),
                net.get("mediumKb"), net.get("highKb"));
        }
        out.println();
    }

    private void writeRoiRankingsSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## ROI Batch Rankings");
        out.println();
        out.println("### Composition Key");
        out.println("- NOBS = Not Observed");
        out.println("- PROF = Missing Profile");
        out.println("- ACC = Access Denied");
        out.println("- FRAME = Framework Safety Denied");
        out.println("- SAM = Unsupported SAM");
        out.println("- T1 = Would be Tier 1");
        out.println("- T2 = Would be Tier 2");
        out.println("- UNK = Would be Tier Unknown");
        out.println();
        out.println("| Batch | Candidates | Risk | Recommendation | ROI Score | Composition (NOBS/PROF/ACC/FRAME/SAM) | Projected Tier (T1/T2/UNK) |");
        out.println("|-------|-----------|------|----------------|-----------|-------------------------------------|--------------------------|");
        for (Map<String, Object> batch : report.roiRankings()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> comp = (Map<String, Object>) batch.get("prerequisiteComposition");
            String compStr = comp != null ? String.format("%s / %s / %s / %s / %s",
                comp.get("notObservedCount"), comp.get("missingProfileCount"),
                comp.get("accessDeniedCount"), comp.get("frameworkSafetyDeniedCount"), comp.get("unsupportedSamCount")) : "N/A";
            String tierStr = comp != null ? String.format("%s / %s / %s", comp.get("wouldBeTier1Count"), comp.get("wouldBeTier2Count"), comp.get("wouldBeTierUnknownCount")) : "N/A";

            out.printf("| %s | %s | %s | %s | %s | %s | %s |%n",
                batch.get("batchName"),
                batch.get("candidateCount"),
                batch.get("estimatedRisk"),
                batch.get("recommendation"),
                batch.get("roiScore"),
                compStr,
                tierStr);
        }
        out.println();
    }

    private void writeAdmissionPlansSection(PrintWriter out, RewriteRoiReport report) {
        out.println("## Admission Plans");
        out.println();
        for (Map<String, Object> plan : report.recommendedAdmissionPlans()) {
            out.printf("### %s%n", plan.get("planName"));
            out.println();
            out.printf("_%s_%n", plan.get("description"));
            out.println();
            out.printf("- **Added sites**: %s%n", plan.get("expectedAddedSites"));
            out.printf("- **Total rewrites**: %s%n", plan.get("expectedTotalRewrites"));
            out.printf("- **Tier 1 sites**: %s%n", plan.get("expectedTier1Sites"));
            out.printf("- **Tier 2 sites**: %s%n", plan.get("expectedTier2Sites"));
            out.printf("- **Risk level**: %s%n", plan.get("riskLevel"));
            out.printf("- **Estimated replacement cost**: %s KB%n", plan.get("expectedReplacementCostKb"));
            out.println();
        }
    }

    private void writeFinalRecommendation(PrintWriter out, RewriteRoiReport report) {
        out.println("## Final Recommendation");
        out.println();
        String decision = report.recommendedDecision();
        switch (decision) {
            case "A" -> {
                out.println("### ✅ Decision A — Proceed to controlled admission expansion");
                out.println();
                out.println("Enough safe profitable candidates exist. Use the Conservative plan as Phase 21 baseline.");
            }
            case "B" -> {
                out.println("### ❌ Decision B — Not enough safe candidates");
                out.println();
                out.println("No Phase 21 admission expansion should happen from this report yet.");
                out.println("Reason: The current evidence has 0 Tier A and 0 Tier B candidates, and NOT_OBSERVED sites are mostly unsafe.");
            }
            case "C" -> {
                out.println("### ⚠️ Decision C — Adapter consolidation required");
                out.println();
                out.println("Candidates exist but are mostly Tier 2 expensive.");
                out.println("Implement adapter consolidation before scaling admission.");
            }
            case "D" -> {
                out.println("### ⚠️ Decision D — Candidate scale is profile-limited");
                out.println();
                out.println("No Phase 21 admission expansion should happen from this report yet.");
                out.println("Reason: Current evidence admits no extra safe candidates, but the main limiting factor is missing profile evidence (many structurally safe NOT_OBSERVED sites exist), not absence of visible sites.");
                out.println("Next action: A better training profile is required to unlock further scale.");
            }
            default -> {
                out.println("### ❓ Decision unknown — manual review required");
            }
        }
        out.println();
    }
}
