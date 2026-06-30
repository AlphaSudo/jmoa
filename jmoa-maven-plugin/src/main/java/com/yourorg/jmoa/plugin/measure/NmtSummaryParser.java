package com.yourorg.jmoa.plugin.measure;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NmtSummaryParser {

    private static final Pattern METASPACE_PATTERN =
        Pattern.compile("-\\s+Metaspace\\s+\\(reserved=(\\d+)(KB)?, committed=(\\d+)(KB)?\\)");
    private static final Pattern CLASS_SPACE_PATTERN =
        Pattern.compile("-\\s+(?:Class space|Shared class space)\\s+\\(reserved=(\\d+)(KB)?, committed=(\\d+)(KB)?(?:,.*)?\\)");
    private static final Pattern METADATA_SECTION_PATTERN =
        Pattern.compile("\\(\\s*Metadata:\\s*\\)");
    private static final Pattern METADATA_DETAIL_PATTERN =
        Pattern.compile("\\(\\s*reserved=(\\d+)(KB)?, committed=(\\d+)(KB)?\\)");
    private static final Pattern USED_PATTERN =
        Pattern.compile("\\(\\s*used=(\\d+)(KB)?\\)");
    private static final Pattern CLASS_SPACE_HEADER_PATTERN =
        Pattern.compile("\\(\\s*Class space:\\s*\\)");

    public NmtMetrics parse(File nmtLogFile) throws IOException {
        return parse(Files.readAllLines(nmtLogFile.toPath()));
    }

    NmtMetrics parse(List<String> lines) {
        long metaspaceReservedKb = 0L;
        long metaspaceCommittedKb = 0L;
        long metaspaceUsedKb = 0L;
        long classSpaceReservedKb = 0L;
        long classSpaceCommittedKb = 0L;
        long classSpaceUsedKb = 0L;
        boolean expectMetadataDetail = false;
        boolean expectClassSpaceDetail = false;
        boolean expectMetaspaceUsed = false;
        boolean expectClassSpaceUsed = false;

        for (String line : lines) {
            Matcher metaspaceMatcher = METASPACE_PATTERN.matcher(line);
            if (metaspaceMatcher.find()) {
                metaspaceReservedKb = normalizeToKb(
                    Long.parseLong(metaspaceMatcher.group(1)),
                    metaspaceMatcher.group(2)
                );
                metaspaceCommittedKb = normalizeToKb(
                    Long.parseLong(metaspaceMatcher.group(3)),
                    metaspaceMatcher.group(4)
                );
                expectMetadataDetail = false;
                expectMetaspaceUsed = false;
                expectClassSpaceUsed = false;
            }
            Matcher classSpaceMatcher = CLASS_SPACE_PATTERN.matcher(line);
            if (classSpaceMatcher.find()) {
                classSpaceReservedKb = normalizeToKb(
                    Long.parseLong(classSpaceMatcher.group(1)),
                    classSpaceMatcher.group(2)
                );
                classSpaceCommittedKb = normalizeToKb(
                    Long.parseLong(classSpaceMatcher.group(3)),
                    classSpaceMatcher.group(4)
                );
                expectClassSpaceDetail = false;
                expectClassSpaceUsed = true;
                expectMetaspaceUsed = false;
            }
            if (METADATA_SECTION_PATTERN.matcher(line).find()) {
                expectMetadataDetail = true;
                expectMetaspaceUsed = false;
                expectClassSpaceUsed = false;
                continue;
            }
            if (expectMetadataDetail) {
                Matcher metadataDetailMatcher = METADATA_DETAIL_PATTERN.matcher(line);
                if (metadataDetailMatcher.find()) {
                    expectMetadataDetail = false;
                    expectMetaspaceUsed = true;
                    metaspaceReservedKb = normalizeToKb(
                        Long.parseLong(metadataDetailMatcher.group(1)),
                        metadataDetailMatcher.group(2)
                    );
                    metaspaceCommittedKb = normalizeToKb(
                        Long.parseLong(metadataDetailMatcher.group(3)),
                        metadataDetailMatcher.group(4)
                    );
                    continue;
                }
            }
            if (CLASS_SPACE_HEADER_PATTERN.matcher(line).find()) {
                expectClassSpaceDetail = true;
                expectClassSpaceUsed = true;
                expectMetaspaceUsed = false;
                continue;
            }
            if (expectClassSpaceDetail) {
                Matcher classSpaceDetailMatcher = METADATA_DETAIL_PATTERN.matcher(line);
                if (classSpaceDetailMatcher.find()) {
                    expectClassSpaceDetail = false;
                    classSpaceReservedKb = normalizeToKb(
                        Long.parseLong(classSpaceDetailMatcher.group(1)),
                        classSpaceDetailMatcher.group(2)
                    );
                    classSpaceCommittedKb = normalizeToKb(
                        Long.parseLong(classSpaceDetailMatcher.group(3)),
                        classSpaceDetailMatcher.group(4)
                    );
                    continue;
                }
            }
            Matcher usedMatcher = USED_PATTERN.matcher(line);
            if (usedMatcher.find()) {
                long usedKb = normalizeToKb(
                    Long.parseLong(usedMatcher.group(1)),
                    usedMatcher.group(2)
                );
                if (expectMetaspaceUsed && metaspaceUsedKb == 0L) {
                    metaspaceUsedKb = usedKb;
                    expectMetaspaceUsed = false;
                    continue;
                }
                if (expectClassSpaceUsed && classSpaceUsedKb == 0L) {
                    classSpaceUsedKb = usedKb;
                    expectClassSpaceUsed = false;
                }
            }
        }

        return new NmtMetrics(
            metaspaceReservedKb,
            metaspaceCommittedKb,
            metaspaceUsedKb,
            classSpaceReservedKb,
            classSpaceCommittedKb,
            classSpaceUsedKb
        );
    }

    private long normalizeToKb(long value, String suffix) {
        if ("KB".equals(suffix)) {
            return value;
        }
        return value / 1024L;
    }

    public record NmtMetrics(
        long metaspaceReservedKb,
        long metaspaceCommittedKb,
        long metaspaceUsedKb,
        long classSpaceReservedKb,
        long classSpaceCommittedKb,
        long classSpaceUsedKb
    ) {
    }
}
