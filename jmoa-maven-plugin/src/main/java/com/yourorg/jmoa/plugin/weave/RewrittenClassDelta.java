package com.yourorg.jmoa.plugin.weave;

public record RewrittenClassDelta(
    String classInternalName,
    String classFilePath,
    int originalClassBytes,
    int rewrittenClassBytes,
    int constantPoolCountBefore,
    int constantPoolCountAfter,
    String originalClassHash,
    String rewrittenClassHash
) {

    public int byteDelta() {
        return rewrittenClassBytes - originalClassBytes;
    }

    public int constantPoolDelta() {
        return constantPoolCountAfter - constantPoolCountBefore;
    }

    public boolean classBytesChanged() {
        return !originalClassHash.equals(rewrittenClassHash);
    }
}
