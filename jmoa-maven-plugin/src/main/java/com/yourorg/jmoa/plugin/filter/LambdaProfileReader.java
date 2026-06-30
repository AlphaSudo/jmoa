package com.yourorg.jmoa.plugin.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class LambdaProfileReader {

    private LambdaProfileReader() {
    }

    public static LambdaProfileIndex read(File profileFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(profileFile);

        Map<String, Long> countsBySiteKey = new LinkedHashMap<>();
        Set<String> hotClasses = new LinkedHashSet<>();
        Set<String> coldClasses = new LinkedHashSet<>();

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
}
