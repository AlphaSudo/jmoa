package com.yourorg.jmoa.plugin.weave;

import com.yourorg.jmoa.plugin.synth.ProjectAwareClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class LambdaClassWeaver {

    public byte[] weaveBytes(byte[] originalBytes, String classInternalName, LambdaWeavingPlan weavingPlan, ClassLoader projectClassLoader) {
        if (weavingPlan.isEmpty()) {
            return originalBytes;
        }

        ClassReader reader = new ClassReader(originalBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        widenTier2SyntheticTargets(classNode, classInternalName, weavingPlan);

        ProjectAwareClassWriter writer = new ProjectAwareClassWriter(
            reader,
            ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS,
            projectClassLoader
        );
        LambdaWeavingClassVisitor visitor = new LambdaWeavingClassVisitor(
            org.objectweb.asm.Opcodes.ASM9,
            writer,
            classInternalName,
            weavingPlan
        );
        classNode.accept(visitor);
        return writer.toByteArray();
    }

    public byte[] weaveClass(File classFile, LambdaWeavingPlan weavingPlan, ClassLoader projectClassLoader) throws IOException {
        if (weavingPlan.isEmpty()) {
            return Files.readAllBytes(classFile.toPath());
        }

        byte[] originalBytes = Files.readAllBytes(classFile.toPath());
        ClassReader reader = new ClassReader(originalBytes);
        return weaveBytes(originalBytes, reader.getClassName(), weavingPlan, projectClassLoader);
    }

    private void widenTier2SyntheticTargets(ClassNode classNode, String classInternalName, LambdaWeavingPlan weavingPlan) {
        boolean interfaceOwner = (classNode.access & Opcodes.ACC_INTERFACE) != 0;
        for (LambdaWeaveTarget target : weavingPlan.targetsBySiteKey().values()) {
            if (target.kind() != LambdaWeaveTarget.WeaveKind.TIER2_ADAPTER
                || !target.requiresAccessWidening()
                || !classInternalName.equals(target.implOwner())) {
                continue;
            }

            for (MethodNode method : classNode.methods) {
                if (method.name.equals(target.implName()) && method.desc.equals(target.implDesc())) {
                    method.access = method.access & ~Opcodes.ACC_PRIVATE;
                    if (interfaceOwner) {
                        method.access = method.access | Opcodes.ACC_PUBLIC;
                    }
                }
            }
        }
    }
}
