package com.yourorg.jmoa.plugin.reducer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Exact classfile-component hashes used to explain the raw preservation gate. */
final class ClassfileComponentHasher {

    ClassfileComponentDigests hash(byte[] bytes) {
        return new Parser(bytes).parse();
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static final class Parser {
        private final byte[] bytes;
        private final ByteArrayOutputStream bytecode = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stackMaps = new ByteArrayOutputStream();
        private final ByteArrayOutputStream bootstrapMethods = new ByteArrayOutputStream();
        private final ByteArrayOutputStream annotations = new ByteArrayOutputStream();
        private int offset;
        private String[] utf8;

        Parser(byte[] bytes) {
            this.bytes = bytes;
        }

        ClassfileComponentDigests parse() {
            if (readU4At(0) != 0xCAFEBABEL) {
                throw new IllegalArgumentException("Invalid classfile magic");
            }
            offset = 8;
            int constantPoolStart = offset;
            int cpCount = readU2();
            utf8 = new String[cpCount];
            parseConstantPool(cpCount);
            int constantPoolEnd = offset;
            skip(6);
            int interfaces = readU2();
            skip(interfaces * 2L);
            parseMembers(false);
            parseMembers(true);
            parseAttributes(false);
            if (offset != bytes.length) {
                throw new IllegalArgumentException("Trailing or malformed classfile bytes");
            }
            return new ClassfileComponentDigests(
                sha256(slice(constantPoolStart, constantPoolEnd)),
                sha256(bytecode.toByteArray()),
                sha256(stackMaps.toByteArray()),
                sha256(bootstrapMethods.toByteArray()),
                sha256(annotations.toByteArray())
            );
        }

        private void parseConstantPool(int cpCount) {
            for (int i = 1; i < cpCount; i++) {
                int tag = readU1();
                switch (tag) {
                    case 1 -> {
                        int length = readU2();
                        require(length);
                        utf8[i] = new String(bytes, offset, length, StandardCharsets.UTF_8);
                        skip(length);
                    }
                    case 3, 4 -> skip(4);
                    case 5, 6 -> { skip(8); i++; }
                    case 7, 8, 16, 19, 20 -> skip(2);
                    case 9, 10, 11, 12, 17, 18 -> skip(4);
                    case 15 -> skip(3);
                    default -> throw new IllegalArgumentException("Unsupported constant-pool tag " + tag);
                }
            }
        }

        private void parseMembers(boolean methods) {
            int count = readU2();
            for (int i = 0; i < count; i++) {
                int memberStart = offset;
                skip(6);
                if (methods) {
                    append(bytecode, bytes, memberStart, offset);
                }
                parseAttributes(methods);
            }
        }

        private void parseAttributes(boolean methodAttributes) {
            int count = readU2();
            for (int i = 0; i < count; i++) {
                int start = offset;
                int nameIndex = readU2();
                int length = checkedLength(readU4());
                int infoStart = offset;
                int end = infoStart + length;
                require(length);
                String name = utf8[nameIndex];
                if (methodAttributes && "Code".equals(name)) {
                    parseCode(infoStart, end);
                } else {
                    collectNamedAttribute(name, start, end);
                }
                offset = end;
            }
        }

        private void parseCode(int infoStart, int end) {
            int cursor = infoStart;
            requireAt(cursor, 8);
            int codeLength = checkedLength(readU4At(cursor + 4));
            int codeEnd = cursor + 8 + codeLength;
            requireAt(cursor, 8 + codeLength + 2);
            int exceptionCount = readU2At(codeEnd);
            int exceptionEnd = codeEnd + 2 + exceptionCount * 8;
            requireAt(codeEnd, 2 + exceptionCount * 8 + 2);
            append(bytecode, bytes, infoStart, exceptionEnd);
            int nestedCount = readU2At(exceptionEnd);
            cursor = exceptionEnd + 2;
            for (int i = 0; i < nestedCount; i++) {
                requireAt(cursor, 6);
                int start = cursor;
                int nameIndex = readU2At(cursor);
                int length = checkedLength(readU4At(cursor + 2));
                int nestedEnd = cursor + 6 + length;
                requireAt(cursor, 6 + length);
                collectNamedAttribute(utf8[nameIndex], start, nestedEnd);
                cursor = nestedEnd;
            }
            if (cursor != end) {
                throw new IllegalArgumentException("Malformed Code attribute");
            }
        }

        private void collectNamedAttribute(String name, int start, int end) {
            if ("StackMapTable".equals(name)) {
                appendFramed(stackMaps, bytes, start, end);
            }
            if ("BootstrapMethods".equals(name)) {
                appendFramed(bootstrapMethods, bytes, start, end);
            }
            if (name != null && name.contains("Annotation")) {
                appendFramed(annotations, bytes, start, end);
            }
        }

        private byte[] slice(int start, int end) {
            byte[] result = new byte[end - start];
            System.arraycopy(bytes, start, result, 0, result.length);
            return result;
        }

        private static void appendFramed(ByteArrayOutputStream out, byte[] source, int start, int end) {
            int length = end - start;
            out.write((length >>> 24) & 0xff);
            out.write((length >>> 16) & 0xff);
            out.write((length >>> 8) & 0xff);
            out.write(length & 0xff);
            append(out, source, start, end);
        }

        private static void append(ByteArrayOutputStream out, byte[] source, int start, int end) {
            out.write(source, start, end - start);
        }

        private int readU1() { require(1); return bytes[offset++] & 0xff; }
        private int readU2() { int value = readU2At(offset); offset += 2; return value; }
        private long readU4() { long value = readU4At(offset); offset += 4; return value; }
        private int readU2At(int at) { requireAt(at, 2); return ((bytes[at] & 0xff) << 8) | (bytes[at + 1] & 0xff); }
        private long readU4At(int at) {
            requireAt(at, 4);
            return ((long) (bytes[at] & 0xff) << 24) | ((long) (bytes[at + 1] & 0xff) << 16)
                | ((long) (bytes[at + 2] & 0xff) << 8) | (bytes[at + 3] & 0xffL);
        }
        private void skip(long count) { int length = checkedLength(count); require(length); offset += length; }
        private void require(int length) { requireAt(offset, length); }
        private void requireAt(int at, int length) {
            if (at < 0 || length < 0 || at > bytes.length - length) {
                throw new IllegalArgumentException("Malformed classfile bounds");
            }
        }
        private static int checkedLength(long length) {
            if (length < 0 || length > Integer.MAX_VALUE) throw new IllegalArgumentException("Unsupported classfile length");
            return (int) length;
        }
    }
}
