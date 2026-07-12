package com.yourorg.jmoa.plugin.recommendation;

import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.AdmissionDecision;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerAdmissionInput;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerRecommendation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ReducerRecommendationEngine {

    private static final Set<String> ALLOWED_ATTRIBUTES = Set.of(
        "LOCALVARIABLETABLE",
        "LOCALVARIABLETYPETABLE"
    );

    private static final List<String> BOUNDARIES = List.of(
        "The recommendation engine is report-only and never mutates bytecode.",
        "A confirmed recommendation applies only to the exact service, launch mode, and runtime policy in the evidence.",
        "Only LocalVariableTable and LocalVariableTypeTable removal is admitted.",
        "Application-class raw reduction is artifact/semantic-only unless its ROI threshold is met or runtime evidence exists for that exact application scope.",
        "New protocols still require semantic smoke, V2-C confirmation, and V2-D attribution."
    );
    private static final long APPLICATION_MIN_REMOVED_BYTES = 32L * 1024L;
    private static final int APPLICATION_MIN_REDUCED_CLASSES = 50;
    private static final long GENERATED_HIGH_ROI_BYTES = 256L * 1024L;
    private static final int GENERATED_HIGH_ROI_CLASSES = 500;

    public ReducerRecommendation recommend(ReducerAdmissionInput input) {
        List<String> unsafeAttributes = unsafeAttributes(input);
        if (input.failedAudits() > 0 || !unsafeAttributes.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            if (input.failedAudits() > 0) {
                reasons.add("Raw byte-preservation audits failed: " + input.failedAudits() + ".");
            }
            if (!unsafeAttributes.isEmpty()) {
                reasons.add("Unsupported attributes were requested or stripped: " + unsafeAttributes + ".");
            }
            return result(input, AdmissionDecision.BLOCK_UNSAFE, false, reasons,
                List.of(), List.of("Restore LVT/LVTT-only reduction and rerun byte-preservation auditing."));
        }

        if (Boolean.FALSE.equals(input.semanticSmokePassed())) {
            return result(input, AdmissionDecision.BLOCK_SEMANTIC_FAILURE, false,
                List.of("Semantic smoke failed for the current artifact."),
                List.of(), List.of("Fix the runtime or artifact failure before any memory screen."));
        }

        if (isFailedScreen(input.screenVerdict())) {
            return result(input, AdmissionDecision.BLOCK_RUNTIME_PROMOTION, false,
                List.of("The runtime screen failed its promotion gate."),
                List.of(), List.of("Keep the result artifact-only or investigate with V2-D before rerunning."));
        }

        if (input.diagnosticOnly()) {
            return result(input, AdmissionDecision.DIAGNOSTIC_ONLY, false,
                List.of("The supplied runtime evidence is explicitly diagnostic-only."),
                List.of(), List.of("Capture non-perturbing confirmation evidence before making a runtime claim."));
        }

        if (input.generatedSurfaceEvidencePresent() && !input.artifactEvidencePresent()) {
            return generatedDiscoveryResult(input);
        }

        if (!"RAW".equals(normalize(input.reducerEngine()))) {
            return result(input, AdmissionDecision.UNKNOWN, false,
                List.of("V2-M admission currently targets the productized raw reducer engine."),
                List.of("raw reducer evidence"),
                List.of("Run the raw LVT/LVTT reducer and its byte-preservation auditor."));
        }

        List<String> artifactGaps = artifactEvidenceGaps(input);
        if (!artifactGaps.isEmpty()) {
            return result(input, AdmissionDecision.BLOCK_NO_EVIDENCE, false,
                List.of("Required raw reducer artifact evidence is incomplete."), artifactGaps,
                List.of("Produce a reduced artifact, manifest v2, and zero-failure raw audit report."));
        }

        if (input.applicationClassEvidencePresent()
            && !applicationRoiMeetsThreshold(input)
            && !hasApplicationRuntimeEvidence(input)) {
            return result(input, AdmissionDecision.APPLICATION_LOW_ROI_ARTIFACT_ONLY, false,
                List.of("Application-class raw reduction is below the initial ROI threshold."),
                List.of("application removable metadata >= 32 KB or >= 50 reduced application classes",
                    "passing application-scope runtime screen"),
                List.of(
                    "Keep application-class raw reduction artifact/semantic-only for this service.",
                    "Run a screen only for application surfaces with plausible ROI or explicit diagnostic intent."
                ));
        }

        if (input.hasV2CConfirmation()) {
            if (!"CONFIRMED_WIN".equals(normalize(input.v2cVerdict()))) {
                return result(input, AdmissionDecision.BLOCK_RUNTIME_PROMOTION, false,
                    List.of("V2-C did not confirm a runtime win: " + input.v2cVerdict() + "."),
                    List.of(), List.of("Do not promote this protocol; investigate or capture new valid evidence."));
            }
            List<String> confirmationGaps = confirmationEvidenceGaps(input);
            if (!confirmationGaps.isEmpty()) {
                return result(input, AdmissionDecision.BLOCK_NO_EVIDENCE, false,
                    List.of("The runtime confirmation is missing required admission gates."), confirmationGaps,
                    List.of("Complete semantic, V2-C, and V2-D evidence before recommendation."));
            }
            boolean protocolMatch = protocolMatches(input);
            if (protocolMatch) {
                return result(input, AdmissionDecision.RECOMMEND_CONFIRMED, true,
                    List.of(
                        "Raw byte-preservation audits passed.",
                        "Semantic smoke passed.",
                        "V2-C confirmed a runtime win.",
                        "V2-D attribution is present.",
                        "The requested service, launch mode, and runtime policy match the confirmed scope."
                    ), List.of(), List.of(
                        "Use the raw reducer only within the confirmed protocol.",
                        "Require fresh V2-C/V2-D evidence for any protocol change."
                    ));
            }
            return result(input, AdmissionDecision.RECOMMEND_SCREEN_REQUIRED, false,
                List.of("A historical raw reducer win exists, but it does not match the requested protocol."),
                protocolGaps(input), List.of(
                    "Run semantic smoke and a fresh single-screen gate for this protocol.",
                    "Promote only after new V2-C confirmation and V2-D attribution."
                ));
        }

        if (Boolean.TRUE.equals(input.semanticSmokePassed())) {
            if (input.applicationClassEvidencePresent() && applicationRoiMeetsThreshold(input)) {
                return result(input, AdmissionDecision.APPLICATION_SCREEN_REQUIRED, false,
                    List.of("Application-class raw reduction has plausible ROI and passed artifact/semantic gates."),
                    List.of("application-scope V2-C confirmation", "application-scope V2-D attribution"),
                    List.of("Run a single screen for this exact application surface before any runtime promotion."));
            }
            return result(input, AdmissionDecision.RECOMMEND_SCREEN_REQUIRED, false,
                List.of("Artifact and semantic gates passed, but no V2-C confirmation exists for this protocol."),
                List.of("V2-C confirmation", "V2-D attribution"),
                List.of("Run a single screen, then three paired runs if the screen passes."));
        }

        if (input.semanticSmokePassed() == null) {
            return result(input, AdmissionDecision.ALLOW_ARTIFACT_ONLY, false,
                List.of("Artifact reduction and byte-preservation auditing passed, but semantic runtime evidence is unavailable."),
                List.of("semantic smoke", "V2-C confirmation", "V2-D attribution"),
                List.of("Do not deploy as a runtime recommendation until semantic smoke is available."));
        }

        return result(input, AdmissionDecision.UNKNOWN, false,
            List.of("The supplied evidence does not map to a known admission state."),
            List.of("deterministic semantic or runtime verdict"),
            List.of("Review the normalized admission input and rerun the recommendation goal."));
    }

    private ReducerRecommendation result(
        ReducerAdmissionInput input,
        AdmissionDecision decision,
        boolean runtimePromotionAllowed,
        List<String> reasons,
        List<String> evidenceGaps,
        List<String> nextActions
    ) {
        return new ReducerRecommendation(
            "v2m-reducer-recommendation",
            Instant.now().toString(),
            decision,
            input.confirmationScope(),
            protocolMatches(input),
            runtimePromotionAllowed,
            reasons,
            evidenceGaps,
            nextActions,
            input,
            BOUNDARIES
        );
    }

    private ReducerRecommendation generatedDiscoveryResult(ReducerAdmissionInput input) {
        if (input.generatedUnsafeFamilyPresent()) {
            return result(input, AdmissionDecision.GENERATED_MUTATION_BLOCKED, false,
                List.of("Generated/proxy-like families include semantic-risk runtime contract classes."),
                List.of("family-specific semantic proof", "runtime-origin proof", "V2-C confirmation"),
                List.of("Keep generated/proxy classes report-only; do not transfer raw reducer confidence."));
        }
        if (generatedHighRoi(input)) {
            return result(input, AdmissionDecision.CANDIDATE_FOR_PROTOTYPE, false,
                List.of("Generated/application discovery found a high-ROI surface worth prototype planning."),
                List.of("semantic smoke", "single-screen runtime gate", "V2-C confirmation", "V2-D attribution"),
                List.of("Open a narrow prototype phase for one family only; keep mutation disabled until gates are defined."));
        }
        return result(input, AdmissionDecision.GENERATED_REPORT_ONLY, false,
            List.of("Generated/application discovery evidence is present, but it is not yet a mutation candidate."),
            List.of("runtime relevance", "high-ROI byte/class threshold", "family-specific safety model"),
            List.of("Continue inventory and runtime correlation before choosing a generated/application optimizer."));
    }

    private static List<String> artifactEvidenceGaps(ReducerAdmissionInput input) {
        List<String> gaps = new ArrayList<>();
        if (!input.artifactEvidencePresent() || input.artifactBytesRemoved() <= 0) {
            gaps.add("positive artifact reduction evidence");
        }
        if (input.classesReduced() <= 0) {
            gaps.add("reduced class records");
        }
        if (!input.rawAuditPresent()) {
            gaps.add("raw byte-preservation audit report");
        }
        return gaps;
    }

    private static List<String> confirmationEvidenceGaps(ReducerAdmissionInput input) {
        List<String> gaps = new ArrayList<>();
        if (!Boolean.TRUE.equals(input.semanticSmokePassed())) {
            gaps.add("passing semantic smoke");
        }
        if (!input.hasV2DAttribution()) {
            gaps.add("V2-D attribution");
        }
        return gaps;
    }

    private static List<String> protocolGaps(ReducerAdmissionInput input) {
        List<String> gaps = new ArrayList<>();
        if (!sameService(input.service(), input.confirmedService())) {
            gaps.add("service does not match confirmed service");
        }
        if (!sameNormalized(input.launchMode(), input.confirmedLaunchMode())) {
            gaps.add("launch mode does not match confirmed launch mode");
        }
        if (!sameNormalized(input.runtimePolicy(), input.confirmedRuntimePolicy())) {
            gaps.add("runtime policy does not match confirmed runtime policy");
        }
        return gaps;
    }

    private static boolean protocolMatches(ReducerAdmissionInput input) {
        return input.hasV2CConfirmation()
            && sameService(input.service(), input.confirmedService())
            && sameNormalized(input.launchMode(), input.confirmedLaunchMode())
            && sameNormalized(input.runtimePolicy(), input.confirmedRuntimePolicy());
    }

    private static boolean applicationRoiMeetsThreshold(ReducerAdmissionInput input) {
        return input.applicationBytesRemoved() >= APPLICATION_MIN_REMOVED_BYTES
            || input.applicationClassesReduced() >= APPLICATION_MIN_REDUCED_CLASSES;
    }

    private static boolean generatedHighRoi(ReducerAdmissionInput input) {
        return input.generatedRuntimeRelevant()
            || input.generatedEstimatedBytes() >= GENERATED_HIGH_ROI_BYTES
            || input.generatedClassCount() >= GENERATED_HIGH_ROI_CLASSES;
    }

    private static boolean hasApplicationRuntimeEvidence(ReducerAdmissionInput input) {
        if (isPassedScreen(input.screenVerdict())) {
            return true;
        }
        return input.hasV2CConfirmation() && input.sourceReports().stream()
            .map(ReducerRecommendationEngine::normalize)
            .anyMatch(name -> name.contains("V2Q") && name.contains("APPLICATION")
                && (name.contains("CONFIRMATION") || name.contains("RUNTIME_SCREEN")));
    }

    private static List<String> unsafeAttributes(ReducerAdmissionInput input) {
        Set<String> unsafe = new HashSet<>();
        for (String attribute : input.strippedAttributes()) {
            if (!ALLOWED_ATTRIBUTES.contains(normalizeAttribute(attribute))) {
                unsafe.add(attribute);
            }
        }
        unsafe.addAll(input.unsafeAttributesRequested());
        return unsafe.stream().sorted().toList();
    }

    private static boolean isFailedScreen(String value) {
        String normalized = normalize(value);
        return normalized.contains("FAILED") || normalized.contains("REGRESSION") || normalized.contains("BLOCKED");
    }

    private static boolean isPassedScreen(String value) {
        String normalized = normalize(value);
        return normalized.contains("PASSED") || normalized.contains("CONFIRMED_WIN");
    }

    private static boolean sameService(String left, String right) {
        String l = normalizeService(left);
        String r = normalizeService(right);
        return !l.isBlank() && l.equals(r);
    }

    private static boolean sameNormalized(String left, String right) {
        String l = normalize(left);
        String r = normalize(right);
        return !l.isBlank() && !"UNKNOWN".equals(l) && l.equals(r);
    }

    private static String normalizeAttribute(String value) {
        return value == null ? "" : value.replace("_", "").replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizeService(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
