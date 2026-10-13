package com.yourorg.jmoa.plugin.siteeconomics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Groups typed site rows into families and computes the monotone frontier
 * quantities: an avoided-cost ESTIMATE derived only from STARTUP+TRAINING
 * counts (a count proxy, never PSS), and the measured generated-layout cost.
 * The estimate basis records which signals were actually present so missing
 * values stay honest.
 */
public final class SiteFamilyAggregator {

    public record SiteFamily(
            String familyKey,
            List<SiteFeatureRow> sites,
            long estimatedAvoidedCostUnits,
            String estimateBasis,
            long measuredGeneratedLayoutCostBytes,
            boolean layoutCostMeasured) {

        public SiteFamily {
            sites = List.copyOf(sites);
        }
    }

    public List<SiteFamily> aggregate(List<SiteFeatureRow> rows) {
        Map<String, List<SiteFeatureRow>> byFamily = new TreeMap<>();
        for (SiteFeatureRow row : rows) {
            byFamily.computeIfAbsent(row.familyKey(), k -> new ArrayList<>()).add(row);
        }
        List<SiteFamily> families = new ArrayList<>();
        for (Map.Entry<String, List<SiteFeatureRow>> entry : byFamily.entrySet()) {
            long selectionUnits = 0L;
            boolean anyCount = false;
            long layoutBytes = 0L;
            boolean anyLayout = false;
            Map<String, Integer> physicalRoots = new LinkedHashMap<>();
            for (SiteFeatureRow row : entry.getValue()) {
                selectionUnits += row.selectionInvocationCount();
                anyCount = anyCount || row.startupInvocationCount() != null || row.trainingInvocationCount() != null;
                if (row.generatedClassBytes() != null) {
                    layoutBytes += row.generatedClassBytes();
                    anyLayout = true;
                }
                if (row.physicalOutputRoot() != null) {
                    physicalRoots.merge(row.physicalOutputRoot(), 1, Integer::sum);
                }
            }
            String basis = (anyCount ? "STARTUP_PLUS_TRAINING_COUNTS" : "NO_COUNTS_NULL_SAFE")
                    + "|" + (anyLayout ? "MEASURED_LAYOUT_BYTES" : "LAYOUT_COST_NULL")
                    + "|roots=" + physicalRoots.size();
            families.add(new SiteFamily(entry.getKey(), entry.getValue(), selectionUnits, basis, layoutBytes, anyLayout));
        }
        return List.copyOf(families);
    }
}
