package com.yourorg.jmoa.plugin.weave;

import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates that every runtime class root that contains rewritten classes
 * referencing a {@code JmoaPkgAdapters$*} adapter class also contains the
 * referenced adapter class file.
 *
 * <p>This is the build-time guard for the PACKAGE_SAM / PACKAGE /
 * PACKAGE_SIGNATURE multi-root adapter placement fix (Phase 32K-F1): if a
 * consolidated adapter was only emitted into the first matching root (the old
 * buggy behaviour), the rewritten class in a second root would reference an
 * adapter that is missing from that root and throw
 * {@code ClassNotFoundException} / {@code NoClassDefFoundError} at runtime.
 *
 * <p>A "root" here is a class output directory (e.g. {@code target/classes},
 * {@code target/spring-aot/main/classes}, or an expanded-dependency directory
 * that will later be re-packaged into an optimized {@code *-jmoa.jar}). The
 * validator treats each root as an independent class-resolution scope, matching
 * how the JVM's nested-jar classloader resolves Spring Boot fat-jar contents.
 */
public final class AdapterReferenceValidator {

    private static final String ADAPTER_BINARY_PREFIX = "JmoaPkgAdapters$";

    private final Log log;

    public AdapterReferenceValidator(Log log) {
        this.log = log;
    }

    /**
     * Validate every root. Returns a result; the caller decides whether a
     * non-empty {@link Result#missingReferences()} should fail the build.
     */
    public Result validate(List<File> roots) throws IOException {
        List<RootReport> reports = new ArrayList<>();
        for (File root : roots) {
            reports.add(validateRoot(root));
        }
        return new Result(reports);
    }

    private RootReport validateRoot(File root) throws IOException {
        Set<String> presentAdapters = new TreeSet<>();
        Map<String, Set<String>> referencedToReferencers = new LinkedHashMap<>();

        if (root != null && root.isDirectory()) {
            final File rootFinal = root;
            Files.walk(root.toPath())
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".class"))
                .forEach(path -> {
                    String internalName = rootFinal.toPath().relativize(path)
                        .toString().replace(File.separatorChar, '/');
                    String binaryName = internalName.replace('/', '.');
                    String internalNameNoSuffix = internalName.endsWith(".class")
                        ? internalName.substring(0, internalName.length() - ".class".length())
                        : internalName;
                    if (internalNameNoSuffix.contains(ADAPTER_BINARY_PREFIX)) {
                        presentAdapters.add(internalNameNoSuffix);
                    }
                    try {
                        byte[] bytes = Files.readAllBytes(path);
                        Set<String> refs = extractAdapterReferences(bytes);
                        if (!refs.isEmpty()) {
                            for (String ref : refs) {
                                referencedToReferencers
                                    .computeIfAbsent(ref, k -> new LinkedHashSet<>())
                                    .add(binaryName);
                            }
                        }
                    } catch (IOException e) {
                        log.warn("Could not read class for adapter validation: " + path
                            + " (" + e.getMessage() + ")");
                    }
                });
        }

        List<MissingReference> missing = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : referencedToReferencers.entrySet()) {
            String referencedInternal = entry.getKey();
            if (!presentAdapters.contains(referencedInternal)) {
                missing.add(new MissingReference(referencedInternal, List.copyOf(entry.getValue())));
            }
        }
        return new RootReport(root,
            List.copyOf(new ArrayList<>(presentAdapters)),
            List.copyOf(new ArrayList<>(referencedToReferencers.keySet())),
            missing);
    }

    /**
     * Collects every distinct adapter internal name referenced by a class, by
     * scanning its constant pool. A rewritten class may reference a
     * {@code JmoaPkgAdapters$*} adapter through a {@code CONSTANT_Class} entry
     * (tag 7) whose Utf8 name is the internal name, or through a
     * {@code CONSTANT_Utf8} entry (tag 1) holding a field/method descriptor
     * such as {@code Lorg/.../JmoaPkgAdapters$Supplier;}. To catch both forms,
     * this walks every Utf8 entry and extracts internal names containing
     * {@code JmoaPkgAdapters$}. The walk is the standard forward scan of the
     * constant pool (JVMS §4.4.5), correctly handling the two-slot Long/Double
     * entries.
     */
    private Set<String> extractAdapterReferences(byte[] classBytes) {
        Set<String> refs = new LinkedHashSet<>();
        int count = readU2(classBytes, 8);
        int offset = 10; // first item starts right after the count
        for (int i = 1; i < count; i++) {
            if (offset + 1 > classBytes.length) {
                break;
            }
            int tag = classBytes[offset] & 0xFF;
            if (tag == 1) { // Utf8
                int len = readU2(classBytes, offset + 1);
                if (offset + 3 + len <= classBytes.length) {
                    String s = new String(classBytes, offset + 3, len,
                        java.nio.charset.StandardCharsets.UTF_8);
                    extractAdapterNamesFromString(s, refs);
                }
            }
            int advance = advanceForTag(classBytes, offset, tag);
            if (advance < 0) {
                return refs;
            }
            offset += advance;
            if (tag == 5 || tag == 6) {
                i++; // Long/Double occupy two constant-pool slots
            }
        }
        return refs;
    }

    private int advanceForTag(byte[] b, int offset, int tag) {
        switch (tag) {
            case 1: return 3 + readU2(b, offset + 1);
            case 3: case 4: return 5;
            case 5: case 6: return 9;
            case 7: case 8: case 16: case 19: case 20: return 3;
            case 9: case 10: case 11: case 12: case 17: case 18: return 5;
            case 15: return 4;
            default: return -1;
        }
    }

    /**
     * Pulls adapter internal names out of a Utf8 value, handling both the bare
     * Class-name form ({@code pkg/JmoaPkgAdapters$X}) and the descriptor form
     * ({@code ...Lpkg/JmoaPkgAdapters$X;...}). Descriptor occurrences are wrapped
     * as {@code L...;}; a leading descriptor {@code L} and trailing {@code ;}
     * are stripped so the result matches the bare internal name.
     */
    private void extractAdapterNamesFromString(String s, Set<String> refs) {
        int idx = 0;
        while (true) {
            int hit = s.indexOf(ADAPTER_BINARY_PREFIX, idx);
            if (hit < 0) {
                return;
            }
            int start = hit;
            while (start > 0 && isInternalNameChar(s.charAt(start - 1))) {
                start--;
            }
            int end = hit + ADAPTER_BINARY_PREFIX.length();
            while (end < s.length() && isInternalNameChar(s.charAt(end))) {
                end++;
            }
            String name = s.substring(start, end);
            // Strip descriptor markers. In a descriptor the object-type prefix 'L'
            // is immediately preceded by a non-internal-name char, so it is the
            // first captured char. Guard: only strip when removing 'L' leaves a
            // name whose first segment is at least 2 chars (a real package
            // segment), avoiding false strips on a package literally named "L".
            if (name.length() > 2 && name.charAt(0) == 'L'
                && name.indexOf('/') > 1
                && Character.isLetter(name.charAt(1))) {
                name = name.substring(1);
            }
            if (name.endsWith(";")) {
                name = name.substring(0, name.length() - 1);
            }
            refs.add(name);
            idx = end;
        }
    }

    private static boolean isInternalNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_' || c == '$' || c == '/';
    }

    private static int readU2(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    // ---- result types ----

    public record Result(List<RootReport> roots) {
        public List<MissingReference> missingReferences() {
            List<MissingReference> all = new ArrayList<>();
            for (RootReport r : roots) {
                all.addAll(r.missing());
            }
            return all;
        }

        public boolean allRootsClean() {
            return missingReferences().isEmpty();
        }
    }

    public record RootReport(
        File root,
        List<String> presentAdapters,
        List<String> referencedAdapters,
        List<MissingReference> missing
    ) {
        public boolean clean() {
            return missing.isEmpty();
        }
    }

    public record MissingReference(
        String referencedAdapterInternal,
        List<String> referencingClassBinaryNames
    ) {}
}
