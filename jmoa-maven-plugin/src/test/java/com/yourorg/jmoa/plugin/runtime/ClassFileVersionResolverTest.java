package com.yourorg.jmoa.plugin.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClassFileVersionResolverTest {

    @Test
    void choosesHighestClassFileVersionAcrossMixedOutputs() throws Exception {
        Path dir = Files.createTempDirectory("jmoa-class-version");
        Path javaEight = dir.resolve("Legacy.class");
        Path javaTwentySix = dir.resolve("Modern.class");

        Files.write(javaEight, classHeader(52));
        Files.write(javaTwentySix, classHeader(70));

        int resolved = ClassFileVersionResolver.resolveHighestVersion(
            List.of(javaEight.toFile(), javaTwentySix.toFile()),
            66
        );

        assertEquals(70, resolved);
    }

    @Test
    void projectClassVersionWinsOverHigherFallback() throws Exception {
        Path dir = Files.createTempDirectory("jmoa-class-version");
        Path javaSeventeen = dir.resolve("Service.class");

        Files.write(javaSeventeen, classHeader(61));

        int resolved = ClassFileVersionResolver.resolveHighestVersion(
            List.of(javaSeventeen.toFile()),
            66
        );

        assertEquals(61, resolved);
    }

    @Test
    void fallsBackWhenNoClassFilesExist() throws Exception {
        int resolved = ClassFileVersionResolver.resolveHighestVersion(List.of(), 66);
        assertEquals(66, resolved);
    }

    private static byte[] classHeader(int majorVersion) {
        return new byte[]{
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
            0, 0,
            (byte) ((majorVersion >>> 8) & 0xFF),
            (byte) (majorVersion & 0xFF)
        };
    }
}
