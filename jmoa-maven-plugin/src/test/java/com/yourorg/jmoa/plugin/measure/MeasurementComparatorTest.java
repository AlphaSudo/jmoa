package com.yourorg.jmoa.plugin.measure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeasurementComparatorTest {

    @Test
    void warnsWhenUnthresholdedMetricsRegress() {
        MeasurementResult baseline = new MeasurementResult(
            MeasurementScenario.BASELINE,
            "example.Main",
            0,
            "NONE",
            100,
            10,
            10,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1000,
            1000,
            900,
            500,
            500,
            450,
            100.0d,
            1,
            List.of(100.0d)
        );
        MeasurementResult candidate = new MeasurementResult(
            MeasurementScenario.MODE_C,
            "example.Main",
            20,
            "ENVIRONMENT_WALL",
            100,
            8,
            8,
            0,
            2,
            0,
            1,
            1,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1100,
            1100,
            1000,
            500,
            500,
            450,
            120.0d,
            1,
            List.of(120.0d)
        );

        MeasurementComparison comparison = new MeasurementComparator().compare(
            baseline,
            candidate,
            null,
            null,
            null
        );

        assertEquals("WARN", comparison.verdict());
        assertEquals(true, comparison.passesThresholds());
    }
}
