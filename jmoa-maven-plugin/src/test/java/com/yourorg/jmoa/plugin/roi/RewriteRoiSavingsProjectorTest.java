package com.yourorg.jmoa.plugin.roi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RewriteRoiSavingsProjectorTest {

    private final RewriteRoiSavingsProjector projector = new RewriteRoiSavingsProjector();

    @Test
    void allFourScenariosPresent() {
        Map<String, Object> projection = projector.projectAt(300, 0.6);

        @SuppressWarnings("unchecked")
        Map<String, Object> gross = (Map<String, Object>) projection.get("grossSavingsKb");
        assertNotNull(gross.get("veryLowKb"), "0.25 KB scenario missing");
        assertNotNull(gross.get("lowKb"), "0.5 KB scenario missing");
        assertNotNull(gross.get("mediumKb"), "1.0 KB scenario missing");
        assertNotNull(gross.get("highKb"), "2.0 KB scenario missing");
    }

    @Test
    void speculativeFlagAlwaysTrue() {
        Map<String, Object> projection = projector.projectAt(500, 0.5);
        assertTrue((Boolean) projection.get("speculative"));
    }

    @Test
    void grossSavingsScaleLinearly() {
        Map<String, Object> proj300 = projector.projectAt(300, 0.5);
        Map<String, Object> proj600 = projector.projectAt(600, 0.5);

        @SuppressWarnings("unchecked")
        Map<String, Object> gross300 = (Map<String, Object>) proj300.get("grossSavingsKb");
        @SuppressWarnings("unchecked")
        Map<String, Object> gross600 = (Map<String, Object>) proj600.get("grossSavingsKb");

        double low300 = (double) gross300.get("lowKb");
        double low600 = (double) gross600.get("lowKb");
        assertEquals(low300 * 2, low600, 0.01, "Gross savings should scale linearly");
    }

    @Test
    void netSavingsAccountForReplacementCost() {
        Map<String, Object> projection = projector.projectAt(300, 0.5);

        @SuppressWarnings("unchecked")
        Map<String, Object> gross = (Map<String, Object>) projection.get("grossSavingsKb");
        @SuppressWarnings("unchecked")
        Map<String, Object> net = (Map<String, Object>) projection.get("netSavingsKb");
        @SuppressWarnings("unchecked")
        Map<String, Object> cost = (Map<String, Object>) projection.get("estimatedReplacementCostKb");

        double grossLow = (double) gross.get("lowKb");
        double netLow = (double) net.get("lowKb");
        double calibratedCost = (double) cost.get("calibratedKb");
        assertEquals(grossLow - calibratedCost, netLow, 0.01);
    }

    @Test
    void observedCalibrationIncludesPhase19Data() {
        Map<String, Object> cal = projector.buildObservedCalibration();
        assertEquals(132, cal.get("lambdasRemoved"));
        assertEquals(67634, cal.get("directReplacementCostBytes"));
        assertEquals(-11, cal.get("usedMetaspaceDeltaKbAfterForcedGC"));
        assertNotNull(cal.get("note"));
        assertTrue(((String) cal.get("note")).contains("speculative"));
    }

    @Test
    void projectProducesAllScalePoints() {
        List<Map<String, Object>> projections = projector.project(134, 0.5);
        // Scale points: 134, 300, 500, 800, 1000
        assertEquals(5, projections.size());
        assertEquals(134, projections.get(0).get("rewriteCount"));
        assertEquals(1000, projections.get(4).get("rewriteCount"));
    }

    @Test
    void breakEvenAnalysisPresent() {
        Map<String, Object> projection = projector.projectAt(300, 0.5);

        @SuppressWarnings("unchecked")
        Map<String, Object> breakEven = (Map<String, Object>) projection.get("breakEven");
        assertNotNull(breakEven);
        assertNotNull(breakEven.get("breaksEvenAtVeryLow"));
        assertNotNull(breakEven.get("breaksEvenAtLow"));
        assertNotNull(breakEven.get("breaksEvenAtMedium"));
        assertNotNull(breakEven.get("breaksEvenAtHigh"));
    }
}
