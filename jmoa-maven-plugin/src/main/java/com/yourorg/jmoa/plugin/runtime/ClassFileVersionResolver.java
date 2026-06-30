package com.yourorg.jmoa.plugin.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public final class ClassFileVersionResolver {

    private ClassFileVersionResolver() {
    }

    public static int resolveHighestVersion(List<File> classFiles, int fallbackVersion) throws IOException {
        int resolved = -1;
        for (File classFile : classFiles) {
            resolved = Math.max(resolved, readMajorVersion(classFile));
        }
        return resolved >= 0 ? resolved : fallbackVersion;
    }

    public static int readMajorVersion(File classFile) throws IOException {
        try (FileInputStream input = new FileInputStream(classFile)) {
            byte[] header = input.readNBytes(8);
            if (header.length < 8) {
                throw new IOException("Class file header is truncated: " + classFile.getAbsolutePath());
            }
            int magic = ((header[0] & 0xFF) << 24)
                | ((header[1] & 0xFF) << 16)
                | ((header[2] & 0xFF) << 8)
                | (header[3] & 0xFF);
            if (magic != 0xCAFEBABE) {
                throw new IOException("Invalid class file header: " + classFile.getAbsolutePath());
            }
            return ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
        }
    }
}
