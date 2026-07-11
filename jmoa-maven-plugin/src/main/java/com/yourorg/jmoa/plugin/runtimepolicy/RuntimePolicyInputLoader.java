package com.yourorg.jmoa.plugin.runtimepolicy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyContext;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;

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

public final class RuntimePolicyInputLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public RuntimePolicyAdmissionInput load(File inputDirectory, RuntimePolicyContext context) throws IOException {
        if (inputDirectory == null || !inputDirectory.isDirectory()) {
            throw new IllegalArgumentException("Runtime-policy input directory does not exist: " + inputDirectory);
        }
        File normalized = new File(inputDirectory, "runtime-policy-admission-input.json");
        if (normalized.isFile()) {
            return applyContext(MAPPER.readValue(normalized, RuntimePolicyAdmissionInput.class), context);
        }
        return aggregate(inputDirectory, context);
    }

    private RuntimePolicyAdmissionInput aggregate(File inputDirectory, RuntimePolicyContext context) throws IOException {
        List<String> sources = new ArrayList<>();
        String service = null;
        String launchMode = null;
        String runtimePolicy = null;
        String reducerEngine = "unknown";
        String artifactSha256 = "";
        String cdsArchiveSha256 = "";
        Boolean cdsEnabled = null;
        Boolean appCdsEnabled = null;
        Boolean leydenEnabled = null;
        Boolean javaagentPresent = null;
        boolean artifactEvidencePresent = false;
        Boolean runtimeStackAvailable = null;
        Boolean semanticSmokePassed = null;
        Boolean runtimeMaterializationProofPresent = null;
        Boolean cdsMappedAtRuntime = null;
        boolean hasV2c = false;
        String v2cVerdict = "NO_CLAIM";
        boolean hasV2d = false;
        String screenVerdict = "NOT_RUN";

        Optional<File> confirmation = find(inputDirectory,
            name -> name.equals("jmoa-paired-confirmation.json") || name.endsWith("-confirmation.json"));
        if (confirmation.isPresent()) {
            JsonNode root = read(confirmation.get(), sources);
            service = text(root, "service", service);
            launchMode = firstText(root, "launchMode", root.path("runtimeScope"), "launchMode", launchMode);
            runtimePolicy = firstText(root, "runtimePolicy", root.path("runtimeScope"), "runtimePolicy", runtimePolicy);
            cdsEnabled = firstBoolean(root, "cds", root.path("runtimeScope"), "cdsMode", cdsEnabled);
            appCdsEnabled = firstBoolean(root, "appCds", root.path("runtimeScope"), "appCdsMode", appCdsEnabled);
            leydenEnabled = booleanValue(root, "leyden", leydenEnabled);
            javaagentPresent = firstBoolean(root, "javaagentPresent", root.path("runtimeScope"), "javaagentPresent", javaagentPresent);
            v2cVerdict = confirmationVerdict(root);
            hasV2c = "CONFIRMED_WIN".equals(RuntimePolicyRegistry.normalize(v2cVerdict));
            artifactEvidencePresent = true;
        }

        Optional<File> validation = find(inputDirectory,
            name -> name.equals("jmoa-evidence-validation.json") || name.endsWith("-v2c-validation.json"));
        if (validation.isPresent()) {
            JsonNode root = read(validation.get(), sources);
            JsonNode details = root.path("validation");
            runtimePolicy = firstText(root, "runtimePolicy", details, "runtimePolicy", runtimePolicy);
            cdsEnabled = firstBoolean(root, "cdsMode", details, "cdsMode", cdsEnabled);
            javaagentPresent = firstBoolean(root, "javaagentPresent", details, "javaagentPresent", javaagentPresent);
            String validationVerdict = confirmationVerdict(root);
            if (!"NO_CLAIM".equals(RuntimePolicyRegistry.normalize(validationVerdict))) {
                v2cVerdict = validationVerdict;
                hasV2c = "CONFIRMED_WIN".equals(RuntimePolicyRegistry.normalize(v2cVerdict));
            }
        }

        Optional<File> attribution = find(inputDirectory,
            name -> name.equals("jmoa-memory-attribution.json") || name.endsWith("-v2d-attribution.json"));
        if (attribution.isPresent()) {
            JsonNode root = read(attribution.get(), sources);
            hasV2d = root.path("v2cValid").asBoolean("PASSED".equals(RuntimePolicyRegistry.normalize(text(root, "status", ""))));
        }

        Optional<File> semantic = findPreferred(
            inputDirectory,
            name -> name.endsWith("-semantic-smoke-result.json"),
            name -> name.equals("jmoa-semantic-smoke.json") || name.endsWith("-semantic-smoke.json")
        );
        if (semantic.isPresent()) {
            semanticSmokePassed = semanticStatus(read(semantic.get(), sources));
        }

        Optional<File> materialization = findPreferred(
            inputDirectory,
            name -> name.endsWith("-materialization-proof-result.json"),
            name -> name.equals("jmoa-runtime-materialization-proof.json")
                || name.endsWith("-materialization-proof.json")
        );
        if (materialization.isPresent()) {
            JsonNode root = read(materialization.get(), sources);
            runtimeMaterializationProofPresent = materializationStatus(root);
            runtimeStackAvailable = runtimeMaterializationProofPresent;
            artifactEvidencePresent = artifactEvidencePresent || Boolean.TRUE.equals(runtimeMaterializationProofPresent);
            artifactSha256 = firstText(root, "candidateDependencyLayerSha256", root.path("d2r"), "appJarSha256", artifactSha256);
            cdsArchiveSha256 = firstText(root, "", root.path("d2r"), "cdsSha256", cdsArchiveSha256);
            cdsMappedAtRuntime = firstBoolean(root, "", root.path("d2r"), "cdsMappedInMeasuredRuns", cdsMappedAtRuntime);
        }

        Optional<File> screen = findPreferred(
            inputDirectory,
            name -> name.endsWith("-runtime-screen-result.json"),
            name -> name.equals("jmoa-runtime-screen.json") || name.endsWith("-runtime-screen.json")
                || name.endsWith("-hardened-screen.json")
        );
        if (screen.isPresent()) {
            screenVerdict = screenStatus(read(screen.get(), sources));
        }

        RuntimePolicyScope scope = context == null ? RuntimePolicyScope.UNKNOWN : context.scope();
        RuntimePolicyAdmissionInput input = new RuntimePolicyAdmissionInput(
            "v2n-runtime-policy-admission-input",
            service,
            launchMode,
            runtimePolicy,
            reducerEngine,
            artifactSha256,
            cdsArchiveSha256,
            "UNKNOWN",
            cdsEnabled,
            appCdsEnabled,
            leydenEnabled,
            javaagentPresent,
            artifactEvidencePresent,
            runtimeStackAvailable,
            semanticSmokePassed,
            runtimeMaterializationProofPresent,
            cdsMappedAtRuntime,
            hasV2c,
            v2cVerdict,
            hasV2d,
            screenVerdict,
            false,
            context != null && context.mode() == RuntimePolicyModels.RuntimeRecommendationMode.PREFLIGHT,
            scope,
            sources
        );
        return applyContext(input, context);
    }

    private static RuntimePolicyAdmissionInput applyContext(RuntimePolicyAdmissionInput input, RuntimePolicyContext context) {
        if (context == null) {
            return input;
        }
        return new RuntimePolicyAdmissionInput(
            input.metadataVersion(),
            override(context.service(), input.service()),
            override(context.launchMode(), input.launchMode()),
            override(context.runtimePolicy(), input.runtimePolicy()),
            override(context.reducerEngine(), input.reducerEngine()),
            override(context.artifactSha256(), input.artifactSha256()),
            override(context.cdsArchiveSha256(), input.cdsArchiveSha256()),
            input.comparisonRuntimePolicy(),
            input.cdsEnabled(),
            input.appCdsEnabled(),
            input.leydenEnabled(),
            input.javaagentPresent(),
            input.artifactEvidencePresent(),
            input.runtimeStackAvailable(),
            input.semanticSmokePassed(),
            input.runtimeMaterializationProofPresent(),
            input.cdsMappedAtRuntime(),
            input.hasV2CConfirmation(),
            input.v2cVerdict(),
            input.hasV2DAttribution(),
            input.screenVerdict(),
            input.diagnosticOnly() || RuntimePolicyRegistry.normalize(context.runtimePolicy()).contains("DIAGNOSTIC"),
            context.mode() == RuntimePolicyModels.RuntimeRecommendationMode.PREFLIGHT,
            context.scope() == RuntimePolicyScope.UNKNOWN ? input.scope() : context.scope(),
            input.sourceReports()
        );
    }

    private static Optional<File> find(File root, Predicate<String> predicate) throws IOException {
        try (Stream<Path> paths = Files.walk(root.toPath(), 4)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> predicate.test(path.getFileName().toString().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(Path::toString))
                .map(Path::toFile)
                .findFirst();
        }
    }

    private static Optional<File> findPreferred(
        File root,
        Predicate<String> preferred,
        Predicate<String> fallback
    ) throws IOException {
        Optional<File> preferredFile = find(root, preferred);
        return preferredFile.isPresent() ? preferredFile : find(root, fallback);
    }

    private static JsonNode read(File file, List<String> sources) throws IOException {
        sources.add(file.getName());
        return MAPPER.readTree(file);
    }

    private static String confirmationVerdict(JsonNode root) {
        String verdict = text(root, "verdict", "");
        if (!verdict.isBlank()) {
            return verdict;
        }
        verdict = text(root.path("confirmation"), "verdict", "");
        if (!verdict.isBlank()) {
            return verdict;
        }
        verdict = text(root.path("v2c"), "verdict", "");
        return verdict.isBlank() ? text(root, "status", "NO_CLAIM") : verdict;
    }

    private static Boolean semanticStatus(JsonNode root) {
        String status = RuntimePolicyRegistry.normalize(text(root, "status", ""));
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

    private static Boolean materializationStatus(JsonNode root) {
        String status = RuntimePolicyRegistry.normalize(text(root, "status", ""));
        if (status.contains("PASSED")) {
            return true;
        }
        if (status.contains("NOT_ATTEMPTED") || status.contains("FAILED") || status.contains("BLOCKED")) {
            return false;
        }
        return root.path("allMaterializedHashesMatchReduced").asBoolean(false)
            || root.path("dynamicOriginsVerified").asBoolean(false)
            || root.path("allMaterializedHashesMatchedReducedJars").asBoolean(false);
    }

    private static String screenStatus(JsonNode root) {
        String status = text(root, "status", "");
        if (!status.isBlank()) {
            return status;
        }
        String decision = text(root, "decision", "");
        if (!decision.isBlank()) {
            return decision;
        }
        if (root.path("promotionGate").has("screenPassed")) {
            return root.path("promotionGate").path("screenPassed").asBoolean() ? "PASSED" : "SCREEN_FAILED";
        }
        return "NOT_RUN";
    }

    private static String firstText(JsonNode root, String rootField, JsonNode nested, String nestedField, String fallback) {
        String value = text(root, rootField, "");
        return value.isBlank() ? text(nested, nestedField, fallback) : value;
    }

    private static Boolean firstBoolean(JsonNode root, String rootField, JsonNode nested, String nestedField, Boolean fallback) {
        Boolean value = booleanValue(root, rootField, null);
        return value == null ? booleanValue(nested, nestedField, fallback) : value;
    }

    private static Boolean booleanValue(JsonNode root, String field, Boolean fallback) {
        if (field == null || field.isBlank() || root == null || !root.hasNonNull(field)) {
            return fallback;
        }
        JsonNode value = root.path(field);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        String normalized = RuntimePolicyRegistry.normalize(value.asText());
        if (normalized.equals("ON") || normalized.equals("TRUE") || normalized.equals("PRESENT")) {
            return true;
        }
        if (normalized.equals("OFF") || normalized.equals("FALSE") || normalized.equals("ABSENT")) {
            return false;
        }
        return fallback;
    }

    private static String text(JsonNode root, String field, String fallback) {
        if (root != null && field != null && !field.isBlank() && root.hasNonNull(field)
            && !root.path(field).asText().isBlank()) {
            return root.path(field).asText();
        }
        return fallback;
    }

    private static String override(String requested, String existing) {
        if (requested == null || requested.isBlank() || "UNKNOWN".equals(RuntimePolicyRegistry.normalize(requested))) {
            return existing;
        }
        return requested.trim();
    }
}
