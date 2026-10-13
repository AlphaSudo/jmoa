package com.yourorg.jmoa.plugin.overlay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parses an overlay document into the typed {@link ServicePolicyOverlay} while
 * recording every unknown policy field. Unknown fields are never silently
 * dropped: the validator refuses them so a service cannot smuggle unvetted
 * policy into the universal engine. Loading never throws on shape problems;
 * structural gaps become validator decisions.
 */
public final class ServicePolicyOverlayLoader {

    private static final Set<String> KNOWN_TOP_LEVEL = Set.of(
            "schemaVersion", "service", "sourceRevision", "profileSha256",
            "configTreeSha256", "safeSamsSha256", "workloadId", "allowedModes", "guardrails");
    private static final Set<String> KNOWN_ALLOWED_MODE_FIELDS = Set.of(
            "tier1AdapterModes", "tier2Consolidations", "reducerOperations",
            "reducerJarLayouts", "packagingModes");
    private static final Set<String> KNOWN_GUARDRAIL_FIELDS = Set.of(
            "requiredSemanticRequests", "requireZeroWorkloadErrors", "requireMutationProof",
            "requireZeroFallback", "admissionManifestRequired", "requireProtectedComponentAudit",
            "claimDiagnosticsSeparately");

    private final ObjectMapper mapper = new ObjectMapper();

    public LoadedOverlay load(Path path) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        if (root == null || !root.isObject()) {
            return new LoadedOverlay(null, Set.of(), Set.of(), Set.of());
        }
        Set<String> unknownTopLevel = unknownFields(root, KNOWN_TOP_LEVEL);
        Set<String> unknownAllowedModes = Set.of();
        Set<String> unknownGuardrails = Set.of();

        ServicePolicyOverlay.AllowedModes allowedModes = null;
        JsonNode modesNode = root.get("allowedModes");
        if (modesNode != null && modesNode.isObject()) {
            unknownAllowedModes = unknownFields(modesNode, KNOWN_ALLOWED_MODE_FIELDS);
            allowedModes = new ServicePolicyOverlay.AllowedModes(
                    stringSet(modesNode.get("tier1AdapterModes")),
                    stringSet(modesNode.get("tier2Consolidations")),
                    stringSet(modesNode.get("reducerOperations")),
                    stringSet(modesNode.get("reducerJarLayouts")),
                    stringSet(modesNode.get("packagingModes")));
        }

        ServicePolicyOverlay.Guardrails guardrails = null;
        JsonNode guardrailsNode = root.get("guardrails");
        if (guardrailsNode != null && guardrailsNode.isObject()) {
            unknownGuardrails = unknownFields(guardrailsNode, KNOWN_GUARDRAIL_FIELDS);
            guardrails = new ServicePolicyOverlay.Guardrails(
                    guardrailsNode.path("requiredSemanticRequests").asInt(-1),
                    guardrailsNode.path("requireZeroWorkloadErrors").asBoolean(false),
                    guardrailsNode.path("requireMutationProof").asBoolean(false),
                    guardrailsNode.path("requireZeroFallback").asBoolean(false),
                    guardrailsNode.path("admissionManifestRequired").asBoolean(false),
                    guardrailsNode.path("requireProtectedComponentAudit").asBoolean(false),
                    guardrailsNode.path("claimDiagnosticsSeparately").asBoolean(false));
        }

        ServicePolicyOverlay overlay = new ServicePolicyOverlay(
                text(root.get("schemaVersion")),
                text(root.get("service")),
                text(root.get("sourceRevision")),
                text(root.get("profileSha256")),
                text(root.get("configTreeSha256")),
                text(root.get("safeSamsSha256")),
                text(root.get("workloadId")),
                allowedModes,
                guardrails);
        return new LoadedOverlay(overlay, unknownTopLevel, unknownAllowedModes, unknownGuardrails);
    }

    public record LoadedOverlay(
            ServicePolicyOverlay overlay,
            Set<String> unknownTopLevelFields,
            Set<String> unknownAllowedModeFields,
            Set<String> unknownGuardrailFields) {
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static Set<String> stringSet(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            }
        }
        return values;
    }

    private static Set<String> unknownFields(JsonNode object, Set<String> known) {
        Set<String> unknown = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(name -> {
            if (!known.contains(name)) {
                unknown.add(name);
            }
        });
        return unknown;
    }
}
