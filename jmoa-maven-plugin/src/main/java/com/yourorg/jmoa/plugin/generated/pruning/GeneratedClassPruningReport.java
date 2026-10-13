package com.yourorg.jmoa.plugin.generated.pruning;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.ModuleVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Report-only T14 integration. The report is built from the final rewritten
 * roots. Structured ASM references drive reachability; a conservative UTF-8
 * constant-pool fallback only adds keep edges for reflective/string uses that
 * structured bytecode cannot prove. Resources and configured reflection
 * allowlists are roots. Deletion is deliberately not exposed here.
 */
public final class GeneratedClassPruningReport {

    public static final Set<String> DEFAULT_GENERATED_PREFIXES = Set.of(
            "jmoa/runtime/JmoaRuntime", "jmoa/runtime/JmoaDirectAdapters");
    public static final Set<String> DEFAULT_GENERATED_FRAGMENTS = Set.of("JmoaPkgAdapters$");

    public record ClassOrigin(int rootOrdinal, String root, String path, long bytes, String sha256) {
    }

    public record ReferenceEvidence(
            Set<String> structuredClassReferrers,
            Set<String> conservativeUtf8Referrers,
            Set<String> resourceReferrers) {
        public ReferenceEvidence {
            structuredClassReferrers = orderedSet(structuredClassReferrers);
            conservativeUtf8Referrers = orderedSet(conservativeUtf8Referrers);
            resourceReferrers = orderedSet(resourceReferrers);
        }
    }

    public record ManifestEntry(
            String internalName,
            boolean removable,
            String reason,
            long bytes,
            String sourceHash,
            Set<String> referenceEdges,
            List<ClassOrigin> origins) {
        public ManifestEntry {
            referenceEdges = orderedSet(referenceEdges);
            origins = List.copyOf(origins);
        }
    }

    public record PruningManifest(
            String schemaVersion,
            boolean deletionEnabled,
            int generatedClassCount,
            int removableCount,
            long removableBytes,
            List<ManifestEntry> entries,
            Map<String, ReferenceEvidence> referenceEvidence,
            List<String> resourceFilesScanned,
            List<String> configuredReflectionAllowlists,
            int declaredGeneratedClassCount,
            List<String> missingDeclaredGeneratedClasses,
            List<String> unaccountedGeneratedClasses,
            List<String> danglingGeneratedReferences,
            boolean safetyReviewPassed,
            List<String> notes) {
        public PruningManifest {
            entries = List.copyOf(entries);
            referenceEvidence = Collections.unmodifiableMap(new LinkedHashMap<>(referenceEvidence));
            resourceFilesScanned = List.copyOf(resourceFilesScanned);
            configuredReflectionAllowlists = List.copyOf(configuredReflectionAllowlists);
            missingDeclaredGeneratedClasses = List.copyOf(missingDeclaredGeneratedClasses);
            unaccountedGeneratedClasses = List.copyOf(unaccountedGeneratedClasses);
            danglingGeneratedReferences = List.copyOf(danglingGeneratedReferences);
            notes = List.copyOf(notes);
        }
    }

    private record PhysicalClass(String internalName, int rootOrdinal, Path root, Path path) {
    }

    public static PruningManifest build(List<File> outputRoots, Set<String> explicitRuntimeEntryPoints)
            throws IOException {
        return build(outputRoots, explicitRuntimeEntryPoints, List.of(), Set.of());
    }

    public static PruningManifest build(
            List<File> outputRoots,
            Set<String> explicitRuntimeEntryPoints,
            List<File> configuredReflectionAllowlists) throws IOException {
        return build(outputRoots, explicitRuntimeEntryPoints, configuredReflectionAllowlists, Set.of());
    }

    /**
     * Builds the report from the final output roots. {@code knownGeneratedClasses}
     * is an inventory boundary, not a reachability root: callers provide the
     * exact classes emitted by generation/weaving even when their names are
     * package-local and therefore cannot be recognized by a global prefix.
     * Only {@code explicitRuntimeEntryPoints} receives root semantics.
     */
    public static PruningManifest build(
            List<File> outputRoots,
            Set<String> explicitRuntimeEntryPoints,
            List<File> configuredReflectionAllowlists,
            Set<String> knownGeneratedClasses) throws IOException {
        List<Path> roots = outputRoots.stream().map(File::toPath)
                .map(path -> path.toAbsolutePath().normalize()).toList();
        List<PhysicalClass> allClasses = collectClasses(roots);

        Set<String> knownGenerated = knownGeneratedClasses == null
                ? Set.of()
                : Set.copyOf(knownGeneratedClasses);

        Map<String, List<PhysicalClass>> generated = new TreeMap<>();
        for (PhysicalClass physical : allClasses) {
            if (isJmoaGenerated(physical.internalName()) || knownGenerated.contains(physical.internalName())) {
                generated.computeIfAbsent(physical.internalName(), ignored -> new ArrayList<>()).add(physical);
            }
        }
        Set<String> presentClassNames = new LinkedHashSet<>();
        allClasses.stream().map(PhysicalClass::internalName).forEach(presentClassNames::add);
        Set<String> declaredGenerated = new LinkedHashSet<>(knownGenerated);
        if (explicitRuntimeEntryPoints != null) {
            declaredGenerated.addAll(explicitRuntimeEntryPoints);
        }
        List<String> missingDeclared = declaredGenerated.stream()
                .filter(name -> !presentClassNames.contains(name)).sorted().toList();
        List<String> unaccountedGenerated = generated.keySet().stream()
                .filter(name -> !declaredGenerated.contains(name)).sorted().toList();
        Set<String> missingDeclaredSet = Set.copyOf(missingDeclared);
        Set<String> danglingReferences = new LinkedHashSet<>();

        Map<String, Set<String>> structuredReferrers = new TreeMap<>();
        Map<String, Set<String>> conservativeReferrers = new TreeMap<>();
        Map<String, Set<String>> resourceReferrers = new TreeMap<>();
        for (String candidate : generated.keySet()) {
            structuredReferrers.put(candidate, new LinkedHashSet<>());
            conservativeReferrers.put(candidate, new LinkedHashSet<>());
            resourceReferrers.put(candidate, new LinkedHashSet<>());
        }
        Utf8CandidateMatcher conservativeMatcher = new Utf8CandidateMatcher(generated.keySet());

        // Exact typed references are primary. Constant-pool substring matches
        // add only conservative edges for reflection/string/bootstrap cases.
        for (PhysicalClass physical : allClasses) {
            Set<String> structured = structuredReferences(physical.path());
            Set<String> utf8 = GeneratedClassReachabilityAnalyzer.constantPoolUtf8Values(physical.path());
            for (String missing : missingDeclaredSet) {
                if (structured.contains(missing)) {
                    danglingReferences.add("class:" + physical.internalName() + "->" + missing);
                }
            }
            for (String candidate : structured) {
                if (!candidate.equals(physical.internalName()) && structuredReferrers.containsKey(candidate)) {
                    structuredReferrers.get(candidate).add(physical.internalName());
                }
            }
            for (String candidate : conservativeMatcher.matches(utf8)) {
                if (!candidate.equals(physical.internalName()) && !structured.contains(candidate)) {
                    conservativeReferrers.get(candidate).add(physical.internalName());
                }
            }
        }

        List<Path> resources = discoverResources(roots, configuredReflectionAllowlists);
        for (Path resource : resources) {
            String text = new String(Files.readAllBytes(resource), StandardCharsets.ISO_8859_1);
            for (String missing : missingDeclaredSet) {
                if (text.contains(missing) || text.contains(missing.replace('/', '.'))) {
                    danglingReferences.add("resource:" + resource.toAbsolutePath().normalize() + "->" + missing);
                }
            }
            for (String candidate : conservativeMatcher.matches(Set.of(text))) {
                resourceReferrers.get(candidate).add(resource.toAbsolutePath().normalize().toString());
            }
        }

        Map<String, GeneratedClassReachabilityAnalyzer.ClassReferenceSet> refMap = new TreeMap<>();
        Map<String, Set<String>> resourceMap = new TreeMap<>();
        Map<String, ReferenceEvidence> evidence = new TreeMap<>();
        for (String candidate : generated.keySet()) {
            Set<String> classRefs = new LinkedHashSet<>(structuredReferrers.get(candidate));
            classRefs.addAll(conservativeReferrers.get(candidate));
            refMap.put(candidate, new GeneratedClassReachabilityAnalyzer.ClassReferenceSet(orderedSet(classRefs)));
            resourceMap.put(candidate, orderedSet(resourceReferrers.get(candidate)));
            evidence.put(candidate, new ReferenceEvidence(
                    structuredReferrers.get(candidate), conservativeReferrers.get(candidate),
                    resourceReferrers.get(candidate)));
        }

        Set<String> duplicates = new LinkedHashSet<>();
        Map<String, Long> sizes = new TreeMap<>();
        Map<String, String> sourceHashes = new TreeMap<>();
        Map<String, List<ClassOrigin>> origins = new TreeMap<>();
        for (Map.Entry<String, List<PhysicalClass>> entry : generated.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.add(entry.getKey());
            }
            long bytes = 0L;
            StringBuilder combinedHashInput = new StringBuilder();
            List<ClassOrigin> classOrigins = new ArrayList<>();
            for (PhysicalClass physical : entry.getValue()) {
                long physicalBytes = Files.size(physical.path());
                String hash = sha256(Files.readAllBytes(physical.path()));
                bytes += physicalBytes;
                combinedHashInput.append(physical.rootOrdinal()).append(':').append(hash).append('\n');
                classOrigins.add(new ClassOrigin(
                        physical.rootOrdinal(), physical.root().toString(), physical.path().toString(), physicalBytes, hash));
            }
            sizes.put(entry.getKey(), bytes);
            sourceHashes.put(entry.getKey(), sha256(combinedHashInput.toString().getBytes(StandardCharsets.UTF_8)));
            origins.put(entry.getKey(), List.copyOf(classOrigins));
        }

        GeneratedClassReachabilityAnalyzer.GeneratedClassReachability reachability =
                new GeneratedClassReachabilityAnalyzer.GeneratedClassReachability(
                        orderedSet(generated.keySet()), refMap, resourceMap, orderedSet(duplicates));
        GeneratedClassPruningPlan.PruningPlan plan = GeneratedClassPruningPlan.build(
                reachability, explicitRuntimeEntryPoints, sizes, sourceHashes, Set.of());

        List<ManifestEntry> entries = plan.entries().stream().map(entry -> new ManifestEntry(
                entry.internalName(), entry.removable(), entry.reason(), entry.bytes(), entry.sourceHash(),
                entry.referenceEdges(), origins.getOrDefault(entry.internalName(), List.of()))).toList();
        List<String> dangling = danglingReferences.stream().sorted().toList();
        boolean safetyReviewPassed = missingDeclared.isEmpty()
                && unaccountedGenerated.isEmpty()
                && dangling.isEmpty();
        return new PruningManifest(
                "jmoa-generated-class-pruning-report-v3", false, generated.size(),
                plan.removableCount(), plan.removableBytes(), entries, evidence,
                resources.stream().map(path -> path.toAbsolutePath().normalize().toString()).toList(),
                configuredReflectionAllowlists.stream().map(file -> file.toPath().toAbsolutePath().normalize().toString()).toList(),
                declaredGenerated.size(), missingDeclared, unaccountedGenerated, dangling, safetyReviewPassed,
                List.of(
                        "report-only: no deletion API is invoked by the Mojo",
                        "exact generator/weave outputs are inventoried even when their names are outside global JMOA prefixes",
                        "structured ASM references drive reachability; conservative UTF-8 matches only add keep edges",
                        "conservative UTF-8 references are indexed with a deterministic multi-pattern automaton rather than candidate-by-constant scanning",
                        "unreachable generated SCCs are removable as a unit; external roots are followed transitively",
                        "safetyReviewPassed requires zero missing declared classes, unaccounted generated classes, and dangling declared-generated references",
                        "duplicates across physical roots are kept pending root-local provenance proof",
                        "AdapterReferenceValidator is rerun by the Mojo after this report is emitted"));
    }

    /**
     * Deterministic Aho-Corasick matcher for internal and dotted generated
     * class names. It preserves the former conservative substring semantics
     * while scanning each UTF-8 constant/resource once.
     */
    private static final class Utf8CandidateMatcher {
        private static final class Node {
            private final Map<Character, Node> next = new TreeMap<>();
            private final Set<String> candidates = new LinkedHashSet<>();
            private Node failure;
        }

        private final Node root = new Node();

        private Utf8CandidateMatcher(Set<String> candidates) {
            for (String candidate : candidates.stream().sorted().toList()) {
                add(candidate, candidate);
                add(candidate.replace('/', '.'), candidate);
            }
            buildFailureLinks();
        }

        private void add(String pattern, String candidate) {
            Node node = root;
            for (int index = 0; index < pattern.length(); index++) {
                node = node.next.computeIfAbsent(pattern.charAt(index), ignored -> new Node());
            }
            node.candidates.add(candidate);
        }

        private void buildFailureLinks() {
            root.failure = root;
            ArrayDeque<Node> queue = new ArrayDeque<>();
            for (Node child : root.next.values()) {
                child.failure = root;
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                Node parent = queue.remove();
                for (Map.Entry<Character, Node> edge : parent.next.entrySet()) {
                    char value = edge.getKey();
                    Node child = edge.getValue();
                    Node failure = parent.failure;
                    while (failure != root && !failure.next.containsKey(value)) {
                        failure = failure.failure;
                    }
                    if (failure.next.containsKey(value) && failure.next.get(value) != child) {
                        failure = failure.next.get(value);
                    }
                    child.failure = failure;
                    child.candidates.addAll(failure.candidates);
                    queue.add(child);
                }
            }
        }

        private Set<String> matches(Set<String> values) {
            Set<String> matches = new LinkedHashSet<>();
            for (String value : values) {
                Node node = root;
                for (int index = 0; index < value.length(); index++) {
                    char character = value.charAt(index);
                    while (node != root && !node.next.containsKey(character)) {
                        node = node.failure;
                    }
                    Node next = node.next.get(character);
                    node = next == null ? root : next;
                    matches.addAll(node.candidates);
                }
            }
            return orderedSet(matches);
        }
    }

    private static List<PhysicalClass> collectClasses(List<Path> roots) throws IOException {
        List<PhysicalClass> classes = new ArrayList<>();
        for (int ordinal = 0; ordinal < roots.size(); ordinal++) {
            Path root = roots.get(ordinal);
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                List<Path> paths = stream.filter(path -> path.toString().endsWith(".class")).sorted().toList();
                for (Path path : paths) {
                    String internalName = GeneratedClassReachabilityAnalyzer.internalNameOf(root, path);
                    if (internalName != null) {
                        classes.add(new PhysicalClass(internalName, ordinal, root, path.toAbsolutePath().normalize()));
                    }
                }
            }
        }
        return List.copyOf(classes);
    }

    private static boolean isJmoaGenerated(String internalName) {
        return DEFAULT_GENERATED_PREFIXES.stream().anyMatch(prefix -> internalName.equals(prefix)
                || internalName.startsWith(prefix + "$") || internalName.startsWith(prefix + "/"))
                || DEFAULT_GENERATED_FRAGMENTS.stream().anyMatch(internalName::contains);
    }

    private static List<Path> discoverResources(List<Path> roots, List<File> configured) throws IOException {
        Set<Path> resources = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                for (Path file : stream.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    if (isRootResource(relative)) {
                        resources.add(file.toAbsolutePath().normalize());
                    }
                }
            }
        }
        for (File configuredFile : configured) {
            if (configuredFile == null || !configuredFile.isFile()) {
                throw new IOException("Configured pruning reflection allowlist is missing: " + configuredFile);
            }
            resources.add(configuredFile.toPath().toAbsolutePath().normalize());
        }
        return resources.stream().sorted().toList();
    }

    private static boolean isRootResource(String name) {
        return name.startsWith("META-INF/services/")
                || name.equals("META-INF/spring.factories")
                || name.equals("META-INF/spring.handlers")
                || name.equals("META-INF/spring.schemas")
                || name.startsWith("META-INF/spring/")
                || name.equals("META-INF/MANIFEST.MF")
                || name.endsWith("reflect-config.json")
                || name.endsWith("reflection-config.json");
    }

    static Set<String> structuredReferences(Path classFile) throws IOException {
        Set<String> references = new LinkedHashSet<>();
        new ClassReader(Files.readAllBytes(classFile)).accept(new ReferenceCollector(references), 0);
        return orderedSet(references);
    }

    private static final class ReferenceCollector extends ClassVisitor {
        private final Set<String> refs;

        private ReferenceCollector(Set<String> refs) {
            super(Opcodes.ASM9);
            this.refs = refs;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            addInternal(superName);
            if (interfaces != null) Collections.addAll(refs, interfaces);
            addSignature(signature, false);
        }

        @Override public void visitOuterClass(String owner, String name, String descriptor) { addInternal(owner); addDescriptor(descriptor); }
        @Override public void visitNestHost(String nestHost) { addInternal(nestHost); }
        @Override public void visitNestMember(String nestMember) { addInternal(nestMember); }
        @Override public void visitPermittedSubclass(String permittedSubclass) { addInternal(permittedSubclass); }
        @Override public void visitInnerClass(String name, String outerName, String innerName, int access) { addInternal(name); addInternal(outerName); }

        @Override
        public ModuleVisitor visitModule(String name, int access, String version) {
            return new ModuleVisitor(Opcodes.ASM9) {
                @Override public void visitMainClass(String mainClass) { addInternal(mainClass); }
                @Override public void visitUse(String service) { addInternal(service); }
                @Override public void visitProvide(String service, String... providers) {
                    addInternal(service);
                    if (providers != null) Collections.addAll(refs, providers);
                }
            };
        }

        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { addDescriptor(descriptor); return annotationVisitor(); }
        @Override public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) { addDescriptor(descriptor); return annotationVisitor(); }

        @Override
        public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            addDescriptor(descriptor); addSignature(signature, true);
            return new RecordComponentVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
                @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
            };
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            addDescriptor(descriptor); addSignature(signature, true); addConstant(value);
            return new FieldVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
                @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            addDescriptor(descriptor); addSignature(signature, false);
            if (exceptions != null) Collections.addAll(refs, exceptions);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotationDefault() { return annotationVisitor(); }
                @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
                @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
                @Override public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
                @Override public AnnotationVisitor visitInsnAnnotation(int ref, TypePath path, String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
                @Override public AnnotationVisitor visitTryCatchAnnotation(int ref, TypePath path, String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
                @Override public AnnotationVisitor visitLocalVariableAnnotation(int ref, TypePath path, Label[] start, Label[] end, int[] index, String desc, boolean visible) { addDescriptor(desc); return annotationVisitor(); }
                @Override public void visitTypeInsn(int opcode, String type) { addInternal(type); }
                @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) { addInternal(owner); addDescriptor(desc); }
                @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) { addInternal(owner); addDescriptor(desc); }
                @Override public void visitInvokeDynamicInsn(String indyName, String desc, Handle bootstrap, Object... args) {
                    addDescriptor(desc); addHandle(bootstrap); if (args != null) for (Object arg : args) addConstant(arg);
                }
                @Override public void visitLdcInsn(Object value) { addConstant(value); }
                @Override public void visitMultiANewArrayInsn(String desc, int dimensions) { addDescriptor(desc); }
                @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) { addInternal(type); }
                @Override public void visitLocalVariable(String localName, String desc, String sig, Label start, Label end, int index) { addDescriptor(desc); addSignature(sig, true); }
                @Override public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
                    addFrameValues(local); addFrameValues(stack);
                }
            };
        }

        private AnnotationVisitor annotationVisitor() {
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override public void visit(String name, Object value) { addConstant(value); }
                @Override public void visitEnum(String name, String descriptor, String value) { addDescriptor(descriptor); }
                @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) { addDescriptor(descriptor); return annotationVisitor(); }
                @Override public AnnotationVisitor visitArray(String name) { return annotationVisitor(); }
            };
        }

        private void addFrameValues(Object[] values) {
            if (values == null) return;
            for (Object value : values) if (value instanceof String internalName) addInternal(internalName);
        }

        private void addConstant(Object value) {
            if (value instanceof Type type) addType(type);
            else if (value instanceof Handle handle) addHandle(handle);
            else if (value instanceof ConstantDynamic dynamic) {
                addDescriptor(dynamic.getDescriptor());
                addHandle(dynamic.getBootstrapMethod());
                for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) addConstant(dynamic.getBootstrapMethodArgument(i));
            }
        }

        private void addHandle(Handle handle) { if (handle != null) { addInternal(handle.getOwner()); addDescriptor(handle.getDesc()); } }
        private void addInternal(String name) { if (name != null && !name.isBlank()) refs.add(name); }

        private void addDescriptor(String descriptor) {
            if (descriptor == null || descriptor.isBlank()) return;
            try { addType(Type.getType(descriptor)); } catch (RuntimeException ignored) { /* conservative UTF-8 fallback covers malformed/novel forms */ }
        }

        private void addType(Type type) {
            if (type == null) return;
            if (type.getSort() == Type.ARRAY) addType(type.getElementType());
            else if (type.getSort() == Type.OBJECT) refs.add(type.getInternalName());
            else if (type.getSort() == Type.METHOD) {
                for (Type argument : type.getArgumentTypes()) addType(argument);
                addType(type.getReturnType());
            }
        }

        private void addSignature(String signature, boolean typeOnly) {
            if (signature == null || signature.isBlank()) return;
            try {
                SignatureVisitor visitor = new SignatureVisitor(Opcodes.ASM9) {
                    @Override public void visitClassType(String name) { addInternal(name); }
                };
                if (typeOnly) new SignatureReader(signature).acceptType(visitor);
                else new SignatureReader(signature).accept(visitor);
            } catch (RuntimeException ignored) { /* conservative UTF-8 fallback remains */ }
        }
    }

    private static <T> Set<T> orderedSet(Iterable<T> values) {
        LinkedHashSet<T> result = new LinkedHashSet<>();
        for (T value : values) result.add(value);
        return Collections.unmodifiableSet(result);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).toUpperCase();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private GeneratedClassPruningReport() {
    }
}
