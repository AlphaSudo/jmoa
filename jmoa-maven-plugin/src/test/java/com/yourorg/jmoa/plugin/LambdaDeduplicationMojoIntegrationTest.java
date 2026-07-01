package com.yourorg.jmoa.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.jmoa.plugin.scanner.ClassFileWalker;
import com.yourorg.jmoa.plugin.scanner.LambdaScanner;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaDeduplicationMojoIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void optimizeGoalRewritesCleanProjectAgainstAlignedProfile() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-it-project");
        Path sourceDir = projectDir.resolve(Path.of("src", "main", "java", "example"));
        Path targetDir = projectDir.resolve("target");
        Path classesDir = targetDir.resolve("classes");
        Path runtimeClassesDir = targetDir.resolve("runtime-classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Files.createDirectories(runtimeClassesDir);

        compileRuntimeLibrary(runtimeClassesDir);

        Path sourceFile = sourceDir.resolve("SampleFlow.java");
        Files.writeString(sourceFile, sampleFlowSource());
        compileProject(sourceFile, classesDir);

        Path profileFile = targetDir.resolve("jmoa-profile.json");
        writeAlignedProfile(classesDir, profileFile);

        OptimizeMojo mojo = new OptimizeMojo();
        MavenProject project = buildProject(projectDir, classesDir, runtimeClassesDir);
        setField(mojo, "project", project);
        setField(mojo, "excludes", List.of());
        setField(mojo, "reportOnly", false);
        setField(mojo, "widenSynthetics", true);
        setField(mojo, "skip", false);
        setField(mojo, "verbose", false);
        setField(mojo, "profilePath", profileFile.toFile());
        setField(mojo, "hotThreshold", 10_000L);
        setField(mojo, "frameworkExclusions", List.of());
        setField(mojo, "generateTier1Runtime", true);
        setField(mojo, "failOnMissingRuntimeLibrary", false);
        setField(mojo, "failFastRewrite", false);
        setField(mojo, "mode", JmoaExecutionMode.MODE_A);
        setField(mojo, "additionalClassDirectories", List.of());
        setField(mojo, "debugProfileMatches", false);

        mojo.execute();

        Path reportFile = targetDir.resolve("jmoa-lambda-report.json");
        assertTrue(Files.isRegularFile(reportFile), "expected integration report to be written");

        JsonNode report = MAPPER.readTree(reportFile.toFile());
        assertEquals("MODE_A", report.path("executionMode").asText());
        assertEquals(5, report.path("filterSummary").path("eligibleSites").asInt());
        assertEquals(3, report.path("filterSummary").path("tier1EligibleSites").asInt());
        assertEquals(2, report.path("filterSummary").path("tier2EligibleSites").asInt());
        assertEquals(5, report.path("filterSummary").path("observedProfileSites").asInt());
        assertEquals(5, report.path("weaveSummary").path("plannedSites").asInt());
        assertEquals(1, report.path("weaveSummary").path("rewrittenClasses").asInt());
        assertEquals(3, report.path("tier1RuntimeSummary").path("supportedTier1Sites").asInt());
        assertEquals(2, report.path("tier2AdapterSummary").path("generatedTier2Adapters").asInt());
        assertTrue(report.path("replacementCostSummary").path("generatedRuntimeClassBytes").asInt() > 0);
        assertEquals(2, report.path("replacementCostSummary").path("generatedPackageAdapterCount").asInt());
        assertTrue(report.path("replacementCostSummary").path("generatedPackageAdapterClassBytes").asInt() > 0);
        assertTrue(report.path("replacementCostSummary").path("rewrittenClassGrowthSummary").path("totalByteDelta").asInt() > 0);
        assertEquals(
            report.path("replacementCostSummary").path("rewrittenClassGrowthSummary").path("rewrittenClassesMeasured").asInt(),
            report.path("replacementCostSummary").path("rewrittenClassGrowthSummary").path("rewrittenClassesWithChangedHashes").asInt()
        );
        assertTrue(report.path("replacementCostSummary").path("constantPoolGrowthSummary").path("totalConstantPoolEntryDelta").asInt() > 0);

        Path runtimeClass = classesDir.resolve(Path.of("jmoa", "runtime", "JmoaRuntime.class"));
        assertTrue(Files.isRegularFile(runtimeClass), "expected generated Tier 1 runtime class");

        List<Path> tier2Adapters;
        try (var stream = Files.list(classesDir.resolve("example"))) {
            tier2Adapters = stream
                .filter(path -> path.getFileName().toString().startsWith("JmoaPkgAdapters$SampleFlow$"))
                .toList();
        }
        assertEquals(2, tier2Adapters.size(), "expected generated Tier 2 package adapters");

        List<String> output = invokeOptimizedSample(classesDir, runtimeClassesDir);
        assertEquals(List.of("A", "B"), output);

        deleteRecursively(projectDir);
    }

    @Test
    void optimizeGoalWritesGeneratedClassInventoryWhenSyntheticInventoryEnabled() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-it-generated-inventory");
        Path sourceDir = projectDir.resolve(Path.of("src", "main", "java", "example"));
        Path targetDir = projectDir.resolve("target");
        Path classesDir = targetDir.resolve("classes");
        Path runtimeClassesDir = targetDir.resolve("runtime-classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(classesDir);
        Files.createDirectories(runtimeClassesDir);

        compileRuntimeLibrary(runtimeClassesDir);

        Path sourceFile = sourceDir.resolve("App__BeanDefinitions.java");
        Files.writeString(sourceFile, springAotBeanDefinitionsSource());
        compileProject(sourceFile, classesDir);

        OptimizeMojo mojo = new OptimizeMojo();
        MavenProject project = buildProject(projectDir, classesDir, runtimeClassesDir);
        setField(mojo, "project", project);
        setField(mojo, "excludes", List.of());
        setField(mojo, "reportOnly", true);
        setField(mojo, "widenSynthetics", true);
        setField(mojo, "skip", false);
        setField(mojo, "verbose", false);
        setField(mojo, "hotThreshold", 10_000L);
        setField(mojo, "frameworkExclusions", List.of());
        setField(mojo, "generateTier1Runtime", false);
        setField(mojo, "failOnMissingRuntimeLibrary", false);
        setField(mojo, "failFastRewrite", false);
        setField(mojo, "mode", JmoaExecutionMode.MODE_A);
        setField(mojo, "additionalClassDirectories", List.of());
        setField(mojo, "debugProfileMatches", false);
        setField(mojo, "syntheticEnabled", true);
        setField(mojo, "syntheticInventoryOnly", true);
        setField(mojo, "syntheticOptimizeFamily", "none");
        setField(mojo, "syntheticFailOnUnsafe", true);
        setField(mojo, "syntheticScanClasspathJars", false);

        mojo.execute();

        Path inventoryFile = targetDir.resolve("generated-class-inventory.json");
        Path markdownFile = targetDir.resolve("generated-class-inventory.md");
        Path breakdownFile = targetDir.resolve("generated-class-family-breakdown.json");
        Path taxonomyFile = targetDir.resolve("generated-class-safety-taxonomy.json");
        Path prototypeFile = targetDir.resolve("synthetic-optimizer-prototype-report.json");
        Path roiV2File = targetDir.resolve("jmoa-roi-v2-report.json");
        assertTrue(Files.isRegularFile(inventoryFile), "expected generated-class JSON inventory");
        assertTrue(Files.isRegularFile(markdownFile), "expected generated-class Markdown inventory");
        assertTrue(Files.isRegularFile(breakdownFile), "expected generated-class family breakdown");
        assertTrue(Files.isRegularFile(taxonomyFile), "expected generated-class safety taxonomy");
        assertTrue(Files.isRegularFile(prototypeFile), "expected synthetic prototype report");
        assertTrue(Files.isRegularFile(roiV2File), "expected generated-class ROI v2 report");

        JsonNode inventory = MAPPER.readTree(inventoryFile.toFile());
        assertEquals("v2-a1-inventory", inventory.path("metadataVersion").asText());
        assertEquals(1, inventory.path("totalClassesScanned").asInt());
        assertEquals(1, inventory.path("generatedLikeClasses").asInt());
        JsonNode record = inventory.path("classes").get(0);
        assertEquals("example.App__BeanDefinitions", record.path("className").asText());
        assertEquals("SPRING_AOT_BEAN_DEFINITIONS", record.path("family").asText());
        assertEquals("UNKNOWN", record.path("riskLevel").asText());

        JsonNode taxonomy = MAPPER.readTree(taxonomyFile.toFile());
        assertEquals("v2-a3-safety-taxonomy", taxonomy.path("metadataVersion").asText());
        assertEquals("SAFE_TO_REPACK_ONLY", taxonomy.path("eligibility").get(0).path("safetyCategory").asText());

        JsonNode prototype = MAPPER.readTree(prototypeFile.toFile());
        assertEquals("REPORT_ONLY", prototype.path("mode").asText());
        assertEquals(false, prototype.path("bytecodeMutationEnabled").asBoolean());

        deleteRecursively(projectDir);
    }

    @Test
    void optimizeGoalLeavesHotAndExcludedSitesUntouched() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-it-safety");
        Path sourceRoot = projectDir.resolve(Path.of("src", "main", "java"));
        Path sampleDir = sourceRoot.resolve("example");
        Path frameworkDir = sourceRoot.resolve(Path.of("example", "framework"));
        Path targetDir = projectDir.resolve("target");
        Path classesDir = targetDir.resolve("classes");
        Path runtimeClassesDir = targetDir.resolve("runtime-classes");
        Files.createDirectories(sampleDir);
        Files.createDirectories(frameworkDir);
        Files.createDirectories(classesDir);
        Files.createDirectories(runtimeClassesDir);

        compileRuntimeLibrary(runtimeClassesDir);

        Path frameworkSource = frameworkDir.resolve("FrameworkFlow.java");
        Path sampleSource = sampleDir.resolve("SelectiveFlow.java");
        Files.writeString(frameworkSource, frameworkFnsSource());
        Files.writeString(sampleSource, selectiveFlowSource());
        compileProject(List.of(frameworkSource, sampleSource), classesDir);

        Path profileFile = targetDir.resolve("jmoa-profile.json");
        writeSelectiveProfile(classesDir, profileFile);

        OptimizeMojo mojo = new OptimizeMojo();
        MavenProject project = buildProject(projectDir, classesDir, runtimeClassesDir);
        setField(mojo, "project", project);
        setField(mojo, "excludes", List.of());
        setField(mojo, "reportOnly", false);
        setField(mojo, "widenSynthetics", true);
        setField(mojo, "skip", false);
        setField(mojo, "verbose", false);
        setField(mojo, "profilePath", profileFile.toFile());
        setField(mojo, "hotThreshold", 10L);
        setField(mojo, "frameworkExclusions", List.of("example/framework"));
        setField(mojo, "generateTier1Runtime", true);
        setField(mojo, "failOnMissingRuntimeLibrary", false);
        setField(mojo, "failFastRewrite", false);
        setField(mojo, "mode", JmoaExecutionMode.MODE_A);
        setField(mojo, "additionalClassDirectories", List.of());
        setField(mojo, "debugProfileMatches", false);

        mojo.execute();

        Path reportFile = targetDir.resolve("jmoa-lambda-report.json");
        JsonNode report = MAPPER.readTree(reportFile.toFile());
        assertEquals(5, report.path("filterSummary").path("totalDecisions").asInt());
        assertEquals(3, report.path("filterSummary").path("eligibleSites").asInt());
        assertEquals(1, report.path("filterSummary").path("excludedByRule").path("HOT_SITE").path("count").asInt());
        assertEquals(1, report.path("filterSummary").path("excludedByRule").path("FRAMEWORK_EXCLUDED").path("count").asInt());
        assertEquals(3, report.path("weaveSummary").path("plannedSites").asInt());
        assertEquals(1, report.path("weaveSummary").path("rewrittenClasses").asInt());

        Path optimizedClass = classesDir.resolve(Path.of("example", "SelectiveFlow.class"));
        Path excludedClass = classesDir.resolve(Path.of("example", "framework", "FrameworkFlow.class"));
        assertEquals(1, countLambdaInvokeDynamicSites(optimizedClass), "hot site should remain invokedynamic");
        assertEquals(1, countLambdaInvokeDynamicSites(excludedClass), "excluded-package site should remain invokedynamic");

        List<String> output = invokeSelectiveSample(classesDir, runtimeClassesDir);
        assertEquals(List.of("A", "B"), output);

        deleteRecursively(projectDir);
    }

    @Test
    void modeCFrameworkFilteringRewritesSafeExpandedFixtureAndDeniesUnknownFixture() throws Exception {
        Path projectDir = Files.createTempDirectory("jmoa-it-mode-c");
        Path sourceRoot = projectDir.resolve(Path.of("src", "main", "java"));
        Path appDir = sourceRoot.resolve("example");
        Path safeDepSourceDir = projectDir.resolve(Path.of("deps-src", "spring", "org", "springframework", "context"));
        Path unsafeDepSourceDir = projectDir.resolve(Path.of("deps-src", "unknown", "org", "acme", "framework"));
        Path targetDir = projectDir.resolve("target");
        Path classesDir = targetDir.resolve("classes");
        Path runtimeClassesDir = targetDir.resolve("runtime-classes");
        Path safeDepClassesDir = targetDir.resolve(Path.of("deps-classes", "spring"));
        Path unsafeDepClassesDir = targetDir.resolve(Path.of("deps-classes", "unknown"));
        Path repoDir = targetDir.resolve("repo");
        Files.createDirectories(appDir);
        Files.createDirectories(safeDepSourceDir);
        Files.createDirectories(unsafeDepSourceDir);
        Files.createDirectories(classesDir);
        Files.createDirectories(runtimeClassesDir);
        Files.createDirectories(safeDepClassesDir);
        Files.createDirectories(unsafeDepClassesDir);
        Files.createDirectories(repoDir);

        compileRuntimeLibrary(runtimeClassesDir);

        Path appSource = appDir.resolve("Anchor.java");
        Path safeDepSource = safeDepSourceDir.resolve("SafeFrameworkFixture.java");
        Path unsafeDepSource = unsafeDepSourceDir.resolve("UnknownFrameworkFixture.java");
        Files.writeString(appSource, anchorSource());
        Files.writeString(safeDepSource, safeFrameworkFixtureSource());
        Files.writeString(unsafeDepSource, unknownFrameworkFixtureSource());

        compileProject(appSource, classesDir);
        compileProject(safeDepSource, safeDepClassesDir);
        compileProject(unsafeDepSource, unsafeDepClassesDir);

        File safeJar = createJarFromClasses(repoDir.resolve("spring-context-fixture.jar"), safeDepClassesDir);
        File unsafeJar = createJarFromClasses(repoDir.resolve("unknown-framework-fixture.jar"), unsafeDepClassesDir);

        Artifact safeArtifact = artifact("org.springframework", "spring-context-fixture", "1.0", safeJar);
        Artifact unsafeArtifact = artifact("org.acme", "unknown-framework-fixture", "1.0", unsafeJar);

        Path profileFile = targetDir.resolve("jmoa-profile.json");
        writeModeCFrameworkProfile(safeDepClassesDir, unsafeDepClassesDir, profileFile);

        OptimizeMojo mojo = new OptimizeMojo();
        MavenProject project = buildProject(projectDir, classesDir, runtimeClassesDir, Set.of(safeArtifact, unsafeArtifact));
        setField(mojo, "project", project);
        setField(mojo, "excludes", List.of());
        setField(mojo, "reportOnly", false);
        setField(mojo, "widenSynthetics", true);
        setField(mojo, "skip", false);
        setField(mojo, "verbose", false);
        setField(mojo, "profilePath", profileFile.toFile());
        setField(mojo, "hotThreshold", 10_000L);
        setField(mojo, "frameworkExclusions", List.of());
        setField(mojo, "frameworkFiltering", true);
        setField(mojo, "allowSpringAotFrameworkSites", true);
        setField(mojo, "allowExpandedDependencySites", true);
        setField(mojo, "allowUnknownFrameworkSites", false);
        setField(mojo, "frameworkAllowPrefixes", List.of("org.springframework.context"));
        setField(mojo, "frameworkDenyPrefixes", List.of("com.fasterxml.jackson", "org.hibernate"));
        setField(mojo, "frameworkHotThreshold", 10_000L);
        setField(mojo, "generateTier1Runtime", true);
        setField(mojo, "failOnMissingRuntimeLibrary", false);
        setField(mojo, "failFastRewrite", false);
        setField(mojo, "mode", JmoaExecutionMode.MODE_C);
        setField(mojo, "additionalClassDirectories", List.of());
        setField(mojo, "expandDependencies", true);
        setField(mojo, "expandedDepsDir", targetDir.resolve("jmoa-expanded-deps").toFile());
        setField(mojo, "expandIncludes", "");
        setField(mojo, "expandExcludes", "");
        setField(mojo, "cleanExpandedDeps", true);
        setField(mojo, "maxExpandedClasses", 1000);
        setField(mojo, "debugProfileMatches", false);

        mojo.execute();

        Path reportFile = targetDir.resolve("jmoa-lambda-report.json");
        assertTrue(Files.isRegularFile(reportFile), "expected Mode C framework report to be written");

        JsonNode report = MAPPER.readTree(reportFile.toFile());
        assertEquals("MODE_C", report.path("executionMode").asText());
        assertEquals(1, report.path("filterSummary").path("eligibleSites").asInt());
        assertTrue(report.path("filterSummary").path("frameworkSafetySummary").path("allowedFrameworkSites").asInt() >= 1);
        assertTrue(report.path("filterSummary").path("frameworkSafetySummary").path("deniedFrameworkSites").asInt() >= 1);
        assertTrue(report.path("filterSummary").path("excludedByRule").path("FRAMEWORK_SAFETY_DENIED").path("count").asInt() >= 1);
        assertEquals(1, report.path("weaveSummary").path("plannedSites").asInt());
        assertEquals(1, report.path("weaveSummary").path("rewrittenClasses").asInt());
        assertEquals(1, report.path("modeCRewriteSummary").path("rewrittenSites").asInt());
        assertTrue(report.path("replacementCostSummary").path("generatedRuntimeClassBytes").asInt() > 0);
        assertEquals(0, report.path("replacementCostSummary").path("generatedPackageAdapterCount").asInt());
        assertTrue(report.path("replacementCostSummary").path("rewrittenClassGrowthSummary").path("totalByteDelta").asInt() > 0);
        assertEquals(
            report.path("replacementCostSummary").path("rewrittenClassGrowthSummary").path("rewrittenClassesMeasured").asInt(),
            report.path("replacementCostSummary").path("rewrittenClassGrowthSummary").path("rewrittenClassesWithChangedHashes").asInt()
        );
        assertTrue(report.path("replacementCostSummary").path("constantPoolGrowthSummary").path("totalConstantPoolEntryDelta").asInt() > 0);

        JsonNode frameworkDecisions = report.path("filterSummary").path("frameworkDecisions");
        assertTrue(findFrameworkDecision(frameworkDecisions, "org/springframework/context/SafeFrameworkFixture").path("allowed").asBoolean());
        assertFalse(findFrameworkDecision(frameworkDecisions, "org/acme/framework/UnknownFrameworkFixture").path("allowed").asBoolean());

        Path expandedDepsDir = targetDir.resolve("jmoa-expanded-deps");
        Path safeExpandedClass = expandedDepsDir.resolve(Path.of("org.springframework__spring-context-fixture__1.0", "org", "springframework", "context", "SafeFrameworkFixture.class"));
        Path unknownExpandedClass = expandedDepsDir.resolve(Path.of("org.acme__unknown-framework-fixture__1.0", "org", "acme", "framework", "UnknownFrameworkFixture.class"));
        assertEquals(0, countLambdaInvokeDynamicSites(safeExpandedClass), "safe expanded framework site should be rewritten");
        assertEquals(1, countLambdaInvokeDynamicSites(unknownExpandedClass), "unknown expanded framework site should remain invokedynamic");

        String normalized = invokeExpandedFrameworkNormalizer(classesDir, runtimeClassesDir, safeExpandedClass.getParent().getParent().getParent().getParent(), "org.springframework.context.SafeFrameworkFixture");
        assertEquals("hi", normalized);

        deleteRecursively(projectDir);
    }

    private static MavenProject buildProject(Path projectDir, Path classesDir, Path runtimeClassesDir) {
        return buildProject(projectDir, classesDir, runtimeClassesDir, Set.of());
    }

    private static MavenProject buildProject(Path projectDir, Path classesDir, Path runtimeClassesDir, Set<Artifact> artifacts) {
        Build build = new Build();
        build.setDirectory(projectDir.resolve("target").toString());
        build.setOutputDirectory(classesDir.toString());

        Model model = new Model();
        model.setGroupId("com.example");
        model.setArtifactId("jmoa-it-project");
        model.setVersion("1.0-SNAPSHOT");
        model.setBuild(build);

        List<String> classpath = List.of(
            classesDir.toAbsolutePath().toString(),
            runtimeClassesDir.toAbsolutePath().toString()
        );
        return new MavenProject(model) {
            @Override
            public List<String> getCompileClasspathElements() {
                return classpath;
            }

            @Override
            public Set<Artifact> getArtifacts() {
                return artifacts;
            }
        };
    }

    private static void compileProject(Path sourceFile, Path classesDir) throws Exception {
        compileProject(List.of(sourceFile), classesDir);
    }

    private static void compileProject(List<Path> sourceFiles, Path classesDir) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "expected JDK compiler to be available");
        List<String> args = new ArrayList<>();
        args.add("--release");
        args.add("22");
        args.add("-d");
        args.add(classesDir.toString());
        sourceFiles.stream().map(Path::toString).forEach(args::add);
        int exitCode = compiler.run(null, null, null, args.toArray(String[]::new));
        assertEquals(0, exitCode, "sample integration project should compile cleanly");
    }

    private static void compileRuntimeLibrary(Path outputDir) throws Exception {
        Path runtimeSourceDir = Path.of("..", "jmoa-runtime-lib", "src", "main", "java", "jmoa", "runtime")
            .toAbsolutePath()
            .normalize();
        assertTrue(Files.isDirectory(runtimeSourceDir), "expected runtime library sources to be available");

        List<String> sourceFiles;
        try (var stream = Files.list(runtimeSourceDir)) {
            sourceFiles = stream
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .map(path -> path.toAbsolutePath().toString())
                .toList();
        }
        assertFalse(sourceFiles.isEmpty(), "expected runtime library source files");

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "expected JDK compiler to be available");

        List<String> args = new ArrayList<>();
        args.add("--release");
        args.add("22");
        args.add("-d");
        args.add(outputDir.toString());
        args.addAll(sourceFiles);

        int exitCode = compiler.run(null, null, null, args.toArray(String[]::new));
        assertEquals(0, exitCode, "runtime library sources should compile cleanly for integration testing");
    }

    private static void writeAlignedProfile(Path classesDir, Path profileFile) throws Exception {
        var scanResult = LambdaScanner.scanClassFiles(ClassFileWalker.findClassFiles(classesDir.toFile()));
        assertFalse(scanResult.metadata().isEmpty(), "sample integration project should expose stateless lambda metadata");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("serviceName", "jmoa-it-project");
        root.put("hotClasses", List.of());
        root.put("coldClasses", List.of());

        List<Map<String, Object>> lambdaSites = new ArrayList<>();
        scanResult.metadata().forEach(meta -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("siteKey", meta.siteKey());
            entry.put("invocationCount", 2);
            lambdaSites.add(entry);
        });
        root.put("lambdaSites", lambdaSites);

        MAPPER.writerWithDefaultPrettyPrinter().writeValue(profileFile.toFile(), root);
    }

    private static void writeSelectiveProfile(Path classesDir, Path profileFile) throws Exception {
        var scanResult = LambdaScanner.scanClassFiles(ClassFileWalker.findClassFiles(classesDir.toFile()));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("serviceName", "jmoa-it-safety");
        root.put("hotClasses", List.of());
        root.put("coldClasses", List.of());

        List<Map<String, Object>> lambdaSites = new ArrayList<>();
        scanResult.metadata().forEach(meta -> {
            long invocationCount = 2L;
            if (meta.ownerInternalName().equals("example/framework/FrameworkFlow")) {
                invocationCount = 2L;
            } else if (meta.implName().equals("publicKeep")) {
                invocationCount = 25L;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("siteKey", meta.siteKey());
            entry.put("invocationCount", invocationCount);
            lambdaSites.add(entry);
        });
        root.put("lambdaSites", lambdaSites);

        MAPPER.writerWithDefaultPrettyPrinter().writeValue(profileFile.toFile(), root);
    }

    private static void writeModeCFrameworkProfile(Path safeDepClassesDir, Path unsafeDepClassesDir, Path profileFile) throws Exception {
        List<Map<String, Object>> lambdaSites = new ArrayList<>();
        var safeScan = LambdaScanner.scanClassFiles(ClassFileWalker.findClassFiles(safeDepClassesDir.toFile()));
        var unsafeScan = LambdaScanner.scanClassFiles(ClassFileWalker.findClassFiles(unsafeDepClassesDir.toFile()));
        safeScan.metadata().forEach(meta -> lambdaSites.add(profileEntry(meta.siteKey(), 2L)));
        unsafeScan.metadata().forEach(meta -> lambdaSites.add(profileEntry(meta.siteKey(), 2L)));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("serviceName", "jmoa-it-mode-c");
        root.put("hotClasses", List.of());
        root.put("coldClasses", List.of());
        root.put("lambdaSites", lambdaSites);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(profileFile.toFile(), root);
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeOptimizedSample(Path classesDir, Path runtimeClassesDir) throws Exception {
        URL[] urls = {
            classesDir.toUri().toURL(),
            runtimeClassesDir.toUri().toURL()
        };
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            Thread current = Thread.currentThread();
            ClassLoader original = current.getContextClassLoader();
            current.setContextClassLoader(loader);
            try {
                Class<?> type = loader.loadClass("example.SampleFlow");
                Object instance = type.getConstructor().newInstance();
                Method process = type.getMethod("process", List.class);
                return (List<String>) process.invoke(instance, List.of("  a  ", "", " b "));
            } finally {
                current.setContextClassLoader(original);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> invokeSelectiveSample(Path classesDir, Path runtimeClassesDir) throws Exception {
        URL[] urls = {
            classesDir.toUri().toURL(),
            runtimeClassesDir.toUri().toURL()
        };
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            Thread current = Thread.currentThread();
            ClassLoader original = current.getContextClassLoader();
            current.setContextClassLoader(loader);
            try {
                Class<?> type = loader.loadClass("example.SelectiveFlow");
                Object instance = type.getConstructor().newInstance();
                Method process = type.getMethod("process", List.class);
                return (List<String>) process.invoke(instance, List.of("  a  ", "", " b "));
            } finally {
                current.setContextClassLoader(original);
            }
        }
    }

    private static int countLambdaInvokeDynamicSites(Path classFile) throws Exception {
        byte[] classBytes = Files.readAllBytes(classFile);
        AtomicInteger count = new AtomicInteger();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                        if ("java/lang/invoke/LambdaMetafactory".equals(bootstrapMethodHandle.getOwner())) {
                            count.incrementAndGet();
                        }
                    }
                };
            }
        }, 0);
        return count.get();
    }

    private static String invokeExpandedFrameworkNormalizer(
        Path classesDir,
        Path runtimeClassesDir,
        Path expandedRoot,
        String className
    ) throws Exception {
        URL[] urls = {
            classesDir.toUri().toURL(),
            runtimeClassesDir.toUri().toURL(),
            expandedRoot.toUri().toURL()
        };
        try (URLClassLoader loader = new URLClassLoader(urls, null)) {
            Thread current = Thread.currentThread();
            ClassLoader original = current.getContextClassLoader();
            current.setContextClassLoader(loader);
            try {
                Class<?> type = loader.loadClass(className);
                Object instance = type.getConstructor().newInstance();
                Method normalizer = type.getMethod("normalizer");
                Object function = normalizer.invoke(instance);
                Method apply = function.getClass().getMethod("apply", Object.class);
                return (String) apply.invoke(function, "  hi  ");
            } finally {
                current.setContextClassLoader(original);
            }
        }
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

    private static Artifact artifact(String groupId, String artifactId, String version, File file) {
        DefaultArtifact artifact = new DefaultArtifact(
            groupId,
            artifactId,
            version,
            "compile",
            "jar",
            null,
            new DefaultArtifactHandler("jar")
        );
        artifact.setFile(file);
        return artifact;
    }

    private static File createJarFromClasses(Path jarPath, Path classesRoot) throws Exception {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            try (var stream = Files.walk(classesRoot)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String entryName = classesRoot.relativize(file).toString().replace('\\', '/');
                    jar.putNextEntry(new JarEntry(entryName));
                    jar.write(Files.readAllBytes(file));
                    jar.closeEntry();
                }
            }
        }
        return jarPath.toFile();
    }

    private static Map<String, Object> profileEntry(String siteKey, long invocationCount) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("siteKey", siteKey);
        entry.put("invocationCount", invocationCount);
        return entry;
    }

    private static JsonNode findFrameworkDecision(JsonNode decisions, String ownerClass) {
        for (JsonNode decision : decisions) {
            if (ownerClass.equals(decision.path("ownerClass").asText())) {
                return decision;
            }
        }
        throw new AssertionError("Framework decision not found for " + ownerClass);
    }

    private static String sampleFlowSource() {
        return """
            package example;

            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.function.BiConsumer;
            import java.util.function.Function;
            import java.util.function.Predicate;
            import java.util.function.Supplier;

            public class SampleFlow {

                public List<String> process(List<String> input) {
                    Predicate<String> keep = SampleFlow::publicKeep;
                    Function<String, String> normalize = SampleFlow::packageNormalize;
                    Supplier<LinkedHashMap<String, String>> mapFactory = LinkedHashMap::new;
                    BiConsumer<Map<String, String>, Map<String, String>> merger = Map::putAll;

                    List<String> output = input.stream()
                        .filter(value -> publicKeep(value))
                        .map(normalize)
                        .toList();

                    Map<String, String> copy = mapFactory.get();
                    merger.accept(copy, Map.of("size", Integer.toString(output.size())));
                    return new ArrayList<>(output);
                }

                public static boolean publicKeep(String value) {
                    return value != null && !value.isBlank();
                }

                static String packageNormalize(String value) {
                    return value.trim().toUpperCase();
                }
            }
            """;
    }

    private static String frameworkFnsSource() {
        return """
            package example.framework;

            import java.util.function.Function;

            public final class FrameworkFlow {

                public Function<String, String> excludedNormalize() {
                    return String::trim;
                }
            }
            """;
    }

    private static String anchorSource() {
        return """
            package example;

            public class Anchor {
                public String ping() {
                    return "ok";
                }
            }
            """;
    }

    private static String safeFrameworkFixtureSource() {
        return """
            package org.springframework.context;

            import java.util.function.Function;

            public class SafeFrameworkFixture {

                public Function<String, String> normalizer() {
                    return String::trim;
                }
            }
            """;
    }

    private static String unknownFrameworkFixtureSource() {
        return """
            package org.acme.framework;

            import java.util.function.Function;

            public class UnknownFrameworkFixture {

                public Function<String, String> normalizer() {
                    return String::trim;
                }
            }
            """;
    }

    private static String springAotBeanDefinitionsSource() {
        return """
            package example;

            public final class App__BeanDefinitions {
                public Object register() {
                    return null;
                }
            }
            """;
    }

    private static String selectiveFlowSource() {
        return """
            package example;

            import java.util.ArrayList;
            import java.util.LinkedHashMap;
            import java.util.List;
            import java.util.Map;
            import java.util.function.BiConsumer;
            import java.util.function.Function;
            import java.util.function.Predicate;
            import java.util.function.Supplier;

            public class SelectiveFlow {

                public List<String> process(List<String> input) {
                    Predicate<String> keepHot = SelectiveFlow::publicKeep;
                    Function<String, String> normalize = SelectiveFlow::packageNormalize;
                    Supplier<LinkedHashMap<String, String>> mapFactory = LinkedHashMap::new;
                    BiConsumer<Map<String, String>, Map<String, String>> merger = Map::putAll;

                    List<String> output = input.stream()
                        .filter(keepHot)
                        .map(normalize)
                        .toList();

                    Map<String, String> copy = mapFactory.get();
                    merger.accept(copy, Map.of("size", Integer.toString(output.size())));
                    return new ArrayList<>(output);
                }

                public static boolean publicKeep(String value) {
                    return value != null && !value.isBlank();
                }

                static String packageNormalize(String value) {
                    return value.trim().toUpperCase();
                }
            }
            """;
    }
}
