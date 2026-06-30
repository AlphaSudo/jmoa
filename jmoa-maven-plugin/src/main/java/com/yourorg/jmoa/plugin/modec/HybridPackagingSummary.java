package com.yourorg.jmoa.plugin.modec;

import java.util.List;

public record HybridPackagingSummary(
    boolean enabled,
    List<String> hybridOverlayCoordinates,
    List<HybridOverlayClasspathEntry> overlayEntries,
    int expandedDependencyRuntimeEntries,
    int optimizedJarRuntimeEntries,
    int originalFallbackJarEntries
) {

    public HybridPackagingSummary {
        hybridOverlayCoordinates = hybridOverlayCoordinates == null ? List.of() : List.copyOf(hybridOverlayCoordinates);
        overlayEntries = overlayEntries == null ? List.of() : List.copyOf(overlayEntries);
    }

    public static HybridPackagingSummary disabled() {
        return new HybridPackagingSummary(false, List.of(), List.of(), 0, 0, 0);
    }
}
