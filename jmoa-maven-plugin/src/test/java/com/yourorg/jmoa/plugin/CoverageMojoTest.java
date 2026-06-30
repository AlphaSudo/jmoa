package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.model.LambdaMeta;
import com.yourorg.jmoa.plugin.scanner.ClassFileWalker;
import com.yourorg.jmoa.plugin.scanner.LambdaScanner;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageMojoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void coverageReportWritesZeroDriftForAlignedProfile() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-coverage-report");
        Path classesDir = projectDir.resolve(Path.of("target", "classes"));
        Path sourceDir = projectDir.resolve(Path.of("src", "main", "java", "example"));
        Files.createDirectories(classesDir);
        Files.createDirectories(sourceDir);

        Path sourceFile = sourceDir.resolve("SampleCoverage.java");
        Files.writeString(sourceFile, baseSource());
        compileSource(sourceFile, classesDir);

        Path profilePath = projectDir.resolve(Path.of("target", "baseline-profile.json"));
        writeProfile(classesDir, profilePath);

        CoverageReportMojo mojo = new CoverageReportMojo();
        configureCoverageMojo(mojo, buildProject(projectDir, classesDir), profilePath.toFile());
        mojo.execute();

        Path reportFile = projectDir.resolve(Path.of("target", "jmoa-coverage-report.json"));
        JsonNode report = MAPPER.readTree(reportFile.toFile());
        assertEquals(0, report.path("newSiteCount").asInt());
        assertEquals(0, report.path("missingProfileSiteCount").asInt());

        deleteRecursively(projectDir);
    }

    @Test
    void checkCoverageFailsWhenNewSitesAppear() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-coverage-check");
        Path classesDir = projectDir.resolve(Path.of("target", "classes"));
        Path sourceDir = projectDir.resolve(Path.of("src", "main", "java", "example"));
        Files.createDirectories(classesDir);
        Files.createDirectories(sourceDir);

        Path sourceFile = sourceDir.resolve("SampleCoverage.java");
        Files.writeString(sourceFile, baseSource());
        compileSource(sourceFile, classesDir);

        Path profilePath = projectDir.resolve(Path.of("target", "baseline-profile.json"));
        writeProfile(classesDir, profilePath);

        Files.writeString(sourceFile, driftedSource());
        compileSource(sourceFile, classesDir);

        CheckCoverageMojo mojo = new CheckCoverageMojo();
        configureCoverageMojo(mojo, buildProject(projectDir, classesDir), profilePath.toFile());
        setField(mojo, "failOnNewSites", true);
        setField(mojo, "failOnMissingProfileSites", false);

        MojoFailureException failure = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(failure.getMessage().contains("new lambda site"));

        deleteRecursively(projectDir);
    }

    private static void configureCoverageMojo(Object mojo, MavenProject project, File profilePath) throws Exception {
        setField(mojo, "project", project);
        setField(mojo, "skip", false);
        setField(mojo, "profilePath", profilePath);
        setField(mojo, "mode", JmoaExecutionMode.MODE_A);
        setField(mojo, "additionalClassDirectories", List.of());
    }

    private static MavenProject buildProject(Path projectDir, Path classesDir) {
        Build build = new Build();
        build.setDirectory(projectDir.resolve("target").toString());
        build.setOutputDirectory(classesDir.toString());

        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("coverage-it");
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

    private static void writeProfile(Path classesDir, Path profilePath) throws Exception {
        var scanResult = LambdaScanner.scanClassFiles(ClassFileWalker.findClassFiles(classesDir.toFile()));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("serviceName", "coverage-it");
        root.put("hotClasses", List.of());
        root.put("coldClasses", List.of());

        List<Map<String, Object>> lambdaSites = new ArrayList<>();
        for (LambdaMeta meta : scanResult.metadata()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("siteKey", meta.siteKey());
            entry.put("invocationCount", 1);
            lambdaSites.add(entry);
        }
        root.put("lambdaSites", lambdaSites);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(profilePath.toFile(), root);
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

    private static String baseSource() {
        return """
            package example;

            import java.util.List;
            import java.util.function.Predicate;

            public class SampleCoverage {
                public List<String> normalize(List<String> input) {
                    Predicate<String> keep = SampleCoverage::keep;
                    return input.stream().filter(keep).toList();
                }

                public static boolean keep(String value) {
                    return value != null && !value.isBlank();
                }
            }
            """;
    }

    private static String driftedSource() {
        return """
            package example;

            import java.util.List;
            import java.util.function.Function;
            import java.util.function.Predicate;

            public class SampleCoverage {
                public List<String> normalize(List<String> input) {
                    Predicate<String> keep = SampleCoverage::keep;
                    Function<String, String> normalize = String::trim;
                    return input.stream().filter(keep).map(normalize).toList();
                }

                public static boolean keep(String value) {
                    return value != null && !value.isBlank();
                }
            }
            """;
    }
}
