package com.yourorg.jmoa.plugin.siteeconomics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.filter.LambdaProfileIndex;
import com.yourorg.jmoa.plugin.filter.LambdaProfileReader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Connected profile/static-evidence boundary for T1. It accepts at most three
 * predeclared Tier-2 layout candidates, validates the v3 selection projection
 * through {@link LambdaProfileReader}, creates exact feature rows from audit
 * site keys, trains the model, ranks every qualified candidate, and freezes a
 * single candidate without consulting runtime/PSS outcomes.
 */
public final class SiteEconomicsOrchestrator {
    public record CandidateSpec(String id, String mode, boolean compact) {
        public CandidateSpec {
            if (id == null || id.isBlank() || mode == null || mode.isBlank()) {
                throw new IllegalArgumentException("candidate id and mode are required");
            }
        }
    }

    public record CandidateEvaluation(
            CandidateSpec candidate,
            boolean staticQualified,
            String staticDecision,
            long generatedLayoutBytes,
            int exactSiteKeyCount,
            MonotoneFamilyFrontierRanker.FamilyRanking ranking) {
    }

    public enum Status { SELECTED, REJECTED }

    public record Result(
            String schemaVersion,
            Status status,
            String reason,
            String profileSha256,
            String staticAuditSha256,
            int trainingSelectionSiteCount,
            FamilyEconomicsModelTrainer.TrainingResult modelTraining,
            List<CandidateEvaluation> candidates,
            CandidateSpec selectedCandidate,
            AdmissionManifestGenerator.AdmissionManifest admission) {
        public Result {
            candidates = List.copyOf(candidates);
            if ((status == Status.SELECTED) != (selectedCandidate != null && admission != null)) {
                throw new IllegalArgumentException("Only SELECTED results carry a candidate and admission");
            }
        }
    }

    private record ProfileSite(
            String siteKey, String familyKey, String owner, String packageName, String samDescriptor,
            int startup, int training, int validation, Long firstActivationNanos,
            boolean loaded, Long exactAllocationBytes) {
    }

    private record StaticAdapter(long logicalBytes, List<String> siteKeys, List<String> physicalRoots) {
    }

    public Result evaluate(File profileFile, File staticAuditFile, List<CandidateSpec> candidateSpecs) throws IOException {
        if (candidateSpecs == null || candidateSpecs.isEmpty() || candidateSpecs.size() > 3) {
            throw new IllegalArgumentException("One to three predeclared static candidates are required");
        }
        Set<String> candidateIds = new LinkedHashSet<>();
        if (candidateSpecs.stream().anyMatch(candidate -> !candidateIds.add(candidate.id()))) {
            throw new IllegalArgumentException("Candidate IDs must be unique");
        }
        String profileSha = sha256(profileFile);
        String auditSha = sha256(staticAuditFile);
        LambdaProfileIndex selection = LambdaProfileReader.read(profileFile);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode profile = mapper.readTree(profileFile);
        if (!"jmoa-service-profile-v3".equals(profile.path("schemaVersion").asText())) {
            throw new IOException("Site economics requires a profile v3 input");
        }
        Map<String, ProfileSite> profileSites = readProfileSites(profile, selection);
        JsonNode audit = mapper.readTree(staticAuditFile);
        if (!"jmoa-tier2-static-mode-audit-v1".equals(audit.path("schemaVersion").asText())) {
            throw new IOException("Site economics requires a Tier-2 static mode audit v1 input");
        }

        List<CandidateEvaluation> evaluations = new ArrayList<>();
        Map<String, List<SiteFeatureRow>> rowsByCandidate = new LinkedHashMap<>();
        for (CandidateSpec spec : candidateSpecs) {
            JsonNode mode = findMode(audit, spec);
            boolean qualified = requiredBoolean(mode, "collisionFree")
                    && requiredBoolean(mode, "generatedReferenceClosure")
                    && requiredBoolean(mode, "byteDeterministic")
                    && mode.path("multiRootAdapters").asInt(-1) == 0;
            String staticDecision = qualified ? "STATIC_QUALIFIED"
                    : "STATIC_REJECTED_COLLISION_CLOSURE_DETERMINISM_OR_ROOT_PLACEMENT";
            List<StaticAdapter> adapters = readAdapters(mode, profileSites.keySet());
            List<SiteFeatureRow> rows = buildRows(adapters, profileSites);
            rowsByCandidate.put(spec.id(), rows);
            long bytes = adapters.stream().mapToLong(StaticAdapter::logicalBytes).sum();
            evaluations.add(new CandidateEvaluation(spec, qualified, staticDecision, bytes,
                    (int) adapters.stream().flatMap(adapter -> adapter.siteKeys().stream()).distinct().count(), null));
        }

        String replayMode = profile.path("captureProvenance").path("mode").asText("");
        if (selection.invocationCountsBySiteKey().isEmpty()) {
            String reason = "HISTORICAL_DETERMINISTIC_REPLAY".equals(replayMode)
                    ? "HISTORICAL_REPLAY_HAS_NO_FROZEN_PHASE_OR_ROUTE_ATTRIBUTION"
                    : "NO_STARTUP_OR_TRAINING_ACTIVITY";
            FamilyEconomicsModelTrainer.TrainingResult training = new FamilyEconomicsModelTrainer()
                    .train(rowsByCandidate.values().iterator().next(), profileSha);
            return new Result("jmoa-site-economics-orchestration-v1", Status.REJECTED, reason,
                    profileSha, auditSha, 0, training, evaluations, null, null);
        }

        List<SiteFeatureRow> trainingRows = rowsByCandidate.values().iterator().next();
        FamilyEconomicsModelTrainer.TrainingResult training = new FamilyEconomicsModelTrainer()
                .train(trainingRows, profileSha);
        if (training.status() == FamilyEconomicsModelTrainer.Status.REJECTED) {
            return new Result("jmoa-site-economics-orchestration-v1", Status.REJECTED, training.reason(),
                    profileSha, auditSha, selection.invocationCountsBySiteKey().size(), training, evaluations, null, null);
        }

        MonotoneFamilyFrontierRanker ranker = new MonotoneFamilyFrontierRanker();
        SiteFamilyAggregator aggregator = new SiteFamilyAggregator();
        List<CandidateEvaluation> ranked = new ArrayList<>();
        for (CandidateEvaluation evaluation : evaluations) {
            MonotoneFamilyFrontierRanker.FamilyRanking ranking = ranker.rank(
                    aggregator.aggregate(rowsByCandidate.get(evaluation.candidate().id())), training.model());
            ranked.add(new CandidateEvaluation(evaluation.candidate(), evaluation.staticQualified(),
                    evaluation.staticDecision(), evaluation.generatedLayoutBytes(), evaluation.exactSiteKeyCount(), ranking));
        }
        CandidateEvaluation selected = ranked.stream()
                .filter(CandidateEvaluation::staticQualified)
                .filter(candidate -> !candidate.ranking().admittedFamilyKeys().isEmpty())
                .sorted(Comparator.comparingLong(CandidateEvaluation::generatedLayoutBytes)
                        .thenComparing(candidate -> candidate.candidate().id()))
                .findFirst().orElse(null);
        if (selected == null) {
            return new Result("jmoa-site-economics-orchestration-v1", Status.REJECTED,
                    "NO_STATICALLY_QUALIFIED_CANDIDATE_WITH_ADMITTED_FAMILY", profileSha, auditSha,
                    selection.invocationCountsBySiteKey().size(), training, ranked, null, null);
        }
        AdmissionManifestGenerator.AdmissionManifest admission = new AdmissionManifestGenerator().generate(
                selected.ranking(), profileSha, selected.generatedLayoutBytes());
        return new Result("jmoa-site-economics-orchestration-v1", Status.SELECTED,
                "SELECTED_LOWEST_GENERATED_LAYOUT_BYTES_THEN_CANDIDATE_ID", profileSha, auditSha,
                selection.invocationCountsBySiteKey().size(), training, ranked, selected.candidate(), admission);
    }

    private static Map<String, ProfileSite> readProfileSites(JsonNode profile, LambdaProfileIndex selection) throws IOException {
        Set<String> loaded = new LinkedHashSet<>();
        profile.path("loadedClasses").forEach(node -> loaded.add(node.asText()));
        Map<String, Long> exactFamilyAllocation = new TreeMap<>();
        Map<String, Integer> familySiteCounts = new TreeMap<>();
        Map<String, Integer> exactFamilySiteCounts = new TreeMap<>();
        for (JsonNode site : profile.path("lambdaSites")) {
            String family = site.path("familyKey").asText("");
            familySiteCounts.merge(family, 1, Integer::sum);
            if ("EXACT".equals(site.path("allocationMeasurement").asText()) && site.path("allocationBytes").isIntegralNumber()) {
                exactFamilyAllocation.merge(family, site.path("allocationBytes").longValue(), Math::addExact);
                exactFamilySiteCounts.merge(family, 1, Integer::sum);
            }
        }
        Map<String, ProfileSite> result = new LinkedHashMap<>();
        for (JsonNode site : profile.path("lambdaSites")) {
            String siteKey = site.path("siteKey").asText("");
            String family = site.path("familyKey").asText("");
            Long familyAllocation = familySiteCounts.getOrDefault(family, 0).equals(exactFamilySiteCounts.getOrDefault(family, 0))
                    ? exactFamilyAllocation.get(family) : null;
            JsonNode first = site.get("firstActivationNanos");
            Long firstNanos = first != null && first.isIntegralNumber() ? first.longValue() : null;
            ProfileSite value = new ProfileSite(siteKey, family, site.path("ownerInternalName").asText(),
                    site.path("packageInternalName").asText(), site.path("samMethodTypeDesc").asText(),
                    Math.toIntExact(site.path("startupInvocationCount").longValue()),
                    Math.toIntExact(site.path("trainingInvocationCount").longValue()),
                    Math.toIntExact(site.path("validationInvocationCount").longValue()), firstNanos,
                    loaded.contains(site.path("ownerInternalName").asText()), familyAllocation);
            if (result.put(siteKey, value) != null) throw new IOException("Duplicate profile siteKey: " + siteKey);
        }
        return result;
    }

    private static JsonNode findMode(JsonNode audit, CandidateSpec spec) throws IOException {
        JsonNode found = null;
        for (JsonNode mode : audit.path("modes")) {
            if (spec.mode().equals(mode.path("mode").asText()) && spec.compact() == mode.path("compact").asBoolean()) {
                if (found != null) throw new IOException("Duplicate static audit mode for " + spec.id());
                found = mode;
            }
        }
        if (found == null) throw new IOException("Static audit mode not found for " + spec.id());
        return found;
    }

    private static List<StaticAdapter> readAdapters(JsonNode mode, Set<String> profileSiteKeys) throws IOException {
        if (!mode.path("adapters").isArray()) throw new IOException("Static mode adapters array is required");
        List<StaticAdapter> adapters = new ArrayList<>();
        Set<String> allSiteKeys = new LinkedHashSet<>();
        for (JsonNode adapter : mode.path("adapters")) {
            long bytes = adapter.path("logicalBytes").longValue();
            if (bytes <= 0L || !adapter.path("siteKeys").isArray() || !adapter.path("outputOrigins").isArray()) {
                throw new IOException("Static adapter requires positive bytes, siteKeys, and outputOrigins");
            }
            List<String> keys = new ArrayList<>();
            for (JsonNode key : adapter.path("siteKeys")) {
                String siteKey = key.asText("");
                if (siteKey.isBlank() || !profileSiteKeys.contains(siteKey) || !allSiteKeys.add(siteKey)) {
                    throw new IOException("Static adapter siteKey is blank, absent from profile, or duplicated: " + siteKey);
                }
                keys.add(siteKey);
            }
            if (keys.isEmpty()) throw new IOException("Static adapter must identify at least one siteKey");
            List<String> roots = new ArrayList<>();
            adapter.path("outputOrigins").forEach(origin -> roots.add(origin.path("root").asText()));
            adapters.add(new StaticAdapter(bytes, List.copyOf(keys), List.copyOf(roots)));
        }
        return adapters;
    }

    private static List<SiteFeatureRow> buildRows(List<StaticAdapter> adapters, Map<String, ProfileSite> sites) {
        List<SiteFeatureRow> rows = new ArrayList<>();
        for (StaticAdapter adapter : adapters) {
            List<String> keys = adapter.siteKeys().stream().sorted().toList();
            for (int index = 0; index < keys.size(); index++) {
                ProfileSite site = sites.get(keys.get(index));
                rows.add(new SiteFeatureRow.Builder().siteKey(site.siteKey()).familyKey(site.familyKey())
                        .ownerInternalName(site.owner()).packageInternalName(site.packageName())
                        .physicalOutputRoot(String.join("|", adapter.physicalRoots())).tier("TIER2")
                        .samDescriptor(site.samDescriptor()).startupInvocationCount(site.startup())
                        .trainingInvocationCount(site.training()).validationInvocationCount(site.validation())
                        .firstActivationNanos(site.firstActivationNanos()).loadedClassObserved(site.loaded())
                        .generatedAdapterCount(index == 0 ? 1L : 0L)
                        .generatedClassBytes(index == 0 ? adapter.logicalBytes() : 0L)
                        .measuredFamilyAllocationBytes(site.exactAllocationBytes()).build());
            }
        }
        return List.copyOf(rows);
    }

    private static boolean requiredBoolean(JsonNode node, String field) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) throw new IOException("Static audit requires boolean " + field);
        return value.booleanValue();
    }

    private static String sha256(File file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(file.toPath()))).toUpperCase();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
