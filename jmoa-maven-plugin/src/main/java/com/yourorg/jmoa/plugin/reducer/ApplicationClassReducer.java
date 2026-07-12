package com.yourorg.jmoa.plugin.reducer;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Applies the V2-Q raw reducer only to explicitly admitted packaged classes. */
public final class ApplicationClassReducer {

    private static final String ARTIFACT = "APPLICATION_CLASSES";

    private final ReducerConfig config;
    private final ClassDebugMetadataInspector inspector = new ClassDebugMetadataInspector();
    private final RawLocalVariableDebugAttributeReducer rawReducer = new RawLocalVariableDebugAttributeReducer();
    private final RawReducerBytePreservationAuditor rawAuditor = new RawReducerBytePreservationAuditor();
    private final GeneratedFamilyAdmissionPolicy admissionPolicy = new GeneratedFamilyAdmissionPolicy();

    public ApplicationClassReducer(ReducerConfig config) {
        this.config = config;
    }

    public ApplicationReductionReport reduceOrEstimate() throws IOException {
        if (!config.includeApplicationClasses()) {
            return ApplicationReductionReport.disabled();
        }
        Path input = config.applicationInputDir().toPath().toAbsolutePath().normalize();
        Path output = config.outputDir().toPath().resolve("application-classes").toAbsolutePath().normalize();
        if (output.startsWith(input)) {
            throw new IllegalArgumentException("Application reducer output must not be inside applicationInputDir.");
        }
        List<Path> files;
        try (var stream = Files.walk(input)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        }
        List<ApplicationClassReductionRecord> records = new ArrayList<>();
        List<RawReducerClassAuditRecord> audits = new ArrayList<>();
        int classCount = 0;
        int reducedCount = 0;
        long estimated = 0;
        long removed = 0;
        boolean mutate = config.mutationEnabled();
        if (mutate) {
            Files.createDirectories(output);
        }
        for (Path file : files) {
            Path relative = input.relativize(file);
            if (!file.getFileName().toString().endsWith(".class")) {
                if (mutate) {
                    Path target = output.resolve(relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
                continue;
            }
            classCount++;
            byte[] original = Files.readAllBytes(file);
            ClassDebugMetadata before = inspector.inspect(original);
            GeneratedFamilyAssessment assessment = "module-info.class".equals(relative.toString().replace('\\', '/'))
                ? new GeneratedFamilyAssessment("MODULE_INFO", GeneratedFamilyAdmission.BLOCK_SEMANTIC_RISK,
                    "module-info.class is preserved byte-for-byte.")
                : admissionPolicy.assess(before.className());
            boolean admitted = assessment.admission() == GeneratedFamilyAdmission.ALLOW_METADATA_ONLY;
            if (admitted) {
                estimated += before.removableBytes();
            }
            byte[] emitted = original;
            String status;
            if (mutate && admitted && before.removableBytes() > 0) {
                emitted = rawReducer.reduce(original);
                verifyReadable(emitted, relative.toString());
                ClassDebugMetadata after = inspector.inspect(emitted);
                RawReducerClassAuditRecord audit = rawAuditor.audit(
                    ARTIFACT, relative.toString().replace('\\', '/'), before, after, original, emitted
                );
                audits.add(audit);
                removed += original.length - emitted.length;
                reducedCount++;
                status = "REDUCED_RAW_AUDITED";
            } else if (admitted && before.removableBytes() > 0) {
                status = "ESTIMATED_ALLOW_METADATA_ONLY";
            } else if (admitted) {
                status = "NO_LOCAL_VARIABLE_METADATA";
            } else {
                status = "SKIPPED_" + assessment.admission();
            }
            if (mutate) {
                Path target = output.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.write(target, emitted);
            }
            records.add(new ApplicationClassReductionRecord(
                before.className(),
                relative.toString().replace('\\', '/'),
                assessment.family(),
                assessment.admission(),
                original.length,
                emitted.length,
                mutate && admitted ? before.localVariableTableBytes() - inspector.inspect(emitted).localVariableTableBytes() : 0,
                mutate && admitted ? before.localVariableTypeTableBytes() - inspector.inspect(emitted).localVariableTypeTableBytes() : 0,
                status,
                assessment.reason()
            ));
        }
        records.sort(Comparator.comparing(ApplicationClassReductionRecord::family)
            .thenComparing(ApplicationClassReductionRecord::className));
        return new ApplicationReductionReport(
            true,
            input.toString(),
            mutate ? output.toString() : "",
            classCount,
            reducedCount,
            estimated,
            removed,
            audits.size(),
            0,
            records,
            audits,
            admissionPolicy.taxonomy()
        );
    }

    private static void verifyReadable(byte[] bytes, String relativePath) {
        try {
            new ClassReader(bytes);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Reduced application class is not readable: " + relativePath, e);
        }
    }
}
