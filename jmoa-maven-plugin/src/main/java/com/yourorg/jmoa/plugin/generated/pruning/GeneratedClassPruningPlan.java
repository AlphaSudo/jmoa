package com.yourorg.jmoa.plugin.generated.pruning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic pruning plan for JMOA-generated classes only. A generated
 * class is removable when nothing outside the removable set refers to it:
 * no rewritten class, no resource, no explicit runtime entry point, and it is
 * not present as a duplicate in multiple roots. Entries are ordered by
 * internal name so the plan is byte-stable under input reordering.
 */
public final class GeneratedClassPruningPlan {

    public record PlanEntry(
            String internalName,
            boolean removable,
            String reason,
            long bytes,
            Set<String> referenceEdges,
            String sourceHash) {

        public PlanEntry {
            referenceEdges = Collections.unmodifiableSet(new LinkedHashSet<>(referenceEdges));
        }
    }

    public record PruningPlan(
            List<PlanEntry> entries,
            int removableCount,
            long removableBytes) {

        public boolean isRemovable(String internalName) {
            return entries.stream().filter(e -> e.internalName().equals(internalName))
                    .findFirst().map(PlanEntry::removable).orElse(false);
        }

        public boolean isKept(String internalName) {
            return entries.stream().filter(e -> e.internalName().equals(internalName))
                    .findFirst().map(e -> !e.removable()).orElse(false);
        }

        public boolean contains(String internalName) {
            return entries.stream().anyMatch(e -> e.internalName().equals(internalName));
        }
    }

    public static PruningPlan build(
            GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability,
            Set<String> explicitRuntimeEntryPoints,
            Map<String, Long> classBytes,
            Map<String, String> classSourceHashes,
            Set<String> retainedOrphanReferences) {
        Set<String> entryPoints = Set.copyOf(explicitRuntimeEntryPoints);
        Set<String> retained = retainedOrphanReferences == null ? Set.of() : Set.copyOf(retainedOrphanReferences);
        Set<String> generated = reachability.generatedClasses();

        // referencesByGeneratedClass is target -> referrers. Invert the
        // generated-to-generated portion, then walk from every externally
        // rooted generated class. This is the SCC-safe formulation: an
        // unreachable cycle is removable as a unit, while anything reachable
        // from an application/resource/entry-point root is kept transitively.
        Map<String, Set<String>> outgoingGeneratedEdges = new HashMap<>();
        Set<String> reachable = new LinkedHashSet<>();
        for (String name : generated) {
            outgoingGeneratedEdges.put(name, new LinkedHashSet<>());
            if (entryPoints.contains(name) || retained.contains(name)) {
                reachable.add(name);
            }
        }
        for (Map.Entry<String, GeneratedClassReachabilityAnalyzer.ClassReferenceSet> targetEntry
                : reachability.referencesByGeneratedClass().entrySet()) {
            String target = targetEntry.getKey();
            for (String referrer : targetEntry.getValue().referencingClasses()) {
                if (generated.contains(referrer)) {
                    outgoingGeneratedEdges.computeIfAbsent(referrer, ignored -> new LinkedHashSet<>()).add(target);
                } else {
                    reachable.add(target);
                }
            }
            if (!reachability.resourceReferencesByGeneratedClass().getOrDefault(target, Set.of()).isEmpty()) {
                reachable.add(target);
            }
        }
        // A duplicate may resolve differently across physical roots, so it is
        // itself a conservative root until root-local provenance proves one
        // copy unreachable.
        reachable.addAll(reachability.duplicateGeneratedClasses());
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>(reachable);
        while (!queue.isEmpty()) {
            for (String target : outgoingGeneratedEdges.getOrDefault(queue.removeFirst(), Set.of())) {
                if (reachable.add(target)) {
                    queue.addLast(target);
                }
            }
        }
        List<PlanEntry> entries = new ArrayList<>();
        int removableCount = 0;
        long removableBytes = 0L;
        // Deterministic order: plain internal-name sort.
        Map<String, GeneratedClassReachabilityAnalyzer.ClassReferenceSet> sortedRefs = new TreeMap<>(
                reachability.referencesByGeneratedClass());
        for (Map.Entry<String, GeneratedClassReachabilityAnalyzer.ClassReferenceSet> ref : sortedRefs.entrySet()) {
            String name = ref.getKey();
            Set<String> edges = new LinkedHashSet<>();
            boolean referenced = reachable.contains(name);
            for (String referencingClass : ref.getValue().referencingClasses()) {
                edges.add(referencingClass);
            }
            Set<String> resourceRefs = reachability.resourceReferencesByGeneratedClass().get(name);
            if (resourceRefs != null) {
                edges.addAll(resourceRefs);
            }
            boolean duplicate = reachability.duplicateGeneratedClasses().contains(name);
            boolean entryPoint = entryPoints.contains(name);
            boolean removable = !referenced && !duplicate && !entryPoint;
            String reason;
            if (entryPoint) {
                reason = "KEEP_EXPLICIT_RUNTIME_ENTRY_POINT";
            } else if (duplicate) {
                reason = "KEEP_DUPLICATE_IN_MULTIPLE_ROOTS";
            } else if (referenced) {
                reason = "KEEP_REFERENCED";
            } else {
                reason = "REMOVE_GENERATED_ORPHAN";
            }
            long bytes = classBytes.getOrDefault(name, 0L);
            String hash = classSourceHashes == null ? null : classSourceHashes.get(name);
            entries.add(new PlanEntry(name, removable, reason, bytes, edges, hash));
            if (removable) {
                removableCount++;
                removableBytes += bytes;
            }
        }
        return new PruningPlan(List.copyOf(entries), removableCount, removableBytes);
    }

    private GeneratedClassPruningPlan() {
    }
}
