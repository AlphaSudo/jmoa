package com.yourorg.jmoa.plugin.scanner;

import com.yourorg.jmoa.plugin.model.LambdaMeta;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;

public record LambdaSite(
    File classFile,
    ClassNode classNode,
    MethodNode methodNode,
    InvokeDynamicInsnNode indyInsn,
    int siteOrdinalInMethod,
    String indyName,
    String indyDesc,
    Type samMethodType,
    Handle implHandle,
    Type instantiatedMethodType
) {

    public boolean isCapturing() {
        return Type.getArgumentTypes(indyDesc).length > 0;
    }

    public boolean isSerializable() {
        return "altMetafactory".equals(indyInsn.bsm.getName());
    }

    public String packageInternalName() {
        int slash = classNode.name.lastIndexOf('/');
        return slash >= 0 ? classNode.name.substring(0, slash) : "";
    }

    public String siteKey() {
        return LambdaMeta.buildSiteKey(
            classNode.name,
            methodNode.name,
            methodNode.desc,
            siteOrdinalInMethod,
            indyName,
            indyDesc,
            implHandle.getTag(),
            implHandle.getOwner(),
            implHandle.getName(),
            implHandle.getDesc()
        );
    }

    public LambdaMeta toMeta() {
        return LambdaSiteMetadata.toMeta(this);
    }
}
