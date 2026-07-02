package com.yourorg.jmoa.plugin.evidence;

import com.yourorg.jmoa.plugin.evidence.EvidenceModels.EvidenceCapture;
import com.yourorg.jmoa.plugin.evidence.EvidenceModels.MemoryMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceParsersTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesSmapsRollupAndFullSmapsCategories() throws Exception {
        Path rollup = tempDir.resolve("smaps_rollup");
        Path smaps = tempDir.resolve("smaps");
        Path current = tempDir.resolve("memory.current");
        Files.writeString(rollup, """
            Rss:              1000 kB
            Pss:               800 kB
            Private_Clean:     100 kB
            Private_Dirty:     700 kB
            Shared_Clean:      200 kB
            Shared_Dirty:        0 kB
            """);
        Files.writeString(smaps, """
            1000-2000 rw-p 00000000 00:00 0 [heap]
            Pss:               300 kB
            Private_Dirty:     300 kB
            3000-4000 rw-p 00000000 00:00 0
            Pss:               200 kB
            Private_Dirty:     200 kB
            5000-6000 r-xp 00000000 00:00 0
            Pss:                50 kB
            Private_Dirty:       0 kB
            """);
        Files.writeString(current, "123456");
        EvidenceCapture capture = new EvidenceCapture("r1", "post", rollup.toString(), smaps.toString(),
            null, null, null, null, null, null, current.toString(), null, null);

        MemoryMetrics metrics = new EvidenceParsers().parseMemory(capture);

        assertEquals(800, metrics.pssKb());
        assertEquals(700, metrics.privateDirtyKb());
        assertEquals(123456, metrics.memoryCurrentBytes());
        assertEquals(300, metrics.heapPssKb());
        assertEquals(200, metrics.anonymousRwPssKb());
        assertEquals(50, metrics.anonymousExecutablePssKb());
    }
}
