package com.yourorg.jmoa.plugin.generated.pruning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedClassPruningTest {

    @TempDir
    Path tempDir;

    private final GeneratedClassReachabilityAnalyzer analyzer = new GeneratedClassReachabilityAnalyzer();

    @Test
    void staleGeneratedOrphanIsRemovableWhileReferencedClassesAreKept() throws IOException {
        Path root = root("root");
        writeClass(root, "app/Main", List.of("jmoa/runtime/JmoaDirectAdapters$Used"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Used", List.of());
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Stale", List.of());

        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability =
                analyzer.analyze(request(root));
        GeneratedClassPruningPlan.PruningPlan plan = plan(reachability, root);

        assertTrue(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$Stale"));
        assertFalse(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$Used"));
        assertTrue(plan.entries().stream()
                .filter(e -> e.internalName().equals("jmoa/runtime/JmoaDirectAdapters$Used"))
                .findFirst().orElseThrow().referenceEdges().contains("app/Main"));
    }

    @Test
    void chapter29F1Regression_entryPointReferenceKeepsGeneratedAdapter() throws IOException {
        // F1 reproduction: an explicit runtime entry point (app/Main) uses one
        // generated adapter. Entry points are scan roots, so the adapter must
        // be KEPT — the old code skipped scanning entry points and would have
        // misclassified the adapter as an orphan.
        Path root = root("root");
        writeClass(root, "app/Main", List.of("jmoa/runtime/JmoaDirectAdapters$EntryUsed"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$EntryUsed", List.of());
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Stale", List.of());

        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability = analyzer.analyze(
                new GeneratedClassReachabilityAnalyzer.AnalysisRequest(
                        List.of(root),
                        Set.of("jmoa/runtime/JmoaDirectAdapters"),
                        Set.of("app/Main"),
                        List.of()));
        GeneratedClassPruningPlan.PruningPlan plan = GeneratedClassPruningPlan.build(
                reachability, Set.of("app/Main"), Map.of(), Map.of(), Set.of());

        assertFalse(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$EntryUsed"),
                "an adapter used by an explicit entry point must be kept");
        assertTrue(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$Stale"));
    }

    @Test
    void chapter29F1Regression_prunerRefusesCorruptedPlanBeforeDeletingAnything() throws IOException {
        Path root = root("root");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        writeClass(root, "app/Main", List.of("jmoa/runtime/JmoaDirectAdapters$Used"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Used", List.of());
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Stale", List.of());
        writeClass(outside, "jmoa/runtime/JmoaDirectAdapters$Outside", List.of());

        // Corrupt the plan by hand: mark the still-referenced Used adapter
        // removable. The pruner must validate the FULL plan and refuse before
        // deleting even the legitimately removable Stale class.
        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability =
                analyzer.analyze(request(root, outside));
        GeneratedClassPruningPlan.PruningPlan valid = plan(reachability, root, outside);
        GeneratedClassPruningPlan.PruningPlan corrupted = new GeneratedClassPruningPlan.PruningPlan(
                valid.entries().stream()
                        .map(e -> e.internalName().equals("jmoa/runtime/JmoaDirectAdapters$Used")
                                ? new GeneratedClassPruningPlan.PlanEntry(e.internalName(), true,
                                        "CORRUPTED_REMOVE_REFERENCED", e.bytes(), e.referenceEdges(), e.sourceHash())
                                : e)
                        .toList(),
                valid.removableCount() + 1,
                valid.removableBytes());

        GeneratedClassPruner pruner = new GeneratedClassPruner().withDeletionEnabled(true);
        assertThrows(IOException.class, () -> pruner.prune(corrupted, classFiles(), List.of(root, outside)),
                "a plan that removes a still-referenced class must be refused");
        assertTrue(Files.exists(root.resolve("jmoa/runtime/JmoaDirectAdapters$Stale.class")),
                "refusal must happen before ANY deletion, including legitimate orphans");
        assertTrue(Files.exists(root.resolve("jmoa/runtime/JmoaDirectAdapters$Used.class")));
    }

    @Test
    void serviceDescriptorAndNestedReferencesKeepClasses() throws IOException {
        Path root = root("root");
        writeClass(root, "app/Main", List.of("jmoa/runtime/JmoaDirectAdapters$Outer$Inner"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Outer$Inner", List.of());
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$ServiceOnly", List.of());
        Path services = root.resolve("META-INF/services/jmoa.spi");
        Files.createDirectories(services.getParent());
        Files.writeString(services, "jmoa.runtime.JmoaDirectAdapters$ServiceOnly\n");

        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability =
                analyzer.analyze(new GeneratedClassReachabilityAnalyzer.AnalysisRequest(
                        List.of(root),
                        Set.of("jmoa/runtime/JmoaDirectAdapters"),
                        Set.of(),
                        List.of(services)));
        GeneratedClassPruningPlan.PruningPlan plan = plan(reachability, root);

        assertFalse(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$Outer$Inner"));
        assertFalse(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$ServiceOnly"));
    }

    @Test
    void explicitEntryPointsAndMultiRootDuplicatesAreNeverRemovable() throws IOException {
        Path rootA = root("a");
        Path rootB = root("b");
        writeClass(rootA, "jmoa/runtime/JmoaRuntime", List.of());
        writeClass(rootA, "jmoa/runtime/JmoaDirectAdapters$Dup", List.of());
        writeClass(rootB, "jmoa/runtime/JmoaDirectAdapters$Dup", List.of());

        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability =
                analyzer.analyze(new GeneratedClassReachabilityAnalyzer.AnalysisRequest(
                        List.of(rootA, rootB),
                        Set.of("jmoa/runtime/JmoaRuntime", "jmoa/runtime/JmoaDirectAdapters"),
                        Set.of("jmoa/runtime/JmoaRuntime"),
                        List.of()));
        GeneratedClassPruningPlan.PruningPlan plan = GeneratedClassPruningPlan.build(
                reachability,
                Set.of("jmoa/runtime/JmoaRuntime"),
                Map.of(),
                Map.of(),
                Set.of());

        assertEquals("KEEP_EXPLICIT_RUNTIME_ENTRY_POINT",
                reason(plan, "jmoa/runtime/JmoaRuntime"));
        assertEquals("KEEP_DUPLICATE_IN_MULTIPLE_ROOTS",
                reason(plan, "jmoa/runtime/JmoaDirectAdapters$Dup"));
    }

    @Test
    void orphanReferencedOnlyByAnotherOrphanIsRemovableTogether() throws IOException {
        Path root = root("root");
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$OrphanA", List.of("jmoa/runtime/JmoaDirectAdapters$OrphanB"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$OrphanB", List.of());

        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability =
                analyzer.analyze(request(root));
        GeneratedClassPruningPlan.PruningPlan plan = plan(reachability, root);

        assertTrue(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$OrphanA"));
        assertTrue(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$OrphanB"));
    }

    @Test
    void unreachableStronglyConnectedComponentIsRemovableAsAUnit() throws IOException {
        Path root = root("root");
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$CycleA",
                List.of("jmoa/runtime/JmoaDirectAdapters$CycleB"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$CycleB",
                List.of("jmoa/runtime/JmoaDirectAdapters$CycleA"));

        GeneratedClassPruningPlan.PruningPlan plan = plan(analyzer.analyze(request(root)), root);

        assertTrue(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$CycleA"));
        assertTrue(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$CycleB"));
    }

    @Test
    void externalRootKeepsEntireGeneratedChainTransitively() throws IOException {
        Path root = root("root");
        writeClass(root, "app/Main", List.of("jmoa/runtime/JmoaDirectAdapters$ChainA"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$ChainA",
                List.of("jmoa/runtime/JmoaDirectAdapters$ChainB"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$ChainB", List.of());

        GeneratedClassPruningPlan.PruningPlan plan = plan(analyzer.analyze(request(root)), root);

        assertFalse(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$ChainA"));
        assertFalse(plan.isRemovable("jmoa/runtime/JmoaDirectAdapters$ChainB"));
    }

    @Test
    void planIsDeterministicUnderIdenticalInputs() throws IOException {
        Path root = root("root");
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Z", List.of());
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$A", List.of());

        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability first =
                analyzer.analyze(request(root));
        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability second =
                analyzer.analyze(request(root));
        assertEquals(
                GeneratedClassPruningPlan.build(first, Set.of(), Map.of(), Map.of(), Set.of()).entries(),
                GeneratedClassPruningPlan.build(second, Set.of(), Map.of(), Map.of(), Set.of()).entries());
    }

    @Test
    void prunerIsReportOnlyByDefaultAndRefusesPathsOutsideRoots() throws IOException {
        Path root = root("root");
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        writeClass(root, "app/Main", List.of("jmoa/runtime/JmoaDirectAdapters$Used"));
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Used", List.of());
        writeClass(root, "jmoa/runtime/JmoaDirectAdapters$Stale", List.of());
        writeClass(outside, "jmoa/runtime/JmoaDirectAdapters$Outside", List.of());

        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability =
                analyzer.analyze(request(root, outside));
        GeneratedClassPruningPlan.PruningPlan plan = plan(reachability, root, outside);

        GeneratedClassPruner pruner = new GeneratedClassPruner();
        assertThrows(IOException.class, () -> pruner.prune(plan, classFiles(), List.of(root)),
                "pruner must refuse files outside the declared roots");

        // Default mode is report-only: the full plan validates, no file moves.
        GeneratedClassPruner.PruningResult report = pruner.prune(plan, classFiles(), List.of(root, outside));
        assertEquals(List.of(), report.removedClasses());
        assertTrue(Files.exists(root.resolve("jmoa/runtime/JmoaDirectAdapters$Stale.class")));
        assertTrue(report.validationNotes().stream().anyMatch(n -> n.contains("deletion is disabled")));

        // Explicit enablement deletes exactly the validated orphans.
        GeneratedClassPruner.PruningResult deleted = pruner.withDeletionEnabled(true)
                .prune(plan, classFiles(), List.of(root, outside));
        assertEquals(List.of("jmoa/runtime/JmoaDirectAdapters$Outside", "jmoa/runtime/JmoaDirectAdapters$Stale"),
                deleted.removedClasses());
        assertFalse(Files.exists(root.resolve("jmoa/runtime/JmoaDirectAdapters$Stale.class")));
        assertFalse(Files.exists(outside.resolve("jmoa/runtime/JmoaDirectAdapters$Outside.class")));
        assertTrue(Files.exists(root.resolve("app/Main.class")));
        assertTrue(Files.exists(root.resolve("jmoa/runtime/JmoaDirectAdapters$Used.class")));
    }

    private GeneratedClassReachabilityAnalyzer.AnalysisRequest request(Path... roots) {
        return new GeneratedClassReachabilityAnalyzer.AnalysisRequest(
                List.of(roots), Set.of("jmoa/runtime/JmoaDirectAdapters"), Set.of(), List.of());
    }

    private GeneratedClassPruningPlan.PruningPlan plan(
            GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability, Path... roots)
            throws IOException {
        Map<String, Long> sizes = new java.util.LinkedHashMap<>();
        for (Path root : roots) {
            try (var stream = Files.walk(root)) {
                for (Path file : stream.filter(f -> f.toString().endsWith(".class")).toList()) {
                    String internalName = GeneratedClassReachabilityAnalyzer.internalNameOf(root, file);
                    if (internalName != null) {
                        sizes.put(internalName, Files.size(file));
                    }
                }
            }
        }
        return GeneratedClassPruningPlan.build(reachability, Set.of(), sizes, Map.of(), Set.of());
    }

    private static String reason(GeneratedClassPruningPlan.PruningPlan plan, String internalName) {
        return plan.entries().stream()
                .filter(e -> e.internalName().equals(internalName))
                .findFirst().orElseThrow().reason();
    }

    private Path root(String name) throws IOException {
        Path root = tempDir.resolve(name);
        Files.createDirectories(root);
        return root;
    }

    private java.util.Map<String, Path> classFiles() {
        return Map.ofEntries(
                Map.entry("app/Main", root0().resolve("app/Main.class")),
                Map.entry("jmoa/runtime/JmoaDirectAdapters$Used", root0().resolve("jmoa/runtime/JmoaDirectAdapters$Used.class")),
                Map.entry("jmoa/runtime/JmoaDirectAdapters$Stale", root0().resolve("jmoa/runtime/JmoaDirectAdapters$Stale.class")),
                Map.entry("jmoa/runtime/JmoaDirectAdapters$Outside", outside0().resolve("jmoa/runtime/JmoaDirectAdapters$Outside.class")));
    }

    private Path root0() { return tempDir.resolve("root"); }
    private Path outside0() { return tempDir.resolve("outside"); }

    private static void writeClass(Path root, String internalName, List<String> poolStrings) throws IOException {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V22, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "m", "()V", null, null);
        method.visitCode();
        for (String value : poolStrings) {
            method.visitLdcInsn(value);
            method.visitInsn(Opcodes.POP);
        }
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        Path file = root.resolve(internalName + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, writer.toByteArray());
    }
}
