package com.yourorg.jmoa.plugin.reducer;

import java.util.List;

/** V2-Q admission controls metadata-only reduction, never proxy semantics. */
public final class GeneratedFamilyAdmissionPolicy {

    public GeneratedFamilyAssessment assess(String className) {
        String value = className == null ? "" : className;
        if (value.contains("$$SpringCGLIB") || value.contains("$$Enhancer") || value.contains("$$FastClass")) {
            return blocked("SPRING_CGLIB", GeneratedFamilyAdmission.BLOCK_SEMANTIC_RISK,
                "Spring CGLIB subclasses and dispatch helpers remain semantic-risk report-only classes.");
        }
        if (value.startsWith("jdk.proxy") || value.contains(".$Proxy") || value.contains("com.sun.proxy")) {
            return blocked("JDK_PROXY", GeneratedFamilyAdmission.BLOCK_RUNTIME_DYNAMIC,
                "JDK proxy classes are runtime contract types and are never reduced by V2-Q.");
        }
        if (value.contains("ByteBuddy") || value.contains("$ByteBuddy")) {
            return blocked("BYTE_BUDDY", GeneratedFamilyAdmission.BLOCK_SEMANTIC_RISK,
                "Byte Buddy-generated classes remain report-only because proxy semantics are not admitted.");
        }
        if (value.contains("HibernateProxy") || value.contains("$HibernateProxy")) {
            return blocked("HIBERNATE_PROXY", GeneratedFamilyAdmission.BLOCK_SEMANTIC_RISK,
                "Hibernate proxy classes remain report-only because persistence proxy semantics are not admitted.");
        }
        if (value.contains("__BeanDefinitions") || value.contains("__BeanFactoryRegistrations")
            || value.contains("__ApplicationContextInitializer") || value.contains("__Autowiring")) {
            return blocked("SPRING_AOT", GeneratedFamilyAdmission.REPORT_ONLY,
                "Spring AOT generated helpers are inventoried but not mutated by the first V2-Q admission.");
        }
        if (value.contains("$$Lambda") || value.contains("Lambda$") || value.contains("lambda$")) {
            return blocked("LAMBDA", GeneratedFamilyAdmission.BLOCK_RUNTIME_DYNAMIC,
                "Runtime lambda/hidden-class shapes are outside packaged application metadata reduction.");
        }
        if (value.contains("$DefaultImpls") || value.endsWith("Kt") || value.contains("$$serializer")) {
            return blocked("KOTLIN_SYNTHETIC", GeneratedFamilyAdmission.REPORT_ONLY,
                "Kotlin-generated shapes remain report-only until their metadata contracts are separately admitted.");
        }
        if (value.contains("$") || value.contains("access$")) {
            return blocked("JAVAC_SYNTHETIC", GeneratedFamilyAdmission.REPORT_ONLY,
                "Synthetic-looking packaged classes remain report-only unless explicitly admitted by a future policy.");
        }
        if (value.isBlank()) {
            return blocked("UNKNOWN_GENERATED", GeneratedFamilyAdmission.BLOCK_UNKNOWN,
                "Class name could not be read, so V2-Q refuses mutation.");
        }
        return new GeneratedFamilyAssessment("ORDINARY_APPLICATION", GeneratedFamilyAdmission.ALLOW_METADATA_ONLY,
            "Ordinary packaged application class is admitted for raw LVT/LVTT-only reduction.");
    }

    public List<GeneratedFamilyAssessment> taxonomy() {
        return List.of(
            new GeneratedFamilyAssessment("ORDINARY_APPLICATION", GeneratedFamilyAdmission.ALLOW_METADATA_ONLY,
                "Raw LVT/LVTT-only mutation is permitted with explicit application-class flags."),
            new GeneratedFamilyAssessment("SPRING_AOT", GeneratedFamilyAdmission.REPORT_ONLY,
                "No AOT semantic rewrite in V2-Q."),
            new GeneratedFamilyAssessment("SPRING_CGLIB", GeneratedFamilyAdmission.BLOCK_SEMANTIC_RISK,
                "No proxy method/body mutation."),
            new GeneratedFamilyAssessment("JDK_PROXY", GeneratedFamilyAdmission.BLOCK_RUNTIME_DYNAMIC,
                "No runtime-generated proxy mutation."),
            new GeneratedFamilyAssessment("BYTE_BUDDY", GeneratedFamilyAdmission.BLOCK_SEMANTIC_RISK,
                "No instrumentation/proxy mutation."),
            new GeneratedFamilyAssessment("HIBERNATE_PROXY", GeneratedFamilyAdmission.BLOCK_SEMANTIC_RISK,
                "No persistence proxy mutation."),
            new GeneratedFamilyAssessment("LAMBDA", GeneratedFamilyAdmission.BLOCK_RUNTIME_DYNAMIC,
                "No hidden/runtime-generated lambda mutation."),
            new GeneratedFamilyAssessment("KOTLIN_SYNTHETIC", GeneratedFamilyAdmission.REPORT_ONLY,
                "No Kotlin metadata mutation."),
            new GeneratedFamilyAssessment("JAVAC_SYNTHETIC", GeneratedFamilyAdmission.REPORT_ONLY,
                "No synthetic helper admission yet."),
            new GeneratedFamilyAssessment("UNKNOWN_GENERATED", GeneratedFamilyAdmission.BLOCK_UNKNOWN,
                "Unknown generated-like classes are not mutated."));
    }

    private static GeneratedFamilyAssessment blocked(String family, GeneratedFamilyAdmission admission, String reason) {
        return new GeneratedFamilyAssessment(family, admission, reason);
    }
}
