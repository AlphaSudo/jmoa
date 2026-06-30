package com.yourorg.jmoa.plugin.dedup;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class AccessResolver {

    public enum Visibility {
        PUBLIC,
        PROTECTED,
        PACKAGE_PRIVATE,
        PRIVATE,
        UNKNOWN
    }

    public static Visibility resolveVisibility(Handle handle, ClassLoader cl) {
        try {
            String className = handle.getOwner().replace('/', '.');
            Class<?> clazz = Class.forName(className, false, cl);
            Visibility classVis = getClassVisibility(clazz);

            Visibility memberVis = getMemberVisibility(clazz, handle, cl);

            // The effective visibility is the more restrictive of class and member visibility
            return getMinVisibility(classVis, memberVis);
        } catch (Throwable t) {
            // If anything fails to load, mark as UNKNOWN (safely skipped or restricted)
            return Visibility.UNKNOWN;
        }
    }

    private static Visibility getClassVisibility(Class<?> clazz) {
        int mods = clazz.getModifiers();
        if (Modifier.isPublic(mods)) {
            return Visibility.PUBLIC;
        } else if (Modifier.isProtected(mods)) {
            return Visibility.PROTECTED;
        } else if (Modifier.isPrivate(mods)) {
            return Visibility.PRIVATE;
        } else {
            return Visibility.PACKAGE_PRIVATE;
        }
    }

    private static Visibility getMemberVisibility(Class<?> clazz, Handle handle, ClassLoader cl) throws ClassNotFoundException {
        int tag = handle.getTag();
        String name = handle.getName();
        String desc = handle.getDesc();

        Type[] asmArgTypes = Type.getArgumentTypes(desc);
        Class<?>[] paramClasses = new Class<?>[asmArgTypes.length];
        for (int i = 0; i < asmArgTypes.length; i++) {
            paramClasses[i] = getClassForType(asmArgTypes[i], cl);
        }

        int mods = 0;
        boolean resolved = false;

        switch (tag) {
            case Opcodes.H_INVOKEVIRTUAL, Opcodes.H_INVOKESTATIC, Opcodes.H_INVOKESPECIAL, Opcodes.H_INVOKEINTERFACE -> {
                Method method = findMethod(clazz, name, paramClasses);
                if (method != null) {
                    mods = method.getModifiers();
                    resolved = true;
                }
            }
            case Opcodes.H_NEWINVOKESPECIAL -> {
                try {
                    Constructor<?> constructor = clazz.getDeclaredConstructor(paramClasses);
                    mods = constructor.getModifiers();
                    resolved = true;
                } catch (NoSuchMethodException e) {
                    // ignore
                }
            }
            case Opcodes.H_GETFIELD, Opcodes.H_GETSTATIC, Opcodes.H_PUTFIELD, Opcodes.H_PUTSTATIC -> {
                Field field = findField(clazz, name);
                if (field != null) {
                    mods = field.getModifiers();
                    resolved = true;
                }
            }
        }

        if (!resolved) {
            return Visibility.UNKNOWN;
        }

        if (Modifier.isPublic(mods)) {
            return Visibility.PUBLIC;
        } else if (Modifier.isProtected(mods)) {
            return Visibility.PROTECTED;
        } else if (Modifier.isPrivate(mods)) {
            return Visibility.PRIVATE;
        } else {
            return Visibility.PACKAGE_PRIVATE;
        }
    }

    private static Visibility getMinVisibility(Visibility v1, Visibility v2) {
        if (v1 == Visibility.UNKNOWN || v2 == Visibility.UNKNOWN) {
            return Visibility.UNKNOWN;
        }
        if (v1 == Visibility.PRIVATE || v2 == Visibility.PRIVATE) {
            return Visibility.PRIVATE;
        }
        if (v1 == Visibility.PACKAGE_PRIVATE || v2 == Visibility.PACKAGE_PRIVATE) {
            return Visibility.PACKAGE_PRIVATE;
        }
        if (v1 == Visibility.PROTECTED || v2 == Visibility.PROTECTED) {
            return Visibility.PROTECTED;
        }
        return Visibility.PUBLIC;
    }

    private static Class<?> getClassForType(Type type, ClassLoader cl) throws ClassNotFoundException {
        if (type.getSort() == Type.OBJECT) {
            return Class.forName(type.getClassName(), false, cl);
        } else if (type.getSort() == Type.ARRAY) {
            return Class.forName(type.getDescriptor().replace('/', '.'), false, cl);
        } else {
            return switch (type.getSort()) {
                case Type.BOOLEAN -> boolean.class;
                case Type.BYTE -> byte.class;
                case Type.CHAR -> char.class;
                case Type.SHORT -> short.class;
                case Type.INT -> int.class;
                case Type.LONG -> long.class;
                case Type.FLOAT -> float.class;
                case Type.DOUBLE -> double.class;
                case Type.VOID -> void.class;
                default -> throw new IllegalArgumentException("Unknown type sort: " + type.getSort());
            };
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>[] argTypes) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, argTypes);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        // Search interfaces for default/abstract interface methods
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                return iface.getMethod(name, argTypes);
            } catch (NoSuchMethodException e) {
                // ignore
            }
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
