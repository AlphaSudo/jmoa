package com.yourorg.jmoa.plugin.generated;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public final class GeneratedClassClassifier {

    public GeneratedClassClassification classify(ClassNode classNode) {
        List<String> indicators = new ArrayList<>();
        String name = classNode.name == null ? "" : classNode.name;
        String dotted = name.replace('/', '.');
        boolean cglib = containsAny(name,
            "$$SpringCGLIB",
            "$$EnhancerBySpringCGLIB",
            "$$FastClass",
            "CGLIB$"
        ) || membersContain(classNode, "CGLIB$");
        if (cglib) {
            indicators.add("spring-cglib-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.SPRING_CGLIB, indicators, true);
        }

        if (containsAny(name,
            "__BeanDefinitions",
            "__ApplicationContextInitializer__BeanDefinitions",
            "__Autowiring"
        )) {
            indicators.add("spring-aot-bean-definitions-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS, indicators, true);
        }

        if (containsAny(name, "__BeanFactoryRegistrations", "__ApplicationContextInitializer", "__AotProcessor")) {
            indicators.add("spring-aot-registration-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.SPRING_AOT_REGISTRATION, indicators, true);
        }

        if (isSpringDataGenerated(name, dotted)) {
            indicators.add("spring-data-generated-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.SPRING_DATA_GENERATED, indicators, true);
        }

        if (name.startsWith("jdk/proxy")
            || name.startsWith("com/sun/proxy/$Proxy")
            || simpleName(name).startsWith("$Proxy")) {
            indicators.add("jdk-dynamic-proxy-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.JDK_PROXY, indicators, true);
        }

        if (containsAny(name, "ByteBuddy", "$ByteBuddy", "EnhancerByByteBuddy")) {
            indicators.add("bytebuddy-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.BYTEBUDDY, indicators, true);
        }

        if (containsAny(name, "HibernateProxy", "$HibernateProxy")) {
            indicators.add("hibernate-proxy-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.HIBERNATE_PROXY, indicators, true);
        }

        if (containsAny(name, "$$Lambda")) {
            indicators.add("lambda-hidden-class-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.LAMBDA_METAFATORY_SITE, indicators, true);
        }

        boolean hasLambdaMetafactory = hasLambdaMetafactoryCallSite(classNode);
        if (hasLambdaMetafactory) {
            indicators.add("lambda-metafactory-invokedynamic");
            return new GeneratedClassClassification(GeneratedClassFamily.LAMBDA_METAFATORY_SITE, indicators, true);
        }

        if (hasSyntheticOrBridgeMethod(classNode)) {
            indicators.add("synthetic-or-bridge-method");
            return new GeneratedClassClassification(GeneratedClassFamily.SYNTHETIC_BRIDGE_METHODS, indicators, true);
        }

        if (hasCompilerHelperMethod(classNode)) {
            indicators.add("compiler-helper-method");
            return new GeneratedClassClassification(GeneratedClassFamily.COMPILER_SYNTHETIC_HELPER, indicators, true);
        }

        if (isKotlinSynthetic(name)) {
            indicators.add("kotlin-synthetic-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.KOTLIN_SYNTHETIC, indicators, true);
        }

        if (isAnonymousInnerClass(name)) {
            indicators.add("anonymous-inner-class-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.ANONYMOUS_INNER_CLASS, indicators, true);
        }

        if ((classNode.access & Opcodes.ACC_SYNTHETIC) != 0
            && (classNode.nestHostClass != null || (classNode.nestMembers != null && !classNode.nestMembers.isEmpty()))) {
            indicators.add("nestmate-metadata-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.NESTMATE_GENERATED, indicators, true);
        }

        return new GeneratedClassClassification(GeneratedClassFamily.PLAIN, List.of(), false);
    }

    public GeneratedClassClassification classifyRuntimeClassName(String className) {
        String normalized = normalizeRuntimeName(className);
        List<String> indicators = new ArrayList<>();
        if (containsAny(normalized,
            "$$SpringCGLIB",
            "$$EnhancerBySpringCGLIB",
            "$$FastClass",
            "CGLIB$"
        )) {
            indicators.add("spring-cglib-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.SPRING_CGLIB, indicators, true);
        }
        if (containsAny(normalized,
            "__BeanDefinitions",
            "__ApplicationContextInitializer__BeanDefinitions",
            "__Autowiring"
        )) {
            indicators.add("spring-aot-bean-definitions-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS, indicators, true);
        }
        if (containsAny(normalized, "__BeanFactoryRegistrations", "__ApplicationContextInitializer", "__AotProcessor")) {
            indicators.add("spring-aot-registration-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.SPRING_AOT_REGISTRATION, indicators, true);
        }
        if (isSpringDataGenerated(normalized.replace('.', '/'), normalized)) {
            indicators.add("spring-data-generated-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.SPRING_DATA_GENERATED, indicators, true);
        }
        if (normalized.startsWith("jdk.proxy")
            || normalized.startsWith("com.sun.proxy.$Proxy")
            || simpleName(normalized.replace('.', '/')).startsWith("$Proxy")) {
            indicators.add("jdk-dynamic-proxy-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.JDK_PROXY, indicators, true);
        }
        if (containsAny(normalized, "ByteBuddy", "$ByteBuddy", "EnhancerByByteBuddy")) {
            indicators.add("bytebuddy-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.BYTEBUDDY, indicators, true);
        }
        if (containsAny(normalized, "HibernateProxy", "$HibernateProxy")) {
            indicators.add("hibernate-proxy-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.HIBERNATE_PROXY, indicators, true);
        }
        if (containsAny(normalized, "$$Lambda")) {
            indicators.add("lambda-hidden-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.LAMBDA_METAFATORY_SITE, indicators, true);
        }
        if (isKotlinSynthetic(normalized.replace('.', '/'))) {
            indicators.add("kotlin-synthetic-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.KOTLIN_SYNTHETIC, indicators, true);
        }
        if (isAnonymousInnerClass(normalized.replace('.', '/'))) {
            indicators.add("anonymous-inner-runtime-name-pattern");
            return new GeneratedClassClassification(GeneratedClassFamily.ANONYMOUS_INNER_CLASS, indicators, true);
        }
        return new GeneratedClassClassification(GeneratedClassFamily.PLAIN, indicators, false);
    }

    private static boolean membersContain(ClassNode classNode, String token) {
        if (classNode.fields != null) {
            for (Object field : classNode.fields) {
                String text = String.valueOf(field);
                if (text.contains(token)) {
                    return true;
                }
            }
        }
        if (classNode.methods != null) {
            for (MethodNode method : classNode.methods) {
                if ((method.name != null && method.name.contains(token))
                    || (method.desc != null && method.desc.contains(token))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasLambdaMetafactoryCallSite(ClassNode classNode) {
        if (classNode.methods == null) {
            return false;
        }
        for (MethodNode method : classNode.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof InvokeDynamicInsnNode indy) {
                    Handle bsm = indy.bsm;
                    if (bsm != null && "java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasSyntheticOrBridgeMethod(ClassNode classNode) {
        if (classNode.methods == null) {
            return false;
        }
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_SYNTHETIC) != 0 || (method.access & Opcodes.ACC_BRIDGE) != 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCompilerHelperMethod(ClassNode classNode) {
        if (classNode.methods == null) {
            return false;
        }
        for (MethodNode method : classNode.methods) {
            String name = method.name == null ? "" : method.name;
            if (name.startsWith("access$") || name.startsWith("lambda$") || "$deserializeLambda$".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSpringDataGenerated(String internalName, String dottedName) {
        return containsAny(internalName,
            "SpringData",
            "PropertyAccessor",
            "ObjectInstantiator",
            "__Repository",
            "__RepositoryFragments",
            "__RepositoryMetadata"
        ) || dottedName.contains("springframework.data")
            && containsAny(internalName, "__", "Accessor", "Instantiator", "Repository");
    }

    private static boolean isKotlinSynthetic(String internalName) {
        return internalName.contains("$DefaultImpls")
            || internalName.contains("$WhenMappings")
            || internalName.startsWith("kotlin/") && internalName.contains("$");
    }

    private static boolean isAnonymousInnerClass(String internalName) {
        String simple = simpleName(internalName);
        return simple.matches(".*\\$[0-9]+(?:\\$.*)?");
    }

    private static String simpleName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? internalName : internalName.substring(slash + 1);
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    static String normalizeRuntimeName(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        String normalized = className.trim();
        int moduleIndex = normalized.indexOf(" (");
        if (moduleIndex > 0) {
            normalized = normalized.substring(0, moduleIndex);
        }
        if (normalized.startsWith("[L") && normalized.endsWith(";")) {
            normalized = normalized.substring(2, normalized.length() - 1) + "[]";
        }
        return normalized.replace('/', '.');
    }
}
