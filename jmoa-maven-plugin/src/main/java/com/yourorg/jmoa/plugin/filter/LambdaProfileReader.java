package com.yourorg.jmoa.plugin.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;

public final class LambdaProfileReader {

    private static final Set<String> V3_ROOT_FIELDS = Set.of(
            "schemaVersion", "profileVersion", "serviceName", "generatedAt", "trainingDurationSeconds",
            "identity", "captureProvenance", "lifecycle", "optimizerProjection", "crossFitPartitions",
            "measurementHonesty", "diagnosticEvidence", "loadedClasses", "hotClasses", "coldClasses", "lambdaSites");
    private static final Set<String> V3_SITE_FIELDS = Set.of(
            "siteKey", "ownerInternalName", "packageInternalName", "enclosingMethodName", "enclosingMethodDesc",
            "siteOrdinalInMethod", "indyName", "indyFactoryDesc", "samMethodTypeDesc", "capturing",
            "serializable", "implTag", "implOwner", "implName", "implDesc", "instantiatedMethodTypeDesc",
            "factoryInvocationCount", "startupInvocationCount", "trainingInvocationCount",
            "validationInvocationCount", "unlabelledInvocationCount", "selectionFactoryInvocationCount",
            "firstActivationNanos", "lastActivationNanos", "threads", "threadCardinality", "phaseActivity",
            "routeActivity", "samInvocationCount", "allocationBytes", "fallbackCount", "measurementCoverage",
            "familyKey", "routePartition", "allocationMeasurement", "selectionDecision", "diagnosticJoins");
    private static final List<String> PHASES = List.of("STARTUP", "TRAINING", "VALIDATION", "UNLABELLED");
    private static final List<String> PARTITIONS = List.of("TRAINING", "HELD_OUT", "UNATTRIBUTED");

    private LambdaProfileReader() {
    }

    public static LambdaProfileIndex read(File profileFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(profileFile);

        Map<String, Long> countsBySiteKey = new LinkedHashMap<>();
        Set<String> hotClasses = new LinkedHashSet<>();
        Set<String> coldClasses = new LinkedHashSet<>();

        if ("jmoa-service-profile-v2".equals(root.path("schemaVersion").asText())) {
            readLifecycleProfileV2(root, countsBySiteKey, hotClasses, coldClasses);
            return new LambdaProfileIndex(countsBySiteKey, hotClasses, coldClasses);
        }
        if ("jmoa-service-profile-v3".equals(root.path("schemaVersion").asText())) {
            readLifecycleProfileV3(root, countsBySiteKey, hotClasses, coldClasses);
            return new LambdaProfileIndex(countsBySiteKey, hotClasses, coldClasses);
        }
        if (root.has("schemaVersion")) {
            throw new IOException("Unsupported lifecycle profile schemaVersion: " + root.path("schemaVersion").asText());
        }

        root.path("hotClasses").forEach(node -> hotClasses.add(node.asText()));
        root.path("coldClasses").forEach(node -> coldClasses.add(node.asText()));
        root.path("lambdaSites").forEach(node -> {
            String siteKey = node.path("siteKey").asText("");
            if (!siteKey.isBlank()) {
                countsBySiteKey.put(siteKey, node.path("invocationCount").asLong(0L));
            }
        });

        return new LambdaProfileIndex(countsBySiteKey, hotClasses, coldClasses);
    }

    /**
     * Strict selection projection for v3. Schema-validity is checked again by
     * the evidence pipeline; this reader independently fails closed on every
     * field and invariant capable of changing optimizer selection.
     */
    private static void readLifecycleProfileV3(
            JsonNode root,
            Map<String, Long> countsBySiteKey,
            Set<String> hotClasses,
            Set<String> coldClasses) throws IOException {
        rejectUnknownFields(root, V3_ROOT_FIELDS, "profile v3 root");
        if (!root.path("profileVersion").isInt() || root.path("profileVersion").intValue() != 3) {
            throw new IOException("Profile v3 requires profileVersion=3.");
        }
        JsonNode projection = requiredObject(root, "optimizerProjection", "profile v3 root");
        requireTextEquals(projection, "policy", "STARTUP_PLUS_TRAINING_FACTORY_INVOCATIONS");
        requireTextEquals(projection, "heldOutPhase", "VALIDATION");
        requireTextEquals(projection, "countField", "selectionFactoryInvocationCount");
        if (!projection.path("validationExcludedFromAdmission").isBoolean()
                || !projection.path("validationExcludedFromAdmission").booleanValue()) {
            throw new IOException("Profile v3 must exclude VALIDATION from admission.");
        }
        requireExactTextArray(projection, "selectionPhases", List.of("STARTUP", "TRAINING"));
        requireExactTextArray(projection, "excludedPhases", List.of("VALIDATION", "UNLABELLED"));

        JsonNode partitions = requiredObject(root, "crossFitPartitions", "profile v3 root");
        requireTrue(partitions, "frozenBeforeCollection");
        requireTrue(partitions, "frozenBeforeRanking");
        Set<String> trainingRoutes = requireUniqueTextSet(partitions, "trainingPartitionRoutes");
        Set<String> heldOutRoutes = requireUniqueTextSet(partitions, "heldOutPartitionRoutes");
        if (trainingRoutes.isEmpty() || heldOutRoutes.isEmpty()) {
            throw new IOException("Profile v3 route partitions must both be non-empty.");
        }
        Set<String> overlap = new LinkedHashSet<>(trainingRoutes);
        overlap.retainAll(heldOutRoutes);
        if (!overlap.isEmpty()) throw new IOException("Profile v3 route partitions overlap: " + overlap);

        JsonNode honesty = requiredObject(root, "measurementHonesty", "profile v3 root");
        requireTextEquals(honesty, "allocationFieldPolicy",
                "allocationBytes is null unless allocationMeasurement=EXACT; sampled diagnostics are never presented as exact per-site bytes");
        if (honesty.path("diagnosticJoinPolicy").asText("").isBlank()) {
            throw new IOException("Profile v3 diagnosticJoinPolicy is required.");
        }

        JsonNode loadedClassNodes = root.get("loadedClasses");
        JsonNode siteNodes = root.get("lambdaSites");
        if (loadedClassNodes == null || !loadedClassNodes.isArray()) {
            throw new IOException("Profile v3 requires a loadedClasses array.");
        }
        if (siteNodes == null || !siteNodes.isArray()) {
            throw new IOException("Profile v3 requires a lambdaSites array.");
        }
        boolean historicalReplay = "HISTORICAL_DETERMINISTIC_REPLAY".equals(
                root.path("captureProvenance").path("mode").asText());
        Set<String> loadedClasses = readUniqueTextArray(loadedClassNodes, "Profile v3 loadedClasses");
        Set<String> seenSiteKeys = new LinkedHashSet<>();
        for (JsonNode site : siteNodes) {
            rejectUnknownFields(site, V3_SITE_FIELDS, "profile v3 lambda site");
            String siteKey = site.path("siteKey").asText("");
            if (siteKey.isBlank() || !seenSiteKeys.add(siteKey)) {
                throw new IOException("Profile v3 contains a blank or duplicate siteKey: " + siteKey);
            }
            String owner = site.path("ownerInternalName").asText("");
            if (owner.isBlank()) throw new IOException("Profile v3 site has no ownerInternalName: " + siteKey);
            long startup = requiredNonNegativeLong(site, "startupInvocationCount", siteKey, "v3");
            long training = requiredNonNegativeLong(site, "trainingInvocationCount", siteKey, "v3");
            long validation = requiredNonNegativeLong(site, "validationInvocationCount", siteKey, "v3");
            long unlabelled = requiredNonNegativeLong(site, "unlabelledInvocationCount", siteKey, "v3");
            long factory = requiredPositiveLong(site, "factoryInvocationCount", siteKey, "v3");
            long selection = exactSum(siteKey, startup, training);
            long exactTotal = exactSum(siteKey, selection, validation, unlabelled);
            if (factory != exactTotal) {
                throw new IOException("Profile v3 factory count does not equal all phase counts at " + siteKey);
            }
            if (requiredNonNegativeLong(site, "selectionFactoryInvocationCount", siteKey, "v3") != selection) {
                throw new IOException("Profile v3 selection count is not STARTUP + TRAINING at " + siteKey);
            }
            validatePhaseActivity(site, siteKey, Map.of(
                    "STARTUP", startup, "TRAINING", training, "VALIDATION", validation, "UNLABELLED", unlabelled),
                    historicalReplay);
            validateRouteActivity(site, siteKey, factory, training, validation, trainingRoutes, heldOutRoutes);
            validateMeasurementHonesty(site, siteKey);
            JsonNode decision = requiredObject(site, "selectionDecision", "profile v3 site " + siteKey);
            String disposition = decision.path("disposition").asText("");
            if (!Set.of("ADMIT", "DEFER", "REJECT").contains(disposition)
                    || decision.path("reason").asText("").isBlank()
                    || decision.path("coverage").asText("").isBlank()
                    || decision.path("confidence").asText("").isBlank()) {
                throw new IOException("Profile v3 selectionDecision is incomplete at " + siteKey);
            }
            JsonNode coverage = requiredObject(site, "measurementCoverage", "profile v3 site " + siteKey);
            String factoryCoverage = coverage.path("factoryInvocations").asText();
            if (!("MEASURED".equals(factoryCoverage)
                    || (historicalReplay && "MEASURED_HISTORICAL_TOTAL_ONLY".equals(factoryCoverage)))) {
                throw new IOException("Profile v3 requires measured factory-invocation coverage at " + siteKey);
            }
            if (selection > 0L) {
                countsBySiteKey.put(siteKey, selection);
                hotClasses.add(owner);
            }
        }
        coldClasses.addAll(loadedClasses);
        coldClasses.removeAll(hotClasses);
    }

    private static void validatePhaseActivity(
            JsonNode site, String siteKey, Map<String, Long> expected, boolean historicalReplay) throws IOException {
        JsonNode activity = requiredObject(site, "phaseActivity", "profile v3 site " + siteKey);
        rejectUnknownFields(activity, Set.copyOf(PHASES), "profile v3 phaseActivity at " + siteKey);
        for (String phase : PHASES) {
            JsonNode phaseNode = requiredObject(activity, phase, "profile v3 phaseActivity at " + siteKey);
            long count = requiredNonNegativeLong(phaseNode, "invocationCount", siteKey + "/" + phase, "v3");
            if (count != expected.get(phase)) {
                throw new IOException("Profile v3 phaseActivity count mismatch at " + siteKey + "/" + phase);
            }
            long cardinality = requiredNonNegativeLong(phaseNode, "threadCardinality", siteKey + "/" + phase, "v3");
            JsonNode threads = phaseNode.get("threads");
            if (threads == null || !threads.isArray() || readUniqueTextArray(threads, "threads at " + siteKey).size() != cardinality) {
                throw new IOException("Profile v3 thread cardinality mismatch at " + siteKey + "/" + phase);
            }
            boolean firstNull = phaseNode.path("firstActivationNanos").isNull();
            boolean lastNull = phaseNode.path("lastActivationNanos").isNull();
            if (count == 0L && !(firstNull && lastNull)) {
                throw new IOException("Profile v3 phase timing nullability mismatch at " + siteKey + "/" + phase);
            }
            if (count > 0L && firstNull != lastNull) {
                throw new IOException("Profile v3 phase timing must provide both endpoints or neither at " + siteKey + "/" + phase);
            }
            if (count > 0L && firstNull && !historicalReplay) {
                throw new IOException("Live profile v3 phase timing is missing at " + siteKey + "/" + phase);
            }
            if (count > 0L && !(historicalReplay && firstNull && lastNull)) {
                long first = requiredNonNegativeLong(phaseNode, "firstActivationNanos", siteKey, "v3");
                long last = requiredNonNegativeLong(phaseNode, "lastActivationNanos", siteKey, "v3");
                if (first > last) throw new IOException("Profile v3 phase timing is reversed at " + siteKey);
            }
        }
    }

    private static void validateRouteActivity(
            JsonNode site, String siteKey, long factory, long training, long validation,
            Set<String> trainingRoutes, Set<String> heldOutRoutes) throws IOException {
        JsonNode routes = requiredObject(site, "routeActivity", "profile v3 site " + siteKey);
        JsonNode partitionCounts = requiredObject(routes, "partitionInvocationCounts", "routeActivity at " + siteKey);
        Map<String, Long> declared = new LinkedHashMap<>();
        for (String partition : PARTITIONS) {
            declared.put(partition, requiredNonNegativeLong(partitionCounts, partition, siteKey, "v3"));
        }
        if (exactSum(siteKey, declared.values().stream().mapToLong(Long::longValue).toArray()) != factory) {
            throw new IOException("Profile v3 route partition counts do not equal factory count at " + siteKey);
        }
        JsonNode matrix = requiredObject(routes, "phasePartitionInvocationCounts", "routeActivity at " + siteKey);
        Map<String, Long> columns = new LinkedHashMap<>();
        PARTITIONS.forEach(partition -> columns.put(partition, 0L));
        for (String phase : PHASES) {
            JsonNode row = requiredObject(matrix, phase, "route matrix at " + siteKey);
            long rowTotal = 0L;
            for (String partition : PARTITIONS) {
                long value = requiredNonNegativeLong(row, partition, siteKey + "/" + phase, "v3");
                rowTotal = exactSum(siteKey, rowTotal, value);
                columns.put(partition, exactSum(siteKey, columns.get(partition), value));
                if (value > 0L && "TRAINING".equals(partition) && !"TRAINING".equals(phase)) {
                    throw new IOException("Profile v3 training route leaked outside TRAINING at " + siteKey);
                }
                if (value > 0L && "HELD_OUT".equals(partition) && !"VALIDATION".equals(phase)) {
                    throw new IOException("Profile v3 held-out route leaked outside VALIDATION at " + siteKey);
                }
            }
            long expectedPhase = requiredNonNegativeLong(site, phase.toLowerCase() + "InvocationCount", siteKey, "v3");
            if (rowTotal != expectedPhase) throw new IOException("Profile v3 route matrix phase mismatch at " + siteKey);
        }
        for (String partition : PARTITIONS) {
            if (!columns.get(partition).equals(declared.get(partition))) {
                throw new IOException("Profile v3 route matrix partition mismatch at " + siteKey);
            }
        }
        if (declared.get("TRAINING") != training || declared.get("HELD_OUT") != validation) {
            throw new IOException("Profile v3 route attribution is incomplete for TRAINING/VALIDATION at " + siteKey);
        }
        JsonNode observed = requiredObject(routes, "observedRouteInvocationCounts", "routeActivity at " + siteKey);
        long observedTotal = 0L;
        var fields = observed.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            long count = requiredPositiveLong(observed, entry.getKey(), siteKey, "v3");
            observedTotal = exactSum(siteKey, observedTotal, count);
            if (!trainingRoutes.contains(entry.getKey()) && !heldOutRoutes.contains(entry.getKey())) {
                throw new IOException("Profile v3 observed an unfrozen route at " + siteKey + ": " + entry.getKey());
            }
        }
        if (observedTotal != exactSum(siteKey, training, validation)) {
            throw new IOException("Profile v3 observed route counts mismatch at " + siteKey);
        }
    }

    private static void validateMeasurementHonesty(JsonNode site, String siteKey) throws IOException {
        String allocationMeasurement = site.path("allocationMeasurement").asText("");
        if (!Set.of("NONE", "SAMPLED_JFR", "SAMPLED_NATIVE", "EXACT").contains(allocationMeasurement)) {
            throw new IOException("Profile v3 has unknown allocationMeasurement at " + siteKey);
        }
        JsonNode allocation = site.get("allocationBytes");
        if ("EXACT".equals(allocationMeasurement)) {
            requiredNonNegativeLong(site, "allocationBytes", siteKey, "v3");
        } else if (allocation == null || !allocation.isNull()) {
            throw new IOException("Profile v3 sampled/unmeasured allocationBytes must remain null at " + siteKey);
        }
    }

    /**
     * Projects a lifecycle-aware profile onto the optimizer's training view.
     * STARTUP and TRAINING factory executions are eligible inputs; VALIDATION
     * is deliberately excluded so held-out traffic cannot influence admission.
     */
    private static void readLifecycleProfileV2(
        JsonNode root,
        Map<String, Long> countsBySiteKey,
        Set<String> hotClasses,
        Set<String> coldClasses
    ) throws IOException {
        JsonNode loadedClassNodes = root.get("loadedClasses");
        JsonNode siteNodes = root.get("lambdaSites");
        if (loadedClassNodes == null || !loadedClassNodes.isArray()) {
            throw new IOException("Profile v2 requires a loadedClasses array.");
        }
        if (siteNodes == null || !siteNodes.isArray()) {
            throw new IOException("Profile v2 requires a lambdaSites array.");
        }
        Set<String> loadedClasses = new LinkedHashSet<>();
        Set<String> seenSiteKeys = new LinkedHashSet<>();
        for (JsonNode node : loadedClassNodes) {
            if (!node.isTextual() || node.asText().isBlank()) {
                throw new IOException("Profile v2 loadedClasses entries must be non-blank strings.");
            }
            if (!loadedClasses.add(node.asText())) {
                throw new IOException("Profile v2 contains duplicate loaded class: " + node.asText());
            }
        }

        for (JsonNode node : siteNodes) {
            String siteKey = node.path("siteKey").asText("");
            if (siteKey.isBlank()) {
                throw new IOException("Profile v2 contains a lambda site without siteKey.");
            }
            if (!seenSiteKeys.add(siteKey)) {
                throw new IOException("Profile v2 contains duplicate siteKey: " + siteKey);
            }
            String owner = node.path("ownerInternalName").asText("");
            if (owner.isBlank()) {
                throw new IOException("Profile v2 site has no ownerInternalName: " + siteKey);
            }
            long startup = requiredNonNegativeLong(node, "startupInvocationCount", siteKey, "v2");
            long training = requiredNonNegativeLong(node, "trainingInvocationCount", siteKey, "v2");
            long validation = requiredNonNegativeLong(node, "validationInvocationCount", siteKey, "v2");
            long factory = requiredPositiveLong(node, "factoryInvocationCount", siteKey, "v2");
            long selection;
            long accounted;
            try {
                selection = Math.addExact(startup, training);
                accounted = Math.addExact(selection, validation);
            } catch (ArithmeticException overflow) {
                throw new IOException("Profile v2 invocation count overflow at " + siteKey, overflow);
            }
            if (factory < accounted) {
                throw new IOException("Profile v2 factoryInvocationCount is smaller than its phase counts at " + siteKey);
            }
            if (node.has("unlabelledInvocationCount")) {
                long unlabelled = requiredNonNegativeLong(node, "unlabelledInvocationCount", siteKey, "v2");
                long exactTotal;
                try {
                    exactTotal = Math.addExact(accounted, unlabelled);
                } catch (ArithmeticException overflow) {
                    throw new IOException("Profile v2 invocation count overflow at " + siteKey, overflow);
                }
                if (factory != exactTotal) {
                    throw new IOException("Profile v2 factoryInvocationCount does not equal its labelled plus unlabelled phase counts at " + siteKey);
                }
            }
            if (node.has("selectionFactoryInvocationCount")) {
                long declaredSelection = requiredNonNegativeLong(node, "selectionFactoryInvocationCount", siteKey, "v2");
                if (declaredSelection != selection) {
                    throw new IOException("Profile v2 selectionFactoryInvocationCount does not equal STARTUP + TRAINING at " + siteKey);
                }
            }

            // A validation-only site is absent from the optimizer index. Merely
            // observing it in the hold-out must not change eligibility or heat.
            if (selection == 0L) {
                continue;
            }
            countsBySiteKey.put(siteKey, selection);
            hotClasses.add(owner);
        }
        coldClasses.addAll(loadedClasses);
        coldClasses.removeAll(hotClasses);
    }

    private static long requiredNonNegativeLong(JsonNode node, String field, String siteKey, String version) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0L) {
            throw new IOException("Profile " + version + " requires a non-negative long " + field + " at " + siteKey);
        }
        return value.longValue();
    }

    private static long requiredPositiveLong(JsonNode node, String field, String siteKey, String version) throws IOException {
        long value = requiredNonNegativeLong(node, field, siteKey, version);
        if (value == 0L) {
            throw new IOException("Profile " + version + " requires a positive long " + field + " at " + siteKey);
        }
        return value;
    }

    private static void rejectUnknownFields(JsonNode node, Set<String> allowed, String context) throws IOException {
        if (node == null || !node.isObject()) throw new IOException(context + " must be an object.");
        Set<String> unknown = new LinkedHashSet<>();
        node.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) unknown.add(field); });
        if (!unknown.isEmpty()) throw new IOException("Unknown selection-affecting fields in " + context + ": " + unknown);
    }

    private static JsonNode requiredObject(JsonNode parent, String field, String context) throws IOException {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) throw new IOException(context + " requires object " + field + ".");
        return value;
    }

    private static void requireTextEquals(JsonNode parent, String field, String expected) throws IOException {
        if (!expected.equals(parent.path(field).asText())) throw new IOException(field + " must equal " + expected + ".");
    }

    private static void requireTrue(JsonNode parent, String field) throws IOException {
        if (!parent.path(field).isBoolean() || !parent.path(field).booleanValue()) {
            throw new IOException(field + " must be true.");
        }
    }

    private static void requireExactTextArray(JsonNode parent, String field, List<String> expected) throws IOException {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray() || value.size() != expected.size()) throw new IOException(field + " is invalid.");
        for (int index = 0; index < expected.size(); index++) {
            if (!value.get(index).isTextual() || !expected.get(index).equals(value.get(index).asText())) {
                throw new IOException(field + " must equal " + expected + ".");
            }
        }
    }

    private static Set<String> requireUniqueTextSet(JsonNode parent, String field) throws IOException {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray()) throw new IOException(field + " must be an array.");
        return readUniqueTextArray(value, field);
    }

    private static Set<String> readUniqueTextArray(JsonNode array, String context) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode node : array) {
            if (!node.isTextual() || node.asText().isBlank() || !result.add(node.asText())) {
                throw new IOException(context + " must contain unique non-blank strings.");
            }
        }
        return result;
    }

    private static long exactSum(String siteKey, long... values) throws IOException {
        long result = 0L;
        try {
            for (long value : values) result = Math.addExact(result, value);
            return result;
        } catch (ArithmeticException overflow) {
            throw new IOException("Profile invocation count overflow at " + siteKey, overflow);
        }
    }
}
