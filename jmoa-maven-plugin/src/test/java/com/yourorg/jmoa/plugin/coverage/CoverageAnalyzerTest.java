package com.yourorg.jmoa.plugin.coverage;

import com.yourorg.jmoa.plugin.filter.LambdaProfileIndex;
import com.yourorg.jmoa.plugin.scanner.ClassFileWalker;
import com.yourorg.jmoa.plugin.scanner.LambdaScanner;
import com.yourorg.jmoa.plugin.scanner.ScanResult;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageAnalyzerTest {

    @Test
    void detectsNewAndMissingSites() throws Exception {
        Path projectDir = Files.createTempDirectory("coverage-analyzer");
        Path sourceDir = projectDir.resolve(Path.of("src", "main", "java", "example"));
        Path classesDir = projectDir.resolve(Path.of("target", "classes"));
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);

        Path sourceFile = sourceDir.resolve("AnalyzerSample.java");
        Files.writeString(sourceFile, """
            package example;

            import java.util.List;
            import java.util.function.Function;
            import java.util.function.Predicate;

            public class AnalyzerSample {
                public List<String> run(List<String> input) {
                    Predicate<String> keep = AnalyzerSample::keep;
                    Function<String, String> trim = String::trim;
                    return input.stream().filter(keep).map(trim).toList();
                }

                public static boolean keep(String value) {
                    return value != null && !value.isBlank();
                }
            }
            """);
        compileSource(sourceFile, classesDir);

        ScanResult scanResult = LambdaScanner.scanClassFiles(ClassFileWalker.findClassFiles(classesDir.toFile()));
        List<String> currentSites = scanResult.metadata().stream().map(meta -> meta.siteKey()).sorted().toList();
        LambdaProfileIndex profileIndex = new LambdaProfileIndex(
            Map.of(
                currentSites.getFirst(), 3L,
                "missing-profile-site", 2L
            ),
            Set.of(),
            Set.of()
        );

        CoverageAnalysis analysis = new CoverageAnalyzer().analyze(
            scanResult,
            profileIndex,
            "MODE_A",
            List.of(classesDir.toString())
        );

        assertEquals(scanResult.metadata().size(), analysis.classesScanned());
        assertEquals(scanResult.totalLambdaSites(), analysis.totalLambdaSites());
        assertEquals(scanResult.metadata().size(), analysis.statelessCandidateSites());
        assertEquals(2, analysis.profileSiteCount());
        assertEquals(1, analysis.observedCurrentSites());
        assertEquals(currentSites.size() - 1, analysis.newSiteCount());
        assertEquals(1, analysis.missingProfileSiteCount());
        assertEquals("missing-profile-site", analysis.missingProfileSiteKeys().getFirst());
        assertTrue(analysis.classRootsScanned().contains(classesDir.toString()));

        deleteRecursively(projectDir);
    }

    private static void compileSource(Path sourceFile, Path classesDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        int exitCode = compiler.run(
            null,
            null,
            null,
            "--release",
            "22",
            "-d",
            classesDir.toString(),
            sourceFile.toString()
        );
        assertEquals(0, exitCode);
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
    }
}
