package com.yourorg.jmoa.plugin.reducer;

import java.nio.charset.StandardCharsets;

public final class ClassDebugMetadataInspector {

    public ClassDebugMetadata inspect(byte[] bytes) {
        Parser parser = new Parser(bytes);
        return parser.parse();
    }

    private static final class Parser {
        private final byte[] bytes;
        private int offset;
        private String[] utf8;
        private int[] classNameIndex;
        private long lvt;
        private long lvtt;
        private long line;
        private long stackMap;
        private long annotations;
        private long signature;
        private long bootstrap;
        private long sourceFile;

        Parser(byte[] bytes) {
            this.bytes = bytes;
        }

        ClassDebugMetadata parse() {
            long magic = readU4();
            if (magic != 0xCAFEBABEL) {
                throw new IllegalArgumentException("Invalid classfile magic");
            }
            skip(4);
            int cpCount = readU2();
            utf8 = new String[cpCount];
            classNameIndex = new int[cpCount];
            parseConstantPool(cpCount);
            skip(2);
            int thisClass = readU2();
            skip(2);
            int interfaces = readU2();
            skip(interfaces * 2L);
            int fields = readU2();
            for (int i = 0; i < fields; i++) {
                skipMember();
            }
            int methods = readU2();
            for (int i = 0; i < methods; i++) {
                parseMethod();
            }
            int classAttributes = readU2();
            for (int i = 0; i < classAttributes; i++) {
                ParsedAttribute attribute = parseAttributeHeader();
                addAttribute(attribute.name(), attribute.totalBytes());
                skip(attribute.length());
            }
            String className = "";
            if (thisClass > 0 && thisClass < classNameIndex.length) {
                int nameIndex = classNameIndex[thisClass];
                if (nameIndex > 0 && nameIndex < utf8.length) {
                    className = utf8[nameIndex].replace('/', '.');
                }
            }
            return new ClassDebugMetadata(
                className,
                bytes.length,
                lvt,
                lvtt,
                line,
                stackMap,
                annotations,
                signature,
                bootstrap,
                sourceFile
            );
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
                    case 7 -> classNameIndex[i] = readU2();
                    case 8, 16, 19, 20 -> skip(2);
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
                ParsedAttribute attribute = parseAttributeHeader();
                addAttribute(attribute.name(), attribute.totalBytes());
                skip(attribute.length());
            }
        }

        private void parseMethod() {
            skip(6);
            int attributes = readU2();
            for (int i = 0; i < attributes; i++) {
                ParsedAttribute attribute = parseAttributeHeader();
                addAttribute(attribute.name(), attribute.totalBytes());
                if ("Code".equals(attribute.name())) {
                    parseCodeAttribute(attribute.infoOffset(), attribute.length());
                }
                skip(attribute.length());
            }
        }

        private void parseCodeAttribute(int infoOffset, long length) {
            int cursor = infoOffset;
            cursor += 2;
            cursor += 2;
            long codeLength = readU4At(cursor);
            cursor += 4 + (int) codeLength;
            int exceptionTableLength = readU2At(cursor);
            cursor += 2 + exceptionTableLength * 8;
            int attributes = readU2At(cursor);
            cursor += 2;
            int max = infoOffset + (int) length;
            for (int i = 0; i < attributes && cursor + 6 <= max; i++) {
                String name = utf8[readU2At(cursor)];
                long nestedLength = readU4At(cursor + 2);
                addAttribute(name, 6 + nestedLength);
                cursor += 6 + (int) nestedLength;
            }
        }

        private ParsedAttribute parseAttributeHeader() {
            String name = utf8[readU2()];
            long length = readU4();
            return new ParsedAttribute(name, offset, length);
        }

        private void addAttribute(String name, long bytes) {
            if ("LocalVariableTable".equals(name)) {
                lvt += bytes;
            } else if ("LocalVariableTypeTable".equals(name)) {
                lvtt += bytes;
            } else if ("LineNumberTable".equals(name)) {
                line += bytes;
            } else if ("StackMapTable".equals(name)) {
                stackMap += bytes;
            } else if (name != null && (name.contains("Annotations") || "AnnotationDefault".equals(name))) {
                annotations += bytes;
            } else if ("Signature".equals(name)) {
                signature += bytes;
            } else if ("BootstrapMethods".equals(name)) {
                bootstrap += bytes;
            } else if ("SourceFile".equals(name)) {
                sourceFile += bytes;
            }
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
            offset += (int) count;
        }
    }

    private record ParsedAttribute(String name, int infoOffset, long length) {
        long totalBytes() {
            return 6 + length;
        }
    }
}
