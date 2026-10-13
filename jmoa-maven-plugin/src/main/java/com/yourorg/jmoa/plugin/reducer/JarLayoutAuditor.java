package com.yourorg.jmoa.plugin.reducer;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class JarLayoutAuditor {

    JarLayoutAuditRecord audit(File input, File output, Set<String> intentionallyChangedEntries) throws IOException {
        try (ZipFile left = ZipFile.builder().setFile(input).get();
             ZipFile right = ZipFile.builder().setFile(output).get()) {
            List<ZipArchiveEntry> inputEntries = entries(left.getEntries());
            List<ZipArchiveEntry> outputEntries = entries(right.getEntries());
            boolean orderPreserved = names(inputEntries).equals(names(outputEntries));
            int unchanged = 0;
            int rawPreserved = 0;
            int metadataPreserved = 0;
            List<String> failures = new ArrayList<>();
            int comparable = Math.min(inputEntries.size(), outputEntries.size());
            for (int i = 0; i < comparable; i++) {
                ZipArchiveEntry before = inputEntries.get(i);
                ZipArchiveEntry after = outputEntries.get(i);
                if (!before.getName().equals(after.getName())) {
                    failures.add("ENTRY_ORDER:" + before.getName() + "!=" + after.getName());
                    continue;
                }
                if (intentionallyChangedEntries.contains(before.getName())) {
                    continue;
                }
                unchanged++;
                if (metadataEqual(before, after)) {
                    metadataPreserved++;
                } else {
                    failures.add("ENTRY_METADATA:" + before.getName());
                }
                try (var beforeRaw = left.getRawInputStream(before); var afterRaw = right.getRawInputStream(after)) {
                    if (beforeRaw != null && afterRaw != null
                        && ClassfileComponentHasher.sha256(beforeRaw.readAllBytes())
                            .equals(ClassfileComponentHasher.sha256(afterRaw.readAllBytes()))) {
                        rawPreserved++;
                    } else {
                        failures.add("RAW_PAYLOAD:" + before.getName());
                    }
                }
            }
            if (inputEntries.size() != outputEntries.size()) {
                failures.add("ENTRY_COUNT:" + inputEntries.size() + "!=" + outputEntries.size());
            }
            return new JarLayoutAuditRecord(
                inputEntries.size(), outputEntries.size(), orderPreserved,
                intentionallyChangedEntries.size(), unchanged, rawPreserved, metadataPreserved,
                unchanged == rawPreserved, unchanged == metadataPreserved, failures.stream().limit(200).toList()
            );
        }
    }

    private static boolean metadataEqual(ZipArchiveEntry left, ZipArchiveEntry right) {
        return left.getMethod() == right.getMethod()
            && left.getTime() == right.getTime()
            && Objects.equals(left.getComment(), right.getComment())
            && java.util.Arrays.equals(left.getExtra(), right.getExtra())
            && left.getUnixMode() == right.getUnixMode();
    }

    private static List<ZipArchiveEntry> entries(Enumeration<ZipArchiveEntry> entries) {
        List<ZipArchiveEntry> result = new ArrayList<>();
        while (entries.hasMoreElements()) result.add(entries.nextElement());
        return result;
    }

    private static List<String> names(List<ZipArchiveEntry> entries) {
        return entries.stream().map(ZipArchiveEntry::getName).toList();
    }
}
