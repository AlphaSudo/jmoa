package jmoa.runtime;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class JmoaFactoryContractTest {

    static final class GeneratedRuntimeLoader extends ClassLoader {
        GeneratedRuntimeLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    @Test
    void exposesExpectedFactoryMethodSignatures() throws Exception {
        assertFactory("createFunction", Function.class, "(I)Ljava/util/function/Function;");
        assertFactory("createPredicate", Predicate.class, "(I)Ljava/util/function/Predicate;");
        assertFactory("createSupplier", Supplier.class, "(I)Ljava/util/function/Supplier;");
        assertFactory("createConsumer", Consumer.class, "(I)Ljava/util/function/Consumer;");
        assertFactory("createBiConsumer", BiConsumer.class, "(I)Ljava/util/function/BiConsumer;");
    }

    @Test
    void prefersGeneratedRuntimeFactoryWhenAvailable() throws Exception {
        Function<Object, Object> generatedFunction = value -> "generated-" + value;
        GeneratedRuntimeLoader loader = new GeneratedRuntimeLoader(getClass().getClassLoader());
        Class<?> generatedRuntime = loader.define("jmoa.runtime.JmoaRuntime", generatedRuntimeClassBytes());
        generatedRuntime.getField("FUNCTION_CACHE").set(null, new Function[]{generatedFunction});

        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        resetGeneratedRuntimeBridge();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            Function<Object, Object> resolved = JmoaFactory.createFunction(0);
            assertSame(generatedFunction, resolved);
            assertEquals("generated-patient", resolved.apply("patient"));
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            resetGeneratedRuntimeBridge();
            JmoaRuntimeSupport.resetForTests();
        }
    }

    private static void assertFactory(String methodName, Class<?> returnType, String expectedDescriptor) throws Exception {
        Method method = JmoaFactory.class.getMethod(methodName, int.class);
        assertEquals(returnType, method.getReturnType());
        assertEquals(expectedDescriptor, Type.getMethodDescriptor(method));
    }

    private static void resetGeneratedRuntimeBridge() throws Exception {
        Class<?> bridge = Class.forName("jmoa.runtime.JmoaFactory$GeneratedRuntimeBridge");
        for (String fieldName : new String[]{
            "resolved", "createFunction", "createPredicate", "createSupplier", "createConsumer", "createBiConsumer"
        }) {
            Field field = bridge.getDeclaredField(fieldName);
            field.setAccessible(true);
            if (field.getType() == boolean.class) {
                field.setBoolean(null, false);
            } else {
                field.set(null, null);
            }
        }
    }

    private static byte[] generatedRuntimeClassBytes() {
        String owner = "jmoa/runtime/JmoaRuntime";
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "FUNCTION_CACHE", "[Ljava/util/function/Function;", null, null).visitEnd();

        MethodVisitor createFunction = cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "createFunction",
            "(I)Ljava/util/function/Function;",
            null,
            null
        );
        createFunction.visitCode();
        createFunction.visitFieldInsn(Opcodes.GETSTATIC, owner, "FUNCTION_CACHE", "[Ljava/util/function/Function;");
        createFunction.visitVarInsn(Opcodes.ILOAD, 0);
        createFunction.visitInsn(Opcodes.AALOAD);
        createFunction.visitTypeInsn(Opcodes.CHECKCAST, "java/util/function/Function");
        createFunction.visitInsn(Opcodes.ARETURN);
        createFunction.visitMaxs(2, 1);
        createFunction.visitEnd();

        for (String methodName : new String[]{"createPredicate", "createSupplier", "createConsumer", "createBiConsumer"}) {
            MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                methodName,
                "(I)" + switch (methodName) {
                    case "createPredicate" -> "Ljava/util/function/Predicate;";
                    case "createSupplier" -> "Ljava/util/function/Supplier;";
                    case "createConsumer" -> "Ljava/util/function/Consumer;";
                    default -> "Ljava/util/function/BiConsumer;";
                },
                null,
                null
            );
            mv.visitCode();
            mv.visitInsn(Opcodes.ACONST_NULL);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
        return cw.toByteArray();
    }
}
