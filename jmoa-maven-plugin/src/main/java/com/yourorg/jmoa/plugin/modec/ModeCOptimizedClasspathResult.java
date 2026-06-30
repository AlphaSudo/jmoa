package com.yourorg.jmoa.plugin.modec;

import java.io.File;

public record ModeCOptimizedClasspathResult(
    File classpathFile,
    HybridPackagingSummary hybridPackagingSummary
) {
}
