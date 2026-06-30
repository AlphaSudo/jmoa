package com.yourorg.jmoa.plugin.filter;

import com.yourorg.jmoa.plugin.ClassRootDescriptor;
import com.yourorg.jmoa.plugin.ClassRootKind;
import com.yourorg.jmoa.plugin.scanner.LambdaSite;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LambdaSourceIndex {

    private final Map<String, ClassRootDescriptor> byOwnerInternalName;

    private LambdaSourceIndex(Map<String, ClassRootDescriptor> byOwnerInternalName) {
        this.byOwnerInternalName = Map.copyOf(byOwnerInternalName);
    }

    public static LambdaSourceIndex empty() {
        return new LambdaSourceIndex(Map.of());
    }

    public static LambdaSourceIndex fromSites(List<LambdaSite> sites, List<ClassRootDescriptor> roots) {
        Map<String, ClassRootDescriptor> map = new HashMap<>();
        for (LambdaSite site : sites) {
            for (ClassRootDescriptor root : roots) {
                if (isUnderRoot(site.classFile(), root.rootDirectory())) {
                    map.putIfAbsent(site.classNode().name, root);
                    break;
                }
            }
        }
        return new LambdaSourceIndex(map);
    }

    public static LambdaSourceIndex of(Map<String, ClassRootDescriptor> byOwnerInternalName) {
        return new LambdaSourceIndex(byOwnerInternalName);
    }

    public ClassRootDescriptor rootFor(String ownerInternalName) {
        return byOwnerInternalName.get(ownerInternalName);
    }

    public ClassRootKind rootKindFor(String ownerInternalName) {
        ClassRootDescriptor root = rootFor(ownerInternalName);
        return root == null ? ClassRootKind.PROJECT_OUTPUT : root.kind();
    }

    private static boolean isUnderRoot(File file, File rootDirectory) {
        try {
            String filePath = file.getCanonicalPath();
            String rootPath = rootDirectory.getCanonicalPath();
            return filePath.equals(rootPath) || filePath.startsWith(rootPath + File.separator);
        } catch (IOException e) {
            return false;
        }
    }
}
