package com.yourorg.jmoa.plugin.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ConfirmationScope;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.RecommendationContext;
import com.yourorg.jmoa.plugin.recommendation.RecommendationModels.ReducerAdmissionInput;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class RecommendationInputLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ReducerAdmissionInput load(File inputDirectory, RecommendationContext context) throws IOException {
        if (inputDirectory == null || !inputDirectory.isDirectory()) {
            throw new IllegalArgumentException("Recommendation input directory does not exist: " + inputDirectory);
        }
        File normalized = new File(inputDirectory, "reducer-admission-input.json");
        if (normalized.isFile()) {
            ReducerAdmissionInput input = MAPPER.readValue(normalized, ReducerAdmissionInput.class);
            return applyContext(input, context);
        }
        return aggregate(inputDirectory, context);
    }

    private ReducerAdmissionInput aggregate(File inputDirectory, RecommendationContext context) throws IOException {
        List<String> sources = new ArrayList<>();
        boolean v2bAttributeReportPresent = false;
        long v2bDebugAttributeBytes = 0;
        long v2bLocalVariableTableBytes = 0;
        long artifactBytesRemoved = 0;
        int classesReduced = 0;
        boolean artifactEvidencePresent = false;
        boolean rawAuditPresent = false;
        int failedAudits = 0;
        int signedSkipped = 0;
        int multiReleaseSkipped = 0;
        int sealedSkipped = 0;
        Boolean semanticSmokePassed = null;
        String screenVerdict = "NOT_RUN";
        boolean hasV2c = false;
        String v2cVerdict = "NO_CLAIM";
        boolean hasV2d = false;
        String reducerEngine = "unknown";
        List<String> strippedAttributes = new ArrayList<>();
        String confirmedService = null;
        String confirmedLaunchMode = null;
        String confirmedRuntimePolicy = null;

        Optional<File> attributeReport = find(inputDirectory,
            name -> name.equals("attribute-size-report.json"));
        if (attributeReport.isPresent()) {
            JsonNode root = read(attributeReport.get(), sources);
            v2bAttributeReportPresent = true;
            v2bDebugAttributeBytes = sumLong(root.path("classes"), "debugAttributeBytes");
            v2bLocalVariableTableBytes = sumLong(root.path("classes"), "localVariableTableBytes");
        }

        Optional<File> reducerReport = find(inputDirectory,
            name -> name.equals("reducer-build-report.json"));
        if (reducerReport.isPresent()) {
            JsonNode root = read(reducerReport.get(), sources);
            long original = root.path("totalOriginalBytes").asLong(0);
            long reduced = root.path("totalReducedBytes").asLong(0);
            artifactBytesRemoved = root.path("totalRemovedBytes").asLong(Math.max(0, original - reduced));
            artifactEvidencePresent = root.path("mutationEnabled").asBoolean(false)
                && root.path("jarCount").asInt(0) > 0;
            reducerEngine = text(root, "engine", reducerEngine);
            classesReduced = sum(root.path("artifacts"), "reducedClassCount");
        }

        Optional<File> auditReport = find(inputDirectory,
            name -> name.equals("raw-reducer-byte-preservation-report.json"));
        if (auditReport.isPresent()) {
            JsonNode root = read(auditReport.get(), sources);
            rawAuditPresent = true;
            failedAudits = root.path("failedAuditCount").asInt(0);
            if (root.has("preservedNonTargetStructures")
                && !root.path("preservedNonTargetStructures").asBoolean(false)) {
                failedAudits = Math.max(1, failedAudits);
            }
            classesReduced = root.path("auditedClassCount").asInt(classesReduced);
            reducerEngine = text(root, "engine", reducerEngine);
        }

        Optional<File> safetyReport = find(inputDirectory,
            name -> name.equals("v2f-jar-safety-report.json"));
        if (safetyReport.isPresent()) {
            JsonNode root = read(safetyReport.get(), sources);
            signedSkipped = root.path("signedJarsSkipped").asInt(0);
            multiReleaseSkipped = root.path("multiReleaseJarsSkipped").asInt(0);
            sealedSkipped = root.path("sealedJarsSkipped").asInt(0);
        }

        Optional<File> manifest = find(inputDirectory,
            name -> name.equals("jmoa-reducer-manifest-v2.json"));
        if (manifest.isPresent()) {
            JsonNode root = read(manifest.get(), sources);
            reducerEngine = text(root, "engine", reducerEngine);
            for (JsonNode attribute : root.path("strippedAttributes")) {
                strippedAttributes.add(attribute.asText());
            }
            if (!artifactEvidencePresent) {
                artifactBytesRemoved = sumLong(root.path("artifacts"), "removedBytes");
                classesReduced = sum(root.path("artifacts"), "classesReduced");
                artifactEvidencePresent = root.path("mutationEnabled").asBoolean(false)
                    && root.path("artifacts").isArray()
                    && !root.path("artifacts").isEmpty();
            }
        }

        Optional<File> semanticReport = find(inputDirectory,
            name -> name.equals("jmoa-semantic-smoke.json")
                || name.equals("semantic-smoke-report.json")
                || name.endsWith("-semantic-smoke.json"));
        if (semanticReport.isPresent()) {
            JsonNode root = read(semanticReport.get(), sources);
            semanticSmokePassed = semanticStatus(root);
        }

        Optional<File> screenReport = find(inputDirectory,
            name -> name.equals("jmoa-runtime-screen.json")
                || name.endsWith("-runtime-screen.json")
                || name.endsWith("-hardened-screen.json"));
        if (screenReport.isPresent()) {
            JsonNode root = read(screenReport.get(), sources);
            screenVerdict = screenStatus(root);
        }

        Optional<File> confirmationReport = find(inputDirectory,
            name -> name.equals("jmoa-paired-confirmation.json")
                || (name.endsWith("-confirmation.json") && !name.contains("validation")));
        if (confirmationReport.isPresent()) {
            JsonNode root = read(confirmationReport.get(), sources);
            v2cVerdict = confirmationVerdict(root);
            hasV2c = !"NO_CLAIM".equals(normalize(v2cVerdict));
            confirmedService = text(root, "service", confirmedService);
            JsonNode scope = root.path("runtimeScope");
            confirmedLaunchMode = text(scope, "launchMode", confirmedLaunchMode);
            confirmedRuntimePolicy = text(scope, "runtimePolicy", confirmedRuntimePolicy);
        }

        Optional<File> evidenceValidation = find(inputDirectory,
            name -> name.equals("jmoa-evidence-validation.json"));
        if (evidenceValidation.isPresent()) {
            JsonNode root = read(evidenceValidation.get(), sources);
            JsonNode runs = root.path("runEvidence");
            if (runs.isArray() && !runs.isEmpty()) {
                JsonNode runManifest = runs.get(0).path("manifest");
                confirmedService = text(runManifest, "service", confirmedService);
                confirmedLaunchMode = text(runManifest, "launchMode", confirmedLaunchMode);
                confirmedRuntimePolicy = text(runManifest, "runtimePolicy", confirmedRuntimePolicy);
            }
        }

        Optional<File> attributionReport = find(inputDirectory,
            name -> name.equals("jmoa-memory-attribution.json")
                || name.endsWith("-v2d-attribution.json"));
        if (attributionReport.isPresent()) {
            JsonNode root = read(attributionReport.get(), sources);
            hasV2d = root.path("v2cValid").asBoolean("PASSED".equals(normalize(text(root, "status", ""))));
        }

        String service = context == null ? null : context.service();
        String launchMode = context == null ? null : context.launchMode();
        String runtimePolicy = context == null ? null : context.runtimePolicy();
        ConfirmationScope scope = context == null ? ConfirmationScope.UNKNOWN : context.confirmationScope();
        boolean diagnosticOnly = "DIAGNOSTIC".equals(normalize(runtimePolicy));

        ReducerAdmissionInput input = new ReducerAdmissionInput(
            "v2m-reducer-admission-input",
            service,
            launchMode,
            runtimePolicy,
            v2bAttributeReportPresent,
            v2bDebugAttributeBytes,
            v2bLocalVariableTableBytes,
            artifactBytesRemoved,
            classesReduced,
            artifactEvidencePresent,
            rawAuditPresent,
            failedAudits,
            signedSkipped,
            multiReleaseSkipped,
            sealedSkipped,
            semanticSmokePassed,
            screenVerdict,
            hasV2c,
            v2cVerdict,
            hasV2d,
            diagnosticOnly,
            reducerEngine,
            strippedAttributes,
            List.of(),
            confirmedService,
            confirmedLaunchMode,
            confirmedRuntimePolicy,
            scope,
            sources
        );
        return applyContext(input, context);
    }

    private static ReducerAdmissionInput applyContext(
        ReducerAdmissionInput input,
        RecommendationContext context
    ) {
        if (context == null) {
            return input;
        }
        String service = override(context.service(), input.service());
        String launchMode = override(context.launchMode(), input.launchMode());
        String runtimePolicy = override(context.runtimePolicy(), input.runtimePolicy());
        ConfirmationScope scope = context.confirmationScope() == ConfirmationScope.UNKNOWN
            ? input.confirmationScope()
            : context.confirmationScope();
        return new ReducerAdmissionInput(
            input.metadataVersion(),
            service,
            launchMode,
            runtimePolicy,
            input.v2bAttributeReportPresent(),
            input.v2bDebugAttributeBytes(),
            input.v2bLocalVariableTableBytes(),
            input.artifactBytesRemoved(),
            input.classesReduced(),
            input.artifactEvidencePresent(),
            input.rawAuditPresent(),
            input.failedAudits(),
            input.signedJarsSkipped(),
            input.multiReleaseJarsSkipped(),
            input.sealedJarsSkipped(),
            input.semanticSmokePassed(),
            input.screenVerdict(),
            input.hasV2CConfirmation(),
            input.v2cVerdict(),
            input.hasV2DAttribution(),
            input.diagnosticOnly() || "DIAGNOSTIC".equals(normalize(runtimePolicy)),
            input.reducerEngine(),
            input.strippedAttributes(),
            input.unsafeAttributesRequested(),
            input.confirmedService(),
            input.confirmedLaunchMode(),
            input.confirmedRuntimePolicy(),
            scope,
            input.sourceReports()
        );
    }

    private static JsonNode read(File file, List<String> sources) throws IOException {
        sources.add(file.getName());
        return MAPPER.readTree(file);
    }

    private static Optional<File> find(File root, Predicate<String> namePredicate) throws IOException {
        try (Stream<Path> paths = Files.walk(root.toPath(), 4)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> namePredicate.test(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparingInt(path -> path.getNameCount()))
                .map(Path::toFile)
                .findFirst();
        }
    }

    private static Boolean semanticStatus(JsonNode root) {
        String status = normalize(text(root, "status", ""));
        if (status.contains("PASSED") || status.contains("CONFIRMED")) {
            return true;
        }
        if (status.contains("FAILED") || status.contains("BLOCKED")) {
            return false;
        }
        if (root.has("workloadErrors")) {
            return root.path("workloadErrors").asInt(1) == 0;
        }
        return null;
    }

    private static String screenStatus(JsonNode root) {
        String status = text(root, "status", "");
        if (!status.isBlank()) {
            return status;
        }
        JsonNode gate = root.path("gate");
        if (gate.isObject()) {
            for (JsonNode value : gate) {
                if (value.isBoolean() && !value.asBoolean()) {
                    return "FAILED";
                }
            }
            return "PASSED";
        }
        return "UNKNOWN";
    }

    private static String confirmationVerdict(JsonNode root) {
        String verdict = text(root, "verdict", "");
        if (!verdict.isBlank()) {
            return verdict;
        }
        verdict = text(root.path("v2c"), "verdict", "");
        if (!verdict.isBlank()) {
            return verdict;
        }
        String status = text(root, "status", "NO_CLAIM");
        return "CONFIRMED_WIN".equals(normalize(status)) ? "CONFIRMED_WIN" : status;
    }

    private static int sum(JsonNode array, String field) {
        int total = 0;
        for (JsonNode item : array) {
            total += item.path(field).asInt(0);
        }
        return total;
    }

    private static long sumLong(JsonNode array, String field) {
        long total = 0;
        for (JsonNode item : array) {
            total += item.path(field).asLong(0);
        }
        return total;
    }

    private static String text(JsonNode root, String field, String fallback) {
        if (root != null && root.hasNonNull(field) && !root.path(field).asText().isBlank()) {
            return root.path(field).asText();
        }
        return fallback;
    }

    private static String override(String requested, String existing) {
        if (requested == null || requested.isBlank() || "UNKNOWN".equals(normalize(requested))) {
            return existing;
        }
        return requested.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }
}
