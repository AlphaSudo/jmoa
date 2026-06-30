package com.yourorg.jmoa.plugin.synth;

import com.yourorg.jmoa.plugin.dedup.SharedGroup;
import com.yourorg.jmoa.plugin.scanner.LambdaSite;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.*;

public final class ClassPatcher {

    public static byte[] patchClass(
            File classFile,
            List<LambdaSite> sitesToPatch,
            Map<LambdaSite, SharedGroup> siteToGroupMap,
            ClassLoader projectClassLoader) throws IOException {

        try (InputStream is = new FileInputStream(classFile)) {
            ClassReader cr = new ClassReader(is);
            ClassNode classNode = new ClassNode();
            cr.accept(classNode, 0);

            boolean modified = false;

            // Step 1: Widen private synthetic lambda methods
            for (LambdaSite site : sitesToPatch) {
                SharedGroup group = siteToGroupMap.get(site);
                if (group != null && group.needsAccessWidening()) {
                    for (MethodNode method : classNode.methods) {
                        if (method.name.equals(group.key().implName()) &&
                            method.desc.equals(group.key().implDesc())) {
                            
                            int oldAccess = method.access;
                            method.access = (method.access & ~Opcodes.ACC_PRIVATE);
                            if (method.access != oldAccess) {
                                modified = true;
                            }
                        }
                    }
                }
            }

            // Step 2: Replace InvokeDynamicInsnNode with GETSTATIC
            for (MethodNode method : classNode.methods) {
                if (method.instructions == null) {
                    continue;
                }
                ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
                while (iterator.hasNext()) {
                    AbstractInsnNode insn = iterator.next();
                    if (insn instanceof InvokeDynamicInsnNode indy) {
                        LambdaSite matchingSite = findMatchingSite(method.name, method.desc, indy, sitesToPatch);
                        if (matchingSite != null) {
                            SharedGroup group = siteToGroupMap.get(matchingSite);
                            if (group != null) {
                                String synthClassName = group.synthClassName();
                                String samDesc = Type.getReturnType(indy.desc).getDescriptor();

                                FieldInsnNode getStaticInsn = new FieldInsnNode(
                                    Opcodes.GETSTATIC,
                                    synthClassName,
                                    "INSTANCE",
                                    samDesc
                                );

                                method.instructions.set(indy, getStaticInsn);
                                modified = true;
                            }
                        }
                    }
                }
            }

            if (!modified) {
                return null;
            }

            ProjectAwareClassWriter cw = new ProjectAwareClassWriter(cr, ClassWriter.COMPUTE_FRAMES, projectClassLoader);
            classNode.accept(cw);
            return cw.toByteArray();
        }
    }

    private static LambdaSite findMatchingSite(String methodName, String methodDesc, InvokeDynamicInsnNode indy, List<LambdaSite> sites) {
        for (LambdaSite site : sites) {
            if (site.methodNode().name.equals(methodName) &&
                site.methodNode().desc.equals(methodDesc) &&
                site.indyInsn().name.equals(indy.name) &&
                site.indyInsn().desc.equals(indy.desc) &&
                Objects.equals(site.indyInsn().bsm, indy.bsm) &&
                bsmArgsEqual(site.indyInsn().bsmArgs, indy.bsmArgs)) {
                return site;
            }
        }
        return null;
    }

    private static boolean bsmArgsEqual(Object[] args1, Object[] args2) {
        if (args1 == args2) return true;
        if (args1 == null || args2 == null) return false;
        if (args1.length != args2.length) return false;
        for (int i = 0; i < args1.length; i++) {
            Object o1 = args1[i];
            Object o2 = args2[i];
            if (o1 instanceof Handle h1 && o2 instanceof Handle h2) {
                if (!handlesEqual(h1, h2)) return false;
            } else if (!Objects.equals(o1, o2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean handlesEqual(Handle h1, Handle h2) {
        return h1.getTag() == h2.getTag() &&
               h1.getOwner().equals(h2.getOwner()) &&
               h1.getName().equals(h2.getName()) &&
               h1.getDesc().equals(h2.getDesc()) &&
               h1.isInterface() == h2.isInterface();
    }
}
