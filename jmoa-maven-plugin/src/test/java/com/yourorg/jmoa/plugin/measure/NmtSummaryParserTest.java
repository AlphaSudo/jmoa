package com.yourorg.jmoa.plugin.measure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NmtSummaryParserTest {

    @Test
    void parsesMetaspaceAndClassSpaceMetrics() {
        NmtSummaryParser.NmtMetrics metrics = new NmtSummaryParser().parse(List.of(
            "Some unrelated line",
            "- Class (reserved=4000KB, committed=2500KB)",
            "  (Metadata:)",
            "  (reserved=12345KB, committed=6789KB)",
            "  (used=4567KB)",
            "  (Class space:)",
            "  (reserved=4000KB, committed=2500KB)",
            "  (used=2000KB)",
            "- Metaspace (reserved=13000KB, committed=7000KB)"
        ));

        assertEquals(13000L, metrics.metaspaceReservedKb());
        assertEquals(7000L, metrics.metaspaceCommittedKb());
        assertEquals(4567L, metrics.metaspaceUsedKb());
        assertEquals(4000L, metrics.classSpaceReservedKb());
        assertEquals(2500L, metrics.classSpaceCommittedKb());
        assertEquals(2000L, metrics.classSpaceUsedKb());
    }

    @Test
    void parsesJava26StyleMetricsWithoutKbSuffix() {
        NmtSummaryParser.NmtMetrics metrics = new NmtSummaryParser().parse(List.of(
            "-                     Class (reserved=1074740716, committed=11812332)",
            "                            (  Metadata:   )",
            "                            (    reserved=67122024, committed=1127272)",
            "                            (used=61734544)",
            "                            (Class space:)",
            "                            (reserved=1073741824, committed=10813440)",
            "                            (used=10572752)",
            "-                 Metaspace (reserved=67122024, committed=1127272)",
            "-        Shared class space (reserved=16777312, committed=14549088, readonly=0)"
        ));

        assertEquals(65548L, metrics.metaspaceReservedKb());
        assertEquals(1100L, metrics.metaspaceCommittedKb());
        assertEquals(60287L, metrics.metaspaceUsedKb());
        assertEquals(16384L, metrics.classSpaceReservedKb());
        assertEquals(14208L, metrics.classSpaceCommittedKb());
        assertEquals(10324L, metrics.classSpaceUsedKb());
    }
}
