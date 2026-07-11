package com.yourorg.jmoa.plugin.runtimepolicy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyAdmissionInput;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyRegistryEntry;
import com.yourorg.jmoa.plugin.runtimepolicy.RuntimePolicyModels.RuntimePolicyScope;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class RuntimePolicyRegistry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<RuntimePolicyRegistryEntry> entries;

    public RuntimePolicyRegistry(List<RuntimePolicyRegistryEntry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static RuntimePolicyRegistry empty() {
        return new RuntimePolicyRegistry(List.of());
    }

    public static RuntimePolicyRegistry load(File registryFile) throws IOException {
        if (registryFile == null || !registryFile.isFile()) {
            return empty();
        }
        JsonNode root = MAPPER.readTree(registryFile);
        List<RuntimePolicyRegistryEntry> entries = new ArrayList<>();
        for (JsonNode node : root.path("protocols")) {
            entries.add(MAPPER.treeToValue(node, RuntimePolicyRegistryEntry.class));
        }
        return new RuntimePolicyRegistry(entries);
    }

    public Optional<RuntimePolicyRegistryEntry> find(RuntimePolicyAdmissionInput input) {
        return entries.stream()
            .filter(entry -> sameService(entry.service(), input.service()))
            .filter(entry -> same(entry.launchMode(), input.launchMode()))
            .filter(entry -> same(entry.runtimePolicy(), input.runtimePolicy()))
            .filter(entry -> same(entry.reducerEngine(), input.reducerEngine()))
            .findFirst();
    }

    public List<RuntimePolicyRegistryEntry> entries() {
        return entries;
    }

    public static RuntimePolicyScope scopeOrUnknown(RuntimePolicyRegistryEntry entry) {
        return entry == null ? RuntimePolicyScope.UNKNOWN : entry.scope();
    }

    static boolean sameService(String left, String right) {
        String normalizedLeft = normalizeService(left);
        String normalizedRight = normalizeService(right);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalizedRight);
    }

    static boolean same(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return !normalizedLeft.isBlank() && !"UNKNOWN".equals(normalizedLeft)
            && normalizedLeft.equals(normalizedRight);
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    static String normalizeService(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
