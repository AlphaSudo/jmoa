package com.yourorg.jmoa.plugin.generated.pruning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes a {@link GeneratedClassPruningPlan.PruningPlan}. Chapter-29 F1
 * fix: the ENTIRE plan is validated before a single file is deleted, so an
 * inconsistent plan can never cause partial deletion. The dangling-reference
 * check now walks the correct graph direction — {@code referenceEdges} on an
 * entry hold that entry's REFERRERS, so every removed entry must be checked
 * against its referrers, kept classes, entry points, and resources.
 * Deletion itself remains disabled until the safety proof passes.
 */
public final class GeneratedClassPruner {

    public record PruningResult(
            List<String> removedClasses,
            List<String> keptClasses,
            long removedBytes,
            List<String> validationNotes) {

        public PruningResult {
            removedClasses = List.copyOf(removedClasses);
            keptClasses = List.copyOf(keptClasses);
            validationNotes = List.copyOf(validationNotes);
        }
    }

    private boolean deletionEnabled = false;

    /**
     * Deletion stays disabled by default (report-only). Enabling requires an
     * explicitly reviewed dry-run report with zero unknown or dangling
     * references.
     */
    public GeneratedClassPruner withDeletionEnabled(boolean enabled) {
        this.deletionEnabled = enabled;
        return this;
    }

    public PruningResult prune(
            GeneratedClassPruningPlan.PruningPlan plan,
            Map<String, Path> classFilesByInternalName,
            List<Path> outputRoots) throws IOException {
        // ---- Phase 1: validate the complete plan without deleting anything ----
        List<String> problems = new ArrayList<>();
        for (GeneratedClassPruningPlan.PlanEntry entry : plan.entries()) {
            Path file = classFilesByInternalName.get(entry.internalName());
            if (file == null) {
                problems.add("plan references unknown class file: " + entry.internalName());
                continue;
            }
            if (!isInsideRoots(file, outputRoots)) {
                problems.add("file outside declared output roots: " + file);
            }
            if (!entry.removable()) {
                continue;
            }
            // entry.referenceEdges() holds this entry's REFERRERS. A removed
            // entry must not be referenced by anything that survives.
            for (String referrer : entry.referenceEdges()) {
                if (plan.isKept(referrer)) {
                    problems.add("kept class " + referrer + " still references removable " + entry.internalName());
                } else if (looksLikeResourcePath(referrer)) {
                    problems.add("resource " + referrer + " still references removable " + entry.internalName());
                } else if (!plan.isRemovable(referrer) && !plan.contains(referrer)) {
                    // A referrer outside the plan (for example an explicit
                    // runtime entry point) keeps its targets.
                    problems.add("external root " + referrer + " still references removable " + entry.internalName());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IOException("Pruning plan failed full pre-delete validation; nothing was deleted:"
                    + System.lineSeparator() + " - " + String.join(System.lineSeparator() + " - ", problems));
        }

        // ---- Phase 2: delete only after the whole plan validated ----
        List<String> removed = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        long removedBytes = 0L;
        for (GeneratedClassPruningPlan.PlanEntry entry : plan.entries()) {
            Path file = classFilesByInternalName.get(entry.internalName());
            if (entry.removable()) {
                kept.add(entry.internalName() + " (removable, deletion disabled)");
                if (!deletionEnabled) {
                    continue;
                }
                long size = Files.size(file);
                Files.delete(file);
                removed.add(entry.internalName());
                removedBytes += size;
            } else {
                kept.add(entry.internalName());
            }
        }
        List<String> notes = new ArrayList<>();
        notes.add(deletionEnabled
                ? "full plan validated before any deletion; deletion executed"
                : "full plan validated; deletion is disabled (report-only) until the safety proof passes");
        return new PruningResult(removed, kept, removedBytes, notes);
    }

    private static boolean looksLikeResourcePath(String value) {
        return value.contains("\\") || value.matches("^[A-Za-z]:.*") || value.endsWith(".json")
                || value.endsWith(".txt") || value.endsWith(".properties") || value.endsWith(".factories");
    }

    private static boolean isInsideRoots(Path file, List<Path> roots) {
        Path normalized = file.toAbsolutePath().normalize();
        for (Path root : roots) {
            if (normalized.startsWith(root.toAbsolutePath().normalize())) {
                return true;
            }
        }
        return false;
    }
}
