package com.yourorg.jmoa.plugin.synth;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class ProjectAwareClassWriter extends ClassWriter {
    private final ClassLoader projectClassLoader;

    public ProjectAwareClassWriter(int flags, ClassLoader projectClassLoader) {
        super(flags);
        this.projectClassLoader = projectClassLoader != null ? projectClassLoader : getClass().getClassLoader();
    }

    public ProjectAwareClassWriter(ClassReader classReader, int flags, ClassLoader projectClassLoader) {
        super(classReader, flags);
        this.projectClassLoader = projectClassLoader != null ? projectClassLoader : getClass().getClassLoader();
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            Class<?> c1 = Class.forName(type1.replace('/', '.'), false, projectClassLoader);
            Class<?> c2 = Class.forName(type2.replace('/', '.'), false, projectClassLoader);
            if (c1.isAssignableFrom(c2)) {
                return type1;
            }
            if (c2.isAssignableFrom(c1)) {
                return type2;
            }
            if (c1.isInterface() || c2.isInterface()) {
                return "java/lang/Object";
            }
            do {
                c1 = c1.getSuperclass();
            } while (c1 != null && !c1.isAssignableFrom(c2));
            if (c1 == null) {
                return "java/lang/Object";
            }
            return c1.getName().replace('.', '/');
        } catch (ClassNotFoundException e) {
            return "java/lang/Object";
        }
    }
}
