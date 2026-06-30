package com.yourorg.jmoa.plugin.scanner;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class LambdaScanner {

    public static List<LambdaSite> scanClassFile(File file) throws IOException {
        List<LambdaSite> sites = new ArrayList<>();
        try (InputStream is = new FileInputStream(file)) {
            ClassReader cr = new ClassReader(is);
            ClassNode classNode = new ClassNode();
            cr.accept(classNode, 0);

            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.instructions == null) {
                    continue;
                }
                int lambdaOrdinalInMethod = 0;
                for (AbstractInsnNode insn : methodNode.instructions) {
                    if (insn instanceof InvokeDynamicInsnNode indy) {
                        Handle bsm = indy.bsm;
                        if (bsm != null &&
                            "java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner()) &&
                            "metafactory".equals(bsm.getName())) {

                            // Check stateless: factory method signature has 0 parameters
                            Type[] factoryArgs = Type.getArgumentTypes(indy.desc);
                            if (factoryArgs.length == 0) {
                                Object[] bsmArgs = indy.bsmArgs;
                                if (bsmArgs != null && bsmArgs.length >= 3 &&
                                    bsmArgs[0] instanceof Type samType &&
                                    bsmArgs[1] instanceof Handle implHandle &&
                                    bsmArgs[2] instanceof Type instantiatedType) {

                                    sites.add(new LambdaSite(
                                        file,
                                        classNode,
                                        methodNode,
                                        indy,
                                        lambdaOrdinalInMethod++,
                                        indy.name,
                                        indy.desc,
                                        samType,
                                        implHandle,
                                        instantiatedType
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
        return sites;
    }

    public static ScanResult scanClassFiles(List<File> files) throws IOException {
        List<LambdaSite> allSites = new ArrayList<>();
        int totalLambdaSites = 0;
        int skippedCapturing = 0;
        int skippedSerializable = 0;

        for (File file : files) {
            try (InputStream is = new FileInputStream(file)) {
                ClassReader cr = new ClassReader(is);
                ClassNode classNode = new ClassNode();
                cr.accept(classNode, 0);

                for (MethodNode methodNode : classNode.methods) {
                    if (methodNode.instructions == null) {
                        continue;
                    }
                    int lambdaOrdinalInMethod = 0;
                    for (AbstractInsnNode insn : methodNode.instructions) {
                        if (insn instanceof InvokeDynamicInsnNode indy) {
                            Handle bsm = indy.bsm;
                            if (bsm != null && "java/lang/invoke/LambdaMetafactory".equals(bsm.getOwner())) {
                                totalLambdaSites++;

                                if (!"metafactory".equals(bsm.getName())) {
                                    skippedSerializable++;
                                    continue;
                                }

                                Type[] factoryArgs = Type.getArgumentTypes(indy.desc);
                                if (factoryArgs.length > 0) {
                                    skippedCapturing++;
                                    continue;
                                }

                                Object[] bsmArgs = indy.bsmArgs;
                                if (bsmArgs != null && bsmArgs.length >= 3 &&
                                    bsmArgs[0] instanceof Type samType &&
                                    bsmArgs[1] instanceof Handle implHandle &&
                                    bsmArgs[2] instanceof Type instantiatedType) {

                                    allSites.add(new LambdaSite(
                                        file,
                                        classNode,
                                        methodNode,
                                        indy,
                                        lambdaOrdinalInMethod++,
                                        indy.name,
                                        indy.desc,
                                        samType,
                                        implHandle,
                                        instantiatedType
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }
        return new ScanResult(allSites, totalLambdaSites, skippedCapturing, skippedSerializable);
    }
}
