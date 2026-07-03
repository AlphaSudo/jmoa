package com.yourorg.jmoa.plugin.reducer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class LocalVariableDebugAttributeReducer {

    public byte[] reduce(byte[] originalBytes) {
        ClassReader reader = new ClassReader(originalBytes);
        ClassWriter writer = new ClassWriter(0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
            ) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitLocalVariable(
                        String name,
                        String descriptor,
                        String signature,
                        Label start,
                        Label end,
                        int index
                    ) {
                        // Intentionally suppress LocalVariableTable and LocalVariableTypeTable only.
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        return writer.toByteArray();
    }
}
