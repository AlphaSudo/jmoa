package com.yourorg.jmoa.plugin.scanner;

import com.yourorg.jmoa.plugin.model.LambdaMeta;

import java.util.List;

public record ScanResult(
    List<LambdaSite> sites,
    int totalLambdaSites,
    int skippedCapturing,
    int skippedSerializable
) {

    public List<LambdaMeta> metadata() {
        return sites.stream()
            .map(LambdaSite::toMeta)
            .toList();
    }
}
