package com.yourorg.jmoa.plugin.generated.relevance;

import com.yourorg.jmoa.plugin.generated.GeneratedClassFamily;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Stable V2-S policy registry. It describes discovery and never authorizes a transform. */
public final class GeneratedFamilyRegistry {

    public enum AdmissionState {
        GENERATED_REPORT_ONLY,
        GENERATED_MUTATION_BLOCKED
    }

    public record Definition(
        GeneratedClassFamily family,
        String runtimeNamingPattern,
        String semanticRole,
        String mutationRisk,
        AdmissionState defaultAdmissionState,
        List<String> requiredProofBeforePrototype
    ) {
        public Definition {
            runtimeNamingPattern = runtimeNamingPattern == null ? "" : runtimeNamingPattern;
            semanticRole = semanticRole == null ? "" : semanticRole;
            mutationRisk = mutationRisk == null ? "UNKNOWN" : mutationRisk;
            defaultAdmissionState = defaultAdmissionState == null
                ? AdmissionState.GENERATED_REPORT_ONLY
                : defaultAdmissionState;
            requiredProofBeforePrototype = requiredProofBeforePrototype == null
                ? List.of()
                : List.copyOf(requiredProofBeforePrototype);
        }
    }

    private static final List<String> STANDARD_PROOF = List.of(
        "diagnostic runtime relevance capture",
        "family-specific semantic test plan",
        "bounded mutation concept",
        "V2-C confirmation plan",
        "V2-D attribution plan"
    );

    private final Map<GeneratedClassFamily, Definition> definitions;

    public GeneratedFamilyRegistry() {
        Map<GeneratedClassFamily, Definition> values = new EnumMap<>(GeneratedClassFamily.class);
        values.put(GeneratedClassFamily.LAMBDA_METAFATORY_SITE, reportOnly(GeneratedClassFamily.LAMBDA_METAFATORY_SITE, "$$Lambda / LambdaMetafactory", "runtime lambda implementation or call-site shape", "MEDIUM"));
        values.put(GeneratedClassFamily.SPRING_DATA_GENERATED, reportOnly(GeneratedClassFamily.SPRING_DATA_GENERATED, "SpringData / PropertyAccessor / ObjectInstantiator", "Spring Data generated accessor or repository helper", "MEDIUM"));
        values.put(GeneratedClassFamily.SYNTHETIC_BRIDGE_METHODS, reportOnly(GeneratedClassFamily.SYNTHETIC_BRIDGE_METHODS, "ACC_SYNTHETIC / ACC_BRIDGE", "compiler dispatch or generic bridge helper", "MEDIUM"));
        values.put(GeneratedClassFamily.COMPILER_SYNTHETIC_HELPER, reportOnly(GeneratedClassFamily.COMPILER_SYNTHETIC_HELPER, "access$ / lambda$ / $deserializeLambda$", "compiler-generated access or lambda helper", "MEDIUM"));
        values.put(GeneratedClassFamily.KOTLIN_SYNTHETIC, reportOnly(GeneratedClassFamily.KOTLIN_SYNTHETIC, "$DefaultImpls / $WhenMappings", "Kotlin compiler synthetic helper", "MEDIUM"));
        values.put(GeneratedClassFamily.ANONYMOUS_INNER_CLASS, reportOnly(GeneratedClassFamily.ANONYMOUS_INNER_CLASS, "$<number>", "anonymous inner implementation", "MEDIUM"));
        values.put(GeneratedClassFamily.NESTMATE_GENERATED, reportOnly(GeneratedClassFamily.NESTMATE_GENERATED, "NestHost / NestMembers", "nestmate metadata-bearing class", "MEDIUM"));
        values.put(GeneratedClassFamily.UNKNOWN_GENERATED, reportOnly(GeneratedClassFamily.UNKNOWN_GENERATED, "unknown", "unclassified generated surface", "UNKNOWN"));
        values.put(GeneratedClassFamily.SPRING_CGLIB, blocked(GeneratedClassFamily.SPRING_CGLIB, "$$SpringCGLIB / CGLIB$", "Spring interception subclass", "HIGH"));
        values.put(GeneratedClassFamily.JDK_PROXY, blocked(GeneratedClassFamily.JDK_PROXY, "jdk.proxy / $Proxy", "JDK interface proxy contract", "HIGH"));
        values.put(GeneratedClassFamily.BYTEBUDDY, blocked(GeneratedClassFamily.BYTEBUDDY, "ByteBuddy", "runtime instrumentation/proxy helper", "HIGH"));
        values.put(GeneratedClassFamily.HIBERNATE_PROXY, blocked(GeneratedClassFamily.HIBERNATE_PROXY, "HibernateProxy", "ORM lazy-loading proxy", "HIGH"));
        values.put(GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS, blocked(GeneratedClassFamily.SPRING_AOT_BEAN_DEFINITIONS, "__BeanDefinitions", "Spring AOT bean-registration implementation", "HIGH"));
        values.put(GeneratedClassFamily.SPRING_AOT_REGISTRATION, blocked(GeneratedClassFamily.SPRING_AOT_REGISTRATION, "__BeanFactoryRegistrations / __ApplicationContextInitializer", "Spring AOT context-registration implementation", "HIGH"));
        values.put(GeneratedClassFamily.PLAIN, reportOnly(GeneratedClassFamily.PLAIN, "none", "non-generated accounting record", "UNKNOWN"));
        definitions = Map.copyOf(values);
    }

    public Definition definitionFor(GeneratedClassFamily family) {
        return definitions.getOrDefault(family, reportOnly(family, "unknown", "unclassified generated surface", "UNKNOWN"));
    }

    public List<Definition> definitions() {
        return definitions.values().stream().sorted((left, right) -> left.family().compareTo(right.family())).toList();
    }

    private static Definition reportOnly(GeneratedClassFamily family, String pattern, String role, String risk) {
        return new Definition(family, pattern, role, risk, AdmissionState.GENERATED_REPORT_ONLY, STANDARD_PROOF);
    }

    private static Definition blocked(GeneratedClassFamily family, String pattern, String role, String risk) {
        return new Definition(family, pattern, role, risk, AdmissionState.GENERATED_MUTATION_BLOCKED, STANDARD_PROOF);
    }
}
