package com.yourorg.jmoa.plugin.framework;

import com.yourorg.jmoa.plugin.ClassRootKind;

import java.util.List;

public record FrameworkSafetyDecision(
    String siteKey,
    String ownerClass,
    ClassRootKind rootKind,
    FrameworkSafetyLevel level,
    boolean allowed,
    List<FrameworkSafetyReason> reasons
) {

    public FrameworkSafetyDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static FrameworkSafetyDecision application(String siteKey, String ownerClass) {
        return new FrameworkSafetyDecision(
            siteKey,
            ownerClass,
            ClassRootKind.PROJECT_OUTPUT,
            FrameworkSafetyLevel.APPLICATION,
            true,
            List.of(FrameworkSafetyReason.PROJECT_OUTPUT_CLASS)
        );
    }

    public static FrameworkSafetyDecision allow(
        String siteKey,
        String ownerClass,
        ClassRootKind rootKind,
        FrameworkSafetyLevel level,
        List<FrameworkSafetyReason> reasons
    ) {
        return new FrameworkSafetyDecision(siteKey, ownerClass, rootKind, level, true, reasons);
    }

    public static FrameworkSafetyDecision deny(
        String siteKey,
        String ownerClass,
        ClassRootKind rootKind,
        FrameworkSafetyLevel level,
        List<FrameworkSafetyReason> reasons
    ) {
        return new FrameworkSafetyDecision(siteKey, ownerClass, rootKind, level, false, reasons);
    }
}
