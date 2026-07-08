package com.yourorg.jmoa.plugin.reducer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Audits the raw reducer by independently normalizing original and reduced
 * class bytes. The normalization removes only LVT/LVTT Code sub-attributes; any
 * remaining byte drift means a non-target structure changed and the reducer is
 * rejected.
 */
public final class RawReducerBytePreservationAuditor {

    public RawReducerClassAuditRecord audit(
        String artifact,
        String entryName,
        ClassDebugMetadata before,
        ClassDebugMetadata after,
        byte[] original,
        byte[] reduced
    ) {
        if (after.localVariableTableBytes() != 0 || after.localVariableTypeTableBytes() != 0) {
            throw new IllegalStateException("Raw reducer left local-variable debug metadata: " + entryName);
        }
        byte[] normalizedOriginal = new Normalizer(original).normalize();
        byte[] normalizedReduced = new Normalizer(reduced).normalize();
        if (!Arrays.equals(normalizedOriginal, normalizedReduced)) {
            throw new IllegalStateException("Raw reducer changed non-target classfile bytes: " + entryName);
        }
        List<String> removed = new ArrayList<>();
        if (before.localVariableTableBytes() > 0) {
            removed.add("LocalVariableTable");
        }
        if (before.localVariableTypeTableBytes() > 0) {
            removed.add("LocalVariableTypeTable");
        }
        return new RawReducerClassAuditRecord(
            before.className(),
            artifact,
            entryName,
            ReducerEngine.RAW.propertyValue(),
            sha256(original),
            sha256(reduced),
            original.length,
            reduced.length,
            removed,
            true,
            before.lineNumberTableBytes() == after.lineNumberTableBytes(),
            before.sourceFileAttributeBytes() == after.sourceFileAttributeBytes(),
            before.stackMapTableBytes() == after.stackMapTableBytes(),
            before.annotationAttributeBytes() == after.annotationAttributeBytes(),
            before.signatureAttributeBytes() == after.signatureAttributeBytes(),
            before.bootstrapMethodsAttributeBytes() == after.bootstrapMethodsAttributeBytes(),
            "PRESERVED_EXCEPT_LVT_LVTT"
        );
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static final class Normalizer {
        private final byte[] bytes;
        private int offset;
        private String[] utf8;

        Normalizer(byte[] bytes) {
            this.bytes = bytes;
        }

        byte[] normalize() {
            long magic = readU4At(0);
            if (magic != 0xCAFEBABEL) {
                throw new IllegalArgumentException("Invalid classfile magic");
            }
            offset = 8;
            int cpCount = readU2();
            utf8 = new String[cpCount];
            parseConstantPool(cpCount);
            skip(6);
            int interfaces = readU2();
            skip(interfaces * 2L);
            int fields = readU2();
            for (int i = 0; i < fields; i++) {
                skipMember();
            }
            int methodsCountOffset = offset;
            int methods = readU2();
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length);
            out.write(bytes, 0, methodsCountOffset);
            writeU2(out, methods);
            for (int i = 0; i < methods; i++) {
                writeNormalizedMethod(out);
            }
            out.write(bytes, offset, bytes.length - offset);
            return out.toByteArray();
        }

        private void parseConstantPool(int cpCount) {
            for (int i = 1; i < cpCount; i++) {
                int tag = readU1();
                switch (tag) {
                    case 1 -> {
                        int length = readU2();
                        utf8[i] = new String(bytes, offset, length, StandardCharsets.UTF_8);
                        skip(length);
                    }
                    case 3, 4 -> skip(4);
                    case 5, 6 -> {
                        skip(8);
                        i++;
                    }
                    case 7, 8, 16, 19, 20 -> skip(2);
                    case 9, 10, 11, 12, 17, 18 -> skip(4);
                    case 15 -> skip(3);
                    default -> throw new IllegalArgumentException("Unsupported constant-pool tag " + tag);
                }
            }
        }

        private void skipMember() {
            skip(6);
            int attributes = readU2();
            for (int i = 0; i < attributes; i++) {
                skipAttribute();
            }
        }

        private void skipAttribute() {
            skip(2);
            long length = readU4();
            skip(length);
        }

        private void writeNormalizedMethod(ByteArrayOutputStream out) {
            int methodStart = offset;
            skip(6);
            int attributesCountOffset = offset;
            int attributes = readU2();
            ByteArrayOutputStream attributeBytes = new ByteArrayOutputStream();
            for (int i = 0; i < attributes; i++) {
                int attributeStart = offset;
                int nameIndex = readU2();
                long length = readU4();
                int infoOffset = offset;
                int attributeEnd = infoOffset + checkedLength(length);
                String name = utf8[nameIndex];
                if ("Code".equals(name)) {
                    attributeBytes.writeBytes(normalizeCodeAttribute(nameIndex, infoOffset, checkedLength(length)));
                } else {
                    attributeBytes.write(bytes, attributeStart, attributeEnd - attributeStart);
                }
                offset = attributeEnd;
            }
            out.write(bytes, methodStart, attributesCountOffset - methodStart);
            writeU2(out, attributes);
            out.writeBytes(attributeBytes.toByteArray());
        }

        private byte[] normalizeCodeAttribute(int nameIndex, int infoOffset, int length) {
            int cursor = infoOffset;
            cursor += 2;
            cursor += 2;
            long codeLength = readU4At(cursor);
            cursor += 4 + checkedLength(codeLength);
            int exceptionTableLength = readU2At(cursor);
            cursor += 2 + exceptionTableLength * 8;
            int nestedAttributesCountOffset = cursor;
            int nestedAttributes = readU2At(cursor);
            cursor += 2;
            ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
            int keptNestedAttributes = 0;
            boolean changed = false;
            for (int i = 0; i < nestedAttributes; i++) {
                int nestedStart = cursor;
                int nestedNameIndex = readU2At(cursor);
                long nestedLength = readU4At(cursor + 2);
                int nestedEnd = cursor + 6 + checkedLength(nestedLength);
                String nestedName = utf8[nestedNameIndex];
                if (isLocalVariableDebugAttribute(nestedName)) {
                    changed = true;
                } else {
                    nestedBytes.write(bytes, nestedStart, nestedEnd - nestedStart);
                    keptNestedAttributes++;
                }
                cursor = nestedEnd;
            }
            int infoEnd = infoOffset + length;
            if (cursor != infoEnd) {
                throw new IllegalArgumentException("Malformed Code attribute");
            }
            if (!changed) {
                ByteArrayOutputStream original = new ByteArrayOutputStream(length + 6);
                writeU2(original, nameIndex);
                writeU4(original, length);
                original.write(bytes, infoOffset, length);
                return original.toByteArray();
            }
            ByteArrayOutputStream info = new ByteArrayOutputStream(length);
            info.write(bytes, infoOffset, nestedAttributesCountOffset - infoOffset);
            writeU2(info, keptNestedAttributes);
            info.writeBytes(nestedBytes.toByteArray());
            byte[] newInfo = info.toByteArray();

            ByteArrayOutputStream attribute = new ByteArrayOutputStream(newInfo.length + 6);
            writeU2(attribute, nameIndex);
            writeU4(attribute, newInfo.length);
            attribute.writeBytes(newInfo);
            return attribute.toByteArray();
        }

        private static boolean isLocalVariableDebugAttribute(String name) {
            return "LocalVariableTable".equals(name) || "LocalVariableTypeTable".equals(name);
        }

        private int readU1() {
            return bytes[offset++] & 0xFF;
        }

        private int readU2() {
            int value = readU2At(offset);
            offset += 2;
            return value;
        }

        private long readU4() {
            long value = readU4At(offset);
            offset += 4;
            return value;
        }

        private int readU2At(int at) {
            return ((bytes[at] & 0xFF) << 8) | (bytes[at + 1] & 0xFF);
        }

        private long readU4At(int at) {
            return ((long) (bytes[at] & 0xFF) << 24)
                | ((long) (bytes[at + 1] & 0xFF) << 16)
                | ((long) (bytes[at + 2] & 0xFF) << 8)
                | (long) (bytes[at + 3] & 0xFF);
        }

        private void skip(long count) {
            offset += checkedLength(count);
        }

        private static int checkedLength(long length) {
            if (length < 0 || length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Unsupported classfile length: " + length);
            }
            return (int) length;
        }

        private static void writeU2(ByteArrayOutputStream out, int value) {
            out.write((value >>> 8) & 0xFF);
            out.write(value & 0xFF);
        }

        private static void writeU4(ByteArrayOutputStream out, long value) {
            out.write((int) ((value >>> 24) & 0xFF));
            out.write((int) ((value >>> 16) & 0xFF));
            out.write((int) ((value >>> 8) & 0xFF));
            out.write((int) (value & 0xFF));
        }
    }
}
