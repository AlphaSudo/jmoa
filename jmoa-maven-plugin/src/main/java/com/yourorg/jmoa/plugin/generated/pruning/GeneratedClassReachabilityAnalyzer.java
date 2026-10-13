package com.yourorg.jmoa.plugin.generated.pruning;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Track 14A reachability analysis for JMOA-generated classes.
 *
 * <p>Every output root is scanned after the final weave/self-heal pass. For
 * each classfile the full constant pool is walked and every CONSTANT_Utf8
 * entry is treated as a potential reference; this conservatively covers
 * descriptors, signatures, BootstrapMethods arguments, nest/enclosing
 * metadata, and annotations, because all of them are Utf8 constants.
 * Resource files (service descriptors, Spring indexes, reflection allowlists)
 * are scanned as text. A generated class is referenced when any Utf8 entry or
 * resource line contains its exact internal name as a substring — this
 * over-keeps (for example when a nested name contains the outer name), which
 * is the only safe direction for a pruner.</p>
 */
public final class GeneratedClassReachabilityAnalyzer {

    public record AnalysisRequest(
            List<Path> outputRoots,
            Set<String> generatedInternalNamePrefixes,
            Set<String> explicitRuntimeEntryPoints,
            List<Path> resourceFiles) {
    }

    public record GeneratedClassReachability(
            Set<String> generatedClasses,
            Map<String, ClassReferenceSet> referencesByGeneratedClass,
            Map<String, Set<String>> resourceReferencesByGeneratedClass,
            Set<String> duplicateGeneratedClasses) {

        public boolean isReferenced(String internalName) {
            ClassReferenceSet classRefs = referencesByGeneratedClass.get(internalName);
            boolean classReferenced = classRefs != null && !classRefs.referencingClasses().isEmpty();
            Set<String> resourceRefs = resourceReferencesByGeneratedClass.get(internalName);
            boolean resourceReferenced = resourceRefs != null && !resourceRefs.isEmpty();
            return classReferenced || resourceReferenced;
        }
    }

    public record ClassReferenceSet(Set<String> referencingClasses) {
        public ClassReferenceSet {
            referencingClasses = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(referencingClasses));
        }
    }

    public GeneratedClassReachability analyze(AnalysisRequest request) throws IOException {
        Map<String, Path> classFiles = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (Path root : request.outputRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                for (Path file : stream.filter(f -> f.toString().endsWith(".class")).toList()) {
                    String internalName = internalNameOf(root, file);
                    if (internalName == null) {
                        continue;
                    }
                    if (classFiles.put(internalName, file) != null) {
                        duplicates.add(internalName);
                    }
                }
            }
        }
        Set<String> generated = new TreeSet<>();
        for (String internalName : classFiles.keySet()) {
            for (String prefix : request.generatedInternalNamePrefixes()) {
                if (internalName.equals(prefix) || internalName.startsWith(prefix + "$")
                        || internalName.startsWith(prefix + "/")) {
                    generated.add(internalName);
                    break;
                }
            }
        }

        Map<String, Set<String>> referencingClasses = new LinkedHashMap<>();
        Map<String, Set<String>> referencingResources = new LinkedHashMap<>();
        for (String name : generated) {
            referencingClasses.put(name, new LinkedHashSet<>());
            referencingResources.put(name, new LinkedHashSet<>());
        }

        for (Map.Entry<String, Path> entry : classFiles.entrySet()) {
            String referencingClass = entry.getKey();
            // Chapter-29 F1 fix: explicit runtime entry points are the
            // strongest scan roots — their outgoing references are scanned
            // like every other class's so classes used only by an entry
            // point are correctly kept.
            Set<String> utf8Values = constantPoolUtf8Values(entry.getValue());
            for (String candidate : generated) {
                if (candidate.equals(referencingClass)) {
                    continue;
                }
                for (String value : utf8Values) {
                    if (value.contains(candidate)) {
                        referencingClasses.get(candidate).add(referencingClass);
                        break;
                    }
                }
            }
        }

        for (Path resource : request.resourceFiles()) {
            if (!Files.isRegularFile(resource)) {
                continue;
            }
            String text = Files.readString(resource, StandardCharsets.UTF_8);
            String resourceName = resource.toString();
            for (String candidate : generated) {
                if (text.contains(candidate) || text.contains(candidate.replace('/', '.'))) {
                    referencingResources.get(candidate).add(resourceName);
                }
            }
        }

        Map<String, ClassReferenceSet> referenceMap = new LinkedHashMap<>();
        referencingClasses.forEach((name, refs) -> referenceMap.put(name, new ClassReferenceSet(new LinkedHashSet<>(refs))));
        return new GeneratedClassReachability(
                generated,
                referenceMap,
                referencingResources,
                duplicates);
    }

    static String internalNameOf(Path root, Path classFile) {
        Path relative = root.relativize(classFile);
        String name = relative.toString().replace('\\', '/');
        if (name.endsWith(".class")) {
            return name.substring(0, name.length() - ".class".length());
        }
        return null;
    }

    static Set<String> constantPoolUtf8Values(Path classFile) throws IOException {
        Set<String> values = new LinkedHashSet<>();
        byte[] bytes;
        try (InputStream in = Files.newInputStream(classFile)) {
            bytes = in.readAllBytes();
        }
        if (bytes.length < 10 || (bytes[0] & 0xFF) != 0xCA || (bytes[1] & 0xFF) != 0xFE) {
            throw new IOException("Not a classfile: " + classFile);
        }
        int constantPoolCount = ((bytes[8] & 0xFF) << 8) | (bytes[9] & 0xFF);
        int offset = 10;
        for (int index = 1; index < constantPoolCount; index++) {
            int tag = bytes[offset] & 0xFF;
            offset++;
            switch (tag) {
                case 1 -> { // CONSTANT_Utf8
                    int length = ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
                    values.add(new String(bytes, offset + 2, length, StandardCharsets.UTF_8));
                    offset += 2 + length;
                }
                case 7, 8, 16, 19, 20 -> offset += 2; // Class, String, MethodType, Module, Package
                case 15 -> offset += 3; // MethodHandle
                case 3, 4 -> offset += 4; // Integer, Float
                case 5, 6 -> { // Long, Double: two slots
                    offset += 8;
                    index++;
                }
                case 9, 10, 11, 12, 17, 18 -> offset += 4; // refs, NameAndType, Dynamic, InvokeDynamic
                default -> throw new IOException("Unknown constant pool tag " + tag + " in " + classFile);
            }
        }
        return values;
    }
}
