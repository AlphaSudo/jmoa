package com.yourorg.jmoa.plugin.reducer;

import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public final class JarReducer {

    private final ReducerConfig config;
    private final ClassDebugMetadataInspector inspector = new ClassDebugMetadataInspector();
    private final LocalVariableDebugAttributeReducer asmReducer = new LocalVariableDebugAttributeReducer();
    private final RawLocalVariableDebugAttributeReducer rawReducer = new RawLocalVariableDebugAttributeReducer();
    private final RawReducerBytePreservationAuditor rawAuditor = new RawReducerBytePreservationAuditor();
    private final JarSafetyInspector safetyInspector = new JarSafetyInspector();
    private final ArtifactSelectionPolicy selectionPolicy;
    private final List<RawReducerClassAuditRecord> rawClassAudits = new ArrayList<>();

    public JarReducer(ReducerConfig config) {
        this.config = config;
        this.selectionPolicy = new ArtifactSelectionPolicy(config);
    }

    public ReducerReport reduce() throws IOException {
        if (config.outputDir() != null) {
            config.outputDir().mkdirs();
        }
        rawClassAudits.clear();
        List<JarReductionRecord> jars = new ArrayList<>();
        for (File jar : DebugMetadataSavingsEstimator.jarFiles(config.inputDir())) {
            jars.add(reduceJar(jar));
        }
        ApplicationReductionReport application = new ApplicationClassReducer(config).reduceOrEstimate();
        List<RawReducerClassAuditRecord> allAudits = new ArrayList<>(rawClassAudits);
        allAudits.addAll(application.rawClassAudits());
        return new DebugMetadataSavingsEstimator(config).report(true, jars, allAudits, application);
    }

    private JarReductionRecord reduceJar(File jar) throws IOException {
        File output = new File(config.outputDir(), reducedName(jar.getName()));
        List<ClassReductionRecord> classRecords = new ArrayList<>();
        long estimated = 0;
        int classCount = 0;
        int reducedClassCount = 0;
        int skippedBootstrapMethodsClassCount = 0;
        JarSafetyAssessment safety;
        try (JarFile source = new JarFile(jar)) {
            safety = safetyInspector.assess(source);
            String selectionSkip = selectionPolicy.isSelected(jar.getName()) ? null : "SKIPPED_ARTIFACT_POLICY";
            String skipReason = selectionSkip != null ? selectionSkip : safety.skipReason();
            if (selectionSkip != null || !safety.mutationAllowed()) {
                ScanResult scan = scanSkippedJar(jar, source, skipReason);
                Files.copy(jar.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return new JarReductionRecord(
                    jar.getName(),
                    jar.getAbsolutePath(),
                    output.getAbsolutePath(),
                    JarSafetyInspector.sha256(jar),
                    JarSafetyInspector.sha256(output),
                    jar.length(),
                    output.length(),
                    scan.estimatedRemovableBytes(),
                    0,
                    scan.classCount(),
                    0,
                    0,
                    safety.signedJar(),
                    safety.multiReleaseJar(),
                    safety.sealedJar(),
                    skipReason,
                    "preserve",
                    skipReason,
                    scan.records().stream().limit(200).toList()
                );
            }
            try (JarOutputStream target = new JarOutputStream(new FileOutputStream(output))) {
                Enumeration<JarEntry> entries = source.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    JarEntry copy = new JarEntry(entry.getName());
                    copy.setTime(entry.getTime());
                    target.putNextEntry(copy);
                    byte[] bytes;
                    try (var input = source.getInputStream(entry)) {
                        bytes = input.readAllBytes();
                    }
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    classCount++;
                    ClassDebugMetadata before = inspector.inspect(bytes);
                    estimated += before.removableBytes();
                    if (isModuleInfo(entry.getName())) {
                        target.write(bytes);
                        if (before.removableBytes() > 0) {
                            classRecords.add(skippedRecord(jar.getName(), entry.getName(), before, bytes.length,
                                "SKIPPED_MODULE_INFO"));
                        }
                        continue;
                    }
                    if (before.removableBytes() > 0) {
                        if (before.bootstrapMethodsAttributeBytes() > 0) {
                            if (config.parsedEngine() != ReducerEngine.RAW) {
                                target.write(bytes);
                                skippedBootstrapMethodsClassCount++;
                                classRecords.add(skippedRecord(jar.getName(), entry.getName(), before, bytes.length,
                                    "SKIPPED_BOOTSTRAP_METHODS"));
                                continue;
                            }
                        }
                        byte[] reduced = reduceClass(bytes);
                        verifyClass(reduced, entry.getName());
                        ClassDebugMetadata after = inspector.inspect(reduced);
                        verifyPreserved(before, after, entry.getName());
                        RawReducerClassAuditRecord rawAudit = null;
                        if (config.parsedEngine() == ReducerEngine.RAW) {
                            rawAudit = rawAuditor.audit(jar.getName(), entry.getName(), before, after, bytes, reduced);
                            rawClassAudits.add(rawAudit);
                        }
                        target.write(reduced);
                        reducedClassCount++;
                        classRecords.add(new ClassReductionRecord(
                            before.className(),
                            jar.getName(),
                            entry.getName(),
                            bytes.length,
                            reduced.length,
                            before.localVariableTableBytes() - after.localVariableTableBytes(),
                            before.localVariableTypeTableBytes() - after.localVariableTypeTableBytes(),
                            after.lineNumberTableBytes() == before.lineNumberTableBytes(),
                            after.sourceFileAttributeBytes() == before.sourceFileAttributeBytes(),
                            attributeStillPresent(before.stackMapTableBytes(), after.stackMapTableBytes()),
                            attributeStillPresent(before.annotationAttributeBytes(), after.annotationAttributeBytes()),
                            attributeStillPresent(before.signatureAttributeBytes(), after.signatureAttributeBytes()),
                            attributeStillPresent(before.bootstrapMethodsAttributeBytes(), after.bootstrapMethodsAttributeBytes()),
                            rawAudit != null ? "REDUCED_RAW_AUDITED" : "REDUCED"
                        ));
                    } else {
                        target.write(bytes);
                    }
                    } else {
                        target.write(bytes);
                    }
                    target.closeEntry();
                }
            }
        } catch (Exception e) {
            if (output.isFile() && !output.delete()) {
                output.deleteOnExit();
            }
            throw new IOException("Failed to reduce jar " + jar.getAbsolutePath(), e);
        }
        classRecords.sort(Comparator.comparingLong((ClassReductionRecord r) ->
            r.localVariableTableBytesRemoved() + r.localVariableTypeTableBytesRemoved()).reversed());
        long removed = Math.max(0, jar.length() - output.length());
        return new JarReductionRecord(
            jar.getName(),
            jar.getAbsolutePath(),
            output.getAbsolutePath(),
            JarSafetyInspector.sha256(jar),
            JarSafetyInspector.sha256(output),
            jar.length(),
            output.length(),
            estimated,
            removed,
            classCount,
            reducedClassCount,
            skippedBootstrapMethodsClassCount,
            safety.signedJar(),
            safety.multiReleaseJar(),
            safety.sealedJar(),
            safety.skipReason(),
            "preserve",
            "REDUCED",
            classRecords.stream().limit(200).toList()
        );
    }

    private static String reducedName(String name) {
        return name;
    }

    private byte[] reduceClass(byte[] bytes) {
        return config.parsedEngine() == ReducerEngine.RAW
            ? rawReducer.reduce(bytes)
            : asmReducer.reduce(bytes);
    }

    private ScanResult scanSkippedJar(File jar, JarFile source, String status) throws IOException {
        List<ClassReductionRecord> records = new ArrayList<>();
        long estimated = 0;
        int classCount = 0;
        Enumeration<JarEntry> entries = source.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                continue;
            }
            classCount++;
            byte[] bytes;
            try (var input = source.getInputStream(entry)) {
                bytes = input.readAllBytes();
            }
            ClassDebugMetadata before = inspector.inspect(bytes);
            estimated += before.removableBytes();
            if (before.removableBytes() > 0) {
                records.add(skippedRecord(jar.getName(), entry.getName(), before, bytes.length, status));
            }
        }
        records.sort(Comparator.comparingLong((ClassReductionRecord r) ->
            r.localVariableTableBytesRemoved() + r.localVariableTypeTableBytesRemoved()).reversed());
        return new ScanResult(classCount, estimated, records);
    }

    private static boolean isModuleInfo(String entryName) {
        return "module-info.class".equals(entryName) || entryName.endsWith("/module-info.class");
    }

    private static void verifyClass(byte[] bytes, String entryName) {
        try {
            new ClassReader(bytes);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Reduced class is not readable: " + entryName, e);
        }
    }

    private static void verifyPreserved(ClassDebugMetadata before, ClassDebugMetadata after, String entryName) {
        if (after.localVariableTableBytes() != 0 || after.localVariableTypeTableBytes() != 0) {
            throw new IllegalStateException("Local variable metadata was not fully removed: " + entryName);
        }
        if (after.lineNumberTableBytes() != before.lineNumberTableBytes()) {
            throw new IllegalStateException("LineNumberTable changed unexpectedly: " + entryName);
        }
        if (after.sourceFileAttributeBytes() != before.sourceFileAttributeBytes()) {
            throw new IllegalStateException("SourceFile changed unexpectedly: " + entryName);
        }
        if (!attributeStillPresent(before.stackMapTableBytes(), after.stackMapTableBytes())) {
            throw new IllegalStateException("StackMapTable disappeared unexpectedly: " + entryName);
        }
        if (!attributeStillPresent(before.annotationAttributeBytes(), after.annotationAttributeBytes())) {
            throw new IllegalStateException("Annotation attributes disappeared unexpectedly: " + entryName);
        }
        if (!attributeStillPresent(before.signatureAttributeBytes(), after.signatureAttributeBytes())) {
            throw new IllegalStateException("Signature attribute disappeared unexpectedly: " + entryName);
        }
        if (!attributeStillPresent(before.bootstrapMethodsAttributeBytes(), after.bootstrapMethodsAttributeBytes())) {
            throw new IllegalStateException("BootstrapMethods attribute disappeared unexpectedly: " + entryName);
        }
    }

    private static ClassReductionRecord skippedRecord(
        String artifact,
        String entryName,
        ClassDebugMetadata before,
        int originalBytes,
        String status
    ) {
        return new ClassReductionRecord(
            before.className(),
            artifact,
            entryName,
            originalBytes,
            originalBytes,
            0,
            0,
            true,
            true,
            true,
            true,
            true,
            true,
            status
        );
    }

    private static boolean attributeStillPresent(long before, long after) {
        return before == 0 || after > 0;
    }

    private record ScanResult(
        int classCount,
        long estimatedRemovableBytes,
        List<ClassReductionRecord> records
    ) {
    }
}
