package com.yourorg.jmoa.plugin.size;

import java.util.List;

public record MethodSizeRecord(
    String className,
    String methodName,
    String descriptor,
    List<String> accessFlags,
    boolean synthetic,
    boolean bridge,
    boolean staticInitializer,
    int codeLength,
    MethodSizeRisk threshold,
    int maxStack,
    int maxLocals,
    int exceptionTableLength,
    int instructionCount,
    int branchInstructionCount,
    int switchInstructionCount,
    int invokeInstructionCount,
    int invokedynamicInstructionCount,
    int ldcInstructionCount,
    long annotationBytes,
    long stackMapTableBytes,
    long lineNumberTableBytes,
    long localVariableTableBytes
) {

    public MethodSizeRecord {
        accessFlags = accessFlags == null ? List.of() : List.copyOf(accessFlags);
    }
}
