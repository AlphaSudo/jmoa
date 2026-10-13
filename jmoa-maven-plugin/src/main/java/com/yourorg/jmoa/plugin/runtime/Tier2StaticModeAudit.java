package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.filter.LambdaFilterDecision;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Single-scan static audit for every Tier-2 layout mode. It consumes the exact
 * filtered PetClinic decisions and frozen physical-root ordinals already held
 * by the Mojo; it never mutates output roots and never reads runtime/PSS data.
 */
public final class Tier2StaticModeAudit {

    public record OutputOrigin(int rootOrdinal, String root) {
    }

    public record AdapterAudit(
            String internalName,
            long logicalBytes,
            String sha256,
            int constantPoolEntries,
            int methodCount,
            List<String> siteKeys,
            List<OutputOrigin> outputOrigins) {
        public AdapterAudit {
            siteKeys = List.copyOf(siteKeys);
            outputOrigins = List.copyOf(outputOrigins);
        }

        public int physicalCopies() {
            return outputOrigins.size();
        }

        public long physicalBytes() {
            return logicalBytes * physicalCopies();
        }
    }

    public record ModeAudit(
            String mode,
            boolean compact,
            int tier2Sites,
            int logicalAdapters,
            int physicalEmittedClasses,
            int multiRootAdapters,
            long logicalClassBytes,
            long physicalClassBytes,
            long constantPoolEntries,
            long methodCount,
            boolean collisionFree,
            boolean generatedReferenceClosure,
            boolean byteDeterministic,
            String deterministicDigestSha256,
            List<AdapterAudit> adapters) {
        public ModeAudit {
            adapters = List.copyOf(adapters);
        }
    }

    public record AuditReport(
            String schemaVersion,
            int tier2Sites,
            int physicalRootCount,
            Map<Integer, String> physicalRoots,
            List<ModeAudit> modes,
            List<String> boundaries) {
        public AuditReport {
            physicalRoots = Collections.unmodifiableMap(new LinkedHashMap<>(physicalRoots));
            modes = List.copyOf(modes);
            boundaries = List.copyOf(boundaries);
        }
    }

    private enum Mode {
        CURRENT,
        PACKAGE,
        PACKAGE_SAM,
        PACKAGE_SIGNATURE,
        ROOT_PACKAGE_SIGNATURE
    }

    public static AuditReport build(
            List<LambdaFilterDecision> decisions,
            Map<String, Integer> rootOrdinalBySiteKey,
            Map<Integer, String> physicalRoots,
            int classFileVersion) {
        List<LambdaFilterDecision> ordered = decisions.stream()
                .sorted(java.util.Comparator.comparing(decision -> decision.meta().siteKey())).toList();
        if (ordered.stream().anyMatch(decision -> !rootOrdinalBySiteKey.containsKey(decision.meta().siteKey()))) {
            throw new IllegalArgumentException("Every Tier-2 decision must have a frozen physical-root ordinal");
        }
        List<ModeAudit> modes = List.of(
                audit(Mode.CURRENT, false, ordered, rootOrdinalBySiteKey, physicalRoots, classFileVersion),
                audit(Mode.PACKAGE, false, ordered, rootOrdinalBySiteKey, physicalRoots, classFileVersion),
                audit(Mode.PACKAGE_SAM, false, ordered, rootOrdinalBySiteKey, physicalRoots, classFileVersion),
                audit(Mode.PACKAGE_SAM, true, ordered, rootOrdinalBySiteKey, physicalRoots, classFileVersion),
                audit(Mode.PACKAGE_SIGNATURE, false, ordered, rootOrdinalBySiteKey, physicalRoots, classFileVersion),
                audit(Mode.PACKAGE_SIGNATURE, true, ordered, rootOrdinalBySiteKey, physicalRoots, classFileVersion),
                audit(Mode.ROOT_PACKAGE_SIGNATURE, true, ordered, rootOrdinalBySiteKey, physicalRoots, classFileVersion));
        return new AuditReport(
                "jmoa-tier2-static-mode-audit-v1",
                ordered.size(),
                physicalRoots.size(),
                new TreeMap<>(physicalRoots),
                modes,
                List.of(
                        "static generation audit only: no classes are written and no service runtime is launched",
                        "physical copies are projected from the exact site-to-root map used by the production Mojo",
                        "selection must use predeclared static/mechanistic rules; PSS is not an input"));
    }

    private static ModeAudit audit(
            Mode mode,
            boolean compact,
            List<LambdaFilterDecision> decisions,
            Map<String, Integer> rootOrdinalBySiteKey,
            Map<Integer, String> physicalRoots,
            int classFileVersion) {
        List<Tier2AdapterArtifact> first = generate(mode, compact, decisions, rootOrdinalBySiteKey, classFileVersion);
        List<Tier2AdapterArtifact> second = generate(mode, compact, decisions, rootOrdinalBySiteKey, classFileVersion);
        boolean deterministic = first.size() == second.size();
        for (int index = 0; deterministic && index < first.size(); index++) {
            deterministic = first.get(index).internalName().equals(second.get(index).internalName())
                    && Arrays.equals(first.get(index).classBytes(), second.get(index).classBytes());
        }

        Set<String> names = new LinkedHashSet<>();
        boolean collisionFree = first.stream().allMatch(artifact -> names.add(artifact.internalName()));
        Map<String, Set<Integer>> rootsByAdapter = new LinkedHashMap<>();
        Map<String, Set<String>> siteKeysByAdapter = new LinkedHashMap<>();
        boolean closure = true;
        for (LambdaFilterDecision decision : decisions) {
            List<String> matches = first.stream()
                    .map(Tier2AdapterArtifact::internalName)
                    .filter(name -> matches(mode, compact, decision, rootOrdinalBySiteKey, name)).toList();
            if (matches.size() != 1) {
                closure = false;
                continue;
            }
            rootsByAdapter.computeIfAbsent(matches.getFirst(), ignored -> new LinkedHashSet<>())
                    .add(rootOrdinalBySiteKey.get(decision.meta().siteKey()));
            siteKeysByAdapter.computeIfAbsent(matches.getFirst(), ignored -> new LinkedHashSet<>())
                    .add(decision.meta().siteKey());
        }

        List<AdapterAudit> adapters = new ArrayList<>();
        StringBuilder digestInput = new StringBuilder(mode.name()).append('|').append(compact).append('\n');
        for (Tier2AdapterArtifact artifact : first) {
            String hash = sha256(artifact.classBytes());
            ClassReader reader = new ClassReader(artifact.classBytes());
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            List<OutputOrigin> origins = rootsByAdapter.getOrDefault(artifact.internalName(), Set.of()).stream()
                    .sorted()
                    .map(ordinal -> new OutputOrigin(ordinal, physicalRoots.getOrDefault(ordinal, "UNKNOWN_ROOT")))
                    .toList();
            AdapterAudit adapter = new AdapterAudit(
                    artifact.internalName(), artifact.classBytes().length, hash,
                    Math.max(0, reader.getItemCount() - 1), node.methods.size(),
                    siteKeysByAdapter.getOrDefault(artifact.internalName(), Set.of()).stream().sorted().toList(), origins);
            adapters.add(adapter);
            digestInput.append(adapter.internalName()).append('|').append(adapter.sha256()).append('|');
            for (OutputOrigin origin : adapter.outputOrigins()) {
                digestInput.append(origin.rootOrdinal()).append(':').append(origin.root()).append(',');
            }
            digestInput.append('|');
            for (String siteKey : adapter.siteKeys()) digestInput.append(siteKey).append(',');
            digestInput.append('\n');
        }
        int physicalCopies = adapters.stream().mapToInt(AdapterAudit::physicalCopies).sum();
        int multiRoot = (int) adapters.stream().filter(adapter -> adapter.physicalCopies() > 1).count();
        return new ModeAudit(
                mode.name(), compact, decisions.size(), adapters.size(), physicalCopies, multiRoot,
                adapters.stream().mapToLong(AdapterAudit::logicalBytes).sum(),
                adapters.stream().mapToLong(AdapterAudit::physicalBytes).sum(),
                adapters.stream().mapToLong(AdapterAudit::constantPoolEntries).sum(),
                adapters.stream().mapToLong(AdapterAudit::methodCount).sum(),
                collisionFree, closure && adapters.stream().allMatch(adapter -> adapter.physicalCopies() > 0),
                deterministic, sha256(digestInput.toString().getBytes(StandardCharsets.UTF_8)), adapters);
    }

    private static List<Tier2AdapterArtifact> generate(
            Mode mode,
            boolean compact,
            List<LambdaFilterDecision> decisions,
            Map<String, Integer> rootOrdinalBySiteKey,
            int classFileVersion) {
        Tier2PackageAdapterGenerator generator = new Tier2PackageAdapterGenerator(compact);
        return switch (mode) {
            case CURRENT -> decisions.stream().map(decision -> generator.generate(decision, classFileVersion)).toList();
            case PACKAGE -> generator.generatePackageAdapters(decisions, classFileVersion);
            case PACKAGE_SAM -> generator.generatePackageSamAdapters(decisions, classFileVersion);
            case PACKAGE_SIGNATURE -> generator.generatePackageSignatureAdapters(decisions, classFileVersion);
            case ROOT_PACKAGE_SIGNATURE -> generator.generateRootPackageSignatureAdapters(
                    decisions, rootOrdinalBySiteKey, classFileVersion);
        };
    }

    private static boolean matches(
            Mode mode,
            boolean compact,
            LambdaFilterDecision decision,
            Map<String, Integer> rootOrdinalBySiteKey,
            String artifactName) {
        return switch (mode) {
            case CURRENT -> artifactName.equals(Tier2AdapterNamingStrategy.internalName(decision.meta()));
            case PACKAGE -> artifactName.equals(Tier2AdapterNamingStrategy.packageOnlyInternalName(decision.meta()))
                    || artifactName.equals(Tier2AdapterNamingStrategy.packageSamInternalName(decision.meta()));
            case PACKAGE_SAM -> artifactName.equals(compact
                    ? Tier2AdapterNamingStrategy.compactPackageSamInternalName(decision.meta())
                    : Tier2AdapterNamingStrategy.packageSamInternalName(decision.meta()));
            case PACKAGE_SIGNATURE -> artifactName.equals(compact
                    ? Tier2AdapterNamingStrategy.compactPackageSignatureInternalName(decision.meta())
                    : Tier2AdapterNamingStrategy.packageSignatureInternalName(decision.meta()));
            case ROOT_PACKAGE_SIGNATURE -> artifactName.equals(Tier2AdapterNamingStrategy.rootPackageSignatureInternalName(
                    decision.meta(), rootOrdinalBySiteKey.get(decision.meta().siteKey()), compact));
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Tier2StaticModeAudit() {
    }
}
