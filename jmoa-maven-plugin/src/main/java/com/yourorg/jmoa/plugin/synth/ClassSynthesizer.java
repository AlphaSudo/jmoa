package com.yourorg.jmoa.plugin.synth;

import com.yourorg.jmoa.plugin.dedup.DeduplicationKey;
import com.yourorg.jmoa.plugin.dedup.SharedGroup;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;

public final class ClassSynthesizer {

    public static byte[] synthesize(SharedGroup group) {
        DeduplicationKey key = group.key();
        String internalName = group.synthClassName();
        String samInterface = key.samInterfaceInternalName();
        String samDesc = key.samMethodDescriptor();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // Class access is package-private if package-scoped, else public
        int classAccess = Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        if (group.targetPackageInternal() == null) {
            classAccess |= Opcodes.ACC_PUBLIC;
        }

        cw.visit(Opcodes.V17, classAccess, internalName, null, "java/lang/Object", new String[]{samInterface});

        // 1. Static field INSTANCE
        String fieldDesc = "L" + samInterface + ";";
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "INSTANCE", fieldDesc, null, null).visitEnd();

        // 2. Private Constructor
        MethodVisitor mvConstructor = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        GeneratorAdapter gaConstructor = new GeneratorAdapter(mvConstructor, Opcodes.ACC_PRIVATE, "<init>", "()V");
        gaConstructor.visitCode();
        gaConstructor.loadThis();
        gaConstructor.invokeConstructor(Type.getType(Object.class), new org.objectweb.asm.commons.Method("<init>", "()V"));
        gaConstructor.returnValue();
        gaConstructor.endMethod();

        // 3. Static Initializer <clinit>
        MethodVisitor mvClinit = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        GeneratorAdapter gaClinit = new GeneratorAdapter(mvClinit, Opcodes.ACC_STATIC, "<clinit>", "()V");
        gaClinit.visitCode();
        Type classType = Type.getObjectType(internalName);
        gaClinit.newInstance(classType);
        gaClinit.dup();
        gaClinit.invokeConstructor(classType, new org.objectweb.asm.commons.Method("<init>", "()V"));
        gaClinit.putStatic(classType, "INSTANCE", Type.getType(fieldDesc));
        gaClinit.returnValue();
        gaClinit.endMethod();

        // 4. SAM Method Implementation
        // Find the method name of the functional interface.
        // Usually, the factory method name is the SAM name (e.g. indyName like "apply" or "run").
        String samName = group.sites().get(0).indyName();

        MethodVisitor mvSam = cw.visitMethod(Opcodes.ACC_PUBLIC, samName, samDesc, null, null);
        GeneratorAdapter gaSam = new GeneratorAdapter(mvSam, Opcodes.ACC_PUBLIC, samName, samDesc);
        gaSam.visitCode();

        Type[] samArgs = Type.getArgumentTypes(samDesc);
        Type[] instArgs = Type.getArgumentTypes(key.instantiatedMethodType());
        Type[] implArgs = Type.getArgumentTypes(key.implDesc());

        int tag = key.implTag();

        if (tag == Opcodes.H_NEWINVOKESPECIAL) {
            Type objType = Type.getObjectType(key.implOwner());
            gaSam.newInstance(objType);
            gaSam.dup();
            for (int i = 0; i < implArgs.length; i++) {
                gaSam.loadArg(i);
                convertType(gaSam, samArgs[i], instArgs[i]);
                convertType(gaSam, instArgs[i], implArgs[i]);
            }
            gaSam.invokeConstructor(objType, new org.objectweb.asm.commons.Method("<init>", key.implDesc()));
        } else if (tag >= Opcodes.H_GETFIELD && tag <= Opcodes.H_PUTSTATIC) {
            Type fieldType = Type.getType(key.implDesc());
            Type ownerType = Type.getObjectType(key.implOwner());
            if (tag == Opcodes.H_GETSTATIC) {
                gaSam.getStatic(ownerType, key.implName(), fieldType);
            } else if (tag == Opcodes.H_GETFIELD) {
                gaSam.loadArg(0);
                convertType(gaSam, samArgs[0], instArgs[0]);
                convertType(gaSam, instArgs[0], ownerType);
                gaSam.getField(ownerType, key.implName(), fieldType);
            } else if (tag == Opcodes.H_PUTSTATIC) {
                gaSam.loadArg(0);
                convertType(gaSam, samArgs[0], instArgs[0]);
                convertType(gaSam, instArgs[0], fieldType);
                gaSam.putStatic(ownerType, key.implName(), fieldType);
            } else { // H_PUTFIELD
                gaSam.loadArg(0);
                convertType(gaSam, samArgs[0], instArgs[0]);
                convertType(gaSam, instArgs[0], ownerType);
                gaSam.loadArg(1);
                convertType(gaSam, samArgs[1], instArgs[1]);
                convertType(gaSam, instArgs[1], fieldType);
                gaSam.putField(ownerType, key.implName(), fieldType);
            }
        } else {
            // Method Invocation: virtual, static, special, interface
            boolean isStatic = tag == Opcodes.H_INVOKESTATIC;
            if (!isStatic) {
                // Load receiver
                gaSam.loadArg(0);
                convertType(gaSam, samArgs[0], instArgs[0]);
                convertType(gaSam, instArgs[0], Type.getObjectType(key.implOwner()));

                // Load remaining arguments
                for (int i = 0; i < implArgs.length; i++) {
                    gaSam.loadArg(i + 1);
                    convertType(gaSam, samArgs[i + 1], instArgs[i + 1]);
                    convertType(gaSam, instArgs[i + 1], implArgs[i]);
                }
            } else {
                // Static method arguments
                for (int i = 0; i < implArgs.length; i++) {
                    gaSam.loadArg(i);
                    convertType(gaSam, samArgs[i], instArgs[i]);
                    convertType(gaSam, instArgs[i], implArgs[i]);
                }
            }

            int invokeOpcode = switch (tag) {
                case Opcodes.H_INVOKEVIRTUAL -> Opcodes.INVOKEVIRTUAL;
                case Opcodes.H_INVOKESTATIC -> Opcodes.INVOKESTATIC;
                case Opcodes.H_INVOKESPECIAL -> Opcodes.INVOKESPECIAL;
                case Opcodes.H_INVOKEINTERFACE -> Opcodes.INVOKEINTERFACE;
                default -> throw new IllegalArgumentException("Unsupported tag: " + tag);
            };
            boolean isInterface = tag == Opcodes.H_INVOKEINTERFACE;
            gaSam.visitMethodInsn(invokeOpcode, key.implOwner(), key.implName(), key.implDesc(), isInterface);
        }

        // Return value conversion
        Type implRetType = Type.getReturnType(key.implDesc());
        if (tag == Opcodes.H_NEWINVOKESPECIAL) {
            implRetType = Type.getObjectType(key.implOwner());
        } else if (tag == Opcodes.H_GETFIELD || tag == Opcodes.H_GETSTATIC) {
            implRetType = Type.getType(key.implDesc());
        } else if (tag == Opcodes.H_PUTFIELD || tag == Opcodes.H_PUTSTATIC) {
            implRetType = Type.VOID_TYPE;
        }

        Type samRetType = Type.getReturnType(samDesc);

        if (implRetType.getSort() == Type.VOID) {
            if (samRetType.getSort() != Type.VOID) {
                gaSam.push((String) null);
            }
        } else {
            Type instRetType = Type.getReturnType(key.instantiatedMethodType());
            convertType(gaSam, implRetType, instRetType);
            convertType(gaSam, instRetType, samRetType);
        }

        gaSam.returnValue();
        gaSam.endMethod();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void convertType(GeneratorAdapter ga, Type from, Type to) {
        if (from.equals(to)) {
            return;
        }
        if (isPrimitive(from) && !isPrimitive(to)) {
            ga.box(from);
            if (!to.equals(getBoxedType(from))) {
                ga.checkCast(to);
            }
        } else if (!isPrimitive(from) && isPrimitive(to)) {
            ga.checkCast(getBoxedType(to));
            ga.unbox(to);
        } else if (isPrimitive(from) && isPrimitive(to)) {
            ga.cast(from, to);
        } else {
            ga.checkCast(to);
        }
    }

    private static boolean isPrimitive(Type type) {
        int sort = type.getSort();
        return sort >= Type.BOOLEAN && sort <= Type.DOUBLE;
    }

    private static Type getBoxedType(Type primitiveType) {
        return switch (primitiveType.getSort()) {
            case Type.BOOLEAN -> Type.getType(Boolean.class);
            case Type.BYTE -> Type.getType(Byte.class);
            case Type.CHAR -> Type.getType(Character.class);
            case Type.SHORT -> Type.getType(Short.class);
            case Type.INT -> Type.getType(Integer.class);
            case Type.LONG -> Type.getType(Long.class);
            case Type.FLOAT -> Type.getType(Float.class);
            case Type.DOUBLE -> Type.getType(Double.class);
            default -> throw new IllegalArgumentException("Not a primitive: " + primitiveType);
        };
    }
}
