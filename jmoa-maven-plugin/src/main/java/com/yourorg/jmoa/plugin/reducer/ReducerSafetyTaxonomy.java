package com.yourorg.jmoa.plugin.reducer;

import java.util.List;
import java.util.Map;

public record ReducerSafetyTaxonomy(
    String metadataVersion,
    List<ReducerSafetyEntry> entries,
    Map<String, String> policy
) {
}
