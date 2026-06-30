package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasureImpactMojoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void measureImpactWritesComparisonReportForPreparedClasspaths() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-measure-impact");
        Path targetDir = projectDir.resolve("target");
        Path baselineClassesDir = targetDir.resolve("baseline-classes");
        Path modeCClassesDir = targetDir.resolve("modec-classes");
        Path baselineSourceDir = projectDir.resolve(Path.of("src", "baseline", "example"));
        Path modeCSourceDir = projectDir.resolve(Path.of("src", "modec", "example"));
        Files.createDirectories(baselineClassesDir);
        Files.createDirectories(modeCClassesDir);
        Files.createDirectories(baselineSourceDir);
        Files.createDirectories(modeCSourceDir);

        Path baselineSource = baselineSourceDir.resolve("MeasureMain.java");
        Path modeCSource = modeCSourceDir.resolve("MeasureMain.java");
        Files.writeString(baselineSource, baselineSource());
        Files.writeString(modeCSource, modeCSource());
        compileSource(baselineSource, baselineClassesDir);
        compileSource(modeCSource, modeCClassesDir);

        MeasureImpactMojo mojo = new MeasureImpactMojo();
        setField(mojo, "project", buildProject(projectDir, baselineClassesDir));
        setField(mojo, "skip", false);
        setField(mojo, "mainClass", "example.MeasureMain");
        setField(mojo, "candidateScenario", com.yourorg.jmoa.plugin.measure.MeasurementScenario.MODE_C);
        setField(mojo, "measurementRuns", 1);
        setField(mojo, "executeMeasurements", true);
        setField(mojo, "failOnMeasurementRegression", true);
        setField(mojo, "minLambdaClassReduction", 1);
        setField(mojo, "maxStartupRegressionMs", 1000.0d);
        setField(mojo, "outputDirectory", targetDir.resolve("jmoa-measurements").toFile());
        setField(mojo, "measurementArgs", List.of());
        Path baselineClasspathFile = targetDir.resolve("baseline.classpath.txt");
        Path modecClasspathFile = targetDir.resolve("modec.classpath.txt");
        Files.writeString(baselineClasspathFile, baselineClassesDir.toString());
        Files.writeString(modecClasspathFile, modeCClassesDir.toString());
        setField(mojo, "baselineClasspathFile", baselineClasspathFile.toFile());
        setField(mojo, "modeCClasspathFile", modecClasspathFile.toFile());

        mojo.execute();

        Path reportFile = targetDir.resolve(Path.of("jmoa-measurements", "jmoa-measurement-report.json"));
        Path summaryFile = targetDir.resolve(Path.of("jmoa-measurements", "jmoa-measurement-summary.md"));
        JsonNode report = MAPPER.readTree(reportFile.toFile());
        assertEquals("BASELINE", report.path("baselineScenario").asText());
        assertEquals("MODE_C", report.path("candidateScenario").asText());
        assertEquals(2, report.path("plans").size());
        assertEquals(2, report.path("results").size());
        assertEquals(1, report.path("comparisons").size());
        assertEquals(1, report.path("classDiffs").size());
        assertTrue(report.path("comparison").path("lambdaReductionAbsolute").asInt() >= 1);
        assertTrue(report.path("comparison").path("passesThresholds").asBoolean());
        assertTrue(report.path("results").get(0).has("metaspaceUsedKb"));
        assertTrue(report.path("results").get(0).has("classSpaceUsedKb"));
        assertTrue(report.path("results").get(0).has("jdkInternalClassfileClasses"));
        assertTrue(report.path("results").get(0).has("javaLangClassfileClasses"));
        assertTrue(report.path("results").get(0).has("springCoreClassReadingClasses"));
        assertTrue(Files.exists(summaryFile));
        assertTrue(Files.readString(summaryFile).contains("## Baseline Comparisons"));

        deleteRecursively(projectDir);
    }

    @Test
    void measureImpactUsesScenarioSpecificArgumentFilesInPlans() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-measure-plan-args");
        Path targetDir = projectDir.resolve("target");
        Path classesDir = targetDir.resolve("classes");
        Files.createDirectories(classesDir);

        MeasureImpactMojo mojo = new MeasureImpactMojo();
        setField(mojo, "project", buildProject(projectDir, classesDir));
        setField(mojo, "skip", false);
        setField(mojo, "mainClass", "jmoa.tools.ModeCClasspathLauncher");
        setField(mojo, "targetMainClass", "com.example.Application");
        setField(mojo, "candidateScenario", com.yourorg.jmoa.plugin.measure.MeasurementScenario.MODE_C);
        setField(mojo, "measurementRuns", 1);
        setField(mojo, "executeMeasurements", false);
        setField(mojo, "failOnMeasurementRegression", false);
        setField(mojo, "outputDirectory", targetDir.resolve("jmoa-measurements").toFile());
        setField(mojo, "measurementArgs", List.of("--ignored-default"));
        setField(mojo, "bootstrapClasspathEntries", List.of(classesDir.toFile()));

        Path baselineClasspathFile = targetDir.resolve("baseline.classpath.txt");
        Path modecClasspathFile = targetDir.resolve("modec.classpath.txt");
        Files.writeString(baselineClasspathFile, classesDir.toString());
        Files.writeString(modecClasspathFile, classesDir.toString());
        setField(mojo, "baselineClasspathFile", baselineClasspathFile.toFile());
        setField(mojo, "modeCClasspathFile", modecClasspathFile.toFile());

        Path baselineArgsFile = targetDir.resolve("baseline.args.txt");
        Path modecArgsFile = targetDir.resolve("modec.args.txt");
        Files.writeString(
            baselineArgsFile,
            String.join(System.lineSeparator(),
                "--classpath-file",
                "baseline-launch.txt",
                "--main-class",
                "com.example.Application")
        );
        Files.writeString(
            modecArgsFile,
            String.join(System.lineSeparator(),
                "--classpath-file",
                "modec-launch.txt",
                "--main-class",
                "com.example.Application")
        );
        setField(mojo, "baselineMeasurementArgsFile", baselineArgsFile.toFile());
        setField(mojo, "modeCMeasurementArgsFile", modecArgsFile.toFile());

        mojo.execute();

        Path reportFile = targetDir.resolve(Path.of("jmoa-measurements", "jmoa-measurement-report.json"));
        JsonNode report = MAPPER.readTree(reportFile.toFile());
        JsonNode plans = report.path("plans");
        assertEquals(2, plans.size());
        assertEquals("com.example.Application", plans.get(0).path("mainClass").asText());
        assertTrue(plans.get(0).path("prettyCommand").asText().contains("baseline-launch.txt"));
        assertTrue(plans.get(1).path("prettyCommand").asText().contains("modec-launch.txt"));
        assertTrue(!plans.get(1).path("prettyCommand").asText().contains("--ignored-default"));
        assertTrue(plans.get(1).path("prettyCommand").asText().contains(classesDir.toString()));

        deleteRecursively(projectDir);
    }

    @Test
    void measureImpactIncludesAllConfiguredClasspathFileScenarios() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-measure-classpath-files");
        Path targetDir = projectDir.resolve("target");
        Path classesDir = targetDir.resolve("classes");
        Files.createDirectories(classesDir);

        MeasureImpactMojo mojo = new MeasureImpactMojo();
        setField(mojo, "project", buildProject(projectDir, classesDir));
        setField(mojo, "skip", false);
        setField(mojo, "mainClass", "example.MeasureMain");
        setField(mojo, "candidateScenario", com.yourorg.jmoa.plugin.measure.MeasurementScenario.MODE_C);
        setField(mojo, "measurementRuns", 1);
        setField(mojo, "executeMeasurements", false);
        setField(mojo, "failOnMeasurementRegression", false);
        setField(mojo, "outputDirectory", targetDir.resolve("jmoa-measurements").toFile());

        Path baselineClasspathFile = targetDir.resolve("baseline.classpath.txt");
        Path expandedClasspathOnlyFile = targetDir.resolve("expanded-only.classpath.txt");
        Path modeAClasspathFile = targetDir.resolve("modea.classpath.txt");
        Path modeBClasspathFile = targetDir.resolve("modeb.classpath.txt");
        Path modeCClasspathFile = targetDir.resolve("modec.classpath.txt");
        Path modeCOptimizedJarsClasspathFile = targetDir.resolve("modec-optimized-jars.classpath.txt");
        for (Path path : List.of(baselineClasspathFile, expandedClasspathOnlyFile, modeAClasspathFile, modeBClasspathFile, modeCClasspathFile, modeCOptimizedJarsClasspathFile)) {
            Files.writeString(path, classesDir.toString());
        }
        setField(mojo, "baselineClasspathFile", baselineClasspathFile.toFile());
        setField(mojo, "expandedClasspathOnlyClasspathFile", expandedClasspathOnlyFile.toFile());
        setField(mojo, "modeAClasspathFile", modeAClasspathFile.toFile());
        setField(mojo, "modeBClasspathFile", modeBClasspathFile.toFile());
        setField(mojo, "modeCClasspathFile", modeCClasspathFile.toFile());
        setField(mojo, "modeCOptimizedJarsClasspathFile", modeCOptimizedJarsClasspathFile.toFile());

        mojo.execute();

        Path reportFile = targetDir.resolve(Path.of("jmoa-measurements", "jmoa-measurement-report.json"));
        JsonNode report = MAPPER.readTree(reportFile.toFile());
        assertEquals(6, report.path("plans").size());
        assertEquals("BASELINE", report.path("plans").get(0).path("scenario").asText());
        assertEquals("MODE_A", report.path("plans").get(1).path("scenario").asText());
        assertEquals("EXPANDED_CLASSPATH_ONLY", report.path("plans").get(2).path("scenario").asText());
        assertEquals("MODE_B", report.path("plans").get(3).path("scenario").asText());
        assertEquals("MODE_C", report.path("plans").get(4).path("scenario").asText());
        assertEquals("MODE_C_OPTIMIZED_JARS", report.path("plans").get(5).path("scenario").asText());

        deleteRecursively(projectDir);
    }

    private static MavenProject buildProject(Path projectDir, Path classesDir) {
        Build build = new Build();
        build.setDirectory(projectDir.resolve("target").toString());
        build.setOutputDirectory(classesDir.toString());

        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("measure-it");
        model.setVersion("1.0-SNAPSHOT");
        model.setBuild(build);

        List<String> classpath = List.of(classesDir.toAbsolutePath().toString());
        return new MavenProject(model) {
            @Override
            public List<String> getCompileClasspathElements() {
                return classpath;
            }
        };
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

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
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

    private static String baselineSource() {
        return """
            package example;

            public class MeasureMain {
                public static void main(String[] args) {
                    Runnable first = () -> System.out.print("");
                    Runnable second = () -> System.out.print("");
                    first.run();
                    second.run();
                    System.out.println(first.getClass().getName());
                    System.out.println(second.getClass().getName());
                }
            }
            """;
    }

    private static String modeCSource() {
        return """
            package example;

            public class MeasureMain {
                public static void main(String[] args) {
                    Runnable first = () -> System.out.print("");
                    first.run();
                    System.out.println(first.getClass().getName());
                }
            }
            """;
    }
}
