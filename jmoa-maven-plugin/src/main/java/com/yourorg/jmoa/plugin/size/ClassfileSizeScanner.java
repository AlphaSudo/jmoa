package com.yourorg.jmoa.plugin.size;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.generated.GeneratedClassClassification;
import com.yourorg.jmoa.plugin.generated.GeneratedClassClassifier;
import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;
import com.yourorg.jmoa.plugin.generated.GeneratedClassRiskLevel;
import com.yourorg.jmoa.plugin.generated.GeneratedClassSafetyModel;
import com.yourorg.jmoa.plugin.scanner.ClassFileWalker;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public final class ClassfileSizeScanner {

    private static final String METADATA_VERSION = "v2-b1-classfile-size-profile";

    private final BytecodeSizeConfig config;
    private final GeneratedClassClassifier generatedClassifier;
    private final GeneratedClassSafetyModel safetyModel;

    public ClassfileSizeScanner(BytecodeSizeConfig config) {
        this(config, new GeneratedClassClassifier(), new GeneratedClassSafetyModel());
    }

    ClassfileSizeScanner(
        BytecodeSizeConfig config,
        GeneratedClassClassifier generatedClassifier,
        GeneratedClassSafetyModel safetyModel
    ) {
        this.config = config == null ? BytecodeSizeConfig.defaults() : config;
        this.generatedClassifier = generatedClassifier;
        this.safetyModel = safetyModel;
    }

    public ClassfileSizeProfile scan(List<ClassRootDescriptor> classRoots, List<File> jarFiles) throws IOException {
        ScanAccumulator accumulator = new ScanAccumulator();
        if (classRoots != null) {
            for (ClassRootDescriptor root : classRoots) {
                scanDirectoryRoot(root, accumulator);
            }
        }
        if (jarFiles != null) {
            for (File jarFile : jarFiles) {
                if (jarFile != null && jarFile.exists() && jarFile.isFile() && jarFile.getName().endsWith(".jar")) {
                    scanJarFile(jarFile, accumulator);
                }
            }
        }
        return buildProfile(accumulator);
    }

    private void scanDirectoryRoot(ClassRootDescriptor root, ScanAccumulator accumulator) throws IOException {
        if (root == null || root.rootDirectory() == null || !root.rootDirectory().isDirectory()) {
            return;
        }
        File rootDirectory = root.rootDirectory().getCanonicalFile();
        String sourceRoot = rootDirectory.getAbsolutePath();
        String artifact = artifactFor(root);
        String rootKind = root.kind().name();
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
                rootKind,
                accumulator
            );
        }
    }

    private void scanJarFile(File jarFile, ScanAccumulator accumulator) throws IOException {
        try (JarInputStream jar = new JarInputStream(new FileInputStream(jarFile))) {
            scanJarStream(jar, jarFile.getCanonicalPath(), jarFile.getName(), "JAR", accumulator);
        }
    }

    private void scanNestedJar(
        byte[] jarBytes,
        String sourceRoot,
        String artifact,
        ScanAccumulator accumulator
    ) throws IOException {
        try (JarInputStream nested = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            scanJarStream(nested, sourceRoot, artifact, "BOOT_INF_LIB", accumulator);
        }
    }

    private void scanJarStream(
        JarInputStream jar,
        String sourceRoot,
        String artifact,
        String rootKind,
        ScanAccumulator accumulator
    ) throws IOException {
        JarEntry entry;
        while ((entry = jar.getNextJarEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (name.endsWith(".class")) {
                scanClassBytes(jar.readAllBytes(), sourceRoot, name, artifact, rootKind, accumulator);
            } else if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
                scanNestedJar(jar.readAllBytes(), sourceRoot + "!/" + name, simpleFileName(name), accumulator);
            }
        }
    }

    private void scanClassBytes(
        byte[] classBytes,
        String sourceRoot,
        String sourcePath,
        String artifact,
        String rootKind,
        ScanAccumulator accumulator
    ) throws IOException {
        try {
            ParsedClassfile parsed = new RawClassfileParser(classBytes).parse();
            ClassReader reader = new ClassReader(classBytes);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);
            GeneratedClassClassification generated = generatedClassifier.classify(classNode);
            GeneratedClassRiskLevel risk = safetyModel.riskFor(generated);
            Map<String, MethodNode> methodNodes = methodNodeMap(classNode);
            String className = classNode.name == null ? parsed.className().replace('/', '.') : classNode.name.replace('/', '.');
            List<MethodSizeRecord> methodRecords = new ArrayList<>();
            int invokedynamicInstructionCount = 0;
            int syntheticMethods = 0;
            int bridgeMethods = 0;
            for (ParsedMethod method : parsed.methods()) {
                MethodNode methodNode = methodNodes.get(method.key());
                InstructionCounts counts = instructionCounts(methodNode);
                invokedynamicInstructionCount += counts.invokedynamicInstructionCount();
                if ((method.access() & Opcodes.ACC_SYNTHETIC) != 0) {
                    syntheticMethods++;
                }
                if ((method.access() & Opcodes.ACC_BRIDGE) != 0) {
                    bridgeMethods++;
                }
                methodRecords.add(new MethodSizeRecord(
                    className,
                    method.name(),
                    method.descriptor(),
                    methodFlags(method.access()),
                    (method.access() & Opcodes.ACC_SYNTHETIC) != 0,
                    (method.access() & Opcodes.ACC_BRIDGE) != 0,
                    "<clinit>".equals(method.name()),
                    method.codeLength(),
                    riskFor(method.codeLength()),
                    method.maxStack(),
                    method.maxLocals(),
                    method.exceptionTableLength(),
                    counts.instructionCount(),
                    counts.branchInstructionCount(),
                    counts.switchInstructionCount(),
                    counts.invokeInstructionCount(),
                    counts.invokedynamicInstructionCount(),
                    counts.ldcInstructionCount(),
                    method.annotationBytes(),
                    method.stackMapTableBytes(),
                    method.lineNumberTableBytes(),
                    method.localVariableTableBytes()
                ));
            }

            long totalMethodCodeBytes = parsed.methods().stream().mapToLong(ParsedMethod::codeLength).sum();
            int largestMethodCodeLength = parsed.methods().stream().mapToInt(ParsedMethod::codeLength).max().orElse(0);
            ClassfileSizeRecord classRecord = new ClassfileSizeRecord(
                className,
                classNode.name == null ? parsed.className() : classNode.name,
                sourceRoot,
                sourcePath,
                artifact,
                rootKind,
                classBytes.length,
                reader.getItemCount(),
                parsed.fieldCount(),
                parsed.methods().size(),
                parsed.classAttributeCount(),
                parsed.interfacesCount(),
                parsed.innerClassCount(),
                parsed.nestMemberCount(),
                parsed.recordComponentCount(),
                parsed.bootstrapMethodsCount(),
                parsed.bootstrapMethodArgCount(),
                invokedynamicInstructionCount,
                syntheticMethods,
                bridgeMethods,
                largestMethodCodeLength,
                totalMethodCodeBytes,
                parsed.totalAttributeBytes(),
                parsed.debugAttributeBytes(),
                parsed.annotationAttributeBytes(),
                parsed.stackMapTableBytes(),
                parsed.lineNumberTableBytes(),
                parsed.localVariableTableBytes(),
                parsed.sourceFileAttributeBytes(),
                generated.family(),
                risk,
                generated.generatedLike()
            );
            accumulator.classes.add(classRecord);
            accumulator.methods.addAll(methodRecords);
            accumulator.constantPools.add(new ConstantPoolBloatRecord(
                className,
                artifact,
                reader.getItemCount(),
                parsed.constantPool().utf8EntryCount(),
                parsed.constantPool().classRefCount(),
                parsed.constantPool().methodRefCount(),
                parsed.constantPool().interfaceMethodRefCount(),
                parsed.constantPool().fieldRefCount(),
                parsed.constantPool().nameAndTypeCount(),
                parsed.constantPool().methodHandleCount(),
                parsed.constantPool().methodTypeCount(),
                parsed.constantPool().dynamicConstantCount(),
                parsed.constantPool().invokeDynamicConstantCount(),
                parsed.constantPool().stringConstantCount(),
                parsed.bootstrapMethodsCount(),
                parsed.bootstrapMethodArgCount(),
                parsed.constantPool().duplicateUtf8Count(),
                parsed.constantPool().duplicateDescriptorCount(),
                generated.family()
            ));
            accumulator.attributes.add(new AttributeFootprintRecord(
                className,
                artifact,
                parsed.totalAttributeBytes(),
                parsed.debugAttributeBytes(),
                parsed.annotationAttributeBytes(),
                parsed.stackMapTableBytes(),
                parsed.metadataAttributeBytes(),
                parsed.stackMapTableBytes(),
                parsed.lineNumberTableBytes(),
                parsed.localVariableTableBytes(),
                parsed.sourceFileAttributeBytes(),
                parsed.bootstrapMethodsAttributeBytes(),
                generated.family()
            ));
        } catch (RuntimeException e) {
            throw new IOException("Failed to scan classfile size entry " + sourcePath + " from " + sourceRoot, e);
        }
    }

    private ClassfileSizeProfile buildProfile(ScanAccumulator accumulator) {
        List<ClassfileSizeRecord> classes = accumulator.classes.stream()
            .sorted(Comparator
                .comparing(ClassfileSizeRecord::classfileBytes, Comparator.reverseOrder())
                .thenComparing(ClassfileSizeRecord::className))
            .toList();
        List<MethodSizeRecord> methods = accumulator.methods.stream()
            .sorted(Comparator
                .comparing(MethodSizeRecord::codeLength, Comparator.reverseOrder())
                .thenComparing(MethodSizeRecord::className)
                .thenComparing(MethodSizeRecord::methodName))
            .toList();
        List<ClassfileSizeFamilySummary> families = familyBreakdown(classes);
        return new ClassfileSizeProfile(
            METADATA_VERSION,
            Instant.now().toString(),
            classes.size(),
            classes.stream().mapToLong(ClassfileSizeRecord::classfileBytes).sum(),
            classes.stream().mapToLong(ClassfileSizeRecord::totalMethodCodeBytes).sum(),
            classes.stream().mapToInt(ClassfileSizeRecord::largestMethodCodeLength).max().orElse(0),
            families,
            classes,
            methods,
            accumulator.constantPools.stream()
                .sorted(Comparator
                    .comparing(ConstantPoolBloatRecord::constantPoolCount, Comparator.reverseOrder())
                    .thenComparing(ConstantPoolBloatRecord::className))
                .toList(),
            accumulator.attributes.stream()
                .sorted(Comparator
                    .comparing(AttributeFootprintRecord::totalAttributeBytes, Comparator.reverseOrder())
                    .thenComparing(AttributeFootprintRecord::className))
                .toList()
        );
    }

    private List<ClassfileSizeFamilySummary> familyBreakdown(List<ClassfileSizeRecord> classes) {
        Map<GeneratedClassFamily, List<ClassfileSizeRecord>> byFamily = new EnumMap<>(GeneratedClassFamily.class);
        for (GeneratedClassFamily family : GeneratedClassFamily.values()) {
            byFamily.put(family, new ArrayList<>());
        }
        for (ClassfileSizeRecord record : classes) {
            byFamily.computeIfAbsent(record.generatedFamily(), ignored -> new ArrayList<>()).add(record);
        }
        List<ClassfileSizeFamilySummary> summaries = new ArrayList<>();
        for (Map.Entry<GeneratedClassFamily, List<ClassfileSizeRecord>> entry : byFamily.entrySet()) {
            List<ClassfileSizeRecord> records = entry.getValue();
            if (records.isEmpty()) {
                continue;
            }
            summaries.add(new ClassfileSizeFamilySummary(
                entry.getKey(),
                records.size(),
                (int) records.stream().filter(ClassfileSizeRecord::generatedLike).count(),
                records.stream().mapToLong(ClassfileSizeRecord::classfileBytes).sum(),
                records.stream().mapToLong(ClassfileSizeRecord::totalMethodCodeBytes).sum(),
                records.stream().mapToInt(ClassfileSizeRecord::largestMethodCodeLength).max().orElse(0),
                records.stream().mapToLong(ClassfileSizeRecord::totalAttributeBytes).sum(),
                records.stream().mapToLong(ClassfileSizeRecord::debugAttributeBytes).sum(),
                records.stream().mapToLong(ClassfileSizeRecord::annotationAttributeBytes).sum(),
                records.stream().mapToLong(ClassfileSizeRecord::constantPoolCount).sum()
            ));
        }
        return summaries;
    }

    private MethodSizeRisk riskFor(int codeLength) {
        if (codeLength >= config.failMethodBytes()) {
            return MethodSizeRisk.LIMIT;
        }
        if (codeLength >= 60_000) {
            return MethodSizeRisk.CRITICAL;
        }
        if (codeLength >= config.dangerMethodBytes()) {
            return MethodSizeRisk.DANGER;
        }
        if (codeLength >= config.warnMethodBytes()) {
            return MethodSizeRisk.WARN;
        }
        if (codeLength >= 16_384) {
            return MethodSizeRisk.LARGE;
        }
        if (codeLength >= 8_192) {
            return MethodSizeRisk.NOTICE;
        }
        return MethodSizeRisk.NORMAL;
    }

    private static Map<String, MethodNode> methodNodeMap(ClassNode classNode) {
        Map<String, MethodNode> methods = new HashMap<>();
        if (classNode.methods != null) {
            for (MethodNode method : classNode.methods) {
                methods.put(method.name + method.desc, method);
            }
        }
        return methods;
    }

    private static InstructionCounts instructionCounts(MethodNode method) {
        if (method == null || method.instructions == null) {
            return new InstructionCounts(0, 0, 0, 0, 0, 0);
        }
        int instructions = 0;
        int branches = 0;
        int switches = 0;
        int invokes = 0;
        int invokedynamic = 0;
        int ldc = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof LabelNode || insn instanceof LineNumberNode || insn instanceof FrameNode) {
                continue;
            }
            instructions++;
            if (insn instanceof JumpInsnNode) {
                branches++;
            } else if (insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) {
                switches++;
            } else if (insn instanceof MethodInsnNode) {
                invokes++;
            } else if (insn instanceof InvokeDynamicInsnNode) {
                invokes++;
                invokedynamic++;
            } else if (insn instanceof LdcInsnNode) {
                ldc++;
            }
        }
        return new InstructionCounts(instructions, branches, switches, invokes, invokedynamic, ldc);
    }

    private static List<String> methodFlags(int access) {
        List<String> flags = new ArrayList<>();
        if ((access & Opcodes.ACC_PUBLIC) != 0) flags.add("ACC_PUBLIC");
        if ((access & Opcodes.ACC_PRIVATE) != 0) flags.add("ACC_PRIVATE");
        if ((access & Opcodes.ACC_PROTECTED) != 0) flags.add("ACC_PROTECTED");
        if ((access & Opcodes.ACC_STATIC) != 0) flags.add("ACC_STATIC");
        if ((access & Opcodes.ACC_FINAL) != 0) flags.add("ACC_FINAL");
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) flags.add("ACC_SYNTHETIC");
        if ((access & Opcodes.ACC_BRIDGE) != 0) flags.add("ACC_BRIDGE");
        return flags;
    }

    private static String artifactFor(ClassRootDescriptor root) {
        String path = root.rootDirectory() == null ? "" : root.rootDirectory().getAbsolutePath().replace('\\', '/');
        if (path.contains("/spring-aot/main/classes")) {
            return "spring-aot";
        }
        if (path.endsWith("/dependencies") || path.contains("/dependencies/")) {
            return "exploded-dependencies";
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

    private static String simpleFileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static final class ScanAccumulator {
        final List<ClassfileSizeRecord> classes = new ArrayList<>();
        final List<MethodSizeRecord> methods = new ArrayList<>();
        final List<ConstantPoolBloatRecord> constantPools = new ArrayList<>();
        final List<AttributeFootprintRecord> attributes = new ArrayList<>();
    }

    private record InstructionCounts(
        int instructionCount,
        int branchInstructionCount,
        int switchInstructionCount,
        int invokeInstructionCount,
        int invokedynamicInstructionCount,
        int ldcInstructionCount
    ) {
    }

    private static final class RawClassfileParser {
        private final byte[] bytes;
        private int offset;
        private String[] utf8;

        RawClassfileParser(byte[] bytes) {
            this.bytes = bytes;
        }

        ParsedClassfile parse() {
            long magic = readU4();
            if (magic != 0xCAFEBABEL) {
                throw new IllegalArgumentException("Invalid classfile magic");
            }
            skip(4);
            int cpCount = readU2();
            utf8 = new String[cpCount];
            ConstantPoolCounts cp = parseAndRememberConstantPool(cpCount);
            int access = readU2();
            int thisClass = readU2();
            skip(2);
            int interfacesCount = readU2();
            skip(interfacesCount * 2);
            int fieldCount = readU2();
            AttributeTotals totals = new AttributeTotals();
            for (int i = 0; i < fieldCount; i++) {
                skipMember(totals);
            }
            int methodCount = readU2();
            List<ParsedMethod> methods = new ArrayList<>();
            for (int i = 0; i < methodCount; i++) {
                methods.add(parseMethod(totals));
            }
            int classAttributeCount = readU2();
            int innerClasses = 0;
            int nestMembers = 0;
            int recordComponents = 0;
            int bootstrapMethods = 0;
            int bootstrapArgs = 0;
            for (int i = 0; i < classAttributeCount; i++) {
                ParsedAttribute attribute = parseAttributeHeader();
                totals.add(attribute);
                if ("InnerClasses".equals(attribute.name())) {
                    innerClasses += readU2At(attribute.infoOffset());
                } else if ("NestMembers".equals(attribute.name())) {
                    nestMembers += readU2At(attribute.infoOffset());
                } else if ("Record".equals(attribute.name())) {
                    recordComponents += readU2At(attribute.infoOffset());
                } else if ("BootstrapMethods".equals(attribute.name())) {
                    int cursor = attribute.infoOffset();
                    int count = readU2At(cursor);
                    bootstrapMethods += count;
                    cursor += 2;
                    for (int j = 0; j < count; j++) {
                        cursor += 2;
                        int argCount = readU2At(cursor);
                        cursor += 2 + argCount * 2;
                        bootstrapArgs += argCount;
                    }
                }
                skip(attribute.length());
            }
            return new ParsedClassfile(
                classNameFromThis(thisClass),
                access,
                interfacesCount,
                fieldCount,
                classAttributeCount,
                innerClasses,
                nestMembers,
                recordComponents,
                bootstrapMethods,
                bootstrapArgs,
                cp,
                methods,
                totals.totalAttributeBytes,
                totals.debugAttributeBytes,
                totals.annotationAttributeBytes,
                totals.stackMapTableBytes,
                totals.lineNumberTableBytes,
                totals.localVariableTableBytes,
                totals.sourceFileAttributeBytes,
                totals.bootstrapMethodsAttributeBytes,
                totals.metadataAttributeBytes
            );
        }

        private ConstantPoolCounts parseConstantPool(int cpCount) {
            Map<String, Integer> utf8Counts = new LinkedHashMap<>();
            int utf8EntryCount = 0;
            int classRefCount = 0;
            int methodRefCount = 0;
            int interfaceMethodRefCount = 0;
            int fieldRefCount = 0;
            int nameAndTypeCount = 0;
            int methodHandleCount = 0;
            int methodTypeCount = 0;
            int dynamicConstantCount = 0;
            int invokeDynamicConstantCount = 0;
            int stringConstantCount = 0;
            int[] classNameIndex = new int[cpCount];
            for (int i = 1; i < cpCount; i++) {
                int tag = readU1();
                switch (tag) {
                    case 1 -> {
                        int length = readU2();
                        String value = new String(bytes, offset, length, StandardCharsets.UTF_8);
                        utf8[i] = value;
                        utf8Counts.merge(value, 1, Integer::sum);
                        utf8EntryCount++;
                        skip(length);
                    }
                    case 3, 4 -> skip(4);
                    case 5, 6 -> {
                        skip(8);
                        i++;
                    }
                    case 7 -> {
                        classNameIndex[i] = readU2();
                        classRefCount++;
                    }
                    case 8 -> {
                        skip(2);
                        stringConstantCount++;
                    }
                    case 9 -> {
                        skip(4);
                        fieldRefCount++;
                    }
                    case 10 -> {
                        skip(4);
                        methodRefCount++;
                    }
                    case 11 -> {
                        skip(4);
                        interfaceMethodRefCount++;
                    }
                    case 12 -> {
                        skip(4);
                        nameAndTypeCount++;
                    }
                    case 15 -> {
                        skip(3);
                        methodHandleCount++;
                    }
                    case 16 -> {
                        skip(2);
                        methodTypeCount++;
                    }
                    case 17 -> {
                        skip(4);
                        dynamicConstantCount++;
                    }
                    case 18 -> {
                        skip(4);
                        invokeDynamicConstantCount++;
                    }
                    case 19, 20 -> skip(2);
                    default -> throw new IllegalArgumentException("Unsupported constant-pool tag " + tag);
                }
            }
            int duplicateUtf8 = (int) utf8Counts.values().stream().filter(count -> count > 1).count();
            int duplicateDescriptor = (int) utf8Counts.entrySet().stream()
                .filter(entry -> entry.getKey().contains("(") || entry.getKey().startsWith("L") || entry.getKey().startsWith("["))
                .filter(entry -> entry.getValue() > 1)
                .count();
            return new ConstantPoolCounts(
                classNameIndex,
                utf8EntryCount,
                classRefCount,
                methodRefCount,
                interfaceMethodRefCount,
                fieldRefCount,
                nameAndTypeCount,
                methodHandleCount,
                methodTypeCount,
                dynamicConstantCount,
                invokeDynamicConstantCount,
                stringConstantCount,
                duplicateUtf8,
                duplicateDescriptor
            );
        }

        private void skipMember(AttributeTotals totals) {
            skip(6);
            int attributes = readU2();
            for (int i = 0; i < attributes; i++) {
                ParsedAttribute attribute = parseAttributeHeader();
                totals.add(attribute);
                skip(attribute.length());
            }
        }

        private ParsedMethod parseMethod(AttributeTotals totals) {
            int access = readU2();
            String name = utf8[readU2()];
            String descriptor = utf8[readU2()];
            int attributes = readU2();
            int codeLength = 0;
            int maxStack = 0;
            int maxLocals = 0;
            int exceptionTableLength = 0;
            long annotationBytes = 0;
            long stackMapBytes = 0;
            long lineNumberBytes = 0;
            long localVariableBytes = 0;
            for (int i = 0; i < attributes; i++) {
                ParsedAttribute attribute = parseAttributeHeader();
                totals.add(attribute);
                if ("Code".equals(attribute.name())) {
                    int codeOffset = attribute.infoOffset();
                    maxStack = readU2At(codeOffset);
                    maxLocals = readU2At(codeOffset + 2);
                    long rawCodeLength = readU4At(codeOffset + 4);
                    codeLength = rawCodeLength > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) rawCodeLength;
                    int cursor = codeOffset + 8 + codeLength;
                    exceptionTableLength = readU2At(cursor);
                    cursor += 2 + exceptionTableLength * 8;
                    int codeAttributeCount = readU2At(cursor);
                    cursor += 2;
                    for (int j = 0; j < codeAttributeCount; j++) {
                        String nestedName = utf8[readU2At(cursor)];
                        long nestedLength = readU4At(cursor + 2);
                        long bytes = 6 + nestedLength;
                        if (isStackMapAttribute(nestedName)) {
                            stackMapBytes += bytes;
                            totals.stackMapTableBytes += bytes;
                        }
                        if (isLineNumberAttribute(nestedName)) {
                            lineNumberBytes += bytes;
                            totals.lineNumberTableBytes += bytes;
                            totals.debugAttributeBytes += bytes;
                        }
                        if (isLocalVariableAttribute(nestedName)) {
                            localVariableBytes += bytes;
                            totals.localVariableTableBytes += bytes;
                            totals.debugAttributeBytes += bytes;
                        }
                        if (isAnnotationAttribute(nestedName)) {
                            annotationBytes += bytes;
                            totals.annotationAttributeBytes += bytes;
                        }
                        cursor += 6 + (int) nestedLength;
                    }
                } else if (isAnnotationAttribute(attribute.name())) {
                    annotationBytes += attribute.totalBytes();
                }
                skip(attribute.length());
            }
            return new ParsedMethod(
                access,
                name,
                descriptor,
                codeLength,
                maxStack,
                maxLocals,
                exceptionTableLength,
                annotationBytes,
                stackMapBytes,
                lineNumberBytes,
                localVariableBytes
            );
        }

        private ParsedAttribute parseAttributeHeader() {
            String name = utf8[readU2()];
            long length = readU4();
            return new ParsedAttribute(name, offset, length);
        }

        private String classNameFromThis(int thisClass) {
            int nameIndex = thisClass <= 0 ? 0 : lastConstantPool().classNameIndex()[thisClass];
            return nameIndex > 0 ? utf8[nameIndex] : "";
        }

        private ConstantPoolCounts lastConstantPool;

        private ConstantPoolCounts lastConstantPool() {
            return lastConstantPool;
        }

        private int readU1() {
            return bytes[offset++] & 0xFF;
        }

        private int readU2() {
            int value = readU2At(offset);
            offset += 2;
            return value;
        }

        private long readU4() {
            long value = readU4At(offset);
            offset += 4;
            return value;
        }

        private int readU2At(int at) {
            return ((bytes[at] & 0xFF) << 8) | (bytes[at + 1] & 0xFF);
        }

        private long readU4At(int at) {
            return ((long) (bytes[at] & 0xFF) << 24)
                | ((long) (bytes[at + 1] & 0xFF) << 16)
                | ((long) (bytes[at + 2] & 0xFF) << 8)
                | (long) (bytes[at + 3] & 0xFF);
        }

        private void skip(long count) {
            offset += (int) count;
        }

        private ConstantPoolCounts parseAndRememberConstantPool(int cpCount) {
            lastConstantPool = parseConstantPool(cpCount);
            return lastConstantPool;
        }
    }

    private record ParsedClassfile(
        String className,
        int access,
        int interfacesCount,
        int fieldCount,
        int classAttributeCount,
        int innerClassCount,
        int nestMemberCount,
        int recordComponentCount,
        int bootstrapMethodsCount,
        int bootstrapMethodArgCount,
        ConstantPoolCounts constantPool,
        List<ParsedMethod> methods,
        long totalAttributeBytes,
        long debugAttributeBytes,
        long annotationAttributeBytes,
        long stackMapTableBytes,
        long lineNumberTableBytes,
        long localVariableTableBytes,
        long sourceFileAttributeBytes,
        long bootstrapMethodsAttributeBytes,
        long metadataAttributeBytes
    ) {
    }

    private record ParsedMethod(
        int access,
        String name,
        String descriptor,
        int codeLength,
        int maxStack,
        int maxLocals,
        int exceptionTableLength,
        long annotationBytes,
        long stackMapTableBytes,
        long lineNumberTableBytes,
        long localVariableTableBytes
    ) {
        String key() {
            return name + descriptor;
        }
    }

    private record ParsedAttribute(String name, int infoOffset, long length) {
        long totalBytes() {
            return 6 + length;
        }
    }

    private record ConstantPoolCounts(
        int[] classNameIndex,
        int utf8EntryCount,
        int classRefCount,
        int methodRefCount,
        int interfaceMethodRefCount,
        int fieldRefCount,
        int nameAndTypeCount,
        int methodHandleCount,
        int methodTypeCount,
        int dynamicConstantCount,
        int invokeDynamicConstantCount,
        int stringConstantCount,
        int duplicateUtf8Count,
        int duplicateDescriptorCount
    ) {
    }

    private static final class AttributeTotals {
        long totalAttributeBytes;
        long debugAttributeBytes;
        long annotationAttributeBytes;
        long stackMapTableBytes;
        long lineNumberTableBytes;
        long localVariableTableBytes;
        long sourceFileAttributeBytes;
        long bootstrapMethodsAttributeBytes;
        long metadataAttributeBytes;

        void add(ParsedAttribute attribute) {
            long bytes = attribute.totalBytes();
            totalAttributeBytes += bytes;
            if (isDebugAttribute(attribute.name())) debugAttributeBytes += bytes;
            if (isAnnotationAttribute(attribute.name())) annotationAttributeBytes += bytes;
            if (isStackMapAttribute(attribute.name())) stackMapTableBytes += bytes;
            if (isLineNumberAttribute(attribute.name())) lineNumberTableBytes += bytes;
            if (isLocalVariableAttribute(attribute.name())) localVariableTableBytes += bytes;
            if ("SourceFile".equals(attribute.name())) sourceFileAttributeBytes += bytes;
            if ("BootstrapMethods".equals(attribute.name())) bootstrapMethodsAttributeBytes += bytes;
            if (isMetadataAttribute(attribute.name())) metadataAttributeBytes += bytes;
        }
    }

    private static boolean isDebugAttribute(String name) {
        return isLineNumberAttribute(name)
            || isLocalVariableAttribute(name)
            || "SourceFile".equals(name)
            || "SourceDebugExtension".equals(name);
    }

    private static boolean isLineNumberAttribute(String name) {
        return "LineNumberTable".equals(name);
    }

    private static boolean isLocalVariableAttribute(String name) {
        return "LocalVariableTable".equals(name) || "LocalVariableTypeTable".equals(name);
    }

    private static boolean isStackMapAttribute(String name) {
        return "StackMapTable".equals(name);
    }

    private static boolean isAnnotationAttribute(String name) {
        return name != null && (name.contains("Annotations") || "AnnotationDefault".equals(name));
    }

    private static boolean isMetadataAttribute(String name) {
        return "InnerClasses".equals(name)
            || "EnclosingMethod".equals(name)
            || "NestHost".equals(name)
            || "NestMembers".equals(name)
            || "Record".equals(name)
            || "PermittedSubclasses".equals(name)
            || "Signature".equals(name)
            || "Synthetic".equals(name)
            || "Deprecated".equals(name)
            || "BootstrapMethods".equals(name);
    }
}
