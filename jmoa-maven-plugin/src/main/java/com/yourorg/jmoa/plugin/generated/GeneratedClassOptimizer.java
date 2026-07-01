package com.yourorg.jmoa.plugin.generated;

public final class GeneratedClassOptimizer {

    public void ensureInventoryOnly(String optimizeFamily, boolean inventoryOnly) {
        String family = optimizeFamily == null || optimizeFamily.isBlank() ? "none" : optimizeFamily.trim();
        if (!"none".equalsIgnoreCase(family) || !inventoryOnly) {
            throw new IllegalStateException(
                "JMOA V2-A generated-class optimizer is inventory-only in this release. "
                    + "Use -Djmoa.synthetic.inventoryOnly=true and -Djmoa.synthetic.optimizeFamily=none."
            );
        }
    }
}
