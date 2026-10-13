package com.yourorg.jmoa.plugin.modec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

/**
 * Track T11: logical class/resource origin manifest for a hybrid classpath
 * given in effective classloader order (expanded rewritten roots before their
 * original fallback JARs). Every logical name records ALL contenders and the
 * winning origin. Ambiguity fails closed: differing ServiceLoader descriptors,
 * Spring configuration resources, module metadata, signed or sealed JARs, and
 * unapproved multi-release JARs refuse the whole manifest instead of silently
 * choosing a winner.
 */
public final class HybridOriginManifestGenerator {

    public record Origin(int classpathOrdinal, String container, long bytes, String sha256) {
    }

    public record OriginIdentity(int classpathOrdinal, String path, String kind, String contentSha256) {
    }

    public record LogicalEntry(
            String name,
            List<Origin> contenders,
            Origin winner,
            String sha256,
            String disposition,
            boolean duplicate) {
    }

    public record OriginManifest(
            String schemaVersion,
            int originCount,
            int entryCount,
            int duplicateEntryCount,
            List<LogicalEntry> entries,
            List<String> refusals,
            String orderFingerprint,
            List<OriginIdentity> origins,
            MultiReleasePolicy multiReleasePolicy,
            List<String> multiReleaseProofs) {

        public OriginManifest {
            entries = List.copyOf(entries);
            refusals = List.copyOf(refusals);
            origins = List.copyOf(origins);
            multiReleaseProofs = List.copyOf(multiReleaseProofs);
        }
    }

    /**
     * Typed multi-release policy. REFUSE (default) rejects any JAR carrying
     * META-INF/versions. JDK_FLOOR_PROOF accepts only when every version
     * floor in every origin is strictly ABOVE the frozen runtime JDK feature
     * number, so the versioned entries are provably unreachable under the
     * frozen runtime tuple; the proof (minimum floor) is recorded.
     */
    public record MultiReleasePolicy(String kind, int runtimeJdkFeature) {
        public static MultiReleasePolicy refuse() {
            return new MultiReleasePolicy("REFUSE", 0);
        }

        public static MultiReleasePolicy jdkFloorProof(int runtimeJdkFeature) {
            return new MultiReleasePolicy("JDK_FLOOR_PROOF", runtimeJdkFeature);
        }
    }

    public static OriginManifest generate(List<Path> classpathOrder) throws IOException {
        return generate(classpathOrder, MultiReleasePolicy.refuse());
    }

    public static OriginManifest generate(List<Path> classpathOrder, MultiReleasePolicy multiReleasePolicy) throws IOException {
        List<String> refusals = new ArrayList<>();
        List<String> multiReleaseProofs = new ArrayList<>();
        Map<String, List<Origin>> contenders = new LinkedHashMap<>();
        Map<String, String> contentHashes = new LinkedHashMap<>();
        Map<String, List<String>> contentVariants = new LinkedHashMap<>();

        for (int ordinal = 0; ordinal < classpathOrder.size(); ordinal++) {
            Path origin = classpathOrder.get(ordinal);
            if (Files.isDirectory(origin)) {
                inspectDirectory(ordinal, origin, contenders, contentHashes, contentVariants,
                        refusals, multiReleaseProofs, multiReleasePolicy);
            } else if (origin.toString().endsWith(".jar")) {
                inspectJar(ordinal, origin, contenders, contentHashes, contentVariants, refusals, multiReleaseProofs, multiReleasePolicy);
            } else {
                throw new IOException("Unsupported hybrid classpath origin: " + origin);
            }
        }

        List<LogicalEntry> entries = new ArrayList<>();
        int duplicates = 0;
        for (Map.Entry<String, List<Origin>> entry : contenders.entrySet()) {
            String name = entry.getKey();
            List<Origin> origins = entry.getValue();
            List<String> variants = contentVariants.get(name);
            boolean ambiguousKind = name.startsWith("META-INF/services/")
                    || name.equals("META-INF/spring.factories")
                    || name.startsWith("META-INF/spring/");
            boolean moduleMetadata = name.equals("module-info.class");
            if (moduleMetadata && origins.size() > 1) {
                // Split modules are ambiguous even with byte-identical
                // module descriptors: the module system must see exactly one.
                refusals.add("ambiguous module metadata from " + origins.size() + " origins: " + name);
                continue;
            }
            if (ambiguousKind && variants.size() > 1) {
                refusals.add("ambiguous protected resource with differing content: " + name
                        + " from " + origins.size() + " origins");
                continue;
            }
            if (!ambiguousKind && variants.size() > 1) {
                // Standard classloader shadowing for ordinary classes and
                // resources: first origin wins, contenders stay recorded.
                entries.add(new LogicalEntry(name, origins, origins.get(0), variants.get(0),
                        "WINNER_FIRST_CLASSPATH_SHADOWING", true));
                duplicates++;
                continue;
            }
            if (origins.size() > 1) {
                duplicates++;
                entries.add(new LogicalEntry(name, origins, origins.get(0), variants.get(0),
                        "DUPLICATE_IDENTICAL_CONTENT", true));
            } else {
                entries.add(new LogicalEntry(name, origins, origins.get(0), variants.get(0),
                        "SINGLE_ORIGIN", false));
            }
        }
        if (!refusals.isEmpty()) {
            throw new IOException("Hybrid origin manifest refused ("
                    + refusals.size() + " fail-closed violations):\n  - " + String.join("\n  - ", refusals));
        }
        return new OriginManifest(
                "jmoa-hybrid-origin-manifest-v1",
                classpathOrder.size(),
                entries.size(),
                duplicates,
                entries,
                refusals,
                orderFingerprint(classpathOrder),
                originIdentities(classpathOrder),
                multiReleasePolicy,
                multiReleaseProofs);
    }

    private static void inspectDirectory(int ordinal, Path root, Map<String, List<Origin>> contenders,
            Map<String, String> contentHashes, Map<String, List<String>> contentVariants,
            List<String> refusals, List<String> multiReleaseProofs, MultiReleasePolicy multiReleasePolicy) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths.filter(Files::isRegularFile).toList();
            int minimumFloor = Integer.MAX_VALUE;
            boolean hasMultiRelease = false;
            for (Path file : files) {
                String name = root.relativize(file).toString().replace('\\', '/');
                if (name.startsWith("META-INF/versions/")) {
                    hasMultiRelease = true;
                    String rest = name.substring("META-INF/versions/".length());
                    String floorText = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                    try { minimumFloor = Math.min(minimumFloor, Integer.parseInt(floorText)); }
                    catch (NumberFormatException ignored) { refusals.add("malformed expanded multi-release version directory: " + name + " in " + root); }
                }
                record(ordinal, name, root + "/!" + name, Files.readAllBytes(file),
                        contenders, contentHashes, contentVariants);
            }
            if (hasMultiRelease) {
                if ("JDK_FLOOR_PROOF".equals(multiReleasePolicy.kind())
                        && minimumFloor > multiReleasePolicy.runtimeJdkFeature()) {
                    multiReleaseProofs.add(root + ": expanded floors >= " + minimumFloor
                            + " > runtime JDK " + multiReleasePolicy.runtimeJdkFeature()
                            + "; versioned entries unreachable");
                } else {
                    refusals.add("expanded multi-release directory requires an explicit typed policy: " + root
                            + " (minimum floor " + (minimumFloor == Integer.MAX_VALUE ? "?" : minimumFloor) + ")");
                }
            }
        }
    }

    private static void inspectJar(int ordinal, Path jar, Map<String, List<Origin>> contenders,
            Map<String, String> contentHashes, Map<String, List<String>> contentVariants,
            List<String> refusals, List<String> multiReleaseProofs, MultiReleasePolicy multiReleasePolicy) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile(), false, JarFile.OPEN_READ)) {
            if (jarFile.getEntry("META-INF/MANIFEST.MF") != null) {
                Manifest manifest = jarFile.getManifest();
                String sealed = manifest == null || manifest.getMainAttributes().getValue("Sealed") == null
                        ? null : manifest.getMainAttributes().getValue("Sealed");
                if ("true".equalsIgnoreCase(sealed)) {
                    refusals.add("sealed JAR requires an explicit typed policy: " + jar);
                }
            }
            boolean hasSignature = false;
            boolean hasMultiRelease = false;
            int minMultiReleaseFloor = Integer.MAX_VALUE;
            java.util.Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                String upperName = name.toUpperCase(java.util.Locale.ROOT);
                if (upperName.startsWith("META-INF/") && (upperName.endsWith(".SF") || upperName.endsWith(".DSA") || upperName.endsWith(".RSA") || upperName.endsWith(".EC"))) {
                    hasSignature = true;
                }
                if (name.startsWith("META-INF/versions/") && name.length() > "META-INF/versions/".length()) {
                    hasMultiRelease = true;
                    String rest = name.substring("META-INF/versions/".length());
                    String floorText = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
                    try {
                        int floor = Integer.parseInt(floorText);
                        minMultiReleaseFloor = Math.min(minMultiReleaseFloor, floor);
                    } catch (NumberFormatException ignored) {
                        refusals.add("malformed multi-release version directory: " + name + " in " + jar);
                    }
                }
            }
            if (hasSignature) {
                refusals.add("signed JAR requires an explicit typed policy (signature stripping is not sufficient provenance): " + jar);
            }
            Manifest manifest = jarFile.getManifest();
            if (manifest != null && manifest.getEntries().entrySet().stream().anyMatch(section ->
                    "true".equalsIgnoreCase(section.getValue().getValue("Sealed")))) {
                refusals.add("JAR has a sealed manifest section and requires an explicit typed policy: " + jar);
            }
            if (hasMultiRelease) {
                if ("JDK_FLOOR_PROOF".equals(multiReleasePolicy.kind())
                        && minMultiReleaseFloor > multiReleasePolicy.runtimeJdkFeature()) {
                    multiReleaseProofs.add(jar + ": floors >= " + minMultiReleaseFloor
                            + " > runtime JDK " + multiReleasePolicy.runtimeJdkFeature()
                            + "; versioned entries unreachable under the frozen runtime tuple");
                } else {
                    refusals.add("multi-release JAR requires an explicit typed policy: " + jar
                            + " (minimum version floor " + (minMultiReleaseFloor == Integer.MAX_VALUE ? "?" : minMultiReleaseFloor)
                            + ", runtime JDK ceiling " + multiReleasePolicy.runtimeJdkFeature() + ")");
                }
            }
            entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes;
                try (InputStream in = jarFile.getInputStream(entry)) {
                    bytes = in.readAllBytes();
                }
                record(ordinal, entry.getName(), jar + "/!" + entry.getName(), bytes,
                        contenders, contentHashes, contentVariants);
            }
        }
    }

    private static void record(int ordinal, String name, String container, byte[] bytes,
            Map<String, List<Origin>> contenders, Map<String, String> contentHashes,
            Map<String, List<String>> contentVariants) {
        String hash = sha256(bytes);
        contenders.computeIfAbsent(name, ignored -> new ArrayList<>())
                .add(new Origin(ordinal, container, bytes.length, hash));
        contentHashes.put(name, hash);
        contentVariants.computeIfAbsent(name, ignored -> new ArrayList<>());
        if (!contentVariants.get(name).contains(hash)) {
            contentVariants.get(name).add(hash);
        }
    }

    private static String orderFingerprint(List<Path> classpathOrder) {
        StringBuilder builder = new StringBuilder();
        try {
            for (OriginIdentity origin : originIdentities(classpathOrder)) {
                builder.append(origin.classpathOrdinal()).append('|').append(origin.path()).append('|')
                        .append(origin.kind()).append('|').append(origin.contentSha256()).append('\n');
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot fingerprint hybrid classpath order", e);
        }
        return sha256(builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static List<OriginIdentity> originIdentities(List<Path> classpathOrder) throws IOException {
        List<OriginIdentity> identities = new ArrayList<>();
        for (int ordinal = 0; ordinal < classpathOrder.size(); ordinal++) {
            Path path = classpathOrder.get(ordinal).toAbsolutePath().normalize();
            String kind = Files.isDirectory(path) ? "DIRECTORY" : "JAR";
            String contentHash;
            if (Files.isDirectory(path)) {
                StringBuilder tree = new StringBuilder();
                try (Stream<Path> files = Files.walk(path)) {
                    for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                        tree.append(path.relativize(file).toString().replace('\\', '/')).append('|')
                                .append(sha256(Files.readAllBytes(file))).append('\n');
                    }
                }
                contentHash = sha256(tree.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                contentHash = sha256(Files.readAllBytes(path));
            }
            identities.add(new OriginIdentity(ordinal, path.toString(), kind, contentHash));
        }
        return List.copyOf(identities);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes)).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("sha-256 unavailable", e);
        }
    }
}
