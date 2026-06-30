package com.yourorg.jmoa.plugin.measure;

import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MeasurementExecutorTest {

    @Test
    void acceptsEnvironmentWallLaunchClassification() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-measure-executor");
        Path sourceDir = projectDir.resolve(Path.of("src", "example"));
        Path classesDir = projectDir.resolve("classes");
        Path outputDir = projectDir.resolve("measurements");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Files.createDirectories(outputDir);

        Path sourceFile = sourceDir.resolve("EnvironmentWallMain.java");
        Files.writeString(sourceFile, """
            package example;

            public class EnvironmentWallMain {
                public static void main(String[] args) {
                    System.err.println("JMOA_MODE_C_LAUNCH_CLASSIFICATION=ENVIRONMENT_WALL");
                    System.err.println("synthetic environment wall for measurement");
                    System.exit(20);
                }
            }
            """);
        compileSource(sourceFile, classesDir);

        File classLoadLog = outputDir.resolve("envwall-classload.log").toFile();
        File nmtLog = outputDir.resolve("envwall-nmt.log").toFile();
        MeasurementPlan plan = new MeasurementCommandWriter().buildPlan(
            new MeasurementConfig(
                MeasurementScenario.BASELINE,
                outputDir.toFile(),
                "example.EnvironmentWallMain",
                List.of(),
                "example.EnvironmentWallMain",
                1,
                true,
                false,
                null,
                null
            ),
            resolveJavaExecutable(),
            classesDir.toAbsolutePath().toString()
        );

        MeasurementResult result = new MeasurementExecutor().execute(plan, 1);

        assertEquals(20, result.exitCode());
        assertEquals("ENVIRONMENT_WALL", result.launchClassification());

        deleteRecursively(projectDir);
    }

    private static File resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        return Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java").toFile();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static void compileSource(Path sourceFile, Path classesDir) throws Exception {
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
