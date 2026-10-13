package com.yourorg.jmoa.plugin.overlay;

/**
 * Typed universal mode enums used at the overlay boundary. Free-text mode
 * strings from Mojo parameters are parsed once into these enums; the overlay
 * decision points compare enum values, so an unsupported mode cannot
 * propagate into the engine as an arbitrary string.
 */
public final class PluginModeEnums {

    public enum Tier1AdapterMode {
        RUNTIME, DIRECT;

        public static Tier1AdapterMode parse(String value) {
            return switch (value == null ? "" : value.trim().toUpperCase()) {
                case "RUNTIME" -> RUNTIME;
                case "DIRECT" -> DIRECT;
                default -> throw new IllegalArgumentException("Unsupported tier1AdapterMode: " + value);
            };
        }
    }

    public enum Tier2ConsolidationMode {
        CURRENT, PACKAGE, PACKAGE_SAM, PACKAGE_SIGNATURE, ROOT_PACKAGE_SIGNATURE;

        public static Tier2ConsolidationMode parse(String value) {
            return switch (value == null ? "" : value.trim().toUpperCase()) {
                case "CURRENT" -> CURRENT;
                case "PACKAGE" -> PACKAGE;
                case "PACKAGE_SAM" -> PACKAGE_SAM;
                case "PACKAGE_SIGNATURE" -> PACKAGE_SIGNATURE;
                case "ROOT_PACKAGE_SIGNATURE" -> ROOT_PACKAGE_SIGNATURE;
                default -> throw new IllegalArgumentException("Unsupported tier2AdapterConsolidation: " + value);
            };
        }
    }

    public enum ReducerOperationMode {
        REPACK_ONLY("repack-only"), STRIP("strip");

        private final String wireName;

        ReducerOperationMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ReducerOperationMode parse(String value) {
            for (ReducerOperationMode mode : values()) {
                if (mode.wireName.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported reducer operation: " + value);
        }
    }

    public enum ReducerJarLayoutMode {
        CURRENT("current"), STABLE_RAW_COPY("stable-raw-copy");

        private final String wireName;

        ReducerJarLayoutMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ReducerJarLayoutMode parse(String value) {
            for (ReducerJarLayoutMode mode : values()) {
                if (mode.wireName.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported reducer JAR layout: " + value);
        }
    }

    public enum PackagingMode {
        EXPLODED_BOOT_APP, FAT_BOOT_JAR;

        public static PackagingMode parse(String value) {
            return switch (value == null ? "" : value.trim().toUpperCase()) {
                case "EXPLODED_BOOT_APP" -> EXPLODED_BOOT_APP;
                case "FAT_BOOT_JAR" -> FAT_BOOT_JAR;
                default -> throw new IllegalArgumentException("Unsupported packaging mode: " + value);
            };
        }
    }

    private PluginModeEnums() {
    }
}
