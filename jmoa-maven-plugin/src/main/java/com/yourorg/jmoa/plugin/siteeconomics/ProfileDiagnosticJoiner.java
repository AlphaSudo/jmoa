package com.yourorg.jmoa.plugin.siteeconomics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Offline-only provenance join for perturbing diagnostics. A join annotates
 * resolution and source identity; it never changes allocationBytes. Thus a
 * sampled JFR/native signal cannot become an exact per-site measurement by
 * virtue of sharing a family or process.
 */
public final class ProfileDiagnosticJoiner {
    private static final Set<String> SOURCES = Set.of(
            "JFR", "CLASS_LOAD_LOG", "CLASS_INIT_LOG", "NMT_DETAIL",
            "NATIVE_ALLOCATOR", "GC_LOG");
    private static final Set<String> RESOLUTIONS = Set.of("EXACT_SITE", "FAMILY", "PROCESS", "UNRESOLVED");

    public record DiagnosticEvidence(
            String source,
            String resolution,
            String joinedBy,
            String targetKey,
            String path,
            String sha256) {
        public DiagnosticEvidence {
            if (!SOURCES.contains(source)) throw new IllegalArgumentException("Unknown diagnostic source: " + source);
            if (!RESOLUTIONS.contains(resolution)) throw new IllegalArgumentException("Unknown diagnostic resolution: " + resolution);
            if (joinedBy == null || joinedBy.isBlank() || path == null || path.isBlank()) {
                throw new IllegalArgumentException("joinedBy and path are required");
            }
            if (sha256 == null || !sha256.matches("[0-9A-Fa-f]{64}")) {
                throw new IllegalArgumentException("diagnostic sha256 is required");
            }
            if (("EXACT_SITE".equals(resolution) || "FAMILY".equals(resolution))
                    && (targetKey == null || targetKey.isBlank())) {
                throw new IllegalArgumentException(resolution + " requires targetKey");
            }
            sha256 = sha256.toUpperCase();
        }
    }

    public ObjectNode join(JsonNode input, List<DiagnosticEvidence> evidence) throws IOException {
        if (input == null || !input.isObject()
                || !"jmoa-service-profile-v3".equals(input.path("schemaVersion").asText())) {
            throw new IOException("Diagnostic joining requires profile v3");
        }
        ObjectNode output = ((ObjectNode) input).deepCopy();
        Map<String, ObjectNode> bySite = new LinkedHashMap<>();
        Map<String, List<ObjectNode>> byFamily = new LinkedHashMap<>();
        for (JsonNode value : output.withArray("lambdaSites")) {
            if (!(value instanceof ObjectNode site)) throw new IOException("Profile v3 lambdaSites must contain objects");
            String siteKey = site.path("siteKey").asText("");
            String familyKey = site.path("familyKey").asText("");
            if (siteKey.isBlank() || familyKey.isBlank() || bySite.put(siteKey, site) != null) {
                throw new IOException("Profile v3 has blank/duplicate site or missing family");
            }
            byFamily.computeIfAbsent(familyKey, ignored -> new ArrayList<>()).add(site);
            if (!site.has("diagnosticJoins")) site.putArray("diagnosticJoins");
            if (!site.path("diagnosticJoins").isArray()) throw new IOException("diagnosticJoins must be an array");
        }
        List<DiagnosticEvidence> ordered = evidence.stream()
                .sorted(Comparator.comparing(DiagnosticEvidence::source)
                        .thenComparing(DiagnosticEvidence::resolution)
                        .thenComparing(value -> value.targetKey() == null ? "" : value.targetKey())
                        .thenComparing(DiagnosticEvidence::sha256)).toList();
        ArrayNode rootEvidence = output.putArray("diagnosticEvidence");
        for (DiagnosticEvidence item : ordered) {
            rootEvidence.add(toNode(output, item, true));
            List<ObjectNode> targets = switch (item.resolution()) {
                case "EXACT_SITE" -> {
                    ObjectNode site = bySite.get(item.targetKey());
                    if (site == null) throw new IOException("Diagnostic exact-site target is absent: " + item.targetKey());
                    yield List.of(site);
                }
                case "FAMILY" -> {
                    List<ObjectNode> sites = byFamily.get(item.targetKey());
                    if (sites == null) throw new IOException("Diagnostic family target is absent: " + item.targetKey());
                    yield sites;
                }
                case "PROCESS" -> new ArrayList<>(bySite.values());
                case "UNRESOLVED" -> List.of();
                default -> throw new IllegalStateException("validated resolution became unknown");
            };
            for (ObjectNode site : targets) {
                JsonNode allocationBefore = site.get("allocationBytes");
                ((ArrayNode) site.path("diagnosticJoins")).add(toNode(output, item, false));
                if (allocationBefore == null || !allocationBefore.equals(site.get("allocationBytes"))) {
                    throw new IllegalStateException("Diagnostic join mutated allocationBytes");
                }
            }
        }
        return output;
    }

    private static ObjectNode toNode(ObjectNode owner, DiagnosticEvidence item, boolean includeTarget) {
        ObjectNode node = owner.objectNode();
        node.put("source", item.source());node.put("resolution", item.resolution());node.put("joinedBy", item.joinedBy());
        if (includeTarget && item.targetKey() != null && !item.targetKey().isBlank()) node.put("targetKey", item.targetKey());
        node.put("path", item.path());node.put("sha256", item.sha256());
        return node;
    }
}
