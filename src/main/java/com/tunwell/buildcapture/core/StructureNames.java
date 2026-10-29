package com.tunwell.buildcapture.core;

import java.util.Locale;

/**
 * Rules for structure ids and file names, kept free of Minecraft classes so they
 * can be unit-tested and reused by a port without change.
 *
 * <p>1.16.5's ResourceLocation accepts {@code .} and {@code ..} segments, so
 * validation cannot be left to it: anything that becomes a file path must pass
 * {@link #isSafe(String, String)} or {@link #sanitizeFileName(String)} first.</p>
 */
public final class StructureNames {
    /** Longest id path the importer keeps when it turns a file name into an id. */
    public static final int MAX_FILE_NAME = 60;
    private static final String STRUCTURES_PREFIX = "structures/";
    private static final String NBT_SUFFIX = ".nbt";

    private StructureNames() {
    }

    /**
     * Whether every segment names an ordinary folder or file. Empty segments
     * (double slashes) and {@code .}/{@code ..} would escape the structures
     * folder when resolved against it.
     */
    public static boolean isSafe(String namespace, String path) {
        if (namespace == null || namespace.isEmpty()) {
            return false;
        }
        if (!isSafeSegment(namespace)) {
            return false;
        }
        if (path == null || path.isEmpty()) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (!isSafeSegment(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSafeSegment(String segment) {
        return !segment.isEmpty() && !".".equals(segment) && !"..".equals(segment);
    }

    /**
     * Turns a client-supplied file name into a datapack-safe relative path:
     * lower-case, only {@code [a-z0-9_.-/]}, no dot segments, at most
     * {@link #MAX_FILE_NAME} characters.
     */
    public static String sanitizeFileName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(NBT_SUFFIX)) {
            name = name.substring(0, name.length() - NBT_SUFFIX.length());
        }
        StringBuilder result = new StringBuilder(name.length());
        for (String part : name.split("/", -1)) {
            StringBuilder segment = new StringBuilder(part.length());
            for (int index = 0; index < part.length(); index++) {
                char character = part.charAt(index);
                boolean valid = character >= 'a' && character <= 'z'
                        || character >= '0' && character <= '9'
                        || character == '_' || character == '-' || character == '.';
                segment.append(valid ? character : '_');
            }
            trimUnderscores(segment);
            String text = segment.toString();
            if (text.isEmpty() || ".".equals(text) || "..".equals(text)) {
                continue;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(text);
            if (result.length() >= MAX_FILE_NAME) {
                break;
            }
        }
        return result.length() > MAX_FILE_NAME
                ? result.substring(0, MAX_FILE_NAME)
                : result.toString();
    }

    private static void trimUnderscores(StringBuilder segment) {
        while (segment.length() > 0 && segment.charAt(0) == '_') {
            segment.deleteCharAt(0);
        }
        while (segment.length() > 0 && segment.charAt(segment.length() - 1) == '_') {
            segment.deleteCharAt(segment.length() - 1);
        }
    }

    /**
     * Maps {@code <namespace>/structures/<path>.nbt} back to
     * {@code namespace:path}, or returns {@code null} when the file is not laid
     * out like a structure. Callers still validate the result with
     * {@link #isSafe(String, String)} after parsing it.
     */
    public static String idFromRelative(String relative) {
        if (relative == null) {
            return null;
        }
        int slash = relative.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String namespace = relative.substring(0, slash);
        String rest = relative.substring(slash + 1);
        if (!rest.startsWith(STRUCTURES_PREFIX) || !rest.endsWith(NBT_SUFFIX)) {
            return null;
        }
        return namespace + ":" + rest.substring(
                STRUCTURES_PREFIX.length(), rest.length() - NBT_SUFFIX.length());
    }
}
