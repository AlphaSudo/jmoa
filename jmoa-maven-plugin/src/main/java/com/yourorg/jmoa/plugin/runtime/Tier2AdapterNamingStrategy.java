package com.yourorg.jmoa.plugin.runtime;

import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Tier2AdapterNamingStrategy {

    private Tier2AdapterNamingStrategy() {
    }

    public static String internalName(LambdaMeta meta) {
        String pkg = meta.packageInternalName();
        String ownerSimple = simpleName(meta.ownerInternalName());
        String hash = shortHash(meta.siteKey());
        String className = "JmoaPkgAdapters$" + sanitize(ownerSimple) + "$S" + meta.siteOrdinalInMethod() + "_" + hash;
        if (pkg == null || pkg.isEmpty()) {
            return className;
        }
        return pkg + "/" + className;
    }

    public static String packageSamInternalName(LambdaMeta meta) {
        String pkg = meta.packageInternalName();
        String samSimple = simpleName(meta.samInterfaceInternalName());
        String className = "JmoaPkgAdapters$" + sanitize(samSimple);
        if (pkg == null || pkg.isEmpty()) {
            return className;
        }
        return pkg + "/" + className;
    }

    /**
     * Returns the internal name for a PACKAGE-mode adapter: one adapter class per
     * package, regardless of SAM type.  Falls back to {@link #packageSamInternalName}
     * when the caller needs the package+SAM grouping.
     */
    public static String packageOnlyInternalName(LambdaMeta meta) {
        String pkg = meta.packageInternalName();
        String className = "JmoaPkgAdapters";
        if (pkg == null || pkg.isEmpty()) {
            return className;
        }
        return pkg + "/" + className;
    }

    /**
     * PACKAGE_SIGNATURE mode: one adapter class per package + erased SAM method
     * descriptor.  Merges SAM interfaces with the same erased method shape
     * (e.g. Function and UnaryOperator both have erased desc "(Ljava/lang/Object;)Ljava/lang/Object;").
     */
    public static String packageSignatureInternalName(LambdaMeta meta) {
        String pkg = meta.packageInternalName();
        String sigTag = shortHash(eraseSamMethodDesc(meta.samMethodTypeDesc()));
        String className = "JmoaPkgAdapters$" + sigTag;
        if (pkg == null || pkg.isEmpty()) {
            return className;
        }
        return pkg + "/" + className;
    }

    /**
     * ROOT_PACKAGE_SIGNATURE mode (track T4): one adapter class per physical
     * class root + package + erased SAM descriptor. The root ordinal is the
     * adapter's position in the frozen output-root list, so names are
     * deterministic for a build without embedding absolute paths. Keying by
     * root retains compactness inside a root while preventing multi-root
     * fan-out and cross-root adapter references by construction.
     */
    public static String rootPackageSignatureInternalName(LambdaMeta meta, int rootOrdinal) {
        return rootPackageSignatureInternalName(meta, rootOrdinal, false);
    }

    public static String rootPackageSignatureInternalName(LambdaMeta meta, int rootOrdinal, boolean compact) {
        String pkg = meta.packageInternalName();
        String sigTag = collisionResistantTag(eraseSamMethodDesc(meta.samMethodTypeDesc()));
        String className = (compact ? "J$R" : "JmoaPkgAdapters$R") + rootOrdinal + "_" + sigTag;
        if (pkg == null || pkg.isEmpty()) {
            return className;
        }
        return pkg + "/" + className;
    }

    public static String fieldName(LambdaMeta meta) {
        return "INSTANCE_S" + meta.siteOrdinalInMethod() + "_" + shortHash(meta.siteKey());
    }

    public static String compactFieldName(LambdaMeta meta) {
        return "f" + meta.siteOrdinalInMethod() + "_" + shortHash(meta.siteKey());
    }

    /**
     * Erase a SAM method descriptor: replace all reference types with
     * java/lang/Object, keeping primitives unchanged.
     */
    public static String eraseSamMethodDesc(String samMethodDesc) {
        Type retType = Type.getReturnType(samMethodDesc);
        Type[] argTypes = Type.getArgumentTypes(samMethodDesc);
        StringBuilder sb = new StringBuilder("(");
        for (Type arg : argTypes) {
            sb.append(eraseType(arg).getDescriptor());
        }
        sb.append(')').append(eraseType(retType).getDescriptor());
        return sb.toString();
    }

    private static Type eraseType(Type t) {
        if (t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY) {
            if (t.getSort() == Type.ARRAY) {
                // Keep array dimension, erase element type
                int dims = 0;
                Type elem = t;
                while (elem.getSort() == Type.ARRAY) {
                    dims++;
                    elem = elem.getElementType();
                }
                Type erasedElem = eraseType(elem);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < dims; i++) sb.append('[');
                sb.append(erasedElem.getDescriptor());
                return Type.getType(sb.toString());
            }
            return Type.getObjectType("java/lang/Object");
        }
        return t; // primitives unchanged
    }

    private static String simpleName(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash >= 0 ? internalName.substring(lastSlash + 1) : internalName;
    }

    private static String sanitize(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isJavaIdentifierPart(c)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        if (sb.isEmpty() || !Character.isJavaIdentifierStart(sb.charAt(0))) {
            sb.insert(0, 'X');
        }
        return sb.toString();
    }

    private static String shortHash(String value) {
        return hashPrefix(value, 3);
    }

    private static String collisionResistantTag(String value) {
        // T4 is new, so use 96 bits rather than inheriting the historical
        // 24-bit A-D tag. Generation also verifies logical-key/name uniqueness.
        return hashPrefix(value, 12);
    }

    private static String hashPrefix(String value, int byteCount) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < byteCount; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // --- Compact naming variants ---

    public static String compactPackageSamInternalName(LambdaMeta meta) {
        String pkg = meta.packageInternalName();
        String samSimple = simpleName(meta.samInterfaceInternalName());
        String className = "J$" + sanitize(samSimple);
        if (pkg == null || pkg.isEmpty()) {
            return className;
        }
        return pkg + "/" + className;
    }

    public static String compactPackageSignatureInternalName(LambdaMeta meta) {
        String pkg = meta.packageInternalName();
        String sigTag = shortHash(eraseSamMethodDesc(meta.samMethodTypeDesc()));
        String className = "J$" + sigTag;
        if (pkg == null || pkg.isEmpty()) {
            return className;
        }
        return pkg + "/" + className;
    }
}
