package com.yourorg.jmoa.plugin.generated;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.scanner.ClassFileWalker;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public final class GeneratedClassInventoryScanner {

    private static final String METADATA_VERSION = "v2-a1-inventory";

    private final GeneratedClassClassifier classifier;
    private final GeneratedClassSafetyModel safetyModel;

    public GeneratedClassInventoryScanner() {
        this(new GeneratedClassClassifier(), new GeneratedClassSafetyModel());
    }

    GeneratedClassInventoryScanner(GeneratedClassClassifier classifier, GeneratedClassSafetyModel safetyModel) {
        this.classifier = classifier;
        this.safetyModel = safetyModel;
    }

    public GeneratedClassInventory scan(List<ClassRootDescriptor> classRoots, List<File> jarFiles) throws IOException {
        List<GeneratedClassRecord> records = new ArrayList<>();
        if (classRoots != null) {
            for (ClassRootDescriptor root : classRoots) {
                scanDirectoryRoot(root, records);
            }
        }
        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                if (jarFile != null && jarFile.exists() && jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
                    scanJarFile(jarFile, records);
                }
            }
        }
        return buildInventory(records);
    }

    private void scanDirectoryRoot(ClassRootDescriptor root, List<GeneratedClassRecord> records) throws IOException {
        if (root == null || root.rootDirectory() == null || !root.rootDirectory().isDirectory()) {
            return;
        }
        File rootDirectory = root.rootDirectory().getCanonicalFile();
        String sourceRoot = rootDirectory.getAbsolutePath();
        String artifact = artifactFor(root);
        for (File classFile : ClassFileWalker.findClassFiles(rootDirectory)) {
            String relativePath = rootDirectory.toPath()
                .relativize(classFile.getCanonicalFile().toPath())
                .toString()
                .replace(File.separatorChar, '/');
            scanClassBytes(
                Files.readAllBytes(classFile.toPath()),
                sourceRoot,
                relativePath,
                artifact,
                records
            );
        }
    }

    private void scanJarFile(File jarFile, List<GeneratedClassRecord> records) throws IOException {
        try (JarInputStream jar = new JarInputStream(new FileInputStream(jarFile))) {
            scanJarStream(jar, jarFile.getCanonicalPath(), jarFile.getName(), records);
        }
    }

    private void scanNestedJar(byte[] jarBytes, String sourceRoot, String artifact, List<GeneratedClassRecord> records) throws IOException {
        try (JarInputStream nested = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            scanJarStream(nested, sourceRoot, artifact, records);
        }
    }

    private void scanJarStream(
        JarInputStream jar,
        String sourceRoot,
        String artifact,
        List<GeneratedClassRecord> records
    ) throws IOException {
        JarEntry entry;
        while ((entry = jar.getNextJarEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (name.endsWith(".class")) {
                scanClassBytes(jar.readAllBytes(), sourceRoot, name, artifact, records);
            } else if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
                scanNestedJar(jar.readAllBytes(), sourceRoot + "!/" + name, simpleFileName(name), records);
            }
        }
    }

    private void scanClassBytes(
        byte[] classBytes,
        String sourceRoot,
        String sourcePath,
        String artifact,
        List<GeneratedClassRecord> records
    ) throws IOException {
        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);
            GeneratedClassClassification classification = classifier.classify(classNode);
            records.add(new GeneratedClassRecord(
                classNode.name == null ? "" : classNode.name.replace('/', '.'),
                classNode.name,
                sourceRoot,
                sourcePath,
                artifact,
                classification.family(),
                classFlags(classNode.access),
                classNode.methods == null ? 0 : classNode.methods.size(),
                syntheticMethodCount(classNode),
                bridgeMethodCount(classNode),
                lambdaMethodCount(classNode),
                classBytes.length,
                reader.getItemCount(),
                invokedynamicCount(classNode),
                classification.indicators(),
                safetyModel.riskFor(classification),
                null,
                classification.generatedLike()
            ));
        } catch (RuntimeException e) {
            throw new IOException("Failed to scan generated-class inventory entry " + sourcePath + " from " + sourceRoot, e);
        }
    }

    private GeneratedClassInventory buildInventory(List<GeneratedClassRecord> records) {
        List<GeneratedClassRecord> sortedRecords = records.stream()
            .sorted(Comparator
                .comparing(GeneratedClassRecord::family)
                .thenComparing(GeneratedClassRecord::className)
                .thenComparing(GeneratedClassRecord::sourcePath))
            .toList();
        Map<GeneratedClassFamily, List<GeneratedClassRecord>> byFamily = new EnumMap<>(GeneratedClassFamily.class);
        for (GeneratedClassFamily family : GeneratedClassFamily.values()) {
            byFamily.put(family, new ArrayList<>());
        }
        for (GeneratedClassRecord record : sortedRecords) {
            byFamily.computeIfAbsent(record.family(), ignored -> new ArrayList<>()).add(record);
        }
        List<GeneratedClassFamilySummary> familyBreakdown = new ArrayList<>();
        for (Map.Entry<GeneratedClassFamily, List<GeneratedClassRecord>> entry : byFamily.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            familyBreakdown.add(summary(entry.getKey(), entry.getValue()));
        }
        return new GeneratedClassInventory(
            METADATA_VERSION,
            Instant.now().toString(),
            sortedRecords.size(),
            (int) sortedRecords.stream().filter(GeneratedClassRecord::generatedLike).count(),
            sortedRecords.stream().mapToLong(GeneratedClassRecord::classFileBytes).sum(),
            familyBreakdown,
            sortedRecords
        );
    }

    private GeneratedClassFamilySummary summary(GeneratedClassFamily family, List<GeneratedClassRecord> records) {
        Map<String, Long> artifactCounts = new LinkedHashMap<>();
        for (GeneratedClassRecord record : records) {
            artifactCounts.merge(record.artifact(), 1L, Long::sum);
        }
        return new GeneratedClassFamilySummary(
            family,
            records.size(),
            (int) records.stream().filter(GeneratedClassRecord::generatedLike).count(),
            records.stream().mapToLong(GeneratedClassRecord::classFileBytes).sum(),
            records.stream().mapToInt(GeneratedClassRecord::methodCount).sum(),
            records.stream().mapToInt(GeneratedClassRecord::syntheticMethodCount).sum(),
            records.stream().mapToInt(GeneratedClassRecord::bridgeMethodCount).sum(),
            records.stream().mapToInt(GeneratedClassRecord::lambdaMethodCount).sum(),
            records.stream().mapToInt(GeneratedClassRecord::invokedynamicCount).sum(),
            artifactCounts
        );
    }

    private static String artifactFor(ClassRootDescriptor root) {
        String path = root.rootDirectory() == null ? "" : root.rootDirectory().getAbsolutePath().replace('\\', '/');
        if (path.contains("/spring-aot/main/classes")) {
            return "spring-aot";
        }
        ClassRootKind kind = root.kind();
        if (kind == ClassRootKind.PROJECT_OUTPUT) {
            return "application";
        }
        if (kind == ClassRootKind.EXPANDED_DEPENDENCY) {
            return "expanded-dependency";
        }
        return "additional-directory";
    }

    private static List<String> classFlags(int access) {
        List<String> flags = new ArrayList<>();
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            flags.add("ACC_SYNTHETIC");
        }
        if ((access & Opcodes.ACC_INTERFACE) != 0) {
            flags.add("ACC_INTERFACE");
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            flags.add("ACC_ABSTRACT");
        }
        if ((access & Opcodes.ACC_ANNOTATION) != 0) {
            flags.add("ACC_ANNOTATION");
        }
        if ((access & Opcodes.ACC_ENUM) != 0) {
            flags.add("ACC_ENUM");
        }
        return flags;
    }

    private static int syntheticMethodCount(ClassNode classNode) {
        if (classNode.methods == null) {
            return 0;
        }
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_SYNTHETIC) != 0) {
                count++;
            }
        }
        return count;
    }

    private static int bridgeMethodCount(ClassNode classNode) {
        if (classNode.methods == null) {
            return 0;
        }
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_BRIDGE) != 0) {
                count++;
            }
        }
        return count;
    }

    private static int lambdaMethodCount(ClassNode classNode) {
        if (classNode.methods == null) {
            return 0;
        }
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if (method.name != null && method.name.startsWith("lambda$")) {
                count++;
            }
        }
        return count;
    }

    private static int invokedynamicCount(ClassNode classNode) {
        if (classNode.methods == null) {
            return 0;
        }
        int count = 0;
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof InvokeDynamicInsnNode) {
                    count++;
                }
            }
        }
        return count;
    }

    private static String simpleFileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
